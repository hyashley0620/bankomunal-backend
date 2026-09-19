package com.bankomunal.repository;

import com.bankomunal.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.List;

@Repository
public interface LoanRepository extends JpaRepository<Loan, Long> {
    List<Loan> findByBorrowerUserId(Long userId);

    List<Loan> findAllByOrderByCreatedAtDesc();

    List<Loan> findByGroupIdOrderByCreatedAtDesc(Long groupId);

    /**
     * Cuánto del fondo común de un grupo ya está comprometido en préstamos
     * solidarios que todavía no se desembolsaron — ya sea porque siguen en
     * votación (pending) o porque el socio ya ganó la votación pero aún no
     * acepta el contrato (approved). Ese dinero técnicamente sigue en el
     * balance de la cuenta del grupo (el desembolso real recién descuenta
     * en aceptarContrato), pero ya no está realmente "libre" — dos socios
     * proponiendo préstamos casi al mismo tiempo no deberían poder
     * comprometer, entre los dos, más de lo que el fondo tiene.
     */
    @Query("SELECT COALESCE(SUM(l.montoSolicitado), 0) FROM Loan l " +
            "WHERE l.group.id = :groupId AND l.fundingSource = :fundingSource " +
            "AND l.status IN :estados")
    BigDecimal sumComprometidoPorGrupo(
            @Param("groupId") Long groupId,
            @Param("fundingSource") Loan.FundingSource fundingSource,
            @Param("estados") List<Loan.LoanStatus> estados);

    long count();
}
