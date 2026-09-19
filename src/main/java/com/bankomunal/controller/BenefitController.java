package com.bankomunal.controller;

import com.bankomunal.entity.*;
import com.bankomunal.entity.User;
import com.bankomunal.repository.*;
import com.bankomunal.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@RestController
@RequestMapping("/api/beneficios")
@RequiredArgsConstructor
public class BenefitController {

    private final BenefitRepository benefitRepository;
    private final PointsTransactionRepository pointsRepository;
    private final TransactionRepository transactionRepository;
    private final LoanRepository loanRepository;
    private final AccountRepository accountRepository;
    private final NotificationService notificationService;

    /** 1 punto por cada $10.000 pagados en cuotas de préstamo completadas. */
    private static final BigDecimal PESOS_POR_PUNTO = new BigDecimal("10000");

    /**
     * GET /api/beneficios — ver beneficios activos, con costo en puntos y si el
     * socio puede canjearlos
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getBeneficios(@AuthenticationPrincipal User user) {
        List<Benefit> bens = benefitRepository.findByActivoTrueOrderByCreatedAtDesc();

        // Si no hay beneficios en BD, insertar defaults automáticamente
        if (bens.isEmpty()) {
            bens = seedDefaults();
        }

        int disponibles = puntosDisponibles(user);

        return ResponseEntity.ok(bens.stream().map(b -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", b.getId());
            m.put("titulo", b.getTitulo());
            m.put("descripcion", b.getDescripcion());
            m.put("tipo", b.getTipo());
            if (b.getTasaEspecial() != null)
                m.put("tasaEspecial", b.getTasaEspecial().toPlainString());
            m.put("nivelMinimo", b.getNivelMinimo());
            m.put("costoPuntos", b.getCostoPuntos());
            if (b.getMontoAbono() != null)
                m.put("montoAbono", b.getMontoAbono());
            if (b.getCostoPuntos() != null && b.getCostoPuntos() > 0)
                m.put("puedeCanjear", disponibles >= b.getCostoPuntos());
            return m;
        }).toList());
    }

    /** GET /api/beneficios/puntos — saldo de puntos del socio autenticado */
    @GetMapping("/puntos")
    public ResponseEntity<Map<String, Object>> getPuntos(@AuthenticationPrincipal User user) {
        int acumulados = puntosAcumulados(user);
        int canjeados = -1 * Optional.ofNullable(pointsRepository.sumCanjeadosByUserId(user.getId())).orElse(0);
        int disponibles = Math.max(0, acumulados - canjeados);

        return ResponseEntity.ok(Map.of(
                "acumulados", acumulados,
                "canjeados", canjeados,
                "disponibles", disponibles));
    }

    /** GET /api/beneficios/puntos/historial — movimientos de puntos del socio */
    @GetMapping("/puntos/historial")
    public ResponseEntity<List<Map<String, Object>>> getHistorialPuntos(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(
                pointsRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                        .map(p -> {
                            Map<String, Object> m = new LinkedHashMap<>();
                            m.put("id", p.getId());
                            m.put("tipo", p.getTipo().name());
                            m.put("puntos", p.getPuntos());
                            m.put("descripcion", p.getDescripcion());
                            m.put("fecha", p.getCreatedAt().toString());
                            return m;
                        }).toList());
    }

    /**
     * POST /api/beneficios/{id}/canjear — canjea un beneficio específico con puntos
     */
    @PostMapping("/{id}/canjear")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<Map<String, Object>> canjearBeneficio(
            @PathVariable Long id, @AuthenticationPrincipal User user) {

        Optional<Benefit> opt = benefitRepository.findById(id);
        if (opt.isEmpty() || !opt.get().isActivo())
            return ResponseEntity.badRequest().body(Map.of("mensaje", "Beneficio no disponible."));

        Benefit b = opt.get();
        int costo = b.getCostoPuntos() != null ? b.getCostoPuntos() : 0;
        if (costo <= 0)
            return ResponseEntity.badRequest().body(Map.of("mensaje", "Este beneficio no se canjea con puntos."));

        int disponibles = puntosDisponibles(user);
        if (disponibles < costo)
            return ResponseEntity.badRequest().body(Map.of(
                    "mensaje", "No tienes puntos suficientes. Te faltan " + (costo - disponibles) + " puntos."));

        /* ── Efecto real según el tipo de beneficio ──────────────────────── */
        Map<String, Object> efecto = new HashMap<>();
        if ("abono_capital".equals(b.getTipo())) {
            efecto = aplicarAbonoCapital(b, user);
            if (efecto.containsKey("error"))
                return ResponseEntity.badRequest().body(Map.of("mensaje", efecto.get("error")));
        }
        // Otros tipos con efecto monetario/operativo se pueden sumar aquí más
        // adelante (ej. "seguro" activando una póliza). Por ahora tasa_especial,
        // taller, descuento y general siguen su flujo informativo existente
        // (solicitar aplicación / ver cursos / código de comercio) porque no
        // representan una operación reversible sobre el saldo del socio.

        pointsRepository.save(PointsTransaction.builder()
                .user(user)
                .tipo(PointsTransaction.Tipo.CANJEADO)
                .puntos(-costo)
                .descripcion("Canje: " + b.getTitulo())
                .beneficio(b)
                .build());

        int nuevoSaldo = puntosDisponibles(user);
        String codigo = "BNK-" + System.currentTimeMillis() % 100000;

        Map<String, Object> resp = new HashMap<>(Map.of(
                "mensaje", " ¡Beneficio canjeado exitosamente!",
                "disponibles", nuevoSaldo,
                "codigo", codigo));
        resp.putAll(efecto);
        return ResponseEntity.ok(resp);
    }

    /**
     * Aplica un abono a capital sobre el préstamo activo del socio: descuenta el
     * monto del beneficio directamente del saldo pendiente (no de una cuota
     * específica del plan de pagos, que sigue igual) y registra el movimiento
     * para que aparezca en el historial y tenga comprobante.
     */
    private Map<String, Object> aplicarAbonoCapital(Benefit b, User user) {
        Map<String, Object> out = new HashMap<>();
        BigDecimal monto = b.getMontoAbono();
        if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0) {
            out.put("error", "Este beneficio no tiene un monto de abono configurado. Contacta a un administrador.");
            return out;
        }

        Loan loan = loanRepository.findByBorrowerUserId(user.getId()).stream()
                .filter(l -> l.getStatus() == Loan.LoanStatus.active)
                .findFirst()
                .orElse(null);
        if (loan == null) {
            out.put("error", "No tienes un préstamo activo al cual aplicar el abono.");
            return out;
        }

        BigDecimal saldoAnterior = loan.getSaldoPendiente() != null ? loan.getSaldoPendiente() : BigDecimal.ZERO;
        // No abonar más de lo que realmente se debe.
        BigDecimal montoAplicado = monto.min(saldoAnterior);
        BigDecimal saldoNuevo = saldoAnterior.subtract(montoAplicado).max(BigDecimal.ZERO);

        loan.setSaldoPendiente(saldoNuevo);
        if (saldoNuevo.compareTo(BigDecimal.ZERO) == 0)
            loan.setStatus(Loan.LoanStatus.paid);
        loanRepository.save(loan);

        // Se enlaza a la cuenta del socio (sin tocar su saldo, ya que el abono
        // se paga con puntos, no con dinero de la cuenta) para que la
        // transacción sea visible en su Historial y tenga comprobante: las
        // consultas de historial (TransactionRepository.findByUserId y afines)
        // filtran por originAccount/destinationAccount pertenecientes al
        // socio, así que sin esto la transacción quedaba huérfana.
        Account cuentaSocio = accountRepository
                .findFirstByOwnerUserIdAndAccountTypeAndStatus(
                        user.getId(), Account.AccountType.individual, Account.AccountStatus.active)
                .orElse(null);

        // Tipo "adjustment" (no "loan_payment"): así no se cuenta dos veces
        // como egreso de caja en los totales de Historial/Salud Financiera
        // (que sí tratan loan_payment como salida de dinero real) ni se
        // computa como "cuota pagada" para ganar puntos — el abono se paga
        // con puntos, no con dinero de la cuenta, así que no debe generar
        // más puntos a cambio.
        String ref = "ABC-" + loan.getLoanCode() + "-" + System.currentTimeMillis() % 100000;
        transactionRepository.save(Transaction.builder()
                .txCode(ref)
                .type(Transaction.TxType.adjustment)
                .originAccount(cuentaSocio)
                .amount(montoAplicado)
                .description("Abono a capital (beneficio: " + b.getTitulo() + ") — préstamo " + loan.getLoanCode())
                .reference(ref)
                .status(Transaction.TxStatus.completed)
                .createdBy(user)
                .build());

        notificationService.crearNotificacion(user,
                "Abono a capital aplicado",
                "Se aplicó un abono de $" + montoAplicado + " a tu préstamo " + loan.getLoanCode()
                        + ". Saldo pendiente: $" + saldoNuevo + ".",
                "loan_payment", loan.getId());

        out.put("prestamoCodigo", loan.getLoanCode());
        out.put("montoAplicado", montoAplicado);
        out.put("saldoPendienteAnterior", saldoAnterior);
        out.put("saldoPendienteNuevo", saldoNuevo);
        out.put("referencia", ref);
        out.put("prestamoLiquidado", saldoNuevo.compareTo(BigDecimal.ZERO) == 0);
        return out;
    }

    /** POST /api/beneficios/reservar */
    @PostMapping("/reservar")
    public ResponseEntity<Map<String, Object>> reservar(
            @RequestBody(required = false) Map<String, Object> body,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(Map.of("mensaje", "¡Cupo reservado! Te confirmaremos por correo."));
    }

    /** GET /api/beneficios/codigos */
    @GetMapping("/codigos")
    public ResponseEntity<List<Map<String, Object>>> getCodigos(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(List.of(
                Map.of("codigo", "BNK-2026-A1", "descripcion", "Descuento 10% talleres"),
                Map.of("codigo", "BNK-2026-B2", "descripcion", "Tasa especial préstamo")));
    }

    // ── ADMIN CRUD ──────────────────────────────────────────────────────────

    /** POST /api/beneficios/admin — crear beneficio (solo admin) */
    @PostMapping("/admin")
    public ResponseEntity<Map<String, Object>> crear(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal User user) {
        if (!isAdmin(user))
            return ResponseEntity.status(403).body(Map.of("mensaje", "Acceso denegado."));

        Benefit b = Benefit.builder()
                .titulo(body.getOrDefault("titulo", "Nuevo beneficio").toString())
                .descripcion(body.getOrDefault("descripcion", "").toString())
                .tipo(body.getOrDefault("tipo", "general").toString())
                .nivelMinimo(body.getOrDefault("nivelMinimo", "basico").toString())
                .activo(true)
                .build();

        if (body.get("tasaEspecial") != null && !body.get("tasaEspecial").toString().isBlank())
            b.setTasaEspecial(new BigDecimal(body.get("tasaEspecial").toString()));
        if (body.get("costoPuntos") != null && !body.get("costoPuntos").toString().isBlank())
            b.setCostoPuntos((int) Double.parseDouble(body.get("costoPuntos").toString()));
        if (body.get("montoAbono") != null && !body.get("montoAbono").toString().isBlank())
            b.setMontoAbono(new BigDecimal(body.get("montoAbono").toString()));

        Benefit saved = benefitRepository.save(b);
        return ResponseEntity.ok(Map.of(
                "id", saved.getId(), "titulo", saved.getTitulo(),
                "mensaje", "Beneficio creado exitosamente."));
    }

    /** PUT /api/beneficios/admin/{id} — editar beneficio (solo admin) */
    @PutMapping("/admin/{id}")
    public ResponseEntity<Map<String, Object>> editar(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal User user) {
        if (!isAdmin(user))
            return ResponseEntity.status(403).body(Map.of("mensaje", "Acceso denegado."));

        return benefitRepository.findById(id).map(b -> {
            if (body.containsKey("titulo"))
                b.setTitulo(body.getOrDefault("titulo", "").toString());
            if (body.containsKey("descripcion"))
                b.setDescripcion(body.getOrDefault("descripcion", "").toString());
            if (body.containsKey("tipo"))
                b.setTipo(body.getOrDefault("tipo", "").toString());
            if (body.containsKey("nivelMinimo"))
                b.setNivelMinimo(body.getOrDefault("nivelMinimo", "").toString());
            if (body.containsKey("activo"))
                b.setActivo(Boolean.parseBoolean(body.getOrDefault("activo", "").toString()));
            if (body.containsKey("tasaEspecial") && body.get("tasaEspecial") != null
                    && !body.get("tasaEspecial").toString().isBlank())
                b.setTasaEspecial(new BigDecimal(body.get("tasaEspecial").toString()));
            if (body.containsKey("costoPuntos") && body.get("costoPuntos") != null
                    && !body.get("costoPuntos").toString().isBlank())
                b.setCostoPuntos((int) Double.parseDouble(body.get("costoPuntos").toString()));
            if (body.containsKey("montoAbono") && body.get("montoAbono") != null
                    && !body.get("montoAbono").toString().isBlank())
                b.setMontoAbono(new BigDecimal(body.get("montoAbono").toString()));
            benefitRepository.save(b);
            return ResponseEntity
                    .ok(Map.<String, Object>of("id", b.getId(), "titulo", b.getTitulo(), "mensaje", "Actualizado."));
        }).orElse(ResponseEntity.notFound().<Map<String, Object>>build());
    }

    /** DELETE /api/beneficios/admin/{id} — desactivar beneficio (solo admin) */
    @DeleteMapping("/admin/{id}")
    public ResponseEntity<Map<String, Object>> desactivar(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        if (!isAdmin(user))
            return ResponseEntity.status(403).body(Map.of("mensaje", "Acceso denegado."));

        return benefitRepository.findById(id).map(b -> {
            b.setActivo(false);
            benefitRepository.save(b);
            return ResponseEntity.ok(Map.<String, Object>of("mensaje", "Beneficio desactivado."));
        }).orElse(ResponseEntity.notFound().<Map<String, Object>>build());
    }

    /** GET /api/beneficios/admin — listar todos (activos e inactivos) */
    @GetMapping("/admin")
    public ResponseEntity<List<Benefit>> listarTodos(@AuthenticationPrincipal User user) {
        if (!isAdmin(user))
            return ResponseEntity.status(403).build();
        return ResponseEntity.ok(benefitRepository.findAll());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private boolean isAdmin(User u) {
        return u.getRoles().stream().anyMatch(r -> "admin".equalsIgnoreCase(r.getName()));
    }

    /**
     * Puntos ganados por el socio: 1 punto por cada $10.000 pagados en cuotas
     * completadas, más los puntos otorgados por cursos de educación financiera
     * completados (registrados como movimientos GANADO en el ledger).
     */
    private int puntosAcumulados(User user) {
        BigDecimal pagado = transactionRepository.sumPagosPrestamoCompletados(user.getId());
        if (pagado == null)
            pagado = BigDecimal.ZERO;
        int puntosPrestamos = pagado.divide(PESOS_POR_PUNTO, 0, RoundingMode.DOWN).intValue();
        int puntosCursos = Optional.ofNullable(pointsRepository.sumGanadosByUserId(user.getId())).orElse(0);
        return puntosPrestamos + puntosCursos;
    }

    /** Puntos disponibles = acumulados - canjeados (nunca negativo). */
    private int puntosDisponibles(User user) {
        int acumulados = puntosAcumulados(user);
        int canjeados = -1 * Optional.ofNullable(pointsRepository.sumCanjeadosByUserId(user.getId())).orElse(0);
        return Math.max(0, acumulados - canjeados);
    }

    private List<Benefit> seedDefaults() {
        List<Benefit> defaults = List.of(
                Benefit.builder().titulo("Tasa Preferencial").tipo("tasa_especial")
                        .descripcion("Accede a préstamos con tasa del 1.2% mensual para socios con buen historial.")
                        .tasaEspecial(new BigDecimal("1.20")).nivelMinimo("basico").build(),
                Benefit.builder().titulo("Taller Finanzas Personales").tipo("taller")
                        .descripcion("Próximo taller: Inversión básica y gestión de deudas. Incluye material digital.")
                        .nivelMinimo("basico").costoPuntos(50).build(),
                Benefit.builder().titulo("Seguro de Vida Grupal").tipo("seguro")
                        .descripcion("Cobertura grupal incluida en tu membresía. Aplica para socios activos.")
                        .nivelMinimo("basico").build(),
                Benefit.builder().titulo("Descuento Comercios Aliados").tipo("descuento")
                        .descripcion("10% de descuento en más de 50 comercios aliados de tu comunidad.")
                        .nivelMinimo("basico").costoPuntos(30).build(),
                Benefit.builder().titulo("Abono a Capital").tipo("abono_capital")
                        .descripcion("Canjea tus puntos por un abono directo a capital de tu crédito activo.")
                        .nivelMinimo("basico").costoPuntos(100).montoAbono(new BigDecimal("50000")).build());
        return benefitRepository.saveAll(defaults);
    }
}