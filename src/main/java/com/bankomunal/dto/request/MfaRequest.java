package com.bankomunal.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MfaRequest {
    @NotBlank
    private String email;
    @NotBlank
    private String codigo;
}
