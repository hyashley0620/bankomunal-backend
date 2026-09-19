package com.bankomunal.controller;

import com.bankomunal.entity.*;
import com.bankomunal.repository.*;
import com.bankomunal.service.GroupService;
import com.bankomunal.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.*;
import java.util.*;

@RestController
@RequestMapping("/api/comunidad")
@RequiredArgsConstructor
public class CommunityController {

    private final GroupService groupService;
    private final UserRepository userRepository;
    private final CommunityPostRepository postRepository;
    private final CommunityPostLikeRepository likeRepository;
    private final CommunityPostReportRepository reportRepository;
    private final CommunityPostCommentRepository commentRepository;
    private final GroupMeetingRepository meetingRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final NotificationService notificationService;

    /** GET /api/comunidad/miembros */
    @GetMapping("/miembros")
    public ResponseEntity<List<Map<String, Object>>> miembros() {
        return ResponseEntity.ok(
                userRepository.findAll().stream()
                        .filter(u -> u.getStatus() == User.UserStatus.active)
                        .map(u -> Map.<String, Object>of(
                                "id", u.getId(),
                                "nombre", u.getFirstName() + (u.getLastName() != null ? " " + u.getLastName() : ""),
                                "email", u.getEmail()))
                        .toList());
    }

    /** GET /api/comunidad/grupos */
    @GetMapping("/grupos")
    public ResponseEntity<List<Map<String, Object>>> misGrupos(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(groupService.getMisGrupos(user.getId()));
    }

    /**
     * GET /api/comunidad/admin/grupos — TODOS los grupos del sistema
     */
    @PreAuthorize("hasRole('admin')")
    @GetMapping("/admin/grupos")
    public ResponseEntity<List<Map<String, Object>>> todosLosGrupos() {
        return ResponseEntity.ok(groupService.getTodosLosGruposAdmin());
    }

    /** POST /api/comunidad/grupos */
    @PostMapping("/grupos")
    public ResponseEntity<Map<String, Object>> crearGrupo(
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal User user) {
        String nombre = body.getOrDefault("nombre", "").trim();
        if (nombre.isEmpty())
            return ResponseEntity.badRequest().body(Map.of("mensaje", "El nombre es requerido."));
        return ResponseEntity.ok(groupService.crear(nombre,
                body.getOrDefault("tipo", "mixto"), body.getOrDefault("descripcion", ""), user));
    }

    /** GET /api/comunidad/grupos/{id}/miembros */
    @GetMapping("/grupos/{groupId}/miembros")
    public ResponseEntity<List<Map<String, Object>>> miembrosGrupo(@PathVariable Long groupId) {
        return ResponseEntity.ok(groupService.getMiembrosGrupo(groupId));
    }

    /** POST /api/comunidad/grupos/{id}/miembros */
    @PostMapping("/grupos/{groupId}/miembros")
    public ResponseEntity<Map<String, Object>> agregarMiembro(
            @PathVariable Long groupId,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal User user) {
        if (body.get("userId") == null)
            return ResponseEntity.badRequest().body(Map.of("mensaje", "userId es requerido."));
        Long userId = Long.valueOf(body.getOrDefault("userId", "0").toString());
        String rol = body.containsKey("rol") ? body.getOrDefault("rol", "0").toString() : "miembro";
        return ResponseEntity.ok(groupService.agregarMiembro(groupId, userId, rol, user));
    }

    /**
     * Para poder suspender, expulsar, reactivar o cambiar el rol de grupo de
     * cualquier miembro. Verifica que quien llama sea administrador del
     * sistema o líder del grupo (rol presidente/tesorero/secretario) — y,
     * para nombrar presidente específicamente, que sea el presidente actual
     * o un admin. La validación real ocurre en GroupService.gestionarMiembro.
     * body: { "accion": "cambiar-rol", "rol": "tesorero" } o
     *       { "accion": "expulsar", "motivo": "..." } / "suspender" / "activar"
     */
    @PatchMapping("/grupos/{groupId}/miembros/{userId}")
    public ResponseEntity<Map<String, Object>> gestionarMiembro(
            @PathVariable Long groupId, @PathVariable Long userId,
            @RequestBody Map<String, String> body, @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(groupService.gestionarMiembro(
                groupId, userId, body.getOrDefault("accion", "suspender"), body.get("rol"), body.get("motivo"),
                user));
    }

