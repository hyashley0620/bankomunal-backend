package com.bankomunal.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Un reporte de un socio sobre una publicación del muro de comunidad (ej.
 * contenido ofensivo). No borra ni oculta la publicación por sí solo — solo
 * deja constancia y notifica a los administradores para que la revisen y,
 * si corresponde, la eliminen (ver CommunityController#borrarPublicacion).
 */
@Entity
@Table(name = "community_post_reports")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommunityPostReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private CommunityPost post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reported_by", nullable = false)
    private User reportedBy;

    @Column(length = 500)
    private String motivo;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
