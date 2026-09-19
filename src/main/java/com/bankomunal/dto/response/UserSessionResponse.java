package com.bankomunal.dto.response;

import lombok.*;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSessionResponse {
    private Long id;
    private String ipAddress;
    private String userAgent;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    /** true si expiresAt aún no ha pasado. */
    private boolean activa;
    /** true si es la sesión con la que se hizo esta misma petición. */
    private boolean actual;
}
