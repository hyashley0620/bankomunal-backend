package com.bankomunal.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transaction_limits")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class TransactionLimit {
    public enum Scope {
        global, group, user
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Scope scope = Scope.global;
    @Column(name = "scope_id")
    private Long scopeId;
    @Column(name = "tx_type", length = 50)
    private String txType;
    @Column(name = "max_per_transaction", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal maxPerTransaction = new BigDecimal("5000000");
    @Column(name = "max_per_day", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal maxPerDay = new BigDecimal("10000000");
    @Column(name = "max_per_week", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal maxPerWeek = new BigDecimal("30000000");
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void pre() {
        updatedAt = LocalDateTime.now();
    }
}
