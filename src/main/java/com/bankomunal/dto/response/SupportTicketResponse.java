package com.bankomunal.dto.response;

import lombok.*;
import java.time.LocalDateTime;

@Data
@Builder
public class SupportTicketResponse {
    private Long id;
    private LocalDateTime fechaCreacion;
    private String asunto;
    private String descripcion;
    private String categoria;
    private String prioridad;
    private String estado;
    /** Solo poblados para la vista de admin (lista de todos los tickets). */
    private String solicitanteNombre;
    private String solicitanteEmail;
    private String respuestaAdmin;
    private LocalDateTime fechaRespuesta;
}
