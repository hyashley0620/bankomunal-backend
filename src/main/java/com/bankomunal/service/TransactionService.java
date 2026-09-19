package com.bankomunal.service;

import com.bankomunal.dto.request.*;
import com.bankomunal.dto.response.*;
import com.bankomunal.entity.*;
import com.bankomunal.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final NotificationService notificationService;
    private final FacturaService facturaService;
    private final TransactionLimitService transactionLimitService;
    private final TransactionReceiptRepository transactionReceiptRepository;

    /**
     * Guarda un registro de transacción como operación "mejor esfuerzo", en su
     * propia transacción física (REQUIRES_NEW). Pensado para llamadas donde el
     * registro es secundario a una operación de negocio que ya se aplicó (por
     * ejemplo, aportar al fondo común) — así, si el insert falla, solo se
     * revierte este registro y no arrastra a rollback-only la transacción del
     * llamador. Atrapar la excepción con try/catch NO logra esto por sí solo:
     * una vez que la BD lanza un error dentro de una transacción @Transactional
     * activa, Spring/Hibernate la marcan como rollback-only sin importar si el
     * código de la aplicación atrapó la excepción.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarMovimientoIndependiente(Transaction tx) {
        transactionRepository.save(tx);
    }

    /** Movimientos del usuario con filtro opcional de tipo */
    @Transactional(readOnly = true)
    public List<TransactionResponse> getMovimientos(
            Long userId, LocalDateTime inicio, LocalDateTime fin, String tipo) {

        List<Transaction> txs = (inicio != null && fin != null)
                ? transactionRepository.findByUserIdAndDateRange(userId, inicio, fin)
                : transactionRepository.findByUserId(userId);

        List<TransactionResponse> respuestas = txs.stream().map(t -> {
            String tipoEfectivo = t.getType().name();
            String descEfectiva = t.getDescription();

            if (t.getType() == Transaction.TxType.transfer
                    && t.getDestinationAccount() != null
                    && t.getDestinationAccount().getOwnerUser() != null
                    && t.getDestinationAccount().getOwnerUser().getId().equals(userId)
                    && (t.getOriginAccount() == null
                            || t.getOriginAccount().getOwnerUser() == null
                            || !t.getOriginAccount().getOwnerUser().getId().equals(userId))) {

                tipoEfectivo = Transaction.TxType.transfer_received.name();
                String origenLabel = t.getOriginAccount() != null
                        ? t.getOriginAccount().getAccountCode()
                        : "otra cuenta";
                descEfectiva = "Transferencia recibida de " + origenLabel;
            }

            return TransactionResponse.builder()
                    .tipo(tipoEfectivo)
                    .fecha(t.getCreatedAt())
                    .monto(t.getAmount())
                    .descripcion(descEfectiva)
                    .referencia(t.getReference())
                    .estado(t.getStatus().name())
                    .cuentaOrigen(t.getOriginAccount() != null ? t.getOriginAccount().getAccountCode() : null)
                    .cuentaDestino(t.getDestinationAccount() != null ? t.getDestinationAccount().getAccountCode() : null)
                    .build();
        }).collect(Collectors.toList());

        if (tipo != null && !tipo.isBlank()) {
            final String tipoFiltro = tipo.toLowerCase();
            respuestas = respuestas.stream()
                    .filter(r -> r.getTipo().equalsIgnoreCase(tipoFiltro))
                    .collect(Collectors.toList());
        }

        return respuestas;
    }

    /** Sobrecarga sin filtro de tipo */
    public List<TransactionResponse> getMovimientos(
            Long userId, LocalDateTime inicio, LocalDateTime fin) {
        return getMovimientos(userId, inicio, fin, null);
    }

    /** Transferencia interna + notificación push */
    @Transactional
    public ComprobantResponse transfer(TransferRequest req, User user) {
        Account origen = accountRepository.findByAccountCode(req.getOrigen())
                .orElseThrow(() -> new IllegalArgumentException("Cuenta origen no encontrada."));
        Account destino = accountRepository.findByAccountCode(req.getDestino())
                .orElseThrow(() -> new IllegalArgumentException("Cuenta destino no encontrada."));

        if (origen.getBalance().compareTo(req.getMonto()) < 0)
            throw new IllegalArgumentException("Saldo insuficiente en la cuenta origen.");

        transactionLimitService.validar(user, null, Transaction.TxType.transfer, req.getMonto());

        origen.setBalance(origen.getBalance().subtract(req.getMonto()));
        destino.setBalance(destino.getBalance().add(req.getMonto()));
        accountRepository.save(origen);
        accountRepository.save(destino);

        String ref = "TRF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Transaction tx = Transaction.builder()
                .txCode(ref)
                .type(Transaction.TxType.transfer)
                .originAccount(origen)
                .destinationAccount(destino)
                .amount(req.getMonto())
                .description(req.getDescripcion() != null ? req.getDescripcion() : "Transferencia")
                .reference(ref)
                .status(Transaction.TxStatus.completed)
                .createdBy(user)
                .build();
        transactionRepository.save(tx);

        // Comprobante persistido — 
        // Ahora queda disponible para reimprimir vía
        // GET /api/transferencias/{referencia}/comprobante.

        transactionReceiptRepository.save(TransactionReceipt.builder()
                .transaction(tx)
                .referencia(ref)
                .cuentaOrigen(origen.getAccountCode())
                .cuentaDestino(destino.getAccountCode())
                .tipo("Transferencia")
                .descripcion(tx.getDescription())
                .estado("completada")
                .codigoVerificacion(ref)
                .build());

        /* ── Notificación al remitente ─────────────────────────────────── */
        notificationService.crearNotificacion(user,
                "Transferencia enviada",
                "Enviaste $" + req.getMonto() + " a " + req.getDestino() + ". Ref: " + ref,
                "transfer_sent", tx.getId());

        /* ── Notificación al destinatario (si tiene usuario propietario) ── */
        if (destino.getOwnerUser() != null && !destino.getOwnerUser().getId().equals(user.getId())) {
            notificationService.crearNotificacion(destino.getOwnerUser(),
                    "Transferencia recibida",
                    "Recibiste $" + req.getMonto() + " de " + req.getOrigen() + ". Ref: " + ref,
                    "transfer_received", tx.getId());
        }

        return ComprobantResponse.builder()
                .referencia(ref).tipo("Transferencia").monto(req.getMonto()).moneda("COP")
                .descripcion(tx.getDescription()).estado("completada")
                .fecha(tx.getCreatedAt())
                .cuentaOrigen(origen.getAccountCode())
                .cuentaDestino(destino.getAccountCode())
                .codigoVerificacion(ref)
                .build();
    }

    /** Pago de servicio + notificación */
    @Transactional
    public PaymentResponse pagarServicio(PaymentRequest req, User user) {
        // Recalcula la factura del lado del servidor — no confiamos en el monto
        // que venga del cliente, así evitamos que alguien manipule el pago
        // desde el navegador. El monto real es el que corresponde a esa
        // referencia, igual que en cualquier pasarela de pago de facturas.
        FacturaResponse factura = facturaService.consultarFactura(req.getServicio(), req.getReferencia());

        Account cuenta = accountRepository.findByAccountCode(req.getCuenta())
                .orElseGet(() -> {
                    List<Account> ctas = accountRepository.findByOwnerUserIdAndStatus(
                            user.getId(), Account.AccountStatus.active);
                    if (ctas.isEmpty())
                        throw new IllegalArgumentException("No tiene cuentas activas.");
                    return ctas.get(0);
                });

        if (cuenta.getBalance().compareTo(factura.getMonto()) < 0)
            throw new IllegalArgumentException("Saldo insuficiente.");

        transactionLimitService.validar(user, null, Transaction.TxType.service_payment, factura.getMonto());

        cuenta.setBalance(cuenta.getBalance().subtract(factura.getMonto()));
        accountRepository.save(cuenta);

        String ref = "PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Transaction txPago = transactionRepository.save(Transaction.builder()
                .txCode(ref).type(Transaction.TxType.service_payment)
                .originAccount(cuenta)
                .amount(factura.getMonto())
                .description("Pago " + factura.getEmpresa() + " (ref. " + factura.getReferencia() + ")")
                .reference(req.getReferencia())
                .status(Transaction.TxStatus.completed)
                .createdBy(user)
                .build());

        // Comprobante persistido — mismo mecanismo que en transfer().
        transactionReceiptRepository.save(TransactionReceipt.builder()
                .transaction(txPago)
                .referencia(ref)
                .cuentaOrigen(cuenta.getAccountCode())
                .tipo("Pago de servicio")
                .descripcion(txPago.getDescription())
                .estado("completada")
                .codigoVerificacion(ref)
                .build());

        /* ── Notificación ─────────────────────────────────────────────── */
        notificationService.crearNotificacion(user,
                "Pago realizado",
                "Pagaste $" + factura.getMonto() + " a " + factura.getEmpresa() + ". Ref: " + ref,
                "payment", txPago.getId());

        return PaymentResponse.builder()
                .referencia(ref)
                .empresa(factura.getEmpresa())
                .monto(factura.getMonto())
                .build();
    }

    /**
     * Recupera un comprobante ya emitido por su referencia — permite
     * reimprimirlo después aunque el socio ya haya cerrado la ventana o
     * recargado la página. Solo puede verlo el
     * dueño de la cuenta origen o destino involucrada, o un admin.
     */
    @Transactional(readOnly = true)
    public ComprobantResponse getComprobante(String referencia, User user) {
        TransactionReceipt r = transactionReceiptRepository.findByReferencia(referencia)
                .orElseThrow(() -> new IllegalArgumentException("Comprobante no encontrado."));

        Transaction tx = r.getTransaction();
        boolean esDueno =
                (tx.getOriginAccount() != null && tx.getOriginAccount().getOwnerUser() != null
                        && tx.getOriginAccount().getOwnerUser().getId().equals(user.getId()))
                || (tx.getDestinationAccount() != null && tx.getDestinationAccount().getOwnerUser() != null
                        && tx.getDestinationAccount().getOwnerUser().getId().equals(user.getId()));
        boolean esAdmin = user.getRoles().stream()
                .anyMatch(role -> "admin".equalsIgnoreCase(role.getName()));
        if (!esDueno && !esAdmin)
            throw new SecurityException("No tienes acceso a este comprobante.");

        return ComprobantResponse.builder()
                .referencia(r.getReferencia())
                .tipo(r.getTipo())
                .monto(tx.getAmount())
                .moneda("COP")
                .descripcion(r.getDescripcion())
                .estado(r.getEstado())
                .fecha(r.getCreatedAt())
                .cuentaOrigen(r.getCuentaOrigen())
                .cuentaDestino(r.getCuentaDestino())
                .codigoVerificacion(r.getCodigoVerificacion())
                .build();
    }
}