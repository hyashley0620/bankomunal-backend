package com.bankomunal.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "course_progress", uniqueConstraints = {
        @UniqueConstraint(name = "uq_course_user", columnNames = { "user_id", "course_id" })
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** ID del curso tal como lo define el catálogo */
    @Column(name = "course_id", nullable = false, length = 50)
    private String courseId;

    @Column(name = "course_name", length = 200)
    private String courseName;

    @Column(name = "leccion_actual")
    @Builder.Default
    private Integer leccionActual = 0;

    @Column(nullable = false)
    @Builder.Default
    private Boolean completado = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean certificado = false;

    @Column(name = "codigo_certificado", length = 50)
    private String codigoCertificado;

    @Column(name = "puntos")
    private Integer puntos;

    @Column(name = "fecha_completado")
    private LocalDateTime fechaCompletado;

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
