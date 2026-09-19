package com.bankomunal.service;

import com.bankomunal.entity.*;
import com.bankomunal.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PollService {

    private final PollRepository pollRepository;
    private final PollOptionRepository pollOptionRepository;
    private final PollVoteRepository pollVoteRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;

    /** Crear encuesta con duración opcional (fechaCierre ISO-8601) */
    @Transactional
    public Map<String, Object> crearEncuesta(Long groupId, String titulo, String descripcion,
            List<String> opciones, boolean anonima, boolean cambioRegla, int umbral,
            String fechaCierreStr, User creador) {

        Poll.PollBuilder builder = Poll.builder()
                .titulo(titulo).descripcion(descripcion)
                .isAnonymous(anonima).isRuleChange(cambioRegla)
                .approvalThreshold(umbral).createdBy(creador)
                .status(Poll.PollStatus.open);

        if (fechaCierreStr != null && !fechaCierreStr.isBlank()) {
            try {
                builder.endsAt(LocalDateTime.parse(fechaCierreStr));
            } catch (Exception ignored) {
            }
        }
        if (groupId != null) {
            Group g = groupRepository.findById(groupId)
                    .orElseThrow(() -> new IllegalArgumentException("Grupo no encontrado."));
            boolean esAdmin = creador.getRoles().stream()
                    .anyMatch(r -> "admin".equalsIgnoreCase(r.getName()));
            if (!esAdmin && !groupMemberRepository.existsByGroupIdAndUserId(groupId, creador.getId()))
                throw new SecurityException("Solo los socios de un grupo pueden publicar encuestas para ese grupo.");
            builder.group(g);
        }

        Poll poll = pollRepository.save(builder.build());
        for (String op : opciones)
            if (op != null && !op.isBlank())
                pollOptionRepository.save(PollOption.builder().poll(poll).texto(op).build());

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", poll.getId());
        resp.put("titulo", poll.getTitulo());
        resp.put("fechaCierre", poll.getEndsAt() != null ? poll.getEndsAt().toString() : null);
        resp.put("mensaje", "Encuesta creada exitosamente.");
        return resp;
    }

    /** Votar — valida expiración por fecha */
    @Transactional
    public Map<String, Object> votar(Long pollId, Long optionId, User user) {
        if (pollVoteRepository.existsByPollIdAndUserId(pollId, user.getId()))
            throw new IllegalStateException("Ya has votado en esta encuesta.");

        Poll poll = pollRepository.findById(pollId)
                .orElseThrow(() -> new IllegalArgumentException("Encuesta no encontrada."));

        /*
         * Si la encuesta pertenece a un grupo (incluye las de préstamos
         * solidarios, que reutilizan este mismo sistema — ver
         * GroupLoanService), solo los socios de ESE grupo pueden votar.
         */
        if (poll.getGroup() != null
                && !groupMemberRepository.existsByGroupIdAndUserId(poll.getGroup().getId(), user.getId()))
            throw new SecurityException("Solo los socios de este grupo pueden votar en esta encuesta.");

        /*
         * Encuestas generales (sin grupo): mismo criterio que las de grupo,
         * solo que aquí el "grupo" es toda la cooperativa — solo un socio
         * real puede votar. El admin del sistema es un rol de staff (ver
         * RolePermissionSeeder: "admin" y "socio" son roles excluyentes, no
         * hay usuarios con ambos).
         */
        if (poll.getGroup() == null && user.getRoles().stream()
                .anyMatch(r -> "admin".equalsIgnoreCase(r.getName())))
            throw new SecurityException("El administrador no vota en las encuestas; solo los socios deciden.");

        if (poll.getEndsAt() != null && LocalDateTime.now().isAfter(poll.getEndsAt())) {
            if (poll.getStatus() == Poll.PollStatus.open) {
                poll.setStatus(Poll.PollStatus.closed);
                pollRepository.save(poll);
            }
            throw new IllegalStateException("Esta encuesta ha expirado.");
        }
        if (poll.getStatus() != Poll.PollStatus.open)
            throw new IllegalStateException("La encuesta ya está cerrada.");

        PollOption opt = pollOptionRepository.findById(optionId)
                .orElseThrow(() -> new IllegalArgumentException("Opción no encontrada."));

        pollVoteRepository.save(PollVote.builder().poll(poll).user(user).option(opt).build());
        if (poll.isRuleChange())
            evaluarUmbral(poll);
        return Map.of("mensaje", "Voto registrado exitosamente.");
    }

    /** Tarea programada: cerrar encuestas vencidas cada minuto */
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void cerrarEncuestasVencidas() {
        pollRepository.findByStatus(Poll.PollStatus.open).stream()
                .filter(p -> p.getEndsAt() != null && LocalDateTime.now().isAfter(p.getEndsAt()))
                .forEach(p -> {
                    p.setStatus(Poll.PollStatus.closed);
                    pollRepository.save(p);
                });
    }

    private void evaluarUmbral(Poll poll) {
        List<PollOption> opts = pollOptionRepository.findByPollId(poll.getId());
        long total = pollVoteRepository.findByPollId(poll.getId()).size();
        if (total == 0)
            return;
        long max = opts.stream()
                .mapToLong(o -> pollVoteRepository.countByOptionId(o.getId())).max().orElse(0);
        if ((double) max / total * 100 >= poll.getApprovalThreshold()) {
            poll.setStatus(Poll.PollStatus.closed);
            pollRepository.save(poll);
        }
    }

    /**
     *  - Con groupId explícito: solo lo puede consultar un socio de ESE
     *    grupo o un admin.
     *  - Sin groupId (vista general "Encuestas"): se muestran las encuestas
     *    generales (sin grupo, abiertas a toda la comunidad) más las de los
     *    grupos a los que el usuario pertenece — un admin sigue viendo todo,
     *    para mantener la supervisión que ya tenía en otras partes del panel.
     */
    @Transactional
    public List<Map<String, Object>> getEncuestas(Long groupId, User caller) {
        boolean esAdmin = caller.getRoles().stream()
                .anyMatch(r -> "admin".equalsIgnoreCase(r.getName()));

        List<Poll> polls;
        if (groupId != null) {
            if (!esAdmin && !groupMemberRepository.existsByGroupIdAndUserId(groupId, caller.getId()))
                throw new SecurityException("Solo los socios de este grupo pueden ver sus encuestas.");
            polls = pollRepository.findByGroupIdOrderByCreatedAtDesc(groupId);
        } else if (esAdmin) {
            polls = pollRepository.findAllByOrderByCreatedAtDesc();
        } else {
            Set<Long> misGrupos = groupMemberRepository.findByUserId(caller.getId()).stream()
                    .map(m -> m.getGroup().getId())
                    .collect(java.util.stream.Collectors.toSet());
            polls = pollRepository.findAllByOrderByCreatedAtDesc().stream()
                    .filter(p -> p.getGroup() == null || misGrupos.contains(p.getGroup().getId()))
                    .toList();
        }

        DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        return polls.stream().map(p -> {
            if (p.getEndsAt() != null && LocalDateTime.now().isAfter(p.getEndsAt())
                    && p.getStatus() == Poll.PollStatus.open) {
                p.setStatus(Poll.PollStatus.closed);
                pollRepository.save(p);
            }
            List<PollOption> opts = pollOptionRepository.findByPollId(p.getId());
            long totalVotos = 0;
            List<Map<String, Object>> optsResp = new ArrayList<>();
            for (PollOption o : opts) {
                long votos = pollVoteRepository.countByOptionId(o.getId());
                totalVotos += votos;
                optsResp.add(Map.of("id", o.getId(), "texto", o.getTexto(), "votos", votos));
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("titulo", p.getTitulo());
            m.put("descripcion", p.getDescripcion() != null ? p.getDescripcion() : "");
            m.put("estado", p.getStatus().name());
            m.put("opciones", optsResp);
            m.put("totalVotos", totalVotos);
            m.put("anonima", p.isAnonymous());
            m.put("esRuleChange", p.isRuleChange());
            m.put("umbral", p.getApprovalThreshold());
            m.put("fechaCierre", p.getEndsAt() != null ? p.getEndsAt().format(fmt) : null);
            m.put("createdAt", p.getCreatedAt() != null ? p.getCreatedAt().format(fmt) : null);
            m.put("creadorId", p.getCreatedBy() != null ? p.getCreatedBy().getId() : null);
            // groupId: null = encuesta general (frontend la usa para saber si
            // debe ocultarle el botón "Votar" al admin, que no vota en
            // encuestas generales — ver PollService.votar()).
            m.put("groupId", p.getGroup() != null ? p.getGroup().getId() : null);
            return m;
        }).toList();
    }

    @Transactional(readOnly = true)
    public Map<Long, Long> getMisVotos(User user) {
        List<PollVote> votos = pollVoteRepository.findByUserId(user.getId());
        Map<Long, Long> resultado = new LinkedHashMap<>();
        for (PollVote v : votos) {
            resultado.put(v.getPoll().getId(), v.getOption().getId());
        }
        return resultado;
    }

    /**
     * Cerrar encuesta manualmente.
     * Solo puede hacerlo el admin del sistema o el propio creador de la encuesta.
     */
    @Transactional
    public void cerrarManual(Long pollId, User caller) {
        Poll p = pollRepository.findById(pollId)
                .orElseThrow(() -> new IllegalArgumentException("Encuesta no encontrada."));

        boolean esAdmin = caller.getRoles().stream()
                .anyMatch(r -> "admin".equalsIgnoreCase(r.getName()));
        boolean esCreador = p.getCreatedBy() != null &&
                p.getCreatedBy().getId().equals(caller.getId());

        if (!esAdmin && !esCreador)
            throw new SecurityException(
                    "Solo el administrador o el creador de la encuesta pueden cerrarla.");

        p.setStatus(Poll.PollStatus.closed);
        pollRepository.save(p);
    }

    /**
     * Lista quién votó en una encuesta — SIN revelar qué opción eligió cada
     * quien, para no romper el anonimato del voto en sí. Solo puede verla el
     * admin del sistema o el creador de la encuesta (mismo criterio que
     * cerrarManual).
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getVotantes(Long pollId, User caller) {
        Poll p = pollRepository.findById(pollId)
                .orElseThrow(() -> new IllegalArgumentException("Encuesta no encontrada."));

        boolean esAdmin = caller.getRoles().stream()
                .anyMatch(r -> "admin".equalsIgnoreCase(r.getName()));
        boolean esCreador = p.getCreatedBy() != null &&
                p.getCreatedBy().getId().equals(caller.getId());

        if (!esAdmin && !esCreador)
            throw new SecurityException(
                    "Solo el administrador o el creador de la encuesta pueden ver quién votó.");

        return pollVoteRepository.findByPollId(pollId).stream()
                .map(v -> {
                    Map<String, Object> m = new java.util.HashMap<>();
                    m.put("userId", v.getUser().getId());
                    m.put("nombre", v.getUser().getFirstName()
                            + (v.getUser().getLastName() != null ? " " + v.getUser().getLastName() : ""));
                    m.put("fechaVoto", v.getVotedAt());
                    // Nota: NO se incluye v.getOption() a propósito — el voto en sí
                    // sigue siendo anónimo, solo se confirma que la persona participó.
                    return m;
                })
                .toList();
    }
}
