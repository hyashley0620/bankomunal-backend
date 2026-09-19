package com.bankomunal.controller;

import com.bankomunal.entity.CreditRiskHistory;
import com.bankomunal.entity.Loan;
import com.bankomunal.entity.User;
import com.bankomunal.repository.AccountRepository;
import com.bankomunal.repository.LoanRepository;
import com.bankomunal.repository.TransactionRepository;
import com.bankomunal.service.CreditRiskService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

/**
 * GET /api/salud-financiera — KPIs de salud financiera del usuario autenticado.
 * Soporta filtros opcionales ?mes=6&anio=2026.
 */
@RestController
@RequestMapping("/api/salud-financiera")
@RequiredArgsConstructor
public class FinancialHealthController {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final LoanRepository loanRepository;
    private final CreditRiskService creditRiskService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getSaludFinanciera(
            @RequestParam(required = false) Integer mes,
            @RequestParam(required = false) Integer anio,
            @AuthenticationPrincipal User user) {

        // Rango temporal
        LocalDateTime inicio, fin;
        if (mes != null && anio != null) {
            inicio = LocalDateTime.of(anio, mes, 1, 0, 0);
            fin = inicio.plusMonths(1).minusSeconds(1);
        } else {
            fin = LocalDateTime.now();
            inicio = fin.minusMonths(1);
        }

        Long uid = user.getId();

        // Ingresos y egresos del período — scoped al socio autenticado
        BigDecimal ingresos = transactionRepository.sumIngresosByUsuarioYRango(uid, inicio, fin);
        BigDecimal egresos = transactionRepository.sumEgresosByUsuarioYRango(uid, inicio, fin);
        BigDecimal ingresosReales = transactionRepository.sumIngresosRealesByUsuarioYRango(uid, inicio, fin);
        BigDecimal desembolsosPrestamo = transactionRepository.sumDesembolsosByUsuarioYRango(uid, inicio, fin);
        if (ingresos == null)
            ingresos = BigDecimal.ZERO;
        if (egresos == null)
            egresos = BigDecimal.ZERO;
        if (ingresosReales == null)
            ingresosReales = BigDecimal.ZERO;
        if (desembolsosPrestamo == null)
            desembolsosPrestamo = BigDecimal.ZERO;

        // Saldo total de cuentas activas del usuario
        BigDecimal saldo = accountRepository.findByOwnerUserId(user.getId()).stream()
                .map(a -> a.getBalance() != null ? a.getBalance() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Deuda activa (préstamos activos del usuario)
        List<Loan> misPrestamos = loanRepository.findByBorrowerUserId(user.getId());
        BigDecimal deuda = misPrestamos.stream()
                .filter(l -> l.getStatus() == Loan.LoanStatus.active)
                .map(l -> l.getSaldoPendiente() != null ? l.getSaldoPendiente() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Cuotas mensuales activas
        BigDecimal cuotasMensuales = misPrestamos.stream()
                .filter(l -> l.getStatus() == Loan.LoanStatus.active)
                .map(l -> l.getCuotaMensual() != null ? l.getCuotaMensual() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Puntaje de eficiencia de flujo de caja del período (0-100) — informativo,
        // NO es el score de riesgo crediticio.
        BigDecimal totalFlujo = ingresos.add(egresos);
        int eficiencia = 0;
        if (totalFlujo.compareTo(BigDecimal.ZERO) > 0) {
            eficiencia = ingresos.subtract(egresos)
                    .divide(totalFlujo, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .max(BigDecimal.ZERO).min(BigDecimal.valueOf(100))
                    .intValue();
        }

        // Capacidad de pago = ingresos - egresos - cuotas activas
        BigDecimal capacidadPago = ingresos.subtract(egresos).subtract(cuotasMensuales)
                .max(BigDecimal.ZERO);

        String nivel;
        if (eficiencia >= 70)
            nivel = "Excelente";
        else if (eficiencia >= 40)
            nivel = "Regular";
        else
            nivel = "Crítico";

        // Score de riesgo crediticio REAL — el mismo que ve un admin
        // (CreditRiskService), calculado con el historial de pagos a
        // tiempo/atrasados del socio. Escala 300-850.
        CreditRiskHistory riesgoActual = creditRiskService.getScoreActual(uid);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("puntaje", eficiencia);
        resp.put("nivel", nivel);
        resp.put("totalAhorro", saldo);
        resp.put("totalDeuda", deuda);
        resp.put("capacidadPago", capacidadPago);
        resp.put("ingresos", ingresos);
        resp.put("ingresosReales", ingresosReales);
        resp.put("desembolsosPrestamo", desembolsosPrestamo);
        resp.put("egresos", egresos);
        resp.put("scoreCredito", riesgoActual.getScore());
        resp.put("nivelCredito", riesgoActual.getRiskLevel().name());
        resp.put("periodo", Map.of(
                "inicio", inicio.toLocalDate().toString(),
                "fin", fin.toLocalDate().toString()));
        return ResponseEntity.ok(resp);
    }
}