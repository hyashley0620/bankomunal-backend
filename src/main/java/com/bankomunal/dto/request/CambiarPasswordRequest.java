package com.bankomunal.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class CambiarPasswordRequest {
    @NotBlank
    private String passwordActual;
    @NotBlank
    @Size(min = 6)
    private String passwordNueva;
}
