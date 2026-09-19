package com.bankomunal.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "polls")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class Poll {

    public enum PollStatus {
        open, closed, approved, rejected
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private Group group;

    @Column(nullable = false, length = 255)
    private String titulo;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private PollStatus status = PollStatus.open;

    @Column(name = "is_rule_change")
    @Builder.Default
    private Boolean isRuleChange = false;

    @Column(name = "is_anonymous")
    @Builder.Default
    private Boolean isAnonymous = false;

    @Column(name = "approval_threshold")
    @Builder.Default
    private Integer approvalThreshold = 51;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "ends_at")
    private LocalDateTime endsAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null)
            createdAt = LocalDateTime.now();
        if (isRuleChange == null)
            isRuleChange = false;
        if (isAnonymous == null)
            isAnonymous = false;
        if (approvalThreshold == null)
            approvalThreshold = 51;
    }

    public boolean isRuleChange() {
        return isRuleChange != null && isRuleChange;
    }

    public boolean isAnonymous() {
        return isAnonymous != null && isAnonymous;
    }

    public int getApprovalThreshold() {
        return approvalThreshold != null ? approvalThreshold : 51;
    }
}
