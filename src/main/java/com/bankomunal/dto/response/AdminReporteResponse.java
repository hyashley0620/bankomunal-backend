package com.bankomunal.dto.response;

import lombok.*;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class AdminReporteResponse {
    private List<String> labels;
    private List<BigDecimal> ingresos;
    private List<BigDecimal> egresos;
    private long totalMovimientos;
    private long totalUsuarios;
    private long totalPrestamos;
    private BigDecimal montoCartera;
    private double indiceMora;
    private BigDecimal carteraTotal; // montoCartera
    private long sociosActivos; // usuarios con status=active
    private long prestamosActivos; // préstamos con status=active
    private double tasaPago; // % préstamos pagados a tiempo (0-100)
    private String tendenciaCartera;
    private String tendenciaMora;
    private String tendenciaSocios;
}
