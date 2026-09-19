package com.bankomunal.dto.response;

import lombok.*;
import java.util.List;

@Data
@Builder
public class CourseResponse {
    private Long id;
    private String categoria;
    private String emoji;
    private String color;
    private String titulo;
    private String nivel;
    private String duracion;
    private String descripcion;
    private Integer puntos;
    private boolean activo;
    private int orden;
    private List<LessonResponse> lecciones;

    @Data
    @Builder
    public static class LessonResponse {
        private Long id;
        private String titulo;
        private String contenido;
        private boolean esFinal;
        private QuizResponse quiz; // null si la lección no tiene quiz
    }

    @Data
    @Builder
    public static class QuizResponse {
        private String pregunta;
        private List<String> opciones;
        private Integer correcta;
    }
}
