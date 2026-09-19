package com.bankomunal.service;

import com.bankomunal.dto.request.*;
import com.bankomunal.dto.response.*;
import com.bankomunal.entity.*;
import com.bankomunal.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SupportService {

    private final SupportTicketRepository ticketRepository;
    private final NotificationService notificationService;

    public List<SupportTicketResponse> getMisTickets(Long userId) {
        return ticketRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    public List<SupportTicketResponse> getTodos() {
        return ticketRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    /** Detalle de un ticket puntual (usado por el admin al responder). */
    public SupportTicketResponse getDetalle(Long ticketId) {
        SupportTicket t = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket no encontrado."));
        return toResponse(t);
    }

    @Transactional
    public SupportTicketResponse crear(SupportTicketRequest req, User user) {
        SupportTicket t = ticketRepository.save(SupportTicket.builder()
                .user(user).asunto(req.getAsunto())
                .descripcion(req.getDescripcion())
                .categoria(req.getCategoriaEfectiva()).build());
        return toResponse(t);
    }

    /**
     * El admin responde un ticket y opcionalmente cambia su estado
     * (in_progress / resolved / closed). Notifica al socio que lo creó.
     */
    @Transactional
    public SupportTicketResponse responder(Long ticketId, String respuesta, String nuevoEstado) {
        SupportTicket t = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket no encontrado."));

        if (respuesta != null && !respuesta.isBlank()) {
            t.setRespuestaAdmin(respuesta);
            t.setRespondidoAt(LocalDateTime.now());
        }
        if (nuevoEstado != null && !nuevoEstado.isBlank()) {
            try {
                t.setStatus(SupportTicket.TicketStatus.valueOf(nuevoEstado));
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Estado inválido: " + nuevoEstado);
            }
        }
        SupportTicket guardado = ticketRepository.save(t);

        if (respuesta != null && !respuesta.isBlank()) {
            notificationService.crearNotificacion(guardado.getUser(),
                    "Respuesta a tu ticket #" + guardado.getId(),
                    "Un asesor respondió: " + respuesta,
                    "soporte", guardado.getId());
        }
        return toResponse(guardado);
    }

    private SupportTicketResponse toResponse(SupportTicket t) {
        return SupportTicketResponse.builder()
                .id(t.getId()).fechaCreacion(t.getCreatedAt())
                .asunto(t.getAsunto()).descripcion(t.getDescripcion())
                .categoria(t.getCategoria()).prioridad(t.getPrioridad())
                .estado(t.getStatus().name())
                .solicitanteNombre(t.getUser() != null
                        ? (t.getUser().getFirstName() + " " +
                                (t.getUser().getLastName() != null ? t.getUser().getLastName() : ""))
                        : null)
                .solicitanteEmail(t.getUser() != null ? t.getUser().getEmail() : null)
                .respuestaAdmin(t.getRespuestaAdmin())
                .fechaRespuesta(t.getRespondidoAt())
                .build();
    }
}
