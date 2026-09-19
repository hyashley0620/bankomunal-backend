package com.bankomunal.controller;

import com.bankomunal.entity.User;
import com.bankomunal.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /**
     * Bandeja de "mensajes recientes": une conversaciones directas y chats de
     * grupo en una sola lista ordenada por fecha del último mensaje, para que
     * el socio no tenga que ir a buscar con quién ya habló.
     */
    @GetMapping("/hilos")
    public ResponseEntity<List<Map<String, Object>>> hilos(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(chatService.getHilos(user.getId()));
    }

    /** Historial conversación directa */
    @GetMapping("/conversacion/{otroUserId}")
    public ResponseEntity<List<Map<String, Object>>> conversacion(
            @PathVariable Long otroUserId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(chatService.getConversacion(user.getId(), otroUserId));
    }

    /** Mensajes de grupo */
    @GetMapping("/grupo/{groupId}")
    public ResponseEntity<List<Map<String, Object>>> mensajesGrupo(
            @PathVariable Long groupId, @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(chatService.getMensajesGrupo(groupId, user));
    }

    /** Enviar mensaje */
    @PostMapping("/enviar")
    public ResponseEntity<Map<String, Object>> enviar(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal User user) {
        Long receiverId = body.get("receiverId") != null
                ? Long.valueOf(body.getOrDefault("receiverId", "").toString())
                : null;
        Long groupId = body.get("groupId") != null
                ? Long.valueOf(body.getOrDefault("groupId", "").toString())
                : null;
        String texto = body.getOrDefault("mensaje", "").toString();
        return ResponseEntity.ok(chatService.enviarMensaje(user.getId(), receiverId, groupId, texto));
    }
}