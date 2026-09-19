package com.bankomunal.dto.response;

import com.bankomunal.entity.ReportTemplate;
import lombok.*;
import java.time.LocalDateTime;

/**
 * DTO de respuesta para plantillas de reporte.
 */
@Data
@Builder
public class ReportTemplateResponse {
    private Long id;
    private String nombre;
    private String tipo;
    private String parametros;
    private boolean esPublico;
    private LocalDateTime createdAt;

    public static ReportTemplateResponse from(ReportTemplate t) {
        return ReportTemplateResponse.builder()
                .id(t.getId())
                .nombre(t.getNombre())
                .tipo(t.getTipo())
                .parametros(t.getParametros())
                .esPublico(t.isEsPublico())
                .createdAt(t.getCreatedAt())
                .build();
    }
}