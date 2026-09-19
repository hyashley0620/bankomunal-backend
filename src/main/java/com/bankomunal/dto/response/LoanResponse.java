package com.bankomunal.dto.response;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class LoanResponse {
    private Long id;
    private String estado;
    private BigDecimal montoSolicitado;
    private int plazoMeses;
    private BigDecimal cuotaMensual;
    private LocalDateTime fechaSolicitud;
    private BigDecimal saldoPendiente;
    private int cuotasPagadas;
    /** Fecha de vencimiento de la próxima cuota pendiente (null si no hay). */
    private LocalDate proximoVencimiento;
    /** Motivo que el admin dio al rechazar — null si no aplica. */
    private String motivoRechazo;
}
