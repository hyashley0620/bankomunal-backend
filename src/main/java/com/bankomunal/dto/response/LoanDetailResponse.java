package com.bankomunal.dto.response;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class LoanDetailResponse {
    private Long id;
    private String loanCode;
    private String estado;
    /** "institutional" (capital de la entidad, lo aprueba/rechaza un admin)
     *  o "group_fund" (préstamo solidario, lo aprueba/rechaza la votación
     *  del grupo — el frontend usa esto para no ofrecerle a un admin las
     *  acciones de aprobar/rechazar directo en un solidario). */
    private String fundingSource;
    /** Datos del titular real del préstamo — necesarios para que un admin
     *  vea de quién es el préstamo que está consultando (no del usuario
     *  logueado, que puede ser distinto al dueño). */
    private Long titularId;
    private String titularNombre;
    private String titularEmail;
    private BigDecimal montoSolicitado;
    private BigDecimal principal;
    private BigDecimal cuotaMensual;
    private int plazoMeses;
    private BigDecimal tasaInteresMensual;
    private LocalDateTime fechaSolicitud;
    private LocalDateTime fechaDesembolso;
    /** Fecha en que el admin aprobó (el contrato queda listo para firmar). */
    private LocalDateTime fechaAprobacion;
    /** Momento en que el socio aceptó el contrato — null si aún no lo acepta. */
    private LocalDateTime contratoAceptadoAt;
    /** Motivo que el admin dio al rechazar — null si no aplica. */
    private String motivoRechazo;
    private BigDecimal totalPagar;
    private BigDecimal totalIntereses;
    private BigDecimal saldoPendiente;
    private BigDecimal montoPagado;
    private int cuotasPagadas;
    private int cuotasPendientes;
    private int porcentajePagado;
    private LocalDate proximoVencimiento;
    private List<AmortizacionCuota> amortizacion;
}