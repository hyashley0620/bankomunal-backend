package com.bankomunal.dto.response;

import lombok.*;
import java.time.LocalDateTime;

@Data @Builder
public class AuditLogResponse {
    private Long id;
    private AuditUserRef usuario;
    private String eventType;
    private String objectType;
    private Long objectId;
    private String ipAddress;
    private String userAgent;
    private LocalDateTime createdAt;
    private String userEmail;
    private String details;

    @Data @AllArgsConstructor
    public static class AuditUserRef {
        private Long id;
        private String email;
    }
}
