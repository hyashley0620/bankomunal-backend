package com.bankomunal.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Registro persistente de un respaldo real de la base de datos (dump físico
 * en disco vía mysqldump, no un mock). 
 */
@Entity
@Table(name = "backup_records")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class BackupRecord {

    public enum Metodo {
        MANUAL, AUTOMATICO, PRE_RESTAURACION
    }

    public enum Estado {
        EN_PROGRESO, COMPLETADO, FALLIDO, RESTAURADO
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre_archivo", nullable = false, length = 150)
    private String nombreArchivo;

    /** Ruta absoluta en disco del dump (cifrado en reposo si hay llave configurada). */
    @Column(name = "ruta_archivo", nullable = false, length = 500)
    private String rutaArchivo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Metodo metodo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Estado estado;

    @Column(name = "tamano_bytes")
    private Long tamanoBytes;

    /** SHA-256 del dump al momento de crearlo — se revalida antes de restaurar. */
    @Column(length = 64)
    private String checksum;

    @Column(name = "cifrado")
    @Builder.Default
    private boolean cifrado = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responsable_id")
    private User responsable;

    /** Copia del nombre del responsable al momento del respaldo (sobrevive si el usuario se borra). */
    @Column(name = "responsable_nombre", length = 200)
    private String responsableNombre;

    @Column(columnDefinition = "TEXT")
    private String detalle;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "restored_at")
    private LocalDateTime restoredAt;

    @PrePersist
    void pre() {
        if (createdAt == null)
            createdAt = LocalDateTime.now();
    }
}
