package com.bankomunal.service;

import com.bankomunal.dto.request.LoanRequest;
import com.bankomunal.entity.*;
import com.bankomunal.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Préstamo solidario: un socio pide plata del fondo común de SU grupo, no
 * del capital de la entidad. En vez de que un admin lo apruebe, lo aprueban
 * los propios socios del grupo por votación — reutilizando el sistema de
 * encuestas que ya existía (PollService), no uno nuevo desde cero.
 *
 * Flujo: proponer() crea el préstamo (fundingSource=group_fund, pending) +
 * una encuesta de 2 opciones ligada a él → los socios votan con votar() →
 * cuando el resultado es claro (se llega al umbral o ya no hay forma de que
 * el lado perdedor alcance al ganador), se aprueba o rechaza el préstamo
 * automáticamente, sin que un admin tenga que intervenir. A partir de ahí
 * sigue exactamente el mismo camino que cualquier préstamo: el socio acepta
 * el contrato (LoanService#aceptarContrato) y ahí se desembolsa — pero esta
 * vez desde la cuenta del grupo (ver Loan#fundingSource).
 *
 * A propósito NO se modifica PollService — esta clase solo lee los votos
 * para decidir el resultado, así el sistema de encuestas genérico (usado
 * también para cambios de reglas y decisiones normales del grupo) sigue
 * funcionando exactamente igual que antes de esta funcionalidad.
 */
@Service
@RequiredArgsConstructor
public class GroupLoanService {

    private static final String OPCION_APROBAR = "Sí, aprobar";
    private static final String OPCION_RECHAZAR = "No, rechazar";
    private static final int DIAS_VOTACION_DEFAULT = 5;

    private final LoanService loanService;
    private final PollService pollService;
    private final LoanRepository loanRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final PollRepository pollRepository;
    private final PollOptionRepository pollOptionRepository;
    private final PollVoteRepository pollVoteRepository;
    private final AccountService accountService;
    private final NotificationService notificationService;

    /**
     * Un socio del grupo propone un préstamo solidario. Se valida contra el
     * saldo REALMENTE disponible del fondo — no el balance bruto de la
     * cuenta, sino ese balance menos lo que ya está comprometido en otros
     * préstamos solidarios del mismo grupo que siguen en votación o ya
     * aprobados pero sin desembolsar todavía. Sin esto, dos socios podrían
     * proponer préstamos casi al mismo tiempo y ambos pasarían la
     * validación contra el mismo saldo bruto, sobre-comprometiendo el
     * fondo entre los dos. El saldo se vuelve a validar en el desembolso
     * real (LoanService#aceptarContrato), porque puede cambiar entre la
     * propuesta y el cierre de la votación.
     */
    @Transactional
    public Map<String, Object> proponer(Long groupId, LoanRequest req, User socio) {
        Group grupo = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Grupo no encontrado."));

        if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, socio.getId()))
            throw new SecurityException("No perteneces a este grupo.");

        BigDecimal disponible = getSaldoDisponible(grupo);
        if (disponible.compareTo(req.getMontoSolicitado()) < 0) {
            throw new IllegalArgumentException("El fondo común disponible del grupo ($" + disponible
                    + ") no alcanza para el monto solicitado ($" + req.getMontoSolicitado()
                    + "). Ya hay otros préstamos solidarios comprometiendo parte del fondo.");
        }

        Loan loan = loanService.crearSolicitudGrupal(req, socio, grupo);

        Poll poll = pollRepository.save(Poll.builder()
                .group(grupo)
                .titulo("Préstamo solidario: " + socio.getFirstName() + " solicita $" + req.getMontoSolicitado())
                .descripcion((req.getMotivo() != null && !req.getMotivo().isBlank()
                        ? req.getMotivo() + " — "
                        : "") + "Financiado por el fondo común del grupo. Plazo: " + req.getPlazoMeses()
                        + " meses. Cuota estimada: $" + loan.getCuotaMensual() + ".")
                .isRuleChange(false)
                .isAnonymous(false)
                .approvalThreshold(51)
                .createdBy(socio)
                .status(Poll.PollStatus.open)
                .endsAt(LocalDateTime.now().plusDays(DIAS_VOTACION_DEFAULT))
                .build());
        pollOptionRepository.save(PollOption.builder().poll(poll).texto(OPCION_APROBAR).build());
        pollOptionRepository.save(PollOption.builder().poll(poll).texto(OPCION_RECHAZAR).build());

        loan.setApprovalPollId(poll.getId());
        loanRepository.save(loan);

        notificarSociosDelGrupo(grupo, socio,
                "Nueva votación: préstamo solidario",
                socio.getFirstName() + " propuso un préstamo de $" + req.getMontoSolicitado()
                        + " financiado por el fondo común. Vota en los próximos " + DIAS_VOTACION_DEFAULT + " días.",
                poll.getId());

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("loanId", loan.getId());
        resp.put("pollId", poll.getId());
        resp.put("mensaje", "Solicitud registrada. Se abrió una votación entre los socios del grupo.");
        return resp;
    }

    /**
     * Saldo del fondo común de un grupo que todavía no está comprometido
     * en ningún préstamo solidario en curso — el número real contra el que
     * hay que validar una propuesta nueva, no el balance bruto de la cuenta.
     */
    @Transactional(readOnly = true)
    public BigDecimal getSaldoDisponible(Group grupo) {
        BigDecimal balance = accountService.getCuentaGrupo(grupo).getBalance();
        BigDecimal comprometido = loanRepository.sumComprometidoPorGrupo(
                grupo.getId(), Loan.FundingSource.group_fund,
                List.of(Loan.LoanStatus.pending, Loan.LoanStatus.approved));
        return balance.subtract(comprometido).max(BigDecimal.ZERO);
    }

    /**
     * Desglose del fondo común para que el frontend le muestre al socio,
     * antes de proponer, cuánto hay en total, cuánto ya está comprometido
     * en otras votaciones/contratos pendientes, y cuánto queda libre. Solo
     * un socio de ESE grupo o un admin puede consultarlo.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getFondoInfo(Long groupId, User caller) {
        Group grupo = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Grupo no encontrado."));
        validarPerteneceOAdmin(groupId, caller);
        BigDecimal balance = accountService.getCuentaGrupo(grupo).getBalance();
        BigDecimal comprometido = loanRepository.sumComprometidoPorGrupo(
                groupId, Loan.FundingSource.group_fund,
                List.of(Loan.LoanStatus.pending, Loan.LoanStatus.approved));
        BigDecimal disponible = balance.subtract(comprometido).max(BigDecimal.ZERO);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("balance", balance);
        resp.put("comprometido", comprometido);
        resp.put("disponible", disponible);
        return resp;
    }

    private void validarPerteneceOAdmin(Long groupId, User caller) {
        boolean esAdmin = caller.getRoles().stream()
                .anyMatch(r -> "admin".equalsIgnoreCase(r.getName()));
        if (!esAdmin && !groupMemberRepository.existsByGroupIdAndUserId(groupId, caller.getId()))
            throw new SecurityException("Solo los socios de este grupo pueden ver esta información.");
    }

    /**
     * Vota "Sí, aprobar" o "No, rechazar" en la votación de un préstamo
     * solidario — delega el voto en sí a PollService (misma validación de
     * duplicado/expiración que cualquier encuesta) y, después, revisa si ya
     * hay un resultado claro para resolver el préstamo automáticamente.
     */
    @Transactional
    public Map<String, Object> votar(Long loanId, boolean aprobar, User user) {
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new IllegalArgumentException("Préstamo no encontrado."));
        if (loan.getFundingSource() != Loan.FundingSource.group_fund || loan.getApprovalPollId() == null)
            throw new IllegalArgumentException("Este préstamo no está sujeto a votación.");
        if (loan.getStatus() != Loan.LoanStatus.pending)
            throw new IllegalStateException("Esta votación ya se resolvió.");
        if (!groupMemberRepository.existsByGroupIdAndUserId(loan.getGroup().getId(), user.getId()))
            throw new SecurityException("Solo los socios del grupo pueden votar esta solicitud.");

        Long pollId = loan.getApprovalPollId();
        List<PollOption> opciones = pollOptionRepository.findByPollId(pollId);
        PollOption elegida = opciones.stream()
                .filter(o -> aprobar ? OPCION_APROBAR.equals(o.getTexto()) : OPCION_RECHAZAR.equals(o.getTexto()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No se encontró la opción de voto."));

        pollService.votar(pollId, elegida.getId(), user);

        resolverSiHayResultado(loan, pollId, opciones);

        Loan actualizado = loanRepository.findById(loanId).orElse(loan);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("mensaje", "Voto registrado.");
        resp.put("estadoPrestamo", actualizado.getStatus().name());
        return resp;
    }

    /**
     * Decide si la votación ya tiene un resultado definitivo y, si lo
     * tiene, aprueba o rechaza el préstamo. Un resultado es definitivo
     * cuando: (a) todos los miembros activos del grupo ya votaron, o
     * (b) un lado alcanzó el umbral de aprobación sobre el total de
     * miembros activos (no solo sobre los votos emitidos, para exigir
     * quórum real y no solo mayoría de los que se animaron a votar).
     */
    private void resolverSiHayResultado(Loan loan, Long pollId, List<PollOption> opciones) {
        long totalMiembrosActivos = groupMemberRepository.findByGroupId(loan.getGroup().getId()).stream()
                .filter(m -> m.getStatus() == GroupMember.MemberStatus.active)
                .count();
        if (totalMiembrosActivos == 0)
            return;

        long votosAprobar = opciones.stream()
                .filter(o -> OPCION_APROBAR.equals(o.getTexto()))
                .mapToLong(o -> pollVoteRepository.countByOptionId(o.getId())).sum();
        long votosRechazar = opciones.stream()
                .filter(o -> OPCION_RECHAZAR.equals(o.getTexto()))
                .mapToLong(o -> pollVoteRepository.countByOptionId(o.getId())).sum();
        long totalVotos = votosAprobar + votosRechazar;

        double pctAprobar = (double) votosAprobar / totalMiembrosActivos * 100;
        double pctRechazar = (double) votosRechazar / totalMiembrosActivos * 100;
        int umbral = 51;

        boolean todosVotaron = totalVotos >= totalMiembrosActivos;
        boolean aprobadoPorUmbral = pctAprobar >= umbral;
        boolean rechazadoPorUmbral = pctRechazar >= umbral;

        if (!(todosVotaron || aprobadoPorUmbral || rechazadoPorUmbral))
            return;

        if (aprobadoPorUmbral || (todosVotaron && votosAprobar > votosRechazar)) {
            loanService.aprobarPorVotacion(loan.getId());
            pollRepository.findById(pollId).ifPresent(p -> {
                p.setStatus(Poll.PollStatus.approved);
                pollRepository.save(p);
            });
            notificationService.crearNotificacion(loan.getBorrowerUser(),
                    "¡Tu préstamo solidario fue aprobado por votación!",
                    "El grupo aprobó tu solicitud de $" + loan.getMontoSolicitado()
                            + ". Revisa el contrato y acéptalo para recibir el desembolso.",
                    "loan_approved_pending_signature", loan.getId());
        } else {
            loanService.rechazarPorVotacion(loan.getId(), "El grupo no alcanzó el consenso necesario en la votación.");
            pollRepository.findById(pollId).ifPresent(p -> {
                p.setStatus(Poll.PollStatus.rejected);
                pollRepository.save(p);
            });
            notificationService.crearNotificacion(loan.getBorrowerUser(),
                    "Tu préstamo solidario no fue aprobado",
                    "El grupo no alcanzó el consenso necesario para tu solicitud de $" + loan.getMontoSolicitado() + ".",
                    "loan_rejected", loan.getId());
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listarDelGrupo(Long groupId, User caller) {
        validarPerteneceOAdmin(groupId, caller);
        return loanRepository.findByGroupIdOrderByCreatedAtDesc(groupId).stream()
                .filter(l -> l.getFundingSource() == Loan.FundingSource.group_fund)
                .map(l -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", l.getId());
                    m.put("loanCode", l.getLoanCode());
                    m.put("solicitanteId", l.getBorrowerUser().getId());
                    m.put("solicitanteNombre", l.getBorrowerUser().getFirstName());
                    m.put("monto", l.getMontoSolicitado());
                    m.put("plazoMeses", l.getPlazoMeses());
                    m.put("estado", l.getStatus().name());
                    m.put("pollId", l.getApprovalPollId());
                    m.put("motivo", l.getMotivo());
                    m.put("createdAt", l.getCreatedAt() != null ? l.getCreatedAt().toString() : null);
                    return m;
                }).toList();
    }

    private void notificarSociosDelGrupo(Group grupo, User excluir, String titulo, String mensaje, Long refId) {
        groupMemberRepository.findByGroupId(grupo.getId()).stream()
                .filter(m -> m.getStatus() == GroupMember.MemberStatus.active)
                .filter(m -> !m.getUser().getId().equals(excluir.getId()))
                .forEach(m -> notificationService.crearNotificacion(m.getUser(), titulo, mensaje, "poll_new", refId));
    }
}
