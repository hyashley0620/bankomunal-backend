package com.bankomunal.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class SupportTicketRequest {
    @NotBlank
    private String asunto;
    private String categoria;
    private String tipo;
    private String descripcion;

    /** Devuelve categoria si está presente, si no usa tipo */
    public String getCategoriaEfectiva() {
        return categoria != null ? categoria : (tipo != null ? tipo : "general");
    }
}
