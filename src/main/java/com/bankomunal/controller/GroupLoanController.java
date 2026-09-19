package com.bankomunal.controller;

import com.bankomunal.dto.request.LoanRequest;
import com.bankomunal.entity.User;
import com.bankomunal.service.GroupLoanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Préstamos solidarios: financiados por el fondo común de un grupo,
 * aprobados por votación de sus socios (no por un admin). Vive bajo
 * /api/comunidad porque conceptualmente es una decisión de grupo, igual que
 * las encuestas — de hecho reutiliza el mismo sistema de votación por
 * debajo (ver GroupLoanService).
 */
@RestController
@RequestMapping("/api/comunidad/grupos/{groupId}/prestamos-solidarios")
@RequiredArgsConstructor
public class GroupLoanController {

    private final GroupLoanService groupLoanService;

    /** GET — préstamos solidarios (propuestos, aprobados, rechazados) de este grupo. */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listar(
            @PathVariable Long groupId, @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(groupLoanService.listarDelGrupo(groupId, user));
    }

    /** GET — cuánto tiene el fondo común en total, cuánto ya está comprometido
     *  en préstamos solidarios en votación/aprobados, y cuánto queda disponible
     *  para proponer uno nuevo. */
    @GetMapping("/fondo")
    public ResponseEntity<Map<String, Object>> fondo(
            @PathVariable Long groupId, @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(groupLoanService.getFondoInfo(groupId, user));
    }

    /** POST — un socio del grupo propone un préstamo solidario; abre la votación. */
    @PostMapping
    public ResponseEntity<Map<String, Object>> proponer(
            @PathVariable Long groupId, @Valid @RequestBody LoanRequest req,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(groupLoanService.proponer(groupId, req, user));
    }

    public static class VotoRequest {
        public boolean aprobar;
    }

    /** POST — un socio del grupo vota a favor o en contra de un préstamo solidario. */
    @PostMapping("/{loanId}/votar")
    public ResponseEntity<Map<String, Object>> votar(
            @PathVariable Long groupId, @PathVariable Long loanId,
            @RequestBody VotoRequest req, @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(groupLoanService.votar(loanId, req.aprobar, user));
    }
}