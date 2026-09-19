package com.bankomunal.service;

import com.bankomunal.dto.response.*;
import com.bankomunal.entity.*;
import com.bankomunal.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public List<AuditLogResponse> getLogs(LocalDateTime inicio, LocalDateTime fin) {
        return getLogs(inicio, fin, null);
    }

    /**
     * Obtiene logs con filtro opcional por tipo de evento.
     * Llamado desde GET /api/admin/auditoria?tipo=LOGIN_SUCCESS
     */
    public List<AuditLogResponse> getLogs(LocalDateTime inicio, LocalDateTime fin, String tipo) {
        List<AuditLog> logs;
        if (inicio != null && fin != null && tipo != null && !tipo.isBlank()) {
            logs = auditLogRepository.findByDateRangeAndTipo(inicio, fin, tipo);
        } else if (inicio != null && fin != null) {
            logs = auditLogRepository.findByDateRange(inicio, fin);
        } else if (tipo != null && !tipo.isBlank()) {
            logs = auditLogRepository.findByEventType(tipo);
        } else {
            logs = auditLogRepository.findAllOrderByCreatedAtDesc();
        }
        return logs.stream().map(l -> AuditLogResponse.builder()
                .id(l.getId())
                .usuario(l.getUser() != null
                        ? new AuditLogResponse.AuditUserRef(l.getUser().getId(), l.getUser().getEmail())
                        : null)
                .eventType(l.getEventType())
                .objectType(l.getObjectType())
                .objectId(l.getObjectId())
                .ipAddress(l.getIpAddress())
                .userAgent(l.getUserAgent())
                .createdAt(l.getCreatedAt())
                .userEmail(l.getUser() != null ? l.getUser().getEmail() : null)
                .details(l.getDetails())
                .build()).toList();
    }

    /** Compatibilidad con los llamadores que no tienen un id de objeto a mano. */
    public void log(User user, String eventType, String objectType, String ip, String details) {
        log(user, eventType, objectType, null, ip, details);
    }

    public void log(User user, String eventType, String objectType, Long objectId, String ip, String details) {
        auditLogRepository.save(AuditLog.builder()
                .user(user).eventType(eventType).objectType(objectType).objectId(objectId)
                .ipAddress(ip).userAgent(userAgentActual()).details(details).build());
    }

    /**
     * Header User-Agent de la petición HTTP en curso, si la hay. El backup
     * automático semanal (@Scheduled) y otras llamadas fuera de un request
     * no tienen contexto HTTP — en ese caso queda null, igual que ya
     * ocurría con ip en esos mismos casos.
     */
    private String userAgentActual() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs != null ? attrs.getRequest().getHeader("User-Agent") : null;
    }
}
