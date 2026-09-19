package com.bankomunal.controller;

import com.bankomunal.dto.response.*;
import com.bankomunal.entity.User;
import com.bankomunal.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/notificaciones")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<List<NotificationResponse>> getAll(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(notificationService.getNotificaciones(user.getId()));
    }

    @GetMapping("/no-leidas")
    public ResponseEntity<UnreadNotificationsResponse> countUnread(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(notificationService.countNoLeidas(user.getId()));
    }

    @PostMapping("/marcar-leidas")
    public ResponseEntity<MessageResponse> marcarLeidas(@AuthenticationPrincipal User user) {
        notificationService.marcarTodasLeidas(user.getId());
        return ResponseEntity.ok(new MessageResponse("Notificaciones marcadas como leídas."));
    }

    /** panel.js llama PUT /notificaciones/{id}/leer para marcar individual */
    @PutMapping("/{id}/leer")
    public ResponseEntity<MessageResponse> marcarUnaLeida(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        notificationService.marcarUnaLeida(id, user.getId());
        return ResponseEntity.ok(new MessageResponse("Notificación marcada como leída."));
    }
}