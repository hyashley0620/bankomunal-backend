package com.bankomunal.dto.response;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class FacturaResponse {
    private String tipoServicio;
    private String referencia;
    private String empresa;
    private BigDecimal monto;
    private LocalDate fechaLimite;
    private boolean vencida;
}
