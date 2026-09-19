package com.bankomunal.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "savings_goals")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class SavingsGoal {
    public enum GoalStatus {
        active, completed, cancelled, withdrawn
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private Group group;
    @Column(nullable = false, length = 200)
    private String nombre;
    @Column(name = "monto_meta", nullable = false, precision = 18, scale = 2)
    private BigDecimal montoMeta;
    @Column(name = "monto_actual", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal montoActual = BigDecimal.ZERO;
    @Column(name = "fecha_limite")
    private LocalDate fechaLimite;
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private GoalStatus status = GoalStatus.active;
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void pre() {
        if (createdAt == null)
            createdAt = LocalDateTime.now();
    }
}
