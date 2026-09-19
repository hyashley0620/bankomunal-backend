package com.bankomunal.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "group_members")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class GroupMember {

    public enum MemberRole {
        presidente, tesorero, secretario, miembro
    }

    public enum MemberStatus {
        active, suspended, expelled, removed
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private MemberRole role = MemberRole.miembro;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private MemberStatus status = MemberStatus.active;

    @Column(name = "motivo_expulsion", length = 500)
    private String motivoExpulsion;

    @Column(name = "joined_at")
    private LocalDateTime joinedAt;

    @Column(name = "expelled_at")
    private LocalDateTime expelledAt;

    @PrePersist
    void prePersist() {
        if (joinedAt == null)
            joinedAt = LocalDateTime.now();
    }
}
