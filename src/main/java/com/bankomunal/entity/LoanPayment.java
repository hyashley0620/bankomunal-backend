package com.bankomunal.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "loan_payments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class LoanPayment {

    public enum PaymentStatus {
        pending, paid, overdue
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_id", nullable = false)
    private Loan loan;

    @Column(name = "numero_cuota", nullable = false)
    private Integer numeroCuota;

    @Column(name = "fecha_vencimiento")
    private LocalDate fechaVencimiento;

    @Column(name = "monto_capital", precision = 18, scale = 2)
    private BigDecimal montoCapital;

    @Column(name = "monto_interes", precision = 18, scale = 2)
    private BigDecimal montoInteres;

    @Column(name = "total_cuota", precision = 18, scale = 2)
    private BigDecimal totalCuota;

    @Column(name = "saldo_restante", precision = 18, scale = 2)
    private BigDecimal saldoRestante;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.pending;

    @Column(name = "fecha_pago")
    private LocalDate fechaPago;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null)
            createdAt = LocalDateTime.now();
    }
}
