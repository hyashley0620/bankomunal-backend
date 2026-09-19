package com.bankomunal.service;

import com.bankomunal.entity.*;
import com.bankomunal.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class GroupService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionService transactionService;
    private final TransactionLimitService transactionLimitService;
    private final AccountService accountService;
    private final GroupMeetingRepository meetingRepository;

    /** Crear grupo — el creador no puede ya pertenecer a otro grupo (ver
     *  {@link #agregarMiembro} para la misma regla al agregar un miembro). */
    @Transactional
    public Map<String, Object> crear(String nombre, String tipo, String descripcion, User creador) {
        validarNoPerteneceYaAOtroGrupo(creador.getId(), null);

        Group grupo = groupRepository.save(Group.builder()
                .name(nombre).tipo(tipo).descripcion(descripcion)
                .status(Group.GroupStatus.active)
                .createdBy(creador)
                .fondoComun(BigDecimal.ZERO)
                .build());

        memberRepository.save(GroupMember.builder()
                .group(grupo).user(creador)
                .role(GroupMember.MemberRole.presidente)
                .status(GroupMember.MemberStatus.active)
                .build());

        return Map.of("id", grupo.getId(), "nombre", grupo.getName(),
                "mensaje", "Grupo '" + nombre + "' creado exitosamente.");
    }

    /**
     * Un socio solo puede pertenecer a un grupo a la vez.
     */
    private void validarNoPerteneceYaAOtroGrupo(Long userId, Long excluirGroupId) {
        boolean yaPerteneceAOtroGrupo = memberRepository.findByUserId(userId).stream()
                .filter(m -> excluirGroupId == null || !m.getGroup().getId().equals(excluirGroupId))
                .anyMatch(m -> m.getStatus() == GroupMember.MemberStatus.active
                        || m.getStatus() == GroupMember.MemberStatus.suspended);
        if (yaPerteneceAOtroGrupo)
            throw new IllegalArgumentException(
                    "Este socio ya pertenece a otro grupo. Un socio solo puede pertenecer a un grupo a la vez.");
    }

    /** Agregar un usuario existente a un grupo — solo un líder del grupo o admin del sistema puede hacerlo */
    @Transactional
    public Map<String, Object> agregarMiembro(Long groupId, Long userId, String rol, User caller) {
        Group grupo = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Grupo no encontrado."));
        User nuevo = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));

        if (memberRepository.existsByGroupIdAndUserId(groupId, userId))
            throw new IllegalArgumentException("El usuario ya es miembro de este grupo.");

        validarNoPerteneceYaAOtroGrupo(userId, groupId);

        GroupMember.MemberRole memberRole;
        try {
            memberRole = GroupMember.MemberRole.valueOf(rol != null ? rol.toLowerCase() : "miembro");
        } catch (IllegalArgumentException e) {
            memberRole = GroupMember.MemberRole.miembro;
        }

        validarPuedeAsignarRol(groupId, caller, memberRole);
        liberarRolSiOcupado(groupId, memberRole, null);

        GroupMember member = memberRepository.save(GroupMember.builder()
                .group(grupo).user(nuevo)
                .role(memberRole)
                .status(GroupMember.MemberStatus.active)
                .build());

        return Map.of(
                "id", member.getId(),
                "usuario", nuevo.getFirstName() + " " + (nuevo.getLastName() != null ? nuevo.getLastName() : ""),
                "rol", memberRole.name(),
                "grupo", grupo.getName(),
                "mensaje", "Miembro agregado exitosamente al grupo '" + grupo.getName() + "'.");
    }

    /** Listar miembros activos de un grupo */
    public List<Map<String, Object>> getMiembrosGrupo(Long groupId) {
        return memberRepository.findByGroupId(groupId).stream()
                .filter(m -> m.getStatus() == GroupMember.MemberStatus.active
                        || m.getStatus() == GroupMember.MemberStatus.suspended)
                .map(m -> {
                    User u = m.getUser();
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", m.getId());
                    map.put("userId", u.getId());
                    map.put("nombre", u.getFirstName() + (u.getLastName() != null ? " " + u.getLastName() : ""));
                    map.put("email", u.getEmail());
                    map.put("rol", m.getRole().name());
                    map.put("estado", m.getStatus().name());
                    return map;
                }).toList();
    }

    /** Gestionar miembro (expulsar / suspender / reactivar / cambiar-rol) */
    @Transactional
    public Map<String, Object> gestionarMiembro(Long groupId, Long userId,
            String accion, String nuevoRol, String motivo, User caller) {

        validarEsLiderOAdmin(groupId, caller);

        GroupMember member = memberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Miembro no encontrado en el grupo."));

        switch (accion.toLowerCase()) {
            case "expulsar" -> {
                validarNoTocarPresidenteSinConsentimiento(member, caller);
                member.setStatus(GroupMember.MemberStatus.expelled);
                member.setMotivoExpulsion(motivo != null && !motivo.isBlank() ? motivo : null);
                member.setExpelledAt(LocalDateTime.now());
            }
            case "suspender" -> {
                validarNoTocarPresidenteSinConsentimiento(member, caller);
                member.setStatus(GroupMember.MemberStatus.suspended);
            }
            case "activar" -> {
                member.setStatus(GroupMember.MemberStatus.active);
                member.setMotivoExpulsion(null);
                member.setExpelledAt(null);
            }
            case "cambiar-rol" -> {
                GroupMember.MemberRole rolNuevo;
                try {
                    rolNuevo = GroupMember.MemberRole.valueOf(
                            nuevoRol != null ? nuevoRol.toLowerCase() : "miembro");
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Rol de grupo inválido: " + nuevoRol);
                }
                validarPuedeAsignarRol(groupId, caller, rolNuevo);
                if (rolNuevo != GroupMember.MemberRole.presidente) {
                    validarNoTocarPresidenteSinConsentimiento(member, caller);
                }
                liberarRolSiOcupado(groupId, rolNuevo, member.getUser().getId());
                member.setRole(rolNuevo);
            }
            default -> throw new IllegalArgumentException("Acción inválida: " + accion);
        }
        memberRepository.save(member);
        return Map.of("mensaje", "Acción '" + accion + "' aplicada al miembro.");
    }

    /**
     * Publica o edita el acta (minuta) de una reunión ya creada. Para una
     * reunión de grupo, la puede publicar el líder de ese grupo o un admin
     * del sistema (misma validación que gestionarMiembro). Para una reunión
     * general (sin grupo, group == null) no existe un "líder" a quien
     * preguntar, así que solo un admin del sistema puede publicarla.
     */
    @Transactional
    public Map<String, Object> publicarActa(Long meetingId, String acta, User caller) {
        GroupMeeting reunion = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("Reunión no encontrada."));
        if (reunion.getGroup() != null) {
            validarEsLiderOAdmin(reunion.getGroup().getId(), caller);
        } else {
            boolean esAdminSistema = caller.getRoles().stream()
                    .anyMatch(r -> "admin".equalsIgnoreCase(r.getName()));
            if (!esAdminSistema)
                throw new SecurityException("Solo un administrador puede publicar el acta de una reunión general.");
        }
        reunion.setActa(acta != null && !acta.isBlank() ? acta : null);
        meetingRepository.save(reunion);
        return Map.of("mensaje", "Acta guardada correctamente.", "id", reunion.getId());
    }

    /** ¿Es admin del sistema o líder (presidente/tesorero/secretario) de ese grupo puntual? */
    private void validarEsLiderOAdmin(Long groupId, User caller) {
        boolean esAdminSistema = caller.getRoles().stream()
                .anyMatch(r -> "admin".equalsIgnoreCase(r.getName()));
        if (esAdminSistema)
            return;

        GroupMember callerMember = memberRepository.findByGroupIdAndUserId(groupId, caller.getId())
                .orElseThrow(() -> new SecurityException("No perteneces a este grupo."));
        boolean esLider = callerMember.getRole() == GroupMember.MemberRole.presidente
                || callerMember.getRole() == GroupMember.MemberRole.tesorero
                || callerMember.getRole() == GroupMember.MemberRole.secretario;
        if (!esLider)
            throw new SecurityException("Solo un líder del grupo o administrador puede gestionar miembros.");
    }

    /**
     * Ni tesorero ni secretario pueden expulsar, suspender o quitarle el
     * cargo al presidente vigente de su propio grupo sin su consentimiento
     * — solo el presidente mismo (ej. si quiere renunciar) o un admin del
     * sistema. Evita que alguien "dé un golpe" dentro de su propio círculo.
     */
    private void validarNoTocarPresidenteSinConsentimiento(GroupMember member, User caller) {
        if (member.getRole() != GroupMember.MemberRole.presidente)
            return;
        boolean esElMismoPresidente = member.getUser().getId().equals(caller.getId());
        boolean esAdminSistema = caller.getRoles().stream()
                .anyMatch(r -> "admin".equalsIgnoreCase(r.getName()));
        if (!esElMismoPresidente && !esAdminSistema) {
            throw new SecurityException(
                    "Solo el presidente actual (o un administrador) puede aplicar esta acción sobre sí mismo.");
        }
    }


    private void validarPuedeAsignarRol(Long groupId, User caller, GroupMember.MemberRole rolAAsignar) {
        validarEsLiderOAdmin(groupId, caller);
        if (rolAAsignar != GroupMember.MemberRole.presidente)
            return;

        boolean esAdminSistema = caller.getRoles().stream()
                .anyMatch(r -> "admin".equalsIgnoreCase(r.getName()));
        if (esAdminSistema)
            return;

        boolean esPresidenteActual = memberRepository.findByGroupIdAndUserId(groupId, caller.getId())
                .map(m -> m.getRole() == GroupMember.MemberRole.presidente)
                .orElse(false);
        if (!esPresidenteActual)
            throw new SecurityException(
                    "Solo el presidente actual del grupo (o un administrador) puede nombrar a otro presidente.");
    }

    /**
     * Un grupo solo puede tener UN presidente, UN tesorero y UN secretario a
     * la vez (puede haber varios "miembro" normales, eso sí). Antes de
     * asignarle ese cargo a alguien, si otra persona ya lo tiene, se le
     * quita automáticamente y pasa a "miembro" — como un relevo de cargo.
     * `excluirUserId` es el id de la persona a la que se le está asignando
     * el rol (para no "auto-degradarla" si por casualidad ya lo tenía).
     */
    private void liberarRolSiOcupado(Long groupId, GroupMember.MemberRole rol, Long excluirUserId) {
        if (rol == GroupMember.MemberRole.miembro)
            return; // "miembro" no es un cargo único, cualquier cantidad de gente puede serlo

        memberRepository.findByGroupIdAndRole(groupId, rol).stream()
                .filter(m -> excluirUserId == null || !m.getUser().getId().equals(excluirUserId))
                .forEach(m -> {
                    m.setRole(GroupMember.MemberRole.miembro);
                    memberRepository.save(m);
                });
    }


    /**
     * (ADMIN) Todos los grupos del sistema, sin importar si el admin es
     * miembro o no — para que pueda ver qué grupos existen y supervisarlos.
     */
    public List<Map<String, Object>> getTodosLosGruposAdmin() {
        return groupRepository.findAll().stream()
                .map(g -> {
                    List<GroupMember> miembros = memberRepository.findByGroupId(g.getId());
                    long numMiembros = miembros.stream()
                            .filter(x -> x.getStatus() == GroupMember.MemberStatus.active).count();
                    String presidente = miembros.stream()
                            .filter(x -> x.getStatus() == GroupMember.MemberStatus.active
                                    && x.getRole() == GroupMember.MemberRole.presidente)
                            .findFirst()
                            .map(x -> x.getUser().getFirstName()
                                    + (x.getUser().getLastName() != null ? " " + x.getUser().getLastName() : ""))
                            .orElse("Sin presidente");
                    return Map.<String, Object>of(
                            "id", g.getId(),
                            "nombre", g.getName(),
                            "tipo", g.getTipo() != null ? g.getTipo() : "mixto",
                            "descripcion", g.getDescripcion() != null ? g.getDescripcion() : "",
                            "fondoComun", accountService.getCuentaGrupo(g).getBalance(),
                            "totalMiembros", numMiembros,
                            "presidente", presidente,
                            "estado", g.getStatus() != null ? g.getStatus().name() : "active");
                }).toList();
    }

    public List<Map<String, Object>> getMisGrupos(Long userId) {
        return memberRepository.findByUserId(userId).stream()
                .filter(m -> m.getStatus() == GroupMember.MemberStatus.active)
                .map(m -> {
                    Group g = m.getGroup();
                    long numMiembros = memberRepository.findByGroupId(g.getId()).stream()
                            .filter(x -> x.getStatus() == GroupMember.MemberStatus.active).count();
                    return Map.<String, Object>of(
                            "id", g.getId(),
                            "nombre", g.getName(),
                            "tipo", g.getTipo() != null ? g.getTipo() : "mixto",
                            "descripcion", g.getDescripcion() != null ? g.getDescripcion() : "",
                            "fondoComun", accountService.getCuentaGrupo(g).getBalance(),
                            "totalMiembros", numMiembros,
                            "miRol", m.getRole().name());
                }).toList();
    }

    /** Aportar al fondo común del grupo */
    @Transactional
    public Map<String, Object> aportarFondo(Long groupId, BigDecimal monto, User user) {
        Group grupo = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Grupo no encontrado."));

        Account cuenta = accountRepository.findFirstByOwnerUserIdAndAccountTypeAndStatus(
                user.getId(), Account.AccountType.individual, Account.AccountStatus.active)
                .orElseThrow(() -> new IllegalArgumentException("No tiene cuenta activa."));

        if (cuenta.getBalance().compareTo(monto) < 0)
            throw new IllegalArgumentException("Saldo insuficiente.");

        transactionLimitService.validar(user, grupo, Transaction.TxType.fondo_comun, monto);

        cuenta.setBalance(cuenta.getBalance().subtract(monto));
        accountRepository.save(cuenta);

        Account cuentaGrupo = accountService.getCuentaGrupo(grupo);
        cuentaGrupo.setBalance(cuentaGrupo.getBalance().add(monto));
        accountRepository.save(cuentaGrupo);

        // Se mantiene `fondoComun` en sincronía por compatibilidad con
        // pantallas/reportes que todavía lo leen directo del grupo — pero la
        // fuente de verdad real ya es `cuentaGrupo.getBalance()`.

        BigDecimal nuevoFondo = cuentaGrupo.getBalance();
        grupo.setFondoComun(nuevoFondo);
        groupRepository.save(grupo);

        String txCode = "FC-" + grupo.getId() + "-" + System.currentTimeMillis();
        transactionRepository.save(Transaction.builder()
                    .txCode(txCode)
                    .type(Transaction.TxType.fondo_comun)
                    .originAccount(cuenta)
                    .destinationAccount(cuentaGrupo)
                    .amount(monto)
                    .description("Aporte fondo común — " + grupo.getName())
                    .reference(txCode)
                    .status(Transaction.TxStatus.completed)
                    .createdBy(user)
                    .build());

        return Map.of(
                "fondoComun", nuevoFondo,
                "mensaje", "Aporte de $" + monto + " al fondo común registrado.");
    }

    /**
     * Cuánto ha aportado cada socio al fondo común de un grupo — visible para
     * cualquier miembro del grupo (transparencia sobre un fondo que es de
     * todos) o para un admin. No es información privada de cuentas
     * individuales, solo de los aportes a este fondo compartido específico.
     */
    public Map<String, Object> getAportesPorSocio(Long groupId, User requester) {
        Group grupo = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Grupo no encontrado."));

        boolean esAdmin = requester.getRoles().stream()
                .anyMatch(r -> "admin".equalsIgnoreCase(r.getName()));
        boolean esMiembro = memberRepository.existsByGroupIdAndUserId(groupId, requester.getId());
        if (!esAdmin && !esMiembro) {
            throw new SecurityException("Solo los miembros de este grupo pueden ver los aportes al fondo.");
        }

        List<Transaction> aportes = transactionRepository.findAportesFondoPorGrupo("FC-" + groupId + "-%");

        Map<Long, Map<String, Object>> porSocio = new LinkedHashMap<>();
        for (Transaction t : aportes) {
            if (t.getCreatedBy() == null)
                continue;
            User socio = t.getCreatedBy();
            Map<String, Object> fila = porSocio.computeIfAbsent(socio.getId(), k -> {
                Map<String, Object> f = new LinkedHashMap<>();
                f.put("userId", socio.getId());
                f.put("nombre", socio.getFirstName() + (socio.getLastName() != null ? " " + socio.getLastName() : ""));
                f.put("totalAportado", BigDecimal.ZERO);
                f.put("numeroAportes", 0);
                f.put("ultimoAporte", null);
                return f;
            });
            fila.put("totalAportado", ((BigDecimal) fila.get("totalAportado")).add(t.getAmount()));
            fila.put("numeroAportes", (int) fila.get("numeroAportes") + 1);
            fila.put("ultimoAporte", t.getCreatedAt().toString());
        }

        List<Map<String, Object>> lista = new ArrayList<>(porSocio.values());
        lista.sort((a, b) -> ((BigDecimal) b.get("totalAportado")).compareTo((BigDecimal) a.get("totalAportado")));

        BigDecimal fondoReal = accountService.getCuentaGrupo(grupo).getBalance();
        BigDecimal totalConAportesRegistrados = lista.stream()
                .map(f -> (BigDecimal) f.get("totalAportado"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal sinAsignar = fondoReal.subtract(totalConAportesRegistrados).max(BigDecimal.ZERO);

        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("fondoComun", fondoReal);
        resultado.put("saldoSinAsignar", sinAsignar);
        resultado.put("aportes", lista);
        return resultado;
    }
}
