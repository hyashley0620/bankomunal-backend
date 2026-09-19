package com.bankomunal.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Una lección dentro de un curso, con quiz opcional al final de la
 * lección (no solo del curso completo).
 */
@Entity
@Table(name = "course_lessons")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseLesson {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course curso;

    @Builder.Default
    private int orden = 0;

    @Column(nullable = false, length = 200)
    private String titulo;

    @Column(columnDefinition = "TEXT")
    private String contenido;

    /** Si es la lección de cierre del curso (pantalla de "¡Completado!"). */
    @Builder.Default
    private boolean esFinal = false;

    /* Quiz opcional de la lección */
    @Column(name = "quiz_pregunta", length = 500)
    private String quizPregunta;

    @ElementCollection
    @CollectionTable(name = "course_lesson_options", joinColumns = @JoinColumn(name = "lesson_id"))
    @Column(name = "opcion", length = 300)
    @OrderColumn(name = "posicion")
    @Builder.Default
    private List<String> quizOpciones = new ArrayList<>();

    @Column(name = "quiz_correcta")
    private Integer quizCorrecta; // índice (0-based) de la opción correcta
}
