package com.bankomunal.service;

import com.bankomunal.dto.request.CourseRequest;
import com.bankomunal.dto.response.CourseResponse;
import com.bankomunal.entity.Course;
import com.bankomunal.entity.CourseLesson;
import com.bankomunal.repository.CourseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseRepository cursoRepository;

    /** Catálogo que consume el socio — solo cursos activos. */
    public List<CourseResponse> getCatalogoActivo() {
        return cursoRepository.findByActivoTrueOrderByOrdenAscIdAsc().stream()
                .map(this::toResponse).toList();
    }

    /** Listado completo para el admin — incluye inactivos, para poder reactivarlos. */
    public List<CourseResponse> getTodosAdmin() {
        return cursoRepository.findAllByOrderByOrdenAscIdAsc().stream()
                .map(this::toResponse).toList();
    }

    public CourseResponse getPorId(Long id) {
        return toResponse(buscar(id));
    }

    @Transactional
    public CourseResponse crear(CourseRequest req) {
        Course curso = Course.builder()
                .categoria(req.getCategoria())
                .emoji(req.getEmoji())
                .color(req.getColor())
                .titulo(req.getTitulo())
                .nivel(req.getNivel())
                .duracion(req.getDuracion())
                .descripcion(req.getDescripcion())
                .puntos(req.getPuntos() != null ? req.getPuntos() : 100)
                .activo(req.getActivo() == null || req.getActivo())
                .orden(req.getOrden() != null ? req.getOrden() : 0)
                .lecciones(new ArrayList<>())
                .build();
        aplicarLecciones(curso, req.getLecciones());
        cursoRepository.save(curso);
        return toResponse(curso);
    }

    @Transactional
    public CourseResponse actualizar(Long id, CourseRequest req) {
        Course curso = buscar(id);
        curso.setCategoria(req.getCategoria());
        curso.setEmoji(req.getEmoji());
        curso.setColor(req.getColor());
        curso.setTitulo(req.getTitulo());
        curso.setNivel(req.getNivel());
        curso.setDuracion(req.getDuracion());
        curso.setDescripcion(req.getDescripcion());
        if (req.getPuntos() != null)
            curso.setPuntos(req.getPuntos());
        if (req.getActivo() != null)
            curso.setActivo(req.getActivo());
        if (req.getOrden() != null)
            curso.setOrden(req.getOrden());

        // Reemplaza todas las lecciones (más simple y predecible que hacer un
        // diff fino; orphanRemoval=true se encarga de borrar las anteriores).
        curso.getLecciones().clear();
        aplicarLecciones(curso, req.getLecciones());

        cursoRepository.save(curso);
        return toResponse(curso);
    }

    /** Desactivar (no se borra de verdad para no invalidar certificados/progreso ya emitidos). */
    @Transactional
    public void desactivar(Long id) {
        Course curso = buscar(id);
        curso.setActivo(false);
        cursoRepository.save(curso);
    }

    @Transactional
    public void reactivar(Long id) {
        Course curso = buscar(id);
        curso.setActivo(true);
        cursoRepository.save(curso);
    }

    private Course buscar(Long id) {
        return cursoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Curso no encontrado."));
    }

    private void aplicarLecciones(Course curso, List<CourseRequest.LessonRequest> lecciones) {
        if (lecciones == null)
            return;
        int i = 0;
        for (CourseRequest.LessonRequest lr : lecciones) {
            CourseLesson l = CourseLesson.builder()
                    .curso(curso)
                    .orden(i++)
                    .titulo(lr.getTitulo())
                    .contenido(lr.getContenido())
                    .esFinal(lr.isEsFinal())
                    .quizPregunta(lr.getQuizPregunta())
                    .quizOpciones(lr.getQuizOpciones() != null ? new ArrayList<>(lr.getQuizOpciones()) : new ArrayList<>())
                    .quizCorrecta(lr.getQuizCorrecta())
                    .build();
            curso.getLecciones().add(l);
        }
    }

    private CourseResponse toResponse(Course c) {
        List<CourseResponse.LessonResponse> lecciones = c.getLecciones().stream()
                .map(l -> CourseResponse.LessonResponse.builder()
                        .id(l.getId())
                        .titulo(l.getTitulo())
                        .contenido(l.getContenido())
                        .esFinal(l.isEsFinal())
                        .quiz(l.getQuizPregunta() == null || l.getQuizPregunta().isBlank() ? null
                                : CourseResponse.QuizResponse.builder()
                                        .pregunta(l.getQuizPregunta())
                                        .opciones(l.getQuizOpciones())
                                        .correcta(l.getQuizCorrecta())
                                        .build())
                        .build())
                .toList();

        return CourseResponse.builder()
                .id(c.getId())
                .categoria(c.getCategoria())
                .emoji(c.getEmoji())
                .color(c.getColor())
                .titulo(c.getTitulo())
                .nivel(c.getNivel())
                .duracion(c.getDuracion())
                .descripcion(c.getDescripcion())
                .puntos(c.getPuntos())
                .activo(c.isActivo())
                .orden(c.getOrden())
                .lecciones(lecciones)
                .build();
    }
}
