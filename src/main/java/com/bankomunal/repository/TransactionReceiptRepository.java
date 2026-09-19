package com.bankomunal.repository;

import com.bankomunal.entity.TransactionReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface TransactionReceiptRepository extends JpaRepository<TransactionReceipt, Long> {

    Optional<TransactionReceipt> findByReferencia(String referencia);
}