    /** POST /api/comunidad/grupos/{id}/fondo */
    @PostMapping("/grupos/{groupId}/fondo")
    public ResponseEntity<Map<String, Object>> aportarFondo(
            @PathVariable Long groupId,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal User user) {
        BigDecimal monto = new BigDecimal(body.getOrDefault("monto", "0").toString());
        return ResponseEntity.ok(groupService.aportarFondo(groupId, monto, user));
    }

    /**
     * GET /api/comunidad/grupos/{id}/aportes — cuánto ha aportado cada socio
     * al fondo común. Visible para cualquier miembro del grupo (transparencia
     * sobre un fondo compartido) o un admin.
     */
    @GetMapping("/grupos/{groupId}/aportes")
    public ResponseEntity<Map<String, Object>> aportesFondo(
            @PathVariable Long groupId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(groupService.getAportesPorSocio(groupId, user));
    }

    /**
     * GET /api/comunidad/publicaciones
     * Igual que las encuestas (PollService.getEncuestas): las publicaciones
     * generales (sin grupo) las ve toda la cooperativa; las de un grupo solo
     * las ven sus miembros (el admin ve todo, para conservar la supervisión
     * que ya tiene en el resto del panel).
     */
    @GetMapping("/publicaciones")
    public ResponseEntity<List<Map<String, Object>>> publicaciones(@AuthenticationPrincipal User user) {
        boolean esAdmin = user != null && user.getRoles().stream()
                .anyMatch(r -> "admin".equalsIgnoreCase(r.getName()));

        List<CommunityPost> todas = postRepository.findAllByOrderByCreatedAtDesc();
        List<CommunityPost> posts;
        if (esAdmin || user == null) {
            posts = todas;
        } else {
            Set<Long> misGrupos = groupMemberRepository.findByUserId(user.getId()).stream()
                    .map(m -> m.getGroup().getId())
                    .collect(java.util.stream.Collectors.toSet());
            posts = todas.stream()
                    .filter(p -> p.getGroup() == null || misGrupos.contains(p.getGroup().getId()))
                    .toList();
        }

        List<Long> postIds = posts.stream().map(CommunityPost::getId).toList();
        Set<Long> misLikes = user == null || postIds.isEmpty()
                ? Set.of()
                : new HashSet<>(likeRepository.findByUserIdAndPostIdIn(user.getId(), postIds).stream()
                        .map(l -> l.getPost().getId()).toList());

        return ResponseEntity.ok(
                posts.stream()
                        .map(p -> {
                            Map<String, Object> m = new LinkedHashMap<>();
                            m.put("id", p.getId());
                            m.put("autor", p.getUser().getFirstName() +
                                    (p.getUser().getLastName() != null ? " " + p.getUser().getLastName() : ""));
                            m.put("autorId", p.getUser().getId());
                            m.put("contenido", p.getContenido());
                            m.put("fecha", p.getCreatedAt().toString());
                            m.put("tipo", p.getTipo() != null ? p.getTipo() : "texto");
                            if (p.getGroup() != null) {
                                m.put("groupId", p.getGroup().getId());
                                m.put("groupNombre", p.getGroup().getName());
                            }
                            if (p.getEventoFecha() != null)
                                m.put("eventoFecha", p.getEventoFecha().toString());
                            if (p.getImagenUrl() != null)
                                m.put("imagenUrl", p.getImagenUrl());
                            m.put("likes", likeRepository.countByPostId(p.getId()));
                            m.put("likedByMe", misLikes.contains(p.getId()));
                            m.put("reportedByMe", user != null
                                    && reportRepository.existsByPostIdAndReportedById(p.getId(), user.getId()));
                            m.put("totalComentarios", commentRepository.countByPostId(p.getId()));
                            return m;
                        }).toList());
    }

