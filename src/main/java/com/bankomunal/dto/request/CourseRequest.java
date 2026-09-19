package com.bankomunal.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.List;

@Data
public class CourseRequest {
    private String categoria;
    private String emoji;
    private String color;

    @NotBlank(message = "El título del curso es obligatorio.")
    private String titulo;

    private String nivel;
    private String duracion;
    private String descripcion;
    private Integer puntos;
    private Boolean activo;
    private Integer orden;

    @Valid
    private List<LessonRequest> lecciones;

    @Data
    public static class LessonRequest {
        @NotBlank(message = "El título de la lección es obligatorio.")
        private String titulo;
        private String contenido;
        private boolean esFinal;
        private String quizPregunta;
        private List<String> quizOpciones;
        private Integer quizCorrecta;
    }
}
