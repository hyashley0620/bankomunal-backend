package com.bankomunal.service;

import com.bankomunal.dto.response.*;
import com.bankomunal.entity.*;
import com.bankomunal.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public List<NotificationResponse> getNotificaciones(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(n -> NotificationResponse.builder()
                        .id(n.getId()).titulo(n.getTitulo())
                        .mensaje(n.getMensaje()).leida(n.isRead())
                        .fecha(n.getCreatedAt())
                        .type(n.getType()).referenceId(n.getReferenceId()).build())
                .toList();
    }

    public UnreadNotificationsResponse countNoLeidas(Long userId) {
        return new UnreadNotificationsResponse(
                notificationRepository.countByUserIdAndRead(userId, false));
    }

    @Transactional
    public void marcarTodasLeidas(Long userId) {
        notificationRepository.marcarTodasLeidas(userId);
    }

    /**
     * Marca una notificación individual como leída (panel.js llama PUT
     * /notificaciones/{id}/leer)
     */
    @Transactional
    public void marcarUnaLeida(Long notifId, Long userId) {
        notificationRepository.findById(notifId).ifPresent(n -> {
            if (n.getUser().getId().equals(userId)) {
                n.setRead(true);
                notificationRepository.save(n);
            }
        });
    }

    /** Crear notificación en BD + enviar por WebSocket al usuario (sin referencia a otra entidad) */
    @Transactional
    public void crearNotificacion(User user, String titulo, String mensaje, String type) {
        crearNotificacion(user, titulo, mensaje, type, null);
    }

    /**
     * Crear notificación en BD + enviar por WebSocket al usuario, incluyendo el ID de la
     * entidad relacionada (préstamo, transacción, ticket...) para poder navegar a ella
     * al hacer clic desde el frontend.
     */
    @Transactional
    public void crearNotificacion(User user, String titulo, String mensaje, String type, Long referenceId) {
        Notification saved = notificationRepository.save(
                Notification.builder()
                        .user(user).titulo(titulo).mensaje(mensaje).type(type)
                        .referenceId(referenceId).build());

        /* Push en tiempo real si el usuario tiene sesión WS activa */
        try {
            messagingTemplate.convertAndSendToUser(
                    user.getEmail(),
                    "/queue/notificaciones",
                    Map.of("id", saved.getId(),
                            "titulo", titulo,
                            "mensaje", mensaje,
                            "type", type,
                            "referenceId", referenceId != null ? referenceId : 0));
        } catch (Exception e) {
            /* Si no hay sesión WS activa, la notificación queda en BD igualmente */
        }
    }
}
