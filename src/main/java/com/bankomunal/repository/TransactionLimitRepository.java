package com.bankomunal.repository;

import com.bankomunal.entity.TransactionLimit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TransactionLimitRepository extends JpaRepository<TransactionLimit, Long> {

    /** Todos los límites configurados para un socio o un grupo específico. */
    List<TransactionLimit> findByScopeAndScopeId(TransactionLimit.Scope scope, Long scopeId);

    /** Límites globales (scope_id siempre NULL para scope=global). */
    List<TransactionLimit> findByScopeAndScopeIdIsNull(TransactionLimit.Scope scope);
}
