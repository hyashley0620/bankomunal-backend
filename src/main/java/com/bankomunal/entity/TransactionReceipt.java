package com.bankomunal.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Comprobante persistido de una transferencia o pago.
 * GET /api/transferencias/{referencia}/comprobante.
 */
@Entity
@Table(name = "transaction_receipts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class TransactionReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    @Column(nullable = false, length = 120)
    private String referencia;

    @Column(name = "cuenta_origen", length = 50)
    private String cuentaOrigen;

    @Column(name = "cuenta_destino", length = 50)
    private String cuentaDestino;

    @Column(length = 50)
    private String tipo;

    @Column(length = 500)
    private String descripcion;

    @Column(length = 30)
    private String estado;

    @Column(name = "codigo_verificacion", length = 80)
    private String codigoVerificacion;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null)
            createdAt = LocalDateTime.now();
    }
}
