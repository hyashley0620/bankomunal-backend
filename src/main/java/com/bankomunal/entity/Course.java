package com.bankomunal.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Course del catálogo de Educación Financiera.
 */
@Entity
@Table(name = "courses")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 40)
    private String categoria; // ahorro, credito, presupuesto, inversion, etc.

    @Column(length = 10)
    private String emoji;

    @Column(length = 20)
    private String color;

    @Column(nullable = false, length = 150)
    private String titulo;

    @Column(length = 20)
    private String nivel; // Básico / Intermedio / Avanzado

    @Column(length = 30)
    private String duracion; // texto libre, ej. "45 min"

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    @Builder.Default
    private Integer puntos = 100;

    @Builder.Default
    private boolean activo = true;

    @Builder.Default
    private int orden = 0;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "curso", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("orden ASC")
    @Builder.Default
    private List<CourseLesson> lecciones = new ArrayList<>();

    @PrePersist
    void prePersist() {
        if (createdAt == null)
            createdAt = LocalDateTime.now();
    }
}
