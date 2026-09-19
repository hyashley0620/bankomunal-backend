package com.bankomunal.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class RecoverPasswordRequest {
    @NotBlank
    @Email(regexp = "^[^\\s@]+@[^\\s@]+\\.[a-zA-Z]{2,}$", message = "Ingresa un correo electrónico válido (ejemplo@correo.com).")
    private String email;
}
