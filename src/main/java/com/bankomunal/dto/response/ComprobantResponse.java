package com.bankomunal.dto.response;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComprobantResponse {
    private String referencia;
    private String tipo;
    private BigDecimal monto;
    private String moneda;
    private String descripcion;
    private String estado;
    private LocalDateTime fecha;
    private String cuentaOrigen;
    private String cuentaDestino;
    private String codigoVerificacion;
}
