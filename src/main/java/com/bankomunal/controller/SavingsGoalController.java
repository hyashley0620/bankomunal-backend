package com.bankomunal.controller;

import com.bankomunal.entity.SavingsGoal;
import com.bankomunal.entity.User;
import com.bankomunal.service.SavingsGoalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GET/POST /api/metas-ahorro — metas de ahorro personales del socio.
 *
 * Importante: nunca se devuelve la entidad SavingsGoal directamente. Tiene
 * una relación @ManyToOne LAZY hacia User (y User carga sus roles en
 * automático) — Jackson no puede serializar ese proxy fuera de la
 * transacción (rompe con "No se pudieron cargar tus metas" en el frontend,
 * un LazyInitializationException del lado del servidor) y, aunque pudiera,
 * expondría datos sensibles del usuario como el hash de la contraseña. Cada
 * endpoint arma su propio Map con solo los campos que el frontend necesita.
 */
@RestController
@RequestMapping("/api/metas-ahorro")
@RequiredArgsConstructor
public class SavingsGoalController {

    private final SavingsGoalService savingsGoalService;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> misMetas(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(savingsGoalService.misMetas(user.getId()).stream()
                .map(this::toMap).toList());
    }

    public static class CrearMetaRequest {
        public String nombre;
        public BigDecimal montoMeta;
        public LocalDate fechaLimite;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> crear(
            @RequestBody CrearMetaRequest req, @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(toMap(
                savingsGoalService.crear(user, req.nombre, req.montoMeta, req.fechaLimite)));
    }

    @PostMapping("/{id}/aportar")
    public ResponseEntity<Map<String, Object>> aportar(
            @PathVariable Long id, @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal User user) {
        BigDecimal monto = new BigDecimal(String.valueOf(body.get("monto")));
        return ResponseEntity.ok(toMap(savingsGoalService.aportar(id, user, monto)));
    }

    @PatchMapping("/{id}/cancelar")
    public ResponseEntity<Map<String, Object>> cancelar(@PathVariable Long id, @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(toMap(savingsGoalService.cancelar(id, user)));
    }

    /** Retira a la cuenta individual el dinero de una meta cumplida o cancelada. */
    @PostMapping("/{id}/retirar")
    public ResponseEntity<Map<String, Object>> retirar(@PathVariable Long id, @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(toMap(savingsGoalService.retirar(id, user)));
    }

    private Map<String, Object> toMap(SavingsGoal g) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", g.getId());
        m.put("nombre", g.getNombre());
        m.put("montoMeta", g.getMontoMeta());
        m.put("montoActual", g.getMontoActual());
        m.put("fechaLimite", g.getFechaLimite() != null ? g.getFechaLimite().toString() : null);
        m.put("status", g.getStatus().name());
        return m;
    }
}