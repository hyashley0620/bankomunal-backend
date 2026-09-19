package com.bankomunal.controller;

import com.bankomunal.dto.request.SupportTicketRequest;
import com.bankomunal.dto.response.*;
import com.bankomunal.entity.User;
import com.bankomunal.service.SupportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/soporte")
@RequiredArgsConstructor
public class SupportController {

    private final SupportService supportService;

    @GetMapping("/tickets/mis-tickets")
    public ResponseEntity<List<SupportTicketResponse>> misTickets(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(supportService.getMisTickets(user.getId()));
    }

    @PostMapping("/tickets")
    public ResponseEntity<SupportTicketResponse> crear(
            @Valid @RequestBody SupportTicketRequest req,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(supportService.crear(req, user));
    }
}