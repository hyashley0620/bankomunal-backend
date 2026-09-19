package com.bankomunal.controller;

import com.bankomunal.entity.CourseProgress;
import com.bankomunal.entity.PointsTransaction;
import com.bankomunal.entity.User;
import com.bankomunal.repository.CourseProgressRepository;
import com.bankomunal.repository.CourseRepository;
import com.bankomunal.repository.PointsTransactionRepository;
import com.bankomunal.service.CourseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.UnsupportedEncodingException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/educacion")
@RequiredArgsConstructor
public class CourseController {

    private final CourseProgressRepository progresoRepository;
    private final CourseService courseService;
    private final CourseRepository courseRepository;
    private final PointsTransactionRepository pointsRepository;

    private static final DateTimeFormatter FMT_DISPLAY = DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy",
            new Locale("es"));

    /**
     * GET /api/educacion/catalogo — catálogo de cursos activos, con sus
     * lecciones y quizzes.
     */
    @GetMapping("/catalogo")
    public ResponseEntity<List<com.bankomunal.dto.response.CourseResponse>> catalogo() {
        return ResponseEntity.ok(courseService.getCatalogoActivo());
    }

    /**
     * GET /api/educacion/cursos - progreso del socio en todos los cursos que ha
     * iniciado
     */
    @GetMapping("/cursos")
    public ResponseEntity<List<Map<String, Object>>> misCursos(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(
                progresoRepository.findByUserId(user.getId()).stream().map(p -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", p.getCourseId());
                    m.put("leccionActual", p.getLeccionActual());
                    m.put("completado", p.getCompletado());
                    m.put("certificado", p.getCertificado());
                    return m;
                }).toList());
    }

    /**
     * POST /api/educacion/cursos/{courseId}/avanzar - guarda el avance de leccion
     */
    @PostMapping("/cursos/{courseId}/avanzar")
    public ResponseEntity<Map<String, Object>> avanzar(
            @PathVariable String courseId,
            @RequestBody(required = false) Map<String, Object> body,
            @AuthenticationPrincipal User user) {

        CourseProgress p = progresoRepository.findByUserIdAndCourseId(user.getId(), courseId)
                .orElseGet(() -> CourseProgress.builder().user(user).courseId(courseId).build());

        if (body != null) {
            if (body.get("leccionActual") != null)
                p.setLeccionActual((int) Double.parseDouble(body.get("leccionActual").toString()));
            if (body.get("cursoNombre") != null)
                p.setCourseName(body.get("cursoNombre").toString());
            if (body.get("puntos") != null)
                p.setPuntos((int) Double.parseDouble(body.get("puntos").toString()));
        }
        p.setUpdatedAt(LocalDateTime.now());
        progresoRepository.save(p);
        return ResponseEntity.ok(Map.of("mensaje", "Avance guardado."));
    }

    /**
     * POST /api/educacion/cursos/{courseId}/certificado - marca el curso como
     * completado y emite certificado
     */
    @PostMapping("/cursos/{courseId}/certificado")
    public ResponseEntity<Map<String, Object>> emitirCertificado(
            @PathVariable String courseId,
            @RequestBody(required = false) Map<String, Object> body,
            @AuthenticationPrincipal User user) {

        CourseProgress p = progresoRepository.findByUserIdAndCourseId(user.getId(), courseId)
                .orElseGet(() -> CourseProgress.builder().user(user).courseId(courseId).build());

        /* Los puntos y el nombre del curso salen del catálogo (fuente de verdad
           gestionada por el admin) — nunca se confía en lo que mande el cliente,
           para que no se puedan inflar los puntos ganados. */
        var courseOpt = parseCursoId(courseId).flatMap(courseRepository::findById);
        Integer puntosCurso = courseOpt.map(c -> c.getPuntos()).orElse(null);
        String nombreCurso = courseOpt.map(c -> c.getTitulo()).orElse(null);

        if (nombreCurso != null) {
            p.setCourseName(nombreCurso);
        } else if (body != null && body.get("cursoNombre") != null) {
            p.setCourseName(body.get("cursoNombre").toString());
        }
        p.setPuntos(puntosCurso != null ? puntosCurso : 100);

        p.setCompletado(true);
        p.setCertificado(true);
        boolean esPrimeraVez = p.getCodigoCertificado() == null;
        if (esPrimeraVez) {
            p.setCodigoCertificado("BKM-EDU-" + courseId + "-" + (1000 + new Random().nextInt(9000)));
            p.setFechaCompletado(LocalDateTime.now());
        }
        p.setUpdatedAt(LocalDateTime.now());
        progresoRepository.save(p);

        /* Otorgar los puntos al saldo de Beneficios — solo la primera vez que
           se completa el curso, para que no se puedan ganar puntos repetidos
           reenviando la misma solicitud. */
        if (esPrimeraVez) {
            pointsRepository.save(PointsTransaction.builder()
                    .user(user)
                    .tipo(PointsTransaction.Tipo.GANADO)
                    .puntos(p.getPuntos())
                    .descripcion("Curso completado: " + (p.getCourseName() != null ? p.getCourseName() : courseId))
                    .build());
        }

        return ResponseEntity.ok(Map.of(
                "codigoCertificado", p.getCodigoCertificado(),
                "puntos", p.getPuntos(),
                "mensaje", "Certificado emitido exitosamente."));
    }

    private Optional<Long> parseCursoId(String courseId) {
        try {
            return Optional.of(Long.parseLong(courseId));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * GET /api/educacion/certificados - todos los certificados obtenidos por el
     * socio
     */
    @GetMapping("/certificados")
    public ResponseEntity<List<Map<String, Object>>> misCertificados(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(
                progresoRepository.findByUserIdAndCertificadoTrue(user.getId()).stream().map(p -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("courseId", p.getCourseId());
                    m.put("cursoNombre", p.getCourseName());
                    m.put("codigoCertificado", p.getCodigoCertificado());
                    m.put("puntos", p.getPuntos());
                    m.put("fecha", p.getFechaCompletado() != null ? p.getFechaCompletado().toString() : null);
                    return m;
                }).toList());
    }

    /**
     * GET /api/educacion/certificados/{courseId}/pdf
     */
    @GetMapping("/certificados/{courseId}/pdf")
    public ResponseEntity<byte[]> descargarCertificado(
            @PathVariable String courseId,
            @AuthenticationPrincipal User user) throws UnsupportedEncodingException {

        Optional<CourseProgress> opt = progresoRepository.findByUserIdAndCourseId(user.getId(), courseId);
        if (opt.isEmpty() || !Boolean.TRUE.equals(opt.get().getCertificado()))
            return ResponseEntity.status(404).build();

        CourseProgress p = opt.get();
        String nombreCompleto = user.getFirstName() + " " + (user.getLastName() != null ? user.getLastName() : "");
        String fecha = (p.getFechaCompletado() != null ? p.getFechaCompletado() : LocalDateTime.now())
                .format(FMT_DISPLAY);

        String html = generarHtmlCertificado(
                nombreCompleto.trim(),
                p.getCourseName() != null ? p.getCourseName() : "Curso de Educación Financiera",
                p.getCodigoCertificado(),
                fecha,
                p.getPuntos());

        byte[] bytes = html.getBytes("UTF-8");
        String filename = "certificado-" + courseId + "-" + p.getCodigoCertificado() + ".html";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/html; charset=UTF-8"))
                .body(bytes);
    }

    private String generarHtmlCertificado(String nombre, String curso, String codigo, String fecha, Integer puntos) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang='es'><head><meta charset='UTF-8'>")
                .append("<title>Certificado - ").append(esc(curso)).append("</title>")
                .append("<style>")
                .append("@page{size:landscape;margin:0}")
                .append("body{font-family:Georgia,'Times New Roman',serif;margin:0;padding:50px;")
                .append("background:#f4f1ea;color:#1a202c;display:flex;align-items:center;justify-content:center;min-height:100vh}")
                .append(".cert{border:10px solid #005F73;outline:2px solid #94d2bd;outline-offset:-22px;")
                .append("padding:60px 70px;text-align:center;background:#fff;max-width:900px;width:100%;box-sizing:border-box}")
                .append(".brand{font-size:14px;letter-spacing:4px;color:#005F73;text-transform:uppercase;font-weight:bold}")
                .append(".title{font-size:38px;color:#1a202c;margin:18px 0 6px;font-weight:bold}")
                .append(".sub{font-size:14px;color:#64748b;text-transform:uppercase;letter-spacing:2px;margin-bottom:30px}")
                .append(".nombre{font-size:32px;color:#005F73;margin:18px 0;font-family:Georgia,serif;border-bottom:1px solid #cbd5e1;display:inline-block;padding:0 30px 10px}")
                .append(".curso{font-size:20px;margin:14px 0 28px;color:#1a202c}")
                .append(".curso strong{color:#005F73}")
                .append(".meta{display:flex;justify-content:space-between;margin-top:46px;font-size:12px;color:#64748b}")
                .append(".meta div{text-align:center}")
                .append(".meta strong{display:block;color:#1a202c;font-size:13px;margin-bottom:4px}")
                .append(".medal{font-size:54px;margin-bottom:10px}")
                .append("@media print{body{background:#fff;padding:0}.cert{border:8px solid #005F73;box-shadow:none}}")
                .append("</style></head><body>")
                .append("<div class='cert'>")
                .append("<div class='medal'>&#127942;</div>")
                .append("<div class='brand'>Bankomunal &middot; Educacion Financiera</div>")
                .append("<div class='title'>Certificado de Finalizacion</div>")
                .append("<div class='sub'>Otorgado a</div>")
                .append("<div class='nombre'>").append(esc(nombre.isBlank() ? "Socio Bankomunal" : nombre))
                .append("</div>")
                .append("<div class='curso'>Por completar exitosamente el curso<br><strong>").append(esc(curso))
                .append("</strong>")
                .append(puntos != null ? " &nbsp;&middot;&nbsp; " + puntos + " puntos obtenidos" : "").append("</div>")
                .append("<div class='meta'>")
                .append("<div><strong>").append(esc(fecha)).append("</strong>Fecha de emision</div>")
                .append("<div><strong>").append(esc(codigo)).append("</strong>Codigo de verificacion</div>")
                .append("<div><strong>Bankomunal</strong>Gestion de Microfinanzas Comunitarias</div>")
                .append("</div>")
                .append("</div>")
                .append("<script>window.onload=function(){window.print();}</script>")
                .append("</body></html>");
        return sb.toString();
    }

    private String esc(String s) {
        if (s == null)
            return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
