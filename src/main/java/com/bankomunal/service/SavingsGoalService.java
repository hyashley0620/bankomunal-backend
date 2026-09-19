package com.bankomunal.service;

import com.bankomunal.entity.Account;
import com.bankomunal.entity.SavingsGoal;
import com.bankomunal.entity.Transaction;
import com.bankomunal.entity.User;
import com.bankomunal.repository.AccountRepository;
import com.bankomunal.repository.SavingsGoalRepository;
import com.bankomunal.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Metas de ahorro personales.
 */
@Service
@RequiredArgsConstructor
public class SavingsGoalService {

    private final SavingsGoalRepository goalRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public List<SavingsGoal> misMetas(Long userId) {
        return goalRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public SavingsGoal crear(User user, String nombre, BigDecimal montoMeta, LocalDate fechaLimite) {
        if (nombre == null || nombre.isBlank())
            throw new IllegalArgumentException("La meta necesita un nombre.");
        if (montoMeta == null || montoMeta.compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("El monto de la meta debe ser mayor a cero.");

        return goalRepository.save(SavingsGoal.builder()
                .user(user)
                .nombre(nombre)
                .montoMeta(montoMeta)
                .montoActual(BigDecimal.ZERO)
                .fechaLimite(fechaLimite)
                .status(SavingsGoal.GoalStatus.active)
                .build());
    }

    /**
     * Aporta dinero a una meta: descuenta de la cuenta individual activa del
     * socio y lo suma al progreso de la meta, dejando un movimiento real en
     * `transactions` (tipo `withdrawal`, con referencia a la meta) — igual
     * de trazable que cualquier otra salida de dinero de la cuenta.
     */
    @Transactional
    public SavingsGoal aportar(Long goalId, User user, BigDecimal monto) {
        if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("El monto a aportar debe ser mayor a cero.");

        SavingsGoal meta = goalRepository.findByIdAndUserId(goalId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Meta de ahorro no encontrada."));
        if (meta.getStatus() != SavingsGoal.GoalStatus.active)
            throw new IllegalArgumentException("Esta meta ya no está activa.");

        Account cuenta = accountRepository.findFirstByOwnerUserIdAndAccountTypeAndStatus(
                user.getId(), Account.AccountType.individual, Account.AccountStatus.active)
                .orElseThrow(() -> new IllegalArgumentException("No tiene cuenta activa."));

        if (cuenta.getBalance().compareTo(monto) < 0)
            throw new IllegalArgumentException("Saldo insuficiente para este aporte.");

        cuenta.setBalance(cuenta.getBalance().subtract(monto));
        accountRepository.save(cuenta);

        BigDecimal actualAntes = meta.getMontoActual() != null ? meta.getMontoActual() : BigDecimal.ZERO;
        BigDecimal nuevoActual = actualAntes.add(monto);
        meta.setMontoActual(nuevoActual);

        boolean seCompleto = nuevoActual.compareTo(meta.getMontoMeta()) >= 0;
        if (seCompleto)
            meta.setStatus(SavingsGoal.GoalStatus.completed);
        goalRepository.save(meta);

        String ref = "META-" + goalId + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        transactionRepository.save(Transaction.builder()
                .txCode(ref)
                .type(Transaction.TxType.withdrawal)
                .originAccount(cuenta)
                .amount(monto)
                .description("Aporte a meta de ahorro: " + meta.getNombre())
                .reference(ref)
                .status(Transaction.TxStatus.completed)
                .createdBy(user)
                .build());

        if (seCompleto) {
            notificationService.crearNotificacion(user,
                    " ¡Meta de ahorro alcanzada!",
                    "Completaste tu meta \"" + meta.getNombre() + "\" — ahorraste $" + nuevoActual + ".",
                    "savings_goal_completed", meta.getId());
        }

        return meta;
    }

    @Transactional
    public SavingsGoal cancelar(Long goalId, User user) {
        SavingsGoal meta = goalRepository.findByIdAndUserId(goalId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Meta de ahorro no encontrada."));
        if (meta.getStatus() != SavingsGoal.GoalStatus.active)
            throw new IllegalArgumentException("Esta meta ya no está activa.");
        meta.setStatus(SavingsGoal.GoalStatus.cancelled);
        return goalRepository.save(meta);
    }

    /**
     * Retira el dinero acumulado de una meta cumplida (o cancelada) de vuelta
     * a la cuenta individual del socio.
     */
    @Transactional
    public SavingsGoal retirar(Long goalId, User user) {
        SavingsGoal meta = goalRepository.findByIdAndUserId(goalId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Meta de ahorro no encontrada."));

        if (meta.getStatus() != SavingsGoal.GoalStatus.completed
                && meta.getStatus() != SavingsGoal.GoalStatus.cancelled)
            throw new IllegalArgumentException(
                    "Solo se puede retirar el dinero de una meta cumplida o cancelada.");

        BigDecimal monto = meta.getMontoActual() != null ? meta.getMontoActual() : BigDecimal.ZERO;
        if (monto.compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("Esta meta no tiene saldo para retirar.");

        Account cuenta = accountRepository.findFirstByOwnerUserIdAndAccountTypeAndStatus(
                user.getId(), Account.AccountType.individual, Account.AccountStatus.active)
                .orElseThrow(() -> new IllegalArgumentException("No tiene cuenta activa."));

        cuenta.setBalance(cuenta.getBalance().add(monto));
        accountRepository.save(cuenta);

        meta.setMontoActual(BigDecimal.ZERO);
        meta.setStatus(SavingsGoal.GoalStatus.withdrawn);
        goalRepository.save(meta);

        String ref = "META-RETIRO-" + goalId + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        transactionRepository.save(Transaction.builder()
                .txCode(ref)
                .type(Transaction.TxType.deposit)
                .destinationAccount(cuenta)
                .amount(monto)
                .description("Retiro de meta de ahorro: " + meta.getNombre())
                .reference(ref)
                .status(Transaction.TxStatus.completed)
                .createdBy(user)
                .build());

        return meta;
    }
}