    /** POST /api/comunidad/publicaciones/{id}/like — alterna me gusta */
    @PostMapping("/publicaciones/{postId}/like")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<Map<String, Object>> alternarLike(
            @PathVariable Long postId, @AuthenticationPrincipal User user) {
        if (!postRepository.existsById(postId))
            return ResponseEntity.badRequest().body(Map.of("mensaje", "Publicación no encontrada."));

        boolean yaLeGusta = likeRepository.existsByPostIdAndUserId(postId, user.getId());
        if (yaLeGusta) {
            likeRepository.deleteByPostIdAndUserId(postId, user.getId());
        } else {
            CommunityPost post = postRepository.getReferenceById(postId);
            likeRepository.save(CommunityPostLike.builder().post(post).user(user).build());
        }
        long total = likeRepository.countByPostId(postId);
        return ResponseEntity.ok(Map.of("liked", !yaLeGusta, "likes", total));
    }

    /**
     * POST /api/comunidad/publicaciones — texto, solo imagen, o ambos. 
     * body puede incluir "groupId" (opcional): si viene, la publicación queda
     * asociada a ese grupo y solo la ven sus miembros (y el admin); si no
     * viene, es una publicación general visible para toda la cooperativa.
     * Mismo mecanismo y misma validación de membresía que ya usan las
     * encuestas (PollService.crearEncuesta).
     */
    @PostMapping("/publicaciones")
    public ResponseEntity<Map<String, Object>> crearPublicacion(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal User user) {
        String contenido = body.getOrDefault("contenido", "").toString().trim();

        String imagenUrl = null;
        if (body.containsKey("imagenUrl") && body.get("imagenUrl") != null) {
            String img = body.get("imagenUrl").toString();
            if (img.startsWith("data:image"))
                imagenUrl = img; // base64 data URL
        }

        if (contenido.isEmpty() && imagenUrl == null)
            return ResponseEntity.badRequest().body(Map.of("mensaje", "Escribe algo o agrega una imagen para publicar."));

        Group grupo = null;
        if (body.get("groupId") != null && !body.get("groupId").toString().isBlank()) {
            Long groupId = Long.valueOf(body.get("groupId").toString());
            grupo = groupRepository.findById(groupId)
                    .orElseThrow(() -> new IllegalArgumentException("Grupo no encontrado."));
            boolean esAdmin = user.getRoles().stream().anyMatch(r -> "admin".equalsIgnoreCase(r.getName()));
            if (!esAdmin && !groupMemberRepository.existsByGroupIdAndUserId(groupId, user.getId()))
                return ResponseEntity.status(403)
                        .body(Map.of("mensaje", "Solo los socios de un grupo pueden publicar para ese grupo."));
        }

        CommunityPost post = postRepository.save(
                CommunityPost.builder()
                        .user(user)
                        .group(grupo)
                        .contenido(contenido)
                        .imagenUrl(imagenUrl)
                        .tipo("texto")
                        .build());

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", post.getId());
        resp.put("autor", user.getFirstName() + (user.getLastName() != null ? " " + user.getLastName() : ""));
        resp.put("autorId", user.getId());
        resp.put("contenido", post.getContenido());
        resp.put("imagenUrl", post.getImagenUrl());
        resp.put("fecha", post.getCreatedAt().toString());
        resp.put("tipo", "texto");
        if (grupo != null) {
            resp.put("groupId", grupo.getId());
            resp.put("groupNombre", grupo.getName());
        }
        resp.put("likes", 0);
        resp.put("likedByMe", false);
        resp.put("mensaje", "Publicación creada exitosamente.");
        return ResponseEntity.ok(resp);
    }

    /**
     * DELETE /api/comunidad/publicaciones/{postId} — el autor de la
     * publicación (por ejemplo, si se equivocó al publicar) o un admin
     * (moderación) pueden borrarla. Los "me gusta" y reportes asociados se
     * eliminan en cascada a nivel de base de datos (FK ON DELETE CASCADE).
     */
    @DeleteMapping("/publicaciones/{postId}")
    public ResponseEntity<Map<String, String>> borrarPublicacion(
            @PathVariable Long postId, @AuthenticationPrincipal User user) {
        CommunityPost post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Publicación no encontrada."));

        boolean esAutor = post.getUser().getId().equals(user.getId());
        boolean esAdmin = user.getRoles().stream()
                .anyMatch(r -> "admin".equalsIgnoreCase(r.getName()));
        if (!esAutor && !esAdmin)
            throw new SecurityException("Solo el autor de la publicación o un administrador pueden borrarla.");

        postRepository.delete(post);
        return ResponseEntity.ok(Map.of("mensaje", "Publicación eliminada."));
    }

    /**
     * POST /api/comunidad/publicaciones/{postId}/reportar — un socio marca
     * una publicación ajena como inapropiada (ej. contenido grosero). No la
     * borra ni la oculta por sí solo: deja constancia y notifica a todos
     * los administradores para que la revisen y decidan si la eliminan.
     * Cada socio solo puede reportar una vez la misma publicación.
     */
    @PostMapping("/publicaciones/{postId}/reportar")
    public ResponseEntity<Map<String, Object>> reportarPublicacion(
            @PathVariable Long postId,
            @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal User user) {
        CommunityPost post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Publicación no encontrada."));

        if (post.getUser().getId().equals(user.getId()))
            throw new IllegalArgumentException("No puedes reportar tu propia publicación.");
        if (reportRepository.existsByPostIdAndReportedById(postId, user.getId()))
            throw new IllegalStateException("Ya reportaste esta publicación. Un administrador la revisará.");

        String motivo = body != null ? body.getOrDefault("motivo", "").trim() : "";
        reportRepository.save(CommunityPostReport.builder()
                .post(post)
                .reportedBy(user)
                .motivo(!motivo.isEmpty() ? motivo : "Sin motivo especificado.")
                .build());

        long totalReportes = reportRepository.countByPostId(postId);

        // Best-effort: si notificar falla, el reporte ya quedó registrado igual.
        try {
            String autorNombre = post.getUser().getFirstName()
                    + (post.getUser().getLastName() != null ? " " + post.getUser().getLastName() : "");
            userRepository.findAll().stream()
                    .filter(u -> u.getRoles().stream().anyMatch(r -> "admin".equalsIgnoreCase(r.getName())))
                    .forEach(admin -> notificationService.crearNotificacion(admin,
                            "Publicación reportada",
                            user.getFirstName() + " reportó una publicación de " + autorNombre
                                    + (!motivo.isEmpty() ? ": " + motivo : "")
                                    + ". Reportes totales: " + totalReportes + ".",
                            "post_reported", postId));
        } catch (Exception ignored) {
        }

        return ResponseEntity.ok(Map.of(
                "mensaje", "Publicación reportada. Un administrador la revisará.",
                "totalReportes", totalReportes));
    }

    /**
     * GET /api/comunidad/admin/publicaciones-reportadas — bandeja de
     * moderación: todas las publicaciones que tienen al menos un reporte,
     * con el detalle de quién reportó y por qué, ordenadas por cantidad de
     * reportes (más reportadas primero). Solo admin.
     */
    @PreAuthorize("hasRole('admin')")
    @GetMapping("/admin/publicaciones-reportadas")
    public ResponseEntity<List<Map<String, Object>>> publicacionesReportadas() {
        List<CommunityPostReport> reportes = reportRepository.findAllByOrderByCreatedAtDesc();

        Map<Long, List<CommunityPostReport>> porPost = new LinkedHashMap<>();
        for (CommunityPostReport r : reportes)
            porPost.computeIfAbsent(r.getPost().getId(), k -> new ArrayList<>()).add(r);

        List<Map<String, Object>> resultado = porPost.values().stream().map(reportesDelPost -> {
            CommunityPost post = reportesDelPost.get(0).getPost();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("postId", post.getId());
            m.put("autor", post.getUser().getFirstName()
                    + (post.getUser().getLastName() != null ? " " + post.getUser().getLastName() : ""));
            m.put("contenido", post.getContenido());
            m.put("imagenUrl", post.getImagenUrl());
            m.put("fecha", post.getCreatedAt().toString());
            m.put("totalReportes", reportesDelPost.size());
            m.put("reportes", reportesDelPost.stream().map(r -> {
                Map<String, Object> rm = new LinkedHashMap<>();
                rm.put("reportadoPor", r.getReportedBy().getFirstName()
                        + (r.getReportedBy().getLastName() != null ? " " + r.getReportedBy().getLastName() : ""));
                rm.put("motivo", r.getMotivo());
                rm.put("fecha", r.getCreatedAt().toString());
                return rm;
            }).toList());
            return m;
        }).collect(java.util.stream.Collectors.toCollection(ArrayList::new));

        resultado.sort((a, b) -> ((Integer) b.get("totalReportes")).compareTo((Integer) a.get("totalReportes")));
        return ResponseEntity.ok(resultado);
    }

