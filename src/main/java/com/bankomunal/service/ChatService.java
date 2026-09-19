package com.bankomunal.service;

import com.bankomunal.entity.*;
import com.bankomunal.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatMessageRepository chatRepository;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupChatReadRepository groupChatReadRepository;

    @Transactional
    public List<Map<String, Object>> getConversacion(Long userId1, Long userId2) {
        List<ChatMessage> mensajes = chatRepository.findConversation(userId1, userId2);

        // Marcar como leídos los mensajes que userId1 (quien está consultando
        // ahora mismo) recibió de userId2 y todavía no había abierto.
        mensajes.stream()
                .filter(m -> !m.isLeido()
                        && m.getReceiver() != null && m.getReceiver().getId().equals(userId1)
                        && m.getSender().getId().equals(userId2))
                .forEach(m -> {
                    m.setLeido(true);
                    chatRepository.save(m);
                });

        return mensajes.stream()
                .map(m -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", m.getId());
                    map.put("senderId", m.getSender().getId());
                    map.put("senderNombre", m.getSender().getFirstName());
                    map.put("mensaje", m.getMensaje());
                    map.put("isRead", m.isLeido());
                    map.put("createdAt", m.getCreatedAt().toString());
                    return map;
                }).toList();
    }

    /**
     * Mensajes de un chat de grupo. Solo puede leerlos un miembro activo del
     * grupo (o un admin) — antes cualquier usuario autenticado podía leer el
     * chat de cualquier grupo con solo adivinar/probar su id.
     */
    public List<Map<String, Object>> getMensajesGrupo(Long groupId, User requester) {
        boolean esAdmin = requester.getRoles().stream()
                .anyMatch(r -> "admin".equalsIgnoreCase(r.getName()));
        boolean esMiembro = groupMemberRepository.existsByGroupIdAndUserId(groupId, requester.getId());
        if (!esAdmin && !esMiembro) {
            throw new SecurityException("No tienes permiso para ver los mensajes de este grupo.");
        }
        List<Map<String, Object>> mensajes = chatRepository.findByGroupIdOrderByCreatedAtAsc(groupId).stream()
                .map(m -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", m.getId());
                    map.put("senderId", m.getSender().getId());
                    map.put("senderNombre", m.getSender().getFirstName());
                    map.put("mensaje", m.getMensaje());
                    map.put("createdAt", m.getCreatedAt().toString());
                    return map;
                }).toList();

        /* Al abrir el chat del grupo, se marca como leído hasta ahora. Solo
           para miembros reales (si un admin solo está inspeccionando, no le
           quitamos lo "no leído" a los miembros de verdad). */
        if (esMiembro) {
            marcarGrupoLeido(groupId, requester.getId());
        }
        return mensajes;
    }

    @Transactional
    protected void marcarGrupoLeido(Long groupId, Long userId) {
        GroupChatRead registro = groupChatReadRepository.findByGroupIdAndUserId(groupId, userId)
                .orElse(GroupChatRead.builder()
                        .group(groupRepository.getReferenceById(groupId))
                        .user(userRepository.getReferenceById(userId))
                        .build());
        registro.setLastReadAt(LocalDateTime.now());
        groupChatReadRepository.save(registro);
    }

    @Transactional
    public Map<String, Object> enviarMensaje(Long senderId, Long receiverId, Long groupId, String texto) {
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new IllegalArgumentException("Remitente no encontrado."));

        ChatMessage.ChatMessageBuilder builder = ChatMessage.builder()
                .sender(sender)
                .mensaje(texto)
                .leido(false);

        if (receiverId != null) {
            User receiver = userRepository.findById(receiverId)
                    .orElseThrow(() -> new IllegalArgumentException("Destinatario no encontrado."));
            builder.receiver(receiver);
        }
        if (groupId != null) {
            Group g = groupRepository.findById(groupId)
                    .orElseThrow(() -> new IllegalArgumentException("Grupo no encontrado."));
            boolean esAdmin = sender.getRoles().stream()
                    .anyMatch(r -> "admin".equalsIgnoreCase(r.getName()));
            if (!esAdmin && !groupMemberRepository.existsByGroupIdAndUserId(groupId, senderId)) {
                throw new SecurityException("No perteneces a este grupo.");
            }
            builder.group(g);
        }

        ChatMessage saved = chatRepository.save(builder.build());
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", saved.getId());
        resp.put("mensaje", "Mensaje enviado.");
        resp.put("createdAt", saved.getCreatedAt().toString());
        return resp;
    }

    /**
     * Lista unificada de "hilos" (conversaciones directas + chats de grupo) del
     * usuario, ordenada por fecha del último mensaje descendente. Pensada para
     * pintar una bandeja de "Mensajes recientes" sin que el socio tenga que
     * recordar con quién ya habló.
     */
    public List<Map<String, Object>> getHilos(Long userId) {
        List<Map<String, Object>> hilos = new ArrayList<>();

        /* ── Conversaciones directas ── */
        List<ChatMessage> directos = chatRepository.findDirectMessagesForUser(userId);
        Map<Long, ChatMessage> ultimoPorUsuario = new LinkedHashMap<>();
        Map<Long, Long> noLeidosPorUsuario = new HashMap<>();
        for (ChatMessage m : directos) {
            Long otroId = m.getSender().getId().equals(userId) ? m.getReceiver().getId() : m.getSender().getId();
            ultimoPorUsuario.putIfAbsent(otroId, m); // ya viene ordenado DESC → el primero es el más reciente
            if (!m.isLeido() && m.getReceiver() != null && m.getReceiver().getId().equals(userId)) {
                noLeidosPorUsuario.merge(otroId, 1L, Long::sum);
            }
        }
        for (Map.Entry<Long, ChatMessage> e : ultimoPorUsuario.entrySet()) {
            Long otroId = e.getKey();
            ChatMessage ultimo = e.getValue();
            User otro = userRepository.findById(otroId).orElse(null);
            if (otro == null)
                continue;
            Map<String, Object> hilo = new LinkedHashMap<>();
            hilo.put("tipo", "user");
            hilo.put("id", otroId);
            hilo.put("nombre", (otro.getFirstName() != null ? otro.getFirstName() : "") +
                    (otro.getLastName() != null ? " " + otro.getLastName() : ""));
            hilo.put("ultimoMensaje", ultimo.getMensaje());
            hilo.put("ultimoMensajeFecha", ultimo.getCreatedAt().toString());
            hilo.put("esMio", ultimo.getSender().getId().equals(userId));
            hilo.put("noLeidos", noLeidosPorUsuario.getOrDefault(otroId, 0L));
            hilos.add(hilo);
        }

        /* ── Chats de grupo (de los grupos activos donde el usuario es miembro) ── */
        List<GroupMember> misGrupos = groupMemberRepository.findByUserId(userId).stream()
                .filter(gm -> gm.getStatus() == GroupMember.MemberStatus.active)
                .toList();
        if (!misGrupos.isEmpty()) {
            List<Long> groupIds = misGrupos.stream().map(gm -> gm.getGroup().getId()).toList();
            List<ChatMessage> mensajesGrupo = chatRepository.findGroupMessagesForGroups(groupIds);
            Map<Long, ChatMessage> ultimoPorGrupo = new LinkedHashMap<>();
            Map<Long, List<ChatMessage>> mensajesPorGrupo = new HashMap<>();
            for (ChatMessage m : mensajesGrupo) {
                ultimoPorGrupo.putIfAbsent(m.getGroup().getId(), m);
                mensajesPorGrupo.computeIfAbsent(m.getGroup().getId(), k -> new ArrayList<>()).add(m);
            }
            /* Última lectura de este usuario en cada uno de sus grupos */
            Map<Long, LocalDateTime> lecturaPorGrupo = new HashMap<>();
            groupChatReadRepository.findByUserId(userId)
                    .forEach(r -> lecturaPorGrupo.put(r.getGroup().getId(), r.getLastReadAt()));

            for (GroupMember gm : misGrupos) {
                Long groupId = gm.getGroup().getId();
                ChatMessage ultimo = ultimoPorGrupo.get(groupId);
                if (ultimo == null)
                    continue; // grupo sin mensajes aún: no se muestra en "recientes"

                LocalDateTime ultimaLectura = lecturaPorGrupo.get(groupId);
                long noLeidos = mensajesPorGrupo.getOrDefault(groupId, List.of()).stream()
                        .filter(m -> !m.getSender().getId().equals(userId))
                        .filter(m -> ultimaLectura == null || m.getCreatedAt().isAfter(ultimaLectura))
                        .count();

                Map<String, Object> hilo = new LinkedHashMap<>();
                hilo.put("tipo", "grupo");
                hilo.put("id", groupId);
                hilo.put("nombre", gm.getGroup().getName());
                hilo.put("ultimoMensaje", ultimo.getMensaje());
                hilo.put("ultimoMensajeFecha", ultimo.getCreatedAt().toString());
                hilo.put("esMio", ultimo.getSender().getId().equals(userId));
                hilo.put("noLeidos", noLeidos);
                hilos.add(hilo);
            }
        }

        hilos.sort((a, b) -> ((String) b.get("ultimoMensajeFecha")).compareTo((String) a.get("ultimoMensajeFecha")));
        return hilos;
    }
}
