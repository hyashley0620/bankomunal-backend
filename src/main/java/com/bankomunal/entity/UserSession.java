package com.bankomunal.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Registro real de cada inicio de sesión (hash del JWT emitido, IP,
 * user-agent y vigencia). El diseño de la app solo permite un token activo a la vez por
 * usuario (ver {@link User#getActiveToken()}, validado en JwtAuthFilter),
 * así que esta tabla no habilita múltiples sesiones concurrentes reales —
 * lleva el historial verdadero de accesos y permite cerrarlos
 * explícitamente desde el servidor.
 */
@Entity
@Table(name = "user_sessions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class UserSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** SHA-256 en hex del JWT emitido — nunca se guarda el token crudo. */
    @Column(name = "token_hash", nullable = false, length = 255)
    private String tokenHash;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /** Vigencia del token (igual a su expiración JWT). Cerrar una sesión =
     *  adelantar este valor a "ahora", sin borrar el registro histórico. */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null)
            createdAt = LocalDateTime.now();
    }
}
