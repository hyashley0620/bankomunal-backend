package com.bankomunal.controller;

import com.bankomunal.entity.User;
import com.bankomunal.service.PollService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/encuestas")
@RequiredArgsConstructor
public class PollController {

    private final PollService pollService;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listar(
            @RequestParam(required = false) Long groupId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(pollService.getEncuestas(groupId, user));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> crear(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal User user) {

        Long groupId = body.get("groupId") != null
                ? Long.valueOf(body.get("groupId").toString())
                : null;
        String titulo = body.getOrDefault("titulo", "Sin título").toString();
        String desc = body.getOrDefault("descripcion", "").toString();

        @SuppressWarnings("unchecked")
        List<String> ops = body.get("opciones") instanceof List<?>
                ? (List<String>) body.get("opciones")
                : List.of("Sí", "No");

        boolean anonima = Boolean.parseBoolean(body.getOrDefault("anonima", "false").toString());
        boolean cambioRegl = Boolean.parseBoolean(body.getOrDefault("cambioRegla", "false").toString());
        int umbral = Integer.parseInt(body.getOrDefault("umbral", "51").toString());
        String fechaCierre = body.containsKey("fechaCierre") && body.get("fechaCierre") != null
                ? body.get("fechaCierre").toString()
                : null;

        return ResponseEntity.ok(pollService.crearEncuesta(
                groupId, titulo, desc, ops, anonima, cambioRegl, umbral, fechaCierre, user));
    }

    @PostMapping("/{pollId}/votar")
    public ResponseEntity<Map<String, Object>> votar(
            @PathVariable Long pollId,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal User user) {
        Long optionId = Long.valueOf(body.get("optionId").toString());
        return ResponseEntity.ok(pollService.votar(pollId, optionId, user));
    }

    /**
     * Devuelve los votos del usuario autenticado como { pollId: optionId }.
     */
    @GetMapping("/mis-votos")
    public ResponseEntity<Map<Long, Long>> misVotos(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(pollService.getMisVotos(user));
    }

    /**
     * Lista quién votó en una encuesta (sin revelar qué opción eligieron) —
     * solo visible para el admin o el creador de la encuesta.
     */
    @GetMapping("/{pollId}/votantes")
    public ResponseEntity<?> votantes(@PathVariable Long pollId, @AuthenticationPrincipal User user) {
        try {
            return ResponseEntity.ok(pollService.getVotantes(pollId, user));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("mensaje", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("mensaje", e.getMessage()));
        }
    }

    /**
     * El usuario autenticado al servicio, valida que sea
     * admin o el propio creador de la encuesta antes de cerrarla.
     */
    @PatchMapping("/{pollId}/cerrar")
    public ResponseEntity<Map<String, Object>> cerrar(
            @PathVariable Long pollId,
            @AuthenticationPrincipal User user) {
        pollService.cerrarManual(pollId, user);
        return ResponseEntity.ok(Map.of("mensaje", "Encuesta cerrada."));
    }
}