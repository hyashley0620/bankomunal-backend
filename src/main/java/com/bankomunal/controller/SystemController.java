package com.bankomunal.controller;

import com.bankomunal.dto.response.MessageResponse;
import com.bankomunal.entity.Account;
import com.bankomunal.entity.Transaction;
import com.bankomunal.entity.TransactionLimit;
import com.bankomunal.entity.User;
import com.bankomunal.repository.AccountRepository;
import com.bankomunal.repository.TransactionLimitRepository;
import com.bankomunal.repository.TransactionRepository;
import com.bankomunal.service.AccountService;
import com.bankomunal.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.*;

/**
 * GET/PUT /api/admin/sistema
 * Expone la configuración global del sistema como un objeto plano
 * para que configuracion.html (tab Sistema) pueda leer y guardar
 * los parámetros operativos de Bankomunal.
 */
@RestController
@RequestMapping("/api/admin/sistema")
@RequiredArgsConstructor
@PreAuthorize("hasRole('admin')")
public class SystemController {

    private final SystemConfigService configService;
    private final TransactionLimitRepository transactionLimitRepository;
    private final AccountService accountService;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    // ── GET — leer toda la configuración como DTO plano ───────────────────────
    @GetMapping
    public ResponseEntity<Map<String, Object>> getSistema() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nombreCooperativa", cfg("nombre_cooperativa", "Bankomunal"));
        result.put("emailSoporte", cfg("email_soporte", "soporte@bankomunal.com"));
        result.put("tasaInteresMinima", toDouble(cfg("tasa_min", "1.5")));
        result.put("tasaInteresMaxima", toDouble(cfg("tasa_max", "3.0")));
        result.put("montoMaximoPrestamo", toLong(cfg("monto_max", "5000000")));
        result.put("plazoMaximoMeses", toInt(cfg("plazo_max", "60")));
        result.put("modoMantenimiento", "true".equalsIgnoreCase(cfg("mantenimiento", "false")));
        result.put("registroHabilitado", !"false".equalsIgnoreCase(cfg("registro", "true")));
        result.put("notificacionesAdmin", !"false".equalsIgnoreCase(cfg("notif_admin", "true")));
        return ResponseEntity.ok(result);
    }

    // ── PUT — guardar configuración ───────────────────────────────────────────
    @PutMapping
    public ResponseEntity<MessageResponse> updateSistema(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal User admin) {

        save("nombre_cooperativa", str(body.get("nombreCooperativa")), admin);
        save("email_soporte", str(body.get("emailSoporte")), admin);
        save("tasa_min", str(body.get("tasaInteresMinima")), admin);
        save("tasa_max", str(body.get("tasaInteresMaxima")), admin);
        save("monto_max", str(body.get("montoMaximoPrestamo")), admin);
        save("plazo_max", str(body.get("plazoMaximoMeses")), admin);
        save("mantenimiento", str(body.get("modoMantenimiento")), admin);
        save("registro", str(body.get("registroHabilitado")), admin);
        save("notif_admin", str(body.get("notificacionesAdmin")), admin);

        return ResponseEntity.ok(new MessageResponse("Configuración del sistema actualizada correctamente."));
    }

    // ── Límites de transacción (transaction_limits) ───────────────────────────
    // GET /api/admin/sistema/limites — todos los límites configurados, para la
    // pantalla de configuración. Se hacen cumplir de verdad en
    // TransactionLimitService (transferencias, pagos de servicios, aportes al
    // fondo común) — este endpoint solo administra la tabla.
    @GetMapping("/limites")
    public ResponseEntity<List<TransactionLimit>> getLimites() {
        return ResponseEntity.ok(transactionLimitRepository.findAll());
    }

    public static class LimiteRequest {
        public TransactionLimit.Scope scope;
        public Long scopeId;
        public String txType;
        public BigDecimal maxPerTransaction;
        public BigDecimal maxPerDay;
        public BigDecimal maxPerWeek;
    }

    // POST /api/admin/sistema/limites — crea un límite nuevo (global, por
    // grupo o por socio específico).
    @PostMapping("/limites")
    public ResponseEntity<TransactionLimit> crearLimite(
            @RequestBody LimiteRequest req, @AuthenticationPrincipal User admin) {
        TransactionLimit limite = TransactionLimit.builder()
                .scope(req.scope != null ? req.scope : TransactionLimit.Scope.global)
                .scopeId(req.scopeId)
                .txType(req.txType)
                .maxPerTransaction(req.maxPerTransaction != null ? req.maxPerTransaction : new BigDecimal("5000000"))
                .maxPerDay(req.maxPerDay != null ? req.maxPerDay : new BigDecimal("10000000"))
                .maxPerWeek(req.maxPerWeek != null ? req.maxPerWeek : new BigDecimal("30000000"))
                .updatedBy(admin)
                .build();
        return ResponseEntity.ok(transactionLimitRepository.save(limite));
    }

    // PUT /api/admin/sistema/limites/{id} — edita un límite existente.
    @PutMapping("/limites/{id}")
    public ResponseEntity<TransactionLimit> editarLimite(
            @PathVariable Long id, @RequestBody LimiteRequest req, @AuthenticationPrincipal User admin) {
        TransactionLimit limite = transactionLimitRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Límite no encontrado."));
        if (req.maxPerTransaction != null)
            limite.setMaxPerTransaction(req.maxPerTransaction);
        if (req.maxPerDay != null)
            limite.setMaxPerDay(req.maxPerDay);
        if (req.maxPerWeek != null)
            limite.setMaxPerWeek(req.maxPerWeek);
        if (req.txType != null)
            limite.setTxType(req.txType);
        limite.setUpdatedBy(admin);
        return ResponseEntity.ok(transactionLimitRepository.save(limite));
    }

    // DELETE /api/admin/sistema/limites/{id}
    @DeleteMapping("/limites/{id}")
    public ResponseEntity<MessageResponse> eliminarLimite(@PathVariable Long id) {
        if (!transactionLimitRepository.existsById(id))
            throw new IllegalArgumentException("Límite no encontrado.");
        transactionLimitRepository.deleteById(id);
        return ResponseEntity.ok(new MessageResponse("Límite eliminado."));
    }

    // ── Capital de la entidad (cuenta `fund`) ──────────────────────────────────
    // De aquí sale el desembolso de cualquier préstamo institucional (no de un
    // grupo).

    @GetMapping("/fondo-entidad")
    public ResponseEntity<Map<String, Object>> getFondoEntidad() {
        Account fund = accountService.getCuentaFund();
        return ResponseEntity.ok(Map.of(
                "saldo", fund.getBalance(),
                "codigo", fund.getAccountCode()));
    }

    public static class CapitalizarRequest {
        public BigDecimal monto;
        public String motivo;
    }

    @PostMapping("/capitalizar-fondo")
    @Transactional
    public ResponseEntity<Map<String, Object>> capitalizarFondo(
            @RequestBody CapitalizarRequest req, @AuthenticationPrincipal User admin) {
        if (req.monto == null || req.monto.compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("El monto a capitalizar debe ser mayor a cero.");

        Account fund = accountService.getCuentaFund();
        fund.setBalance(fund.getBalance().add(req.monto));
        accountRepository.save(fund);

        String ref = "CAP-" + System.currentTimeMillis();
        transactionRepository.save(Transaction.builder()
                .txCode(ref)
                .type(Transaction.TxType.adjustment)
                .destinationAccount(fund)
                .amount(req.monto)
                .description("Capitalización del capital de la entidad"
                        + (req.motivo != null && !req.motivo.isBlank() ? " — " + req.motivo : ""))
                .reference(ref)
                .status(Transaction.TxStatus.completed)
                .createdBy(admin)
                .build());

        return ResponseEntity.ok(Map.of(
                "mensaje", "Capitalización aplicada.",
                "saldoNuevo", fund.getBalance()));
    }

    // ── helpers ────────────────────────────────────────────────────────────────
    private String cfg(String key, String def) {
        try {
            String v = configService.getValor(key);
            return v != null ? v : def;
        } catch (Exception e) {
            return def;
        }
    }

    private void save(String key, String val, User admin) {
        if (val == null)
            return;
        try {
            configService.actualizar(key, val, admin);
        } catch (Exception ignored) {
        }
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static double toDouble(String s) {
        try {
            return Double.parseDouble(s);
        } catch (Exception e) {
            return 0;
        }
    }

    private static long toLong(String s) {
        try {
            return (long) Double.parseDouble(s);
        } catch (Exception e) {
            return 0;
        }
    }

    private static int toInt(String s) {
        try {
            return (int) Double.parseDouble(s);
        } catch (Exception e) {
            return 0;
        }
    }
}