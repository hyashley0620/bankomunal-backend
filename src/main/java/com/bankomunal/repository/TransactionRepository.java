package com.bankomunal.repository;

import com.bankomunal.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

       @Query("SELECT t FROM Transaction t WHERE " +
                     "(t.originAccount.id IN (SELECT a.id FROM Account a WHERE a.ownerUser.id = :uid) OR " +
                     " t.destinationAccount.id IN (SELECT a.id FROM Account a WHERE a.ownerUser.id = :uid)) " +
                     "ORDER BY t.createdAt DESC")
       List<Transaction> findByUserId(@Param("uid") Long userId);

       @Query("SELECT t FROM Transaction t WHERE " +
                     "(t.originAccount.id IN (SELECT a.id FROM Account a WHERE a.ownerUser.id = :uid) OR " +
                     " t.destinationAccount.id IN (SELECT a.id FROM Account a WHERE a.ownerUser.id = :uid)) " +
                     "AND t.createdAt BETWEEN :inicio AND :fin ORDER BY t.createdAt DESC")
       List<Transaction> findByUserIdAndDateRange(
                     @Param("uid") Long userId,
                     @Param("inicio") LocalDateTime inicio,
                     @Param("fin") LocalDateTime fin);

       @Deprecated
       @Query("SELECT COALESCE(SUM(t.amount),0) FROM Transaction t " +
                     "WHERE t.type IN ('deposit','loan_disbursement','transfer_received') " +
                     "AND t.createdAt BETWEEN :inicio AND :fin")
       BigDecimal sumIngresosByDateRange(
                     @Param("inicio") LocalDateTime inicio,
                     @Param("fin") LocalDateTime fin);

       @Deprecated
       @Query("SELECT COALESCE(SUM(t.amount),0) FROM Transaction t " +
                     "WHERE t.type IN ('withdrawal','loan_payment','fee','service_payment','fondo_comun','transfer') " +
                     "AND t.createdAt BETWEEN :inicio AND :fin")
       BigDecimal sumEgresosByDateRange(
                     @Param("inicio") LocalDateTime inicio,
                     @Param("fin") LocalDateTime fin);

       /** Ingresos del socio (incluye desembolsos de préstamo) en un rango de fechas. */
       @Query("SELECT COALESCE(SUM(t.amount),0) FROM Transaction t " +
                     "WHERE (t.originAccount.id IN (SELECT a.id FROM Account a WHERE a.ownerUser.id = :uid) OR " +
                     " t.destinationAccount.id IN (SELECT a.id FROM Account a WHERE a.ownerUser.id = :uid)) " +
                     "AND t.type IN ('deposit','loan_disbursement','transfer_received') " +
                     "AND t.createdAt BETWEEN :inicio AND :fin")
       BigDecimal sumIngresosByUsuarioYRango(
                     @Param("uid") Long userId,
                     @Param("inicio") LocalDateTime inicio,
                     @Param("fin") LocalDateTime fin);

       /**
        * Ingresos REALES del socio: excluye desembolsos de préstamo, que son
        * deuda y no ingreso propio. Se usa para no inflar el score/las
        * recomendaciones con dinero prestado.
        */
       @Query("SELECT COALESCE(SUM(t.amount),0) FROM Transaction t " +
                     "WHERE (t.originAccount.id IN (SELECT a.id FROM Account a WHERE a.ownerUser.id = :uid) OR " +
                     " t.destinationAccount.id IN (SELECT a.id FROM Account a WHERE a.ownerUser.id = :uid)) " +
                     "AND t.type IN ('deposit','transfer_received') " +
                     "AND t.createdAt BETWEEN :inicio AND :fin")
       BigDecimal sumIngresosRealesByUsuarioYRango(
                     @Param("uid") Long userId,
                     @Param("inicio") LocalDateTime inicio,
                     @Param("fin") LocalDateTime fin);

       /** Desembolsos de préstamo recibidos por el socio en el rango (subconjunto de ingresos, no ingreso real). */
       @Query("SELECT COALESCE(SUM(t.amount),0) FROM Transaction t " +
                     "WHERE (t.originAccount.id IN (SELECT a.id FROM Account a WHERE a.ownerUser.id = :uid) OR " +
                     " t.destinationAccount.id IN (SELECT a.id FROM Account a WHERE a.ownerUser.id = :uid)) " +
                     "AND t.type = 'loan_disbursement' " +
                     "AND t.createdAt BETWEEN :inicio AND :fin")
       BigDecimal sumDesembolsosByUsuarioYRango(
                     @Param("uid") Long userId,
                     @Param("inicio") LocalDateTime inicio,
                     @Param("fin") LocalDateTime fin);

       /** Egresos del socio en un rango de fechas. */
       @Query("SELECT COALESCE(SUM(t.amount),0) FROM Transaction t " +
                     "WHERE (t.originAccount.id IN (SELECT a.id FROM Account a WHERE a.ownerUser.id = :uid) OR " +
                     " t.destinationAccount.id IN (SELECT a.id FROM Account a WHERE a.ownerUser.id = :uid)) " +
                     "AND t.type IN ('withdrawal','loan_payment','fee','service_payment','fondo_comun','transfer') " +
                     "AND t.createdAt BETWEEN :inicio AND :fin")
       BigDecimal sumEgresosByUsuarioYRango(
                     @Param("uid") Long userId,
                     @Param("inicio") LocalDateTime inicio,
                     @Param("fin") LocalDateTime fin);

       /**
        * Total que un socio ya movió en transacciones de un tipo dado, en una
        * ventana de tiempo — base para validar límites diarios/semanales
        * (TransactionLimitService). Solo cuenta lo que SALIÓ de sus cuentas
        * (originAccount), no lo que recibió.
        */
       @Query("SELECT COALESCE(SUM(t.amount),0) FROM Transaction t " +
                     "WHERE t.originAccount.ownerUser.id = :uid AND t.type = :tipo " +
                     "AND t.status = 'completed' AND t.createdAt BETWEEN :inicio AND :fin")
       BigDecimal sumSalienteByUsuarioYTipo(
                     @Param("uid") Long userId,
                     @Param("tipo") Transaction.TxType tipo,
                     @Param("inicio") LocalDateTime inicio,
                     @Param("fin") LocalDateTime fin);

       /**
        * Total pagado en cuotas de préstamo completadas — base para calcular puntos
        * ganados.
        */
       @Query("SELECT COALESCE(SUM(t.amount),0) FROM Transaction t " +
                     "WHERE t.type = 'loan_payment' AND t.status = 'completed' " +
                     "AND t.originAccount.id IN (SELECT a.id FROM Account a WHERE a.ownerUser.id = :uid)")
       BigDecimal sumPagosPrestamoCompletados(@Param("uid") Long userId);

       /**
        * Aportes al fondo común de un grupo — GroupService.aportarFondo() guarda
        * cada aporte con reference="FC-{groupId}-{timestamp}", así que se pueden
        * encontrar todos los de un grupo por el prefijo.
        */
       @Query("SELECT t FROM Transaction t WHERE t.reference LIKE :prefijo AND t.type = 'fondo_comun' " +
                     "ORDER BY t.createdAt ASC")
       List<Transaction> findAportesFondoPorGrupo(@Param("prefijo") String prefijo);
}
