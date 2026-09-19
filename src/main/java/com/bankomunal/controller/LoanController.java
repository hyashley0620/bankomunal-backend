package com.bankomunal.controller;

import com.bankomunal.dto.request.*;
import com.bankomunal.dto.response.*;
import com.bankomunal.entity.User;
import com.bankomunal.service.LoanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/prestamos")
@RequiredArgsConstructor
public class LoanController {

    private final LoanService loanService;

    /** Simulador */
    @PostMapping("/simular")
    public ResponseEntity<LoanSimulationResponse> simular(@RequestBody LoanSimulateRequest req) {
        return ResponseEntity.ok(loanService.simular(req));
    }

    /** Solicitar préstamo */
    @PostMapping
    public ResponseEntity<LoanResponse> solicitar(
            @Valid @RequestBody LoanRequest req,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(loanService.solicitar(req, user));
    }

    /** Mis préstamos */
    @GetMapping("/mis-prestamos")
    public ResponseEntity<List<LoanResponse>> misPrestamos(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(loanService.getMisPrestamos(user.getId()));
    }

    /** Detalle de un préstamo (el dueño del préstamo o un admin pueden verlo) */
    @GetMapping("/{id}/detalle")
    public ResponseEntity<LoanDetailResponse> detalle(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(loanService.getDetalle(id, user));
    }

    /**
     * POST /api/prestamos/{id}/aceptar
     * El socio acepta/firma el contrato de su préstamo ya aprobado — recién
     * en este momento se hace el desembolso a su cuenta.
     */
    @PostMapping("/{id}/aceptar")
    public ResponseEntity<LoanResponse> aceptarContrato(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(loanService.aceptarContrato(id, user));
    }

    /**
     * Pagar cuota
     */
    @PostMapping("/{id}/pagar")
    public ResponseEntity<MessageResponse> pagar(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal User user) {
        String numeroCuenta = body != null ? body.get("numeroCuenta") : null;
        return ResponseEntity.ok(loanService.pagarCuota(id, user, numeroCuenta));
    }
}