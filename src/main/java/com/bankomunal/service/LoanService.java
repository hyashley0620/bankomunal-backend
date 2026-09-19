package com.bankomunal.service;

import com.bankomunal.dto.request.*;
import com.bankomunal.dto.response.*;
import com.bankomunal.entity.*;
import com.bankomunal.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LoanService {

    private static final BigDecimal TASA_DEFAULT = new BigDecimal("0.0200");

    /**
     * La tasa mensual varía según el plazo elegido — igual que lo anuncia
     * el simulador ("6 meses — Tasa 2.0%", "12 meses — Tasa 1.8%",
     * "24 meses — Tasa 1.5%", ver prestamos.html #plazoSelect).
     */
    private BigDecimal tasaPorPlazo(int plazoMeses) {
        return switch (plazoMeses) {
            case 6 -> new BigDecimal("0.0200");
            case 12 -> new BigDecimal("0.0180");
            case 24 -> new BigDecimal("0.0150");
            default -> TASA_DEFAULT;
        };
    }

    private final LoanRepository loanRepository;
    private final LoanPaymentRepository loanPaymentRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final CreditRiskService creditRiskService;
    private final AccountService accountService;

    /** Simulador */
    public LoanSimulationResponse simular(LoanSimulateRequest req) {
        return calcularSimulacion(req.getMonto(), req.getPlazoMeses(), tasaPorPlazo(req.getPlazoMeses()));
    }

    private LoanSimulationResponse calcularSimulacion(BigDecimal monto, int plazo, BigDecimal tasa) {
        BigDecimal uno = BigDecimal.ONE;
        BigDecimal factor = tasa.add(uno).pow(plazo, new MathContext(10));
        BigDecimal cuota = monto.multiply(tasa).multiply(factor)
                .divide(factor.subtract(uno), 2, RoundingMode.HALF_UP);
        BigDecimal total = cuota.multiply(BigDecimal.valueOf(plazo)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal intereses = total.subtract(monto).setScale(2, RoundingMode.HALF_UP);
        return LoanSimulationResponse.builder().cuotaMensual(cuota).totalPagar(total).totalIntereses(intereses).build();
    }

    /** Solicitar préstamo (flujo normal del socio) — queda en PENDING hasta aprobación del admin */
    @Transactional
    public LoanResponse solicitar(LoanRequest req, User user) {
        return crearSolicitud(req, user, null);
    }

    /**
     * Un admin origina un préstamo a nombre de un socio (ej. alguien que
     * solicitó presencialmente en oficina, sin usar la app). Queda igual en
     * PENDING — no se salta la aprobación, solo evita que el socio tenga que
     * llenar el formulario él mismo.
     */
    @Transactional
    public LoanResponse solicitarParaSocio(LoanRequest req, Long socioId, User admin) {
        User socio = userRepository.findById(socioId)
                .orElseThrow(() -> new IllegalArgumentException("Socio no encontrado."));
        return crearSolicitud(req, socio, admin);
    }

    /**
     * Lógica compartida de creación de solicitud. Si {@code actor} es null o
     * es el mismo socio, es una solicitud normal (autoservicio). Si
     * {@code actor} es un admin distinto del socio, es una solicitud
     * originada por un asesor — se deja constancia en el motivo y se avisa
     * al socio con un mensaje distinto.
     */
    private LoanResponse crearSolicitud(LoanRequest req, User socio, User actor) {
        String code = "LN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        BigDecimal tasa = tasaPorPlazo(req.getPlazoMeses());
        LoanSimulationResponse sim = calcularSimulacion(req.getMontoSolicitado(), req.getPlazoMeses(), tasa);
        boolean origenAdmin = actor != null && !actor.getId().equals(socio.getId());

        String motivo = req.getMotivo();
        if (origenAdmin) {
            motivo = (motivo != null && !motivo.isBlank() ? motivo + " · " : "")
                    + "Registrado en oficina por " + actor.getFirstName();
        }

        Loan loan = Loan.builder()
                .loanCode(code)
                .borrowerUser(socio)
                .montoSolicitado(req.getMontoSolicitado())
                .principal(req.getMontoSolicitado())
                .tasaInteresMensual(tasa)
                .plazoMeses(req.getPlazoMeses())
                .cuotaMensual(sim.getCuotaMensual())
                .status(Loan.LoanStatus.pending) // espera aprobación admin
                .motivo(motivo)
                .saldoPendiente(sim.getTotalPagar())
                .build();
        loanRepository.save(loan);
        generarCuotas(loan, req.getMontoSolicitado(), req.getPlazoMeses());

        String mensajeSocio = origenAdmin
                ? "Un asesor registró una solicitud de crédito a tu nombre por $" + req.getMontoSolicitado()
                        + " a " + req.getPlazoMeses() + " meses. Está pendiente de aprobación. Ref: " + code
                : "Tu crédito de $" + req.getMontoSolicitado() + " a " + req.getPlazoMeses()
                        + " meses está pendiente de aprobación. Ref: " + code;
        notificationService.crearNotificacion(socio, " Solicitud en revisión", mensajeSocio, "loan_pending", loan.getId());

        // Notificar a todos los administradores y tesoreros (menos al que la originó, si aplica) —
        // ambos roles pueden aprobar préstamos, así que ambos deben enterarse de solicitudes nuevas.
        try {
            userRepository.findAll().stream()
                    .filter(u -> u.getRoles().stream()
                            .anyMatch(r -> "admin".equalsIgnoreCase(r.getName())
                                    || "tesorero".equalsIgnoreCase(r.getName())))
                    .filter(u -> !origenAdmin || !u.getId().equals(actor.getId()))
                    .forEach(admin -> notificationService.crearNotificacion(admin,
                            "Nueva solicitud de préstamo",
                            socio.getFirstName() + " solicita $" + req.getMontoSolicitado()
                                    + " a " + req.getPlazoMeses() + " meses. Ref: " + code
                                    + (origenAdmin ? " (registrada por " + actor.getFirstName() + ")" : ""),
                            "loan_request_admin", loan.getId()));
        } catch (Exception ignored) {
        }

        return buildResponse(loan);
    }

    /**
     * (ADMIN) Aprobar préstamo pendiente. NO desembolsa de inmediato —
     * deja el préstamo en estado "approved" con el contrato listo, y el
     * socio debe aceptarlo (ver {@link #aceptarContrato}) para que se haga
     * el desembolso. Así funciona: solicitud → aprobación → contrato → aceptación → desembolso.
     */
    @Transactional
    public LoanResponse aprobar(Long loanId) {
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new IllegalArgumentException("Préstamo no encontrado."));
        if (loan.getFundingSource() == Loan.FundingSource.group_fund)
            throw new IllegalStateException(
                    "Este es un préstamo solidario: su aprobación la decide la votación de los socios del grupo, no un administrador.");
        return aprobarInterno(loan);
    }

    /**
     * Lógica real de aprobación, sin el guardia de "no es admin quien
     * decide los solidarios" — la usan tanto {@link #aprobar} (ruta admin,
     * préstamos institucionales) como {@link #aprobarPorVotacion} (ruta de
     * GroupLoanService, una vez que la votación del grupo ya resolvió a
     * favor de un préstamo solidario).
     */
    private LoanResponse aprobarInterno(Loan loan) {
        if (loan.getStatus() != Loan.LoanStatus.pending)
            throw new IllegalStateException("Solo se pueden aprobar préstamos pendientes. Estado: " + loan.getStatus());

        loan.setStatus(Loan.LoanStatus.approved);
        loan.setApprovedAt(LocalDateTime.now());
        loanRepository.save(loan);

        User user = loan.getBorrowerUser();
        String code = loan.getLoanCode();

        notificationService.crearNotificacion(user,
                "Tu préstamo fue aprobado — falta aceptar el contrato",
                "Tu solicitud " + code + " por $" + loan.getMontoSolicitado() + " a " + loan.getPlazoMeses()
                        + " meses fue aprobada. Revisa el contrato y acéptalo para recibir el desembolso.",
                "loan_approved_pending_signature", loan.getId());

        return buildResponse(loan);
    }

    /**
     * (SOCIO) Acepta/firma el contrato de un préstamo ya aprobado — recién
     * en este momento se hace el desembolso a su cuenta. Solo el dueño del
     * préstamo puede aceptarlo.
     */
    @Transactional
    public LoanResponse aceptarContrato(Long loanId, User requester) {
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new IllegalArgumentException("Préstamo no encontrado."));
        if (!loan.getBorrowerUser().getId().equals(requester.getId()))
            throw new SecurityException("No puedes aceptar el contrato de un préstamo que no es tuyo.");
        if (loan.getStatus() != Loan.LoanStatus.approved)
            throw new IllegalStateException("Este préstamo no tiene un contrato pendiente de aceptación.");

        loan.setStatus(Loan.LoanStatus.active);
        loan.setContratoAceptadoAt(LocalDateTime.now());
        loan.setDisbursedAt(LocalDateTime.now());
        loanRepository.save(loan);

        List<LoanPayment> cuotas = loanPaymentRepository.findByLoanIdOrderByNumeroCuota(loan.getId());
        for (LoanPayment cuota : cuotas) {
            if (cuota.getStatus() == LoanPayment.PaymentStatus.pending) {
                cuota.setFechaVencimiento(LocalDate.now().plusMonths(cuota.getNumeroCuota()));
                loanPaymentRepository.save(cuota);
            }
        }

        User user = loan.getBorrowerUser();
        String code = loan.getLoanCode();

        // De dónde sale la plata: capital de la entidad (siempre)
        // o el fondo común del grupo si es un préstamo solidario aprobado por
        // votación.
        Account cuentaOrigenDesembolso = loan.getFundingSource() == Loan.FundingSource.group_fund
                ? accountService.getCuentaGrupo(loan.getGroup())
                : accountService.getCuentaFund();

        if (cuentaOrigenDesembolso.getBalance().compareTo(loan.getMontoSolicitado()) < 0) {
            throw new IllegalStateException(loan.getFundingSource() == Loan.FundingSource.group_fund
                    ? "El fondo común del grupo no tiene saldo suficiente para este desembolso todavía."
                    : "El capital de la entidad no tiene saldo suficiente para este desembolso.");
        }

        accountRepository.findFirstByOwnerUserIdAndAccountTypeAndStatus(
                user.getId(), Account.AccountType.individual, Account.AccountStatus.active)
                .ifPresent(cta -> {
                    cuentaOrigenDesembolso.setBalance(cuentaOrigenDesembolso.getBalance().subtract(loan.getMontoSolicitado()));
                    accountRepository.save(cuentaOrigenDesembolso);

                    cta.setBalance(cta.getBalance().add(loan.getMontoSolicitado()));
                    accountRepository.save(cta);
                    transactionRepository.save(Transaction.builder()
                            .txCode("DSB-" + code)
                            .type(Transaction.TxType.loan_disbursement)
                            .originAccount(cuentaOrigenDesembolso)
                            .destinationAccount(cta)
                            .amount(loan.getMontoSolicitado())
                            .description("Desembolso préstamo " + code
                                    + (loan.getFundingSource() == Loan.FundingSource.group_fund
                                            ? " (fondo común " + loan.getGroup().getName() + ")"
                                            : ""))
                            .reference(code)
                            .status(Transaction.TxStatus.completed)
                            .createdBy(user)
                            .build());
                });

        notificationService.crearNotificacion(user,
                "Contrato aceptado — préstamo desembolsado",
                "Aceptaste el contrato de tu crédito " + code + ". Ya tienes $" + loan.getMontoSolicitado()
                        + " disponibles en tu cuenta.",
                "loan_disbursed", loan.getId());

        return buildResponse(loan);
    }

    /**
     * (SOCIO, dentro de un grupo) Propone un préstamo solidario financiado
     * por el fondo común del grupo — NO por el capital de la entidad. Queda
     * en `pending`, igual que uno institucional, pero su aprobación no la
     * decide un admin sino la votación de los socios del grupo (ver
     * {@link GroupLoanService}). El desembolso y los pagos ya saben, gracias
     * a {@code fundingSource}, que deben usar la cuenta del grupo.
     */
    @Transactional
    public Loan crearSolicitudGrupal(LoanRequest req, User socio, Group grupo) {
        String code = "SOL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        BigDecimal tasa = tasaPorPlazo(req.getPlazoMeses());
        LoanSimulationResponse sim = calcularSimulacion(req.getMontoSolicitado(), req.getPlazoMeses(), tasa);

        Loan loan = Loan.builder()
                .loanCode(code)
                .borrowerUser(socio)
                .group(grupo)
                .fundingSource(Loan.FundingSource.group_fund)
                .montoSolicitado(req.getMontoSolicitado())
                .principal(req.getMontoSolicitado())
                .tasaInteresMensual(tasa)
                .plazoMeses(req.getPlazoMeses())
                .cuotaMensual(sim.getCuotaMensual())
                .status(Loan.LoanStatus.pending) // espera resultado de la votación
                .motivo(req.getMotivo())
                .saldoPendiente(sim.getTotalPagar())
                .build();
        loanRepository.save(loan);
        generarCuotas(loan, req.getMontoSolicitado(), req.getPlazoMeses());
        return loan;
    }

    /** Usado por GroupLoanService después de que la votación aprueba o rechaza. */
    @Transactional
    public LoanResponse aprobarPorVotacion(Long loanId) {
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new IllegalArgumentException("Préstamo no encontrado."));
        return aprobarInterno(loan);
    }

    @Transactional
    public LoanResponse rechazarPorVotacion(Long loanId, String motivo) {
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new IllegalArgumentException("Préstamo no encontrado."));
        return rechazarInterno(loan, motivo);
    }

    /**
     * (ADMIN): Rechazar préstamo pendiente. Los préstamos solidarios NO se
     * rechazan por esta vía — su resultado lo decide la votación del grupo
     * (ver {@link #rechazarPorVotacion}).
     */
    @Transactional
    public LoanResponse rechazar(Long loanId, String motivo) {
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new IllegalArgumentException("Préstamo no encontrado."));
        if (loan.getFundingSource() == Loan.FundingSource.group_fund)
            throw new IllegalStateException(
                    "Este es un préstamo solidario: su rechazo lo decide la votación de los socios del grupo, no un administrador.");
        return rechazarInterno(loan, motivo);
    }

    private LoanResponse rechazarInterno(Loan loan, String motivo) {
        if (loan.getStatus() != Loan.LoanStatus.pending)
            throw new IllegalStateException("Solo se pueden rechazar préstamos pendientes.");
        loan.setStatus(Loan.LoanStatus.rejected);
        loan.setMotivoRechazo(motivo != null && !motivo.isBlank() ? motivo : "No se especificó un motivo.");
        loanRepository.save(loan);
        notificationService.crearNotificacion(loan.getBorrowerUser(),
                " Solicitud rechazada",
                "Tu solicitud " + loan.getLoanCode() + " fue rechazada" + (motivo != null ? ": " + motivo : "."),
                "loan_rejected", loan.getId());
        return buildResponse(loan);
    }

    private void generarCuotas(Loan loan, BigDecimal monto, int plazo) {
        BigDecimal saldo = monto;
        BigDecimal tasa = loan.getTasaInteresMensual();
        for (int n = 1; n <= plazo; n++) {
            BigDecimal interes = saldo.multiply(tasa).setScale(2, RoundingMode.HALF_UP);
            BigDecimal capital = loan.getCuotaMensual().subtract(interes).setScale(2, RoundingMode.HALF_UP);
            if (n == plazo)
                capital = saldo;
            saldo = saldo.subtract(capital).setScale(2, RoundingMode.HALF_UP);
            if (saldo.compareTo(BigDecimal.ZERO) < 0)
                saldo = BigDecimal.ZERO;
            loanPaymentRepository.save(LoanPayment.builder()
                    .loan(loan).numeroCuota(n)
                    .fechaVencimiento(LocalDate.now().plusMonths(n))
                    .montoCapital(capital).montoInteres(interes)
                    .totalCuota(loan.getCuotaMensual())
                    .saldoRestante(saldo)
                    .status(LoanPayment.PaymentStatus.pending)
                    .build());
        }
    }

    public List<LoanResponse> getMisPrestamos(Long userId) {
        return loanRepository.findByBorrowerUserId(userId).stream()
                .map(this::buildResponse).toList();
    }

    /**
     * Detalle de un préstamo. Solo puede verlo el dueño del préstamo o un
     * usuario con rol admin.
     */
    public LoanDetailResponse getDetalle(Long loanId, User requester) {
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new IllegalArgumentException("Préstamo no encontrado."));

        boolean esDueño = loan.getBorrowerUser().getId().equals(requester.getId());
        boolean puedeGestionar = requester.getRoles().stream()
                .anyMatch(r -> "admin".equalsIgnoreCase(r.getName()) || "tesorero".equalsIgnoreCase(r.getName()));
        if (!esDueño && !puedeGestionar) {
            throw new SecurityException("No tienes permiso para ver este préstamo.");
        }

        List<LoanPayment> cuotas = loanPaymentRepository.findByLoanIdOrderByNumeroCuota(loanId);
        long pagadas = cuotas.stream().filter(c -> c.getStatus() == LoanPayment.PaymentStatus.paid).count();
        long pendientes = cuotas.size() - pagadas;

        BigDecimal totalPagar = loan.getCuotaMensual().multiply(BigDecimal.valueOf(loan.getPlazoMeses()));
        BigDecimal totalIntereses = totalPagar
                .subtract(loan.getPrincipal() != null ? loan.getPrincipal() : loan.getMontoSolicitado());
        int pct = loan.getSaldoPendiente() != null && totalPagar.compareTo(BigDecimal.ZERO) > 0
                ? (int) (100 - loan.getSaldoPendiente().multiply(BigDecimal.valueOf(100))
                        .divide(totalPagar, 0, RoundingMode.HALF_UP).longValue())
                : 0;

        LocalDate proximoVenc = cuotas.stream()
                .filter(c -> c.getStatus() == LoanPayment.PaymentStatus.pending)
                .map(LoanPayment::getFechaVencimiento).findFirst().orElse(null);

        List<AmortizacionCuota> amort = cuotas.stream().map(c -> AmortizacionCuota.builder()
                .numeroCuota(c.getNumeroCuota())
                .vencimiento(c.getFechaVencimiento())
                .capital(c.getMontoCapital()).interes(c.getMontoInteres())
                .totalCuota(c.getTotalCuota()).saldoRestante(c.getSaldoRestante())
                .estado(c.getStatus().name()).fechaPago(c.getFechaPago())
                .build()).toList();

        User titular = loan.getBorrowerUser();

        return LoanDetailResponse.builder()
                .id(loan.getId()).loanCode(loan.getLoanCode()).estado(loan.getStatus().name())
                .fundingSource(loan.getFundingSource() != null ? loan.getFundingSource().name() : "institutional")
                .titularId(titular.getId())
                .titularNombre((titular.getFirstName() != null ? titular.getFirstName() : "") +
                        (titular.getLastName() != null ? " " + titular.getLastName() : ""))
                .titularEmail(titular.getEmail())
                .montoSolicitado(loan.getMontoSolicitado())
                .principal(loan.getPrincipal())
                .cuotaMensual(loan.getCuotaMensual())
                .plazoMeses(loan.getPlazoMeses())
                .tasaInteresMensual(loan.getTasaInteresMensual())
                .fechaSolicitud(loan.getCreatedAt())
                .fechaDesembolso(loan.getDisbursedAt())
                .fechaAprobacion(loan.getApprovedAt())
                .contratoAceptadoAt(loan.getContratoAceptadoAt())
                .motivoRechazo(loan.getMotivoRechazo())
                .totalPagar(totalPagar).totalIntereses(totalIntereses)
                .saldoPendiente(loan.getSaldoPendiente())
                .montoPagado(
                        totalPagar.subtract(loan.getSaldoPendiente() != null ? loan.getSaldoPendiente() : totalPagar))
                .cuotasPagadas((int) pagadas).cuotasPendientes((int) pendientes)
                .porcentajePagado(pct)
                .proximoVencimiento(proximoVenc)
                .amortizacion(amort)
                .build();
    }

    /** Pago de cuota */
    @Transactional
    public MessageResponse pagarCuota(Long loanId, User user) {
        return pagarCuota(loanId, user, null);
    }

    @Transactional
    public MessageResponse pagarCuota(Long loanId, User user, String numeroCuenta) {
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new IllegalArgumentException("Préstamo no encontrado."));
        if (loan.getStatus() != Loan.LoanStatus.active)
            throw new IllegalStateException("El préstamo no está activo (estado: " + loan.getStatus() + ").");

        List<LoanPayment> cuotas = loanPaymentRepository.findByLoanIdOrderByNumeroCuota(loanId);
        LoanPayment proxima = cuotas.stream()
                .filter(c -> c.getStatus() == LoanPayment.PaymentStatus.pending)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No hay cuotas pendientes."));

        Account cuenta;
        if (numeroCuenta != null && !numeroCuenta.isBlank()) {
            cuenta = accountRepository.findByAccountCode(numeroCuenta)
                    .orElseThrow(() -> new IllegalArgumentException("Cuenta no encontrada."));
            boolean perteneceAlUsuario = (cuenta.getOwnerUser() != null
                    && cuenta.getOwnerUser().getId().equals(user.getId()));
            if (!perteneceAlUsuario)
                throw new IllegalArgumentException("La cuenta seleccionada no te pertenece.");
            if (cuenta.getStatus() != Account.AccountStatus.active)
                throw new IllegalArgumentException("La cuenta seleccionada no está activa.");
        } else {
            cuenta = accountRepository.findFirstByOwnerUserIdAndAccountTypeAndStatus(
                    user.getId(), Account.AccountType.individual, Account.AccountStatus.active)
                    .orElseThrow(() -> new IllegalArgumentException("No tiene cuenta activa."));
        }

        if (cuenta.getBalance().compareTo(proxima.getTotalCuota()) < 0)
            throw new IllegalArgumentException("Saldo insuficiente para pagar la cuota.");

        cuenta.setBalance(cuenta.getBalance().subtract(proxima.getTotalCuota()));
        accountRepository.save(cuenta);

        // El pago vuelve al fondo de donde salió el préstamo: capital de la
        // entidad para uno institucional, o el fondo común del grupo si fue
        // un préstamo solidario — no se queda flotando sin destino.
        Account cuentaDestinoPago = loan.getFundingSource() == Loan.FundingSource.group_fund
                ? accountService.getCuentaGrupo(loan.getGroup())
                : accountService.getCuentaFund();
        cuentaDestinoPago.setBalance(cuentaDestinoPago.getBalance().add(proxima.getTotalCuota()));
        accountRepository.save(cuentaDestinoPago);

        proxima.setStatus(LoanPayment.PaymentStatus.paid);
        proxima.setFechaPago(LocalDate.now());
        loanPaymentRepository.save(proxima);

        creditRiskService.registrarPagoCuota(user,
                !LocalDate.now().isAfter(proxima.getFechaVencimiento()),
                loan.getLoanCode(), proxima.getNumeroCuota());

        loan.setCuotasPagadas(loan.getCuotasPagadas() + 1);
        BigDecimal nuevoSaldo = (loan.getSaldoPendiente() != null
                ? loan.getSaldoPendiente()
                : BigDecimal.ZERO)
                .subtract(proxima.getTotalCuota());
        loan.setSaldoPendiente(nuevoSaldo.max(BigDecimal.ZERO));
        if (loan.getSaldoPendiente().compareTo(BigDecimal.ZERO) == 0)
            loan.setStatus(Loan.LoanStatus.paid);
        loanRepository.save(loan);

        transactionRepository.save(Transaction.builder()
                .txCode("PGO-" + loan.getLoanCode() + "-C" + proxima.getNumeroCuota())
                .type(Transaction.TxType.loan_payment)
                .originAccount(cuenta)
                .destinationAccount(cuentaDestinoPago)
                .amount(proxima.getTotalCuota())
                .description("Pago cuota " + proxima.getNumeroCuota() + " préstamo " + loan.getLoanCode())
                .reference(loan.getLoanCode())
                .status(Transaction.TxStatus.completed)
                .createdBy(user)
                .build());

        String msgPago = loan.getStatus() == Loan.LoanStatus.paid
                ? " ¡Préstamo " + loan.getLoanCode() + " pagado completamente!"
                : "Cuota " + proxima.getNumeroCuota() + "/" + loan.getPlazoMeses()
                        + " pagada. Saldo restante: $" + loan.getSaldoPendiente();
        notificationService.crearNotificacion(user, "Pago de cuota registrado", msgPago, "loan_payment", loan.getId());

        return new MessageResponse("Cuota " + proxima.getNumeroCuota() + " pagada exitosamente.");
    }

    /**
     * Registro de pago MANUAL por un admin (ej. el socio pagó en efectivo en
     * oficina). A diferencia de {@link #pagarCuota}, esto NO descuenta de
     * ninguna cuenta digital del socio — solo deja constancia del abono y
     * avanza el préstamo, quedando trazado quién lo registró (auditoría).
     *
     * @param tipoPago   "completo" (paga la próxima cuota íntegra, default)
     *                   o "abono" (abono parcial libre aplicado directo al
     *                   saldo pendiente, sin marcar ninguna cuota como pagada
     *                   — mismo mecanismo que el beneficio "Abono a Capital").
     * @param montoAbono monto del abono; solo se usa cuando tipoPago="abono".
     */
    @Transactional
    public java.util.Map<String, Object> registrarPagoManual(Long loanId, User admin, String metodo,
            String observacion, String tipoPago, BigDecimal montoAbono) {
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new IllegalArgumentException("Préstamo no encontrado."));
        if (loan.getStatus() != Loan.LoanStatus.active && loan.getStatus() != Loan.LoanStatus.approved)
            throw new IllegalArgumentException("Este préstamo no admite pagos en su estado actual.");

        boolean esAbono = "abono".equalsIgnoreCase(tipoPago);
        BigDecimal saldoAnterior = loan.getSaldoPendiente() != null ? loan.getSaldoPendiente() : BigDecimal.ZERO;
        String metodoTexto = (metodo != null && !metodo.isBlank()) ? metodo : "efectivo";

        if (esAbono) return registrarAbonoManual(loan, admin, metodoTexto, observacion, montoAbono, saldoAnterior);
        return registrarPagoCuotaManual(loan, admin, metodoTexto, observacion, saldoAnterior);
    }

    /** Rama "pago completo": paga la próxima cuota pendiente íntegra. */
    private java.util.Map<String, Object> registrarPagoCuotaManual(Loan loan, User admin, String metodoTexto,
            String observacion, BigDecimal saldoAnterior) {
        List<LoanPayment> cuotas = loanPaymentRepository.findByLoanIdOrderByNumeroCuota(loan.getId());
        LoanPayment proxima = cuotas.stream()
                .filter(c -> c.getStatus() == LoanPayment.PaymentStatus.pending)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No hay cuotas pendientes."));

        proxima.setStatus(LoanPayment.PaymentStatus.paid);
        proxima.setFechaPago(LocalDate.now());
        loanPaymentRepository.save(proxima);

        creditRiskService.registrarPagoCuota(loan.getBorrowerUser(),
                !LocalDate.now().isAfter(proxima.getFechaVencimiento()),
                loan.getLoanCode(), proxima.getNumeroCuota());

        loan.setCuotasPagadas(loan.getCuotasPagadas() + 1);
        BigDecimal nuevoSaldo = saldoAnterior.subtract(proxima.getTotalCuota()).max(BigDecimal.ZERO);
        loan.setSaldoPendiente(nuevoSaldo);
        if (nuevoSaldo.compareTo(BigDecimal.ZERO) == 0) loan.setStatus(Loan.LoanStatus.paid);
        loanRepository.save(loan);

        // El efectivo que el socio pagó en oficina entra al fondo de donde
        // salió el préstamo — la entidad o el grupo, según corresponda —
        // aunque no haya salido de ninguna cuenta digital del socio.
        Account cuentaDestinoPago = loan.getFundingSource() == Loan.FundingSource.group_fund
                ? accountService.getCuentaGrupo(loan.getGroup())
                : accountService.getCuentaFund();
        cuentaDestinoPago.setBalance(cuentaDestinoPago.getBalance().add(proxima.getTotalCuota()));
        accountRepository.save(cuentaDestinoPago);

        String desc = "Pago manual (" + metodoTexto + ") cuota " + proxima.getNumeroCuota()
                + " préstamo " + loan.getLoanCode() + " — registrado por " + admin.getFirstName()
                + (observacion != null && !observacion.isBlank() ? " · " + observacion : "");

        // Se enlaza a la cuenta del socio (sin tocar su saldo — el pago no
        // sale de ninguna cuenta digital, se pagó en efectivo en oficina)
        // para que la transacción sea visible en su Historial y tenga
        // comprobante: las consultas de historial (TransactionRepository
        // .findByUserId y afines) filtran por originAccount/destinationAccount
        // pertenecientes al socio.

        Account cuentaSocio = accountRepository
                .findFirstByOwnerUserIdAndAccountTypeAndStatus(
                        loan.getBorrowerUser().getId(), Account.AccountType.individual, Account.AccountStatus.active)
                .orElse(null);

        String txCode = "PGO-MAN-" + loan.getLoanCode() + "-C" + proxima.getNumeroCuota();
        transactionRepository.save(Transaction.builder()
                .txCode(txCode)
                .type(Transaction.TxType.loan_payment)
                .originAccount(cuentaSocio)
                .destinationAccount(cuentaDestinoPago)
                .amount(proxima.getTotalCuota())
                .description(desc)
                .reference(loan.getLoanCode())
                .status(Transaction.TxStatus.completed)
                .createdBy(admin)
                .build());

        String msgPago = loan.getStatus() == Loan.LoanStatus.paid
                ? "¡Tu préstamo " + loan.getLoanCode() + " quedó pagado completamente! (registrado en oficina)"
                : "Se registró tu pago en oficina de la cuota " + proxima.getNumeroCuota() + "/"
                        + loan.getPlazoMeses() + ". Saldo restante: $" + loan.getSaldoPendiente();
        notificationService.crearNotificacion(loan.getBorrowerUser(), "Pago registrado por un asesor", msgPago,
                "loan_payment", loan.getId());

        java.util.Map<String, Object> out = new java.util.HashMap<>();
        out.put("mensaje", "Pago de la cuota " + proxima.getNumeroCuota() + " registrado exitosamente.");
        out.put("tipo", "completo");
        out.put("prestamoCodigo", loan.getLoanCode());
        out.put("numeroCuota", proxima.getNumeroCuota());
        out.put("montoAplicado", proxima.getTotalCuota());
        out.put("saldoAnterior", saldoAnterior);
        out.put("saldoNuevo", loan.getSaldoPendiente());
        out.put("referencia", txCode);
        out.put("prestamoLiquidado", loan.getStatus() == Loan.LoanStatus.paid);
        return out;
    }

    /**
     * Rama "abono": aplica un monto libre (menor a la cuota o al saldo total)
     * directo al saldo pendiente del préstamo, sin marcar ninguna cuota del
     * plan como pagada — mismo mecanismo que el canje del beneficio "Abono a
     * Capital" (BenefitController.aplicarAbonoCapital), para que ambos casos
     * se comporten igual y aparezcan igual en el historial del socio.
     */
    private java.util.Map<String, Object> registrarAbonoManual(Loan loan, User admin, String metodoTexto,
            String observacion, BigDecimal montoAbono, BigDecimal saldoAnterior) {
        if (montoAbono == null || montoAbono.compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("El monto del abono debe ser mayor a cero.");
        if (montoAbono.compareTo(saldoAnterior) > 0)
            throw new IllegalArgumentException("El abono no puede ser mayor al saldo pendiente ($" + saldoAnterior + ").");

        BigDecimal nuevoSaldo = saldoAnterior.subtract(montoAbono).max(BigDecimal.ZERO);
        loan.setSaldoPendiente(nuevoSaldo);
        boolean liquidado = nuevoSaldo.compareTo(BigDecimal.ZERO) == 0;
        if (liquidado) loan.setStatus(Loan.LoanStatus.paid);
        loanRepository.save(loan);

        Account cuentaDestinoPago = loan.getFundingSource() == Loan.FundingSource.group_fund
                ? accountService.getCuentaGrupo(loan.getGroup())
                : accountService.getCuentaFund();
        cuentaDestinoPago.setBalance(cuentaDestinoPago.getBalance().add(montoAbono));
        accountRepository.save(cuentaDestinoPago);

        String desc = "Abono manual (" + metodoTexto + ") a capital del préstamo " + loan.getLoanCode()
                + " — registrado por " + admin.getFirstName()
                + (observacion != null && !observacion.isBlank() ? " · " + observacion : "");

        // Mismo enlace a la cuenta del socio que en registrarPagoCuotaManual

        Account cuentaSocio = accountRepository
                .findFirstByOwnerUserIdAndAccountTypeAndStatus(
                        loan.getBorrowerUser().getId(), Account.AccountType.individual, Account.AccountStatus.active)
                .orElse(null);

        String txCode = "ABN-MAN-" + loan.getLoanCode() + "-" + System.currentTimeMillis() % 100000;
        transactionRepository.save(Transaction.builder()
                .txCode(txCode)
                .type(Transaction.TxType.adjustment)
                .originAccount(cuentaSocio)
                .destinationAccount(cuentaDestinoPago)
                .amount(montoAbono)
                .description(desc)
                .reference(loan.getLoanCode())
                .status(Transaction.TxStatus.completed)
                .createdBy(admin)
                .build());

        String msgPago = liquidado
                ? "¡Tu préstamo " + loan.getLoanCode() + " quedó pagado completamente! (abono registrado en oficina)"
                : "Se registró un abono en oficina de $" + montoAbono + " a tu préstamo " + loan.getLoanCode()
                        + ". Saldo restante: $" + nuevoSaldo;
        notificationService.crearNotificacion(loan.getBorrowerUser(), "Abono registrado por un asesor", msgPago,
                "loan_payment", loan.getId());

        java.util.Map<String, Object> out = new java.util.HashMap<>();
        out.put("mensaje", "Abono de $" + montoAbono + " registrado exitosamente.");
        out.put("tipo", "abono");
        out.put("prestamoCodigo", loan.getLoanCode());
        out.put("numeroCuota", null);
        out.put("montoAplicado", montoAbono);
        out.put("saldoAnterior", saldoAnterior);
        out.put("saldoNuevo", nuevoSaldo);
        out.put("referencia", txCode);
        out.put("prestamoLiquidado", liquidado);
        return out;
    }

    private LoanResponse buildResponse(Loan l) {
        LocalDate proximo = loanPaymentRepository.findByLoanIdOrderByNumeroCuota(l.getId()).stream()
                .filter(c -> c.getStatus() == LoanPayment.PaymentStatus.pending)
                .map(LoanPayment::getFechaVencimiento)
                .findFirst().orElse(null);
        return LoanResponse.builder()
                .id(l.getId()).estado(l.getStatus().name())
                .montoSolicitado(l.getMontoSolicitado())
                .plazoMeses(l.getPlazoMeses())
                .cuotaMensual(l.getCuotaMensual())
                .fechaSolicitud(l.getCreatedAt())
                .saldoPendiente(l.getSaldoPendiente())
                .cuotasPagadas(l.getCuotasPagadas() != null ? l.getCuotasPagadas() : 0)
                .proximoVencimiento(proximo)
                .motivoRechazo(l.getMotivoRechazo())
                .build();
    }
}