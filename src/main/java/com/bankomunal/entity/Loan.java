package com.bankomunal.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "loans")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class Loan {

    public enum LoanStatus {
        pending, approved, active, paid, rejected, defaulted
    }

    /**
     * De dónde sale la plata del desembolso y a dónde vuelve al pagar las
     * cuotas: `entity` = capital propio de Bankomunal (cuenta `fund`,
     * comportamiento de siempre); `group_fund` = fondo común de un grupo
     * específico (préstamo solidario, requiere votación — ver
     * GroupLoanService).
     */
    public enum FundingSource {
        entity, group_fund
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "loan_code", length = 80)
    private String loanCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "borrower_user_id", nullable = false)
    private User borrowerUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private Group group;

    @Enumerated(EnumType.STRING)
    @Column(name = "funding_source", nullable = false)
    @Builder.Default
    private FundingSource fundingSource = FundingSource.entity;

    /** Encuesta (Poll) usada para aprobar un préstamo solidario por votación
     *  de los socios del grupo. Null para préstamos institucionales, que se
     *  aprueban por un admin/tesorero como siempre. */
    @Column(name = "approval_poll_id")
    private Long approvalPollId;

    @Column(name = "monto_solicitado", nullable = false, precision = 18, scale = 2)
    private BigDecimal montoSolicitado;

    @Column(name = "principal", precision = 18, scale = 2)
    private BigDecimal principal;

    @Column(name = "tasa_interes_mensual", precision = 5, scale = 4)
    @Builder.Default
    private BigDecimal tasaInteresMensual = new BigDecimal("0.0200");

    @Column(name = "plazo_meses", nullable = false)
    private Integer plazoMeses;

    @Column(name = "cuota_mensual", precision = 18, scale = 2)
    private BigDecimal cuotaMensual;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private LoanStatus status = LoanStatus.pending;

    @Column(name = "motivo", length = 500)
    private String motivo;

    /** Motivo que el admin escribe al rechazar — visible para el socio en su
     *  lista de préstamos, no solo en la notificación. */
    @Column(name = "motivo_rechazo", length = 500)
    private String motivoRechazo;

    /** Momento en que el socio aceptó el contrato (estado "approved" →
     *  "active"). Antes de esto no hay desembolso. */
    @Column(name = "contrato_aceptado_at")
    private LocalDateTime contratoAceptadoAt;

    @Column(name = "saldo_pendiente", precision = 18, scale = 2)
    private BigDecimal saldoPendiente;

    @Column(name = "cuotas_pagadas")
    @Builder.Default
    private Integer cuotasPagadas = 0;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;
    @Column(name = "disbursed_at")
    private LocalDateTime disbursedAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null)
            createdAt = LocalDateTime.now();
        if (cuotasPagadas == null)
            cuotasPagadas = 0;
    }
}
