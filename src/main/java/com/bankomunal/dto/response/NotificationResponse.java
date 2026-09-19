package com.bankomunal.dto.response;

import lombok.*;
import java.time.LocalDateTime;

@Data
@Builder
public class NotificationResponse {
    private Long id;
    private String titulo;
    private String mensaje;
    private boolean leida;
    private LocalDateTime fecha;
    private String type;
    private Long referenceId;
}
