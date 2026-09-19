package com.bankomunal.dto.response;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class TransactionResponse {
    private String tipo;
    private LocalDateTime fecha;
    private BigDecimal monto;
    private String descripcion;
    private String referencia;
    private String estado;
    private String cuentaOrigen;
    private String cuentaDestino;
}
