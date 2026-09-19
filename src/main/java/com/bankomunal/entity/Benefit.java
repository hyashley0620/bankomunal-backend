package com.bankomunal.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "benefits")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class Benefit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String titulo;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    @Column(length = 50)
    @Builder.Default
    private String tipo = "general";

    @Column(name = "tasa_especial", precision = 5, scale = 2)
    private BigDecimal tasaEspecial;

    @Column(name = "nivel_minimo", length = 50)
    @Builder.Default
    private String nivelMinimo = "basico";

    /**
     * Costo en puntos para canjear este beneficio. Null/0 = no se canjea con puntos
     * (por ejemplo, "Solicitar aplicación" o "Ver alianzas" no consumen puntos).
     */
    @Column(name = "costo_puntos")
    private Integer costoPuntos;

    /**
     * Monto en pesos que se aplica al canjear beneficios con efecto monetario real
     * (por ahora, tipo = "abono_capital": se descuenta directamente del saldo
     * pendiente del préstamo activo del socio).
     */
    @Column(name = "monto_abono", precision = 18, scale = 2)
    private java.math.BigDecimal montoAbono;

    @Column
    @Builder.Default
    private boolean activo = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void pre() {
        if (createdAt == null)
            createdAt = LocalDateTime.now();
    }
}
