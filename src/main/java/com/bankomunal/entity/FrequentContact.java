package com.bankomunal.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * transferencias.html llama a GET /api/contactos y POST /api/contactos
 * desde cargarContactosFrecuentes() y el botón #btnAgregarContacto en
 * paginas.js.
 */
@Entity
@Table(name = "frequent_contacts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class FrequentContact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Usuario propietario de la libreta de contactos */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_id", nullable = false)
    private User ownerUser;

    @Column(nullable = false, length = 150)
    private String nombre;

    /** número de cuenta del destinatario. */
    @Column(nullable = false, length = 150)
    private String email;

    @Column(name = "cuenta_numero", length = 50)
    private String cuentaNumero;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null)
            createdAt = LocalDateTime.now();
    }
}
