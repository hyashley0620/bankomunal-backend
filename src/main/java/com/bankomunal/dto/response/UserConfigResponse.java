package com.bankomunal.dto.response;

import lombok.*;
import java.time.LocalDateTime;

/**
 * Respuesta del GET/PUT /api/usuarios/configuracion
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserConfigResponse {
    private Boolean notifEmail;
    private Boolean notifPush;
    private Boolean notifSms;
    private Boolean alertaSaldo;
    private String idioma;
    private String mensaje;
    private Boolean mfaEnabled;
    private LocalDateTime lastPasswordChange;
}