    /**
     * POST /api/comunidad/admin/publicaciones/{postId}/descartar-reportes —
     * el admin revisó la publicación y decidió que no amerita eliminarla;
     * borra los reportes para que salga de la bandeja de moderación, sin
     * tocar la publicación en sí. Para eliminarla en vez de descartar los
     * reportes, se usa el DELETE de publicaciones normal (el admin también
     * puede borrar cualquier publicación).
     */
    @PreAuthorize("hasRole('admin')")
    @PostMapping("/admin/publicaciones/{postId}/descartar-reportes")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<Map<String, String>> descartarReportes(@PathVariable Long postId) {
        if (!postRepository.existsById(postId))
            throw new IllegalArgumentException("Publicación no encontrada.");
        reportRepository.deleteByPostId(postId);
        return ResponseEntity.ok(Map.of("mensaje", "Reportes descartados. La publicación se mantiene visible."));
    }

    // ── Comentarios ────────────────────────────────────────────────────────────

    /** GET /api/comunidad/publicaciones/{postId}/comentarios */
    @GetMapping("/publicaciones/{postId}/comentarios")
    public ResponseEntity<List<Map<String, Object>>> comentarios(@PathVariable Long postId) {
        if (!postRepository.existsById(postId))
            throw new IllegalArgumentException("Publicación no encontrada.");

        return ResponseEntity.ok(commentRepository.findByPostIdOrderByCreatedAtAsc(postId).stream()
                .map(c -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", c.getId());
                    m.put("autor", c.getUser().getFirstName()
                            + (c.getUser().getLastName() != null ? " " + c.getUser().getLastName() : ""));
                    m.put("autorId", c.getUser().getId());
                    m.put("contenido", c.getContenido());
                    m.put("fecha", c.getCreatedAt().toString());
                    return m;
                }).toList());
    }

    /** POST /api/comunidad/publicaciones/{postId}/comentarios */
    @PostMapping("/publicaciones/{postId}/comentarios")
    public ResponseEntity<Map<String, Object>> comentar(
            @PathVariable Long postId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal User user) {
        CommunityPost post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Publicación no encontrada."));

        String contenido = body.getOrDefault("contenido", "").trim();
        if (contenido.isEmpty())
            return ResponseEntity.badRequest().body(Map.of("mensaje", "Escribe algo para comentar."));

        CommunityPostComment comentario = commentRepository.save(CommunityPostComment.builder()
                .post(post).user(user).contenido(contenido).build());

        if (!post.getUser().getId().equals(user.getId())) {
            try {
                String resumen = contenido.length() > 80 ? contenido.substring(0, 80) + "..." : contenido;
                notificationService.crearNotificacion(post.getUser(),
                        "Nuevo comentario",
                        user.getFirstName() + " comentó tu publicación: \"" + resumen + "\"",
                        "post_comment", postId);
            } catch (Exception ignored) {
            }
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", comentario.getId());
        resp.put("autor", user.getFirstName() + (user.getLastName() != null ? " " + user.getLastName() : ""));
        resp.put("autorId", user.getId());
        resp.put("contenido", comentario.getContenido());
        resp.put("fecha", comentario.getCreatedAt().toString());
        return ResponseEntity.ok(resp);
    }

    /**
     * DELETE /api/comunidad/publicaciones/{postId}/comentarios/{commentId}
     * — puede borrarlo el autor del comentario, el autor de la publicación, o
     * un admin.
     */
    @DeleteMapping("/publicaciones/{postId}/comentarios/{commentId}")
    public ResponseEntity<Map<String, String>> borrarComentario(
            @PathVariable Long postId, @PathVariable Long commentId,
            @AuthenticationPrincipal User user) {
        CommunityPostComment comentario = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("Comentario no encontrado."));
        if (!comentario.getPost().getId().equals(postId))
            throw new IllegalArgumentException("El comentario no pertenece a esta publicación.");

        boolean esAutorComentario = comentario.getUser().getId().equals(user.getId());
        boolean esAutorPost = comentario.getPost().getUser().getId().equals(user.getId());
        boolean esAdmin = user.getRoles().stream()
                .anyMatch(r -> "admin".equalsIgnoreCase(r.getName()));
        if (!esAutorComentario && !esAutorPost && !esAdmin)
            throw new SecurityException("No tienes permiso para borrar este comentario.");

        commentRepository.delete(comentario);
        return ResponseEntity.ok(Map.of("mensaje", "Comentario eliminado."));
    }

    // ── Eventos ────────────────────────────────────────────────────────────────

    /**
     * GET /api/comunidad/eventos — igual que publicaciones/encuestas: los
     * eventos generales (sin grupo) los ve toda la cooperativa; los de un
     * grupo solo los ven sus miembros (el admin ve todo).
     */
    @GetMapping("/eventos")
    public ResponseEntity<List<Map<String, Object>>> eventos(@AuthenticationPrincipal User user) {
        boolean esAdmin = user != null && user.getRoles().stream()
                .anyMatch(r -> "admin".equalsIgnoreCase(r.getName()));
        Set<Long> misGrupos = (user == null || esAdmin) ? Set.of()
                : groupMemberRepository.findByUserId(user.getId()).stream()
                        .map(m -> m.getGroup().getId())
                        .collect(java.util.stream.Collectors.toSet());

        List<Map<String, Object>> result = new ArrayList<>();
        try {
            meetingRepository.findAllByOrderByFechaAsc().stream()
                    .filter(m -> esAdmin || user == null
                            || m.getGroup() == null || misGrupos.contains(m.getGroup().getId()))
                    .forEach(m -> {
                        Map<String, Object> ev = new LinkedHashMap<>();
                        ev.put("id", m.getId());
                        ev.put("titulo", m.getTitulo());
                        ev.put("tipo", "reunion");
                        ev.put("fecha", m.getFecha() != null ? m.getFecha().toLocalDate().toString() : "");
                        ev.put("hora", m.getFecha() != null ? m.getFecha().toLocalTime().toString() : "");
                        ev.put("descripcion", m.getDescripcion() != null ? m.getDescripcion() : "");
                        ev.put("lugar", m.getLugar() != null ? m.getLugar() : "");
                        ev.put("linkVirtual", m.getLinkVirtual() != null ? m.getLinkVirtual() : "");
                        ev.put("acta", m.getActa() != null ? m.getActa() : "");
                        if (m.getGroup() != null) {
                            ev.put("groupId", m.getGroup().getId());
                            ev.put("groupNombre", m.getGroup().getName());
                        }
                        result.add(ev);
                    });
        } catch (Exception ignored) {
        }

        if (result.isEmpty()) {
            Map<String, Object> def = new LinkedHashMap<>();
            def.put("id", 1L);
            def.put("titulo", "Reunión mensual del grupo");
            def.put("tipo", "reunion");
            def.put("fecha", LocalDate.now().plusDays(7).toString());
            def.put("hora", "09:00");
            def.put("descripcion", "Revisión de aportes y créditos del mes");
            def.put("lugar", "");
            result.add(def);
        }
        return ResponseEntity.ok(result);
    }

    /**
     * PATCH /api/comunidad/eventos/{id}/acta — publica o edita el acta
     * (minuta) de una reunión ya realizada. body: { "acta": "..." }
     */
    @PatchMapping("/eventos/{id}/acta")
    public ResponseEntity<Map<String, Object>> publicarActa(
            @PathVariable Long id, @RequestBody Map<String, String> body,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(groupService.publicarActa(id, body.get("acta"), user));
    }

    /**
     * POST /api/comunidad/eventos — además publica un aviso en el muro de
     * Publicaciones.
     *
     * body puede incluir "groupId" (opcional, igual que en publicaciones):
     * si viene, el evento queda asociado a ese grupo y solo lo ven sus
     * miembros (y el admin); si no viene, es un evento general visible
     * para toda la cooperativa.
     */
    @PostMapping("/eventos")
    public ResponseEntity<Map<String, Object>> crearEvento(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal User user) {

        String titulo = body.getOrDefault("titulo", "Evento").toString();
        String desc = body.getOrDefault("descripcion", "").toString();
        String lugar = body.getOrDefault("lugar", "").toString();
        String linkVirtual = body.getOrDefault("linkVirtual", "").toString().trim();
        String fechaStr = body.getOrDefault("fecha", LocalDate.now().plusDays(7).toString()).toString();
        String horaStr = body.getOrDefault("hora", "").toString();

        LocalDate fecha = LocalDate.now().plusDays(7);
        try {
            fecha = LocalDate.parse(fechaStr);
        } catch (Exception ignored) {
        }

        LocalTime hora = LocalTime.of(9, 0);
        if (!horaStr.isBlank()) {
            try {
                hora = LocalTime.parse(horaStr);
            } catch (Exception ignored) {
            }
        }
        LocalDateTime fechaDt = fecha.atTime(hora);

        // Validación básica: si mandan un link, que al menos parezca una URL
        if (!linkVirtual.isBlank() && !linkVirtual.matches("^https?://.+")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("mensaje", "El link de la reunión debe empezar con http:// o https://"));
        }

        // Grupo destino: igual que en publicaciones, opcional. Si viene, el
        // admin puede usar cualquier grupo sin ser miembro; un socio solo
        // puede crear el evento para un grupo del que sí es miembro.
        Group grupo = null;
        if (body.get("groupId") != null && !body.get("groupId").toString().isBlank()) {
            Long groupId = Long.valueOf(body.get("groupId").toString());
            grupo = groupRepository.findById(groupId)
                    .orElse(null);
            if (grupo == null)
                return ResponseEntity.badRequest().body(Map.of("mensaje", "Grupo no encontrado."));
            boolean esAdmin = user.getRoles().stream().anyMatch(r -> "admin".equalsIgnoreCase(r.getName()));
            if (!esAdmin && !groupMemberRepository.existsByGroupIdAndUserId(groupId, user.getId()))
                return ResponseEntity.status(403)
                        .body(Map.of("mensaje", "Solo los socios de un grupo pueden crear eventos para ese grupo."));
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        try {
            GroupMeeting.GroupMeetingBuilder builder = GroupMeeting.builder()
                    .titulo(titulo).descripcion(desc).lugar(lugar).linkVirtual(linkVirtual.isBlank() ? null : linkVirtual)
                    .fecha(fechaDt).createdBy(user).group(grupo);

            GroupMeeting saved = meetingRepository.save(builder.build());
            resp.put("id", saved.getId());
            resp.put("titulo", saved.getTitulo());
            resp.put("tipo", "reunion");
            resp.put("fecha", saved.getFecha().toLocalDate().toString());
            resp.put("hora", saved.getFecha().toLocalTime().toString());
            resp.put("descripcion", saved.getDescripcion() != null ? saved.getDescripcion() : "");
            resp.put("lugar", saved.getLugar() != null ? saved.getLugar() : "");
            if (grupo != null) {
                resp.put("groupId", grupo.getId());
                resp.put("groupNombre", grupo.getName());
            }

            // Publicar también un aviso en el muro de Publicaciones, con el
            // mismo alcance (grupo) que el evento — un evento de grupo no
            // debe anunciarse en el muro general.
            try {
                String aviso = " Nuevo evento: " + titulo
                        + (lugar != null && !lugar.isBlank() ? " · " + lugar : "")
                        + (desc != null && !desc.isBlank() ? "\n" + desc : "");
                postRepository.save(CommunityPost.builder()
                        .user(user)
                        .group(grupo)
                        .contenido(aviso)
                        .tipo("evento")
                        .eventoFecha(fecha)
                        .build());
            } catch (Exception ignored) {
                /* el evento ya quedó creado; la publicación es secundaria */ }

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "mensaje", "No se pudo crear el evento: " + e.getMessage()));
        }
        resp.put("mensaje", "Evento creado exitosamente.");
        return ResponseEntity.ok(resp);
    }
}