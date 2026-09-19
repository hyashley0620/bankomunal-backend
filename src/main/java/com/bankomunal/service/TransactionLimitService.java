package com.bankomunal.service;

import com.bankomunal.entity.Group;
import com.bankomunal.entity.Transaction;
import com.bankomunal.entity.TransactionLimit;
import com.bankomunal.entity.User;
import com.bankomunal.repository.TransactionLimitRepository;
import com.bankomunal.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Hace cumplir los límites configurados en `transaction_limits`.
 *
 * Prioridad de resolución (el primero que exista gana, no se acumulan):
 *   1) límite específico del socio (scope=user)      — el admin puede
 *      apretar o aflojar el límite de una persona puntual
 *   2) límite específico del grupo (scope=group)      — solo aplica a
 *      operaciones con contexto de grupo, como el fondo común
 *   3) límite global (scope=global)                   — el que aplica por
 *      defecto a todo el mundo
 * Dentro de cada nivel, se prefiere un límite específico para ese tipo de
 * transacción (tx_type = 'transfer', etc.) sobre uno genérico
 * (tx_type = NULL, "aplica a todo").
 *
 * Si no hay ningún límite configurado en ningún nivel, no se bloquea nada
 * (comportamiento permisivo por ausencia de configuración, no por bug).
 */
@Service
@RequiredArgsConstructor
public class TransactionLimitService {

    private final TransactionLimitRepository limitRepository;
    private final TransactionRepository transactionRepository;

    /**
     * Valida que `monto` no viole ningún límite aplicable para `user` (y,
     * si aplica, `group`) en una transacción de tipo `tipo`. Lanza
     * IllegalArgumentException con un mensaje claro para el usuario si algún
     * límite se violaría — el mismo tipo de excepción que ya usa el resto
     * de la app para errores de validación, para que el manejo de errores
     * del frontend (extraerMensajeError) funcione igual que siempre.
     */
    @Transactional(readOnly = true)
    public void validar(User user, Group group, Transaction.TxType tipo, BigDecimal monto) {
        TransactionLimit limite = resolver(TransactionLimit.Scope.user, user.getId(), tipo)
                .or(() -> group != null
                        ? resolver(TransactionLimit.Scope.group, group.getId(), tipo)
                        : Optional.empty())
                .or(() -> resolver(TransactionLimit.Scope.global, null, tipo))
                .orElse(null);

        if (limite == null) {
            return; // sin límite configurado para este socio/grupo/tipo — no se bloquea
        }

        if (monto.compareTo(limite.getMaxPerTransaction()) > 0) {
            throw new IllegalArgumentException(
                    "El monto supera el límite por transacción ($" + formatear(limite.getMaxPerTransaction()) + ").");
        }

        LocalDateTime inicioHoy = LocalDate.now().atStartOfDay();
        LocalDateTime finHoy = inicioHoy.plusDays(1);
        BigDecimal totalHoy = transactionRepository.sumSalienteByUsuarioYTipo(
                user.getId(), tipo, inicioHoy, finHoy);
        if (totalHoy.add(monto).compareTo(limite.getMaxPerDay()) > 0) {
            throw new IllegalArgumentException(
                    "Esta operación superaría tu límite diario de $" + formatear(limite.getMaxPerDay())
                            + " (ya llevas $" + formatear(totalHoy) + " hoy).");
        }

        LocalDateTime inicioSemana = LocalDate.now()
                .with(DayOfWeek.MONDAY).atStartOfDay();
        BigDecimal totalSemana = transactionRepository.sumSalienteByUsuarioYTipo(
                user.getId(), tipo, inicioSemana, finHoy);
        if (totalSemana.add(monto).compareTo(limite.getMaxPerWeek()) > 0) {
            throw new IllegalArgumentException(
                    "Esta operación superaría tu límite semanal de $" + formatear(limite.getMaxPerWeek())
                            + " (ya llevas $" + formatear(totalSemana) + " esta semana).");
        }
    }

    private Optional<TransactionLimit> resolver(TransactionLimit.Scope scope, Long scopeId, Transaction.TxType tipo) {
        List<TransactionLimit> candidatos = scopeId != null
                ? limitRepository.findByScopeAndScopeId(scope, scopeId)
                : limitRepository.findByScopeAndScopeIdIsNull(scope);

        String tipoStr = tipo.name();
        return candidatos.stream()
                .filter(l -> tipoStr.equalsIgnoreCase(l.getTxType()))
                .findFirst()
                .or(() -> candidatos.stream().filter(l -> l.getTxType() == null).findFirst());
    }

    private String formatear(BigDecimal monto) {
        return String.format("%,.0f", monto);
    }
}
