package com.bankomunal.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class LoginRequest {
    @NotBlank
    @Email(regexp = "^[^\\s@]+@[^\\s@]+\\.[a-zA-Z]{2,}$", message = "Ingresa un correo electrónico válido (ejemplo@correo.com).")
    private String email;
    @NotBlank
    private String password;
    /** IP del cliente — inyectada por el controller desde HttpServletRequest */
    private String ip;
    /** User-Agent del cliente — inyectado por el controller desde HttpServletRequest */
    private String userAgent;
}
