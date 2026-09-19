package com.bankomunal.repository;

import com.bankomunal.entity.CreditRiskHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface CreditRiskHistoryRepository extends JpaRepository<CreditRiskHistory, Long> {
    List<CreditRiskHistory> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<CreditRiskHistory> findFirstByUserIdOrderByCreatedAtDesc(Long userId);
}
