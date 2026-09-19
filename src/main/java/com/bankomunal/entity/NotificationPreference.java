package com.bankomunal.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Preferencias de notificación por usuario.
 * Mapeada a la tabla `notification_preferences`.
 */
@Entity
@Table(name = "notification_preferences")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class NotificationPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Relación 1-1 con el usuario dueño de estas preferencias */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "notif_email", nullable = false)
    @Builder.Default
    private Boolean notifEmail = true;

    @Column(name = "notif_push", nullable = false)
    @Builder.Default
    private Boolean notifPush = true;

    @Column(name = "notif_sms", nullable = false)
    @Builder.Default
    private Boolean notifSms = false;

    @Column(name = "alerta_saldo", nullable = false)
    @Builder.Default
    private Boolean alertaSaldo = true;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        this.updatedAt = LocalDateTime.now();
    }
}
