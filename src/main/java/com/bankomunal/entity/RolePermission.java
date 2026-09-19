package com.bankomunal.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Permiso de un rol sobre un módulo específico del sistema (Dashboard,
 * Préstamos, Usuarios, etc).
 */
@Entity
@Table(name = "role_permissions", uniqueConstraints = @UniqueConstraint(columnNames = { "role_id", "modulo" }))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RolePermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Column(nullable = false, length = 50)
    private String modulo;

    @Builder.Default
    private boolean leer = false;
    @Builder.Default
    private boolean crear = false;
    @Builder.Default
    private boolean editar = false;
    @Builder.Default
    private boolean borrar = false;
}
