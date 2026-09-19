package com.bankomunal.service;

import com.bankomunal.entity.CreditRiskHistory;
import com.bankomunal.entity.User;
import com.bankomunal.repository.CreditRiskHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Calcula y registra el score de riesgo crediticio de cada socio, con base
 * en su comportamiento de pago real.
 *
 * Modelo simple tipo "score" (300–850, igual de intención que un buró de
 * crédito real, sin pretender ser uno):
 *   - Arranca en 700 (riesgo medio) para un socio sin historial.
 *   - +15 puntos por cada cuota pagada A TIEMPO.
 *   - -40 puntos por cada cuota pagada CON ATRASO.
 *   - -80 puntos si un préstamo se rechaza por mal historial (uso futuro).
 * Se guarda un registro nuevo en cada evento — así queda la traza completa
 * de cómo fue cambiando el riesgo de un socio a lo largo del tiempo, que es
 * justamente para lo que existe la tabla.
 */
@Service
@RequiredArgsConstructor
public class CreditRiskService {

    private static final int SCORE_INICIAL = 700;
    private static final int SCORE_MIN = 300;
    private static final int SCORE_MAX = 850;

    private final CreditRiskHistoryRepository riskRepository;

    /** Último score registrado, o el valor inicial neutro si el socio no tiene historial aún. */
    @Transactional(readOnly = true)
    public CreditRiskHistory getScoreActual(Long userId) {
        return riskRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)
                .orElseGet(() -> CreditRiskHistory.builder()
                        .score(SCORE_INICIAL)
                        .riskLevel(nivelParaScore(SCORE_INICIAL))
                        .motivo("Sin historial de pagos todavía.")
                        .build());
    }

    /** Historial completo, del más reciente al más antiguo. */
    @Transactional(readOnly = true)
    public List<CreditRiskHistory> getHistorial(Long userId) {
        return riskRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /**
     * Registra el pago de una cuota (a tiempo o con atraso) y actualiza el
     * score. Se ejecuta en su propia transacción (REQUIRES_NEW), igual que
     * TransactionService#registrarMovimientoIndependiente — el registro de
     * riesgo es secundario al pago en sí; si por algo falla no debe tumbar
     * el pago que ya se aplicó.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarPagoCuota(User user, boolean aTiempo, String loanCode, int numeroCuota) {
        int scoreAnterior = getScoreActual(user.getId()).getScore();
        int delta = aTiempo ? 15 : -40;
        int nuevoScore = clamp(scoreAnterior + delta);

        String motivo = aTiempo
                ? "Cuota " + numeroCuota + " de " + loanCode + " pagada a tiempo."
                : "Cuota " + numeroCuota + " de " + loanCode + " pagada con atraso.";

        riskRepository.save(CreditRiskHistory.builder()
                .user(user)
                .score(nuevoScore)
                .riskLevel(nivelParaScore(nuevoScore))
                .motivo(motivo)
                .build());
    }

    /** Penalización cuando un préstamo termina en mora/incumplimiento declarado por un admin. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarIncumplimiento(User user, String loanCode) {
        int scoreAnterior = getScoreActual(user.getId()).getScore();
        int nuevoScore = clamp(scoreAnterior - 80);
        riskRepository.save(CreditRiskHistory.builder()
                .user(user)
                .score(nuevoScore)
                .riskLevel(nivelParaScore(nuevoScore))
                .motivo("Incumplimiento registrado en préstamo " + loanCode + ".")
                .build());
    }

    private int clamp(int score) {
        return Math.max(SCORE_MIN, Math.min(SCORE_MAX, score));
    }

    private CreditRiskHistory.RiskLevel nivelParaScore(int score) {
        if (score >= 700)
            return CreditRiskHistory.RiskLevel.bajo;
        if (score >= 500)
            return CreditRiskHistory.RiskLevel.medio;
        return CreditRiskHistory.RiskLevel.alto;
    }
}
