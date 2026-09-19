package com.bankomunal.repository;

import com.bankomunal.entity.FrequentContact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface FrequentContactRepository extends JpaRepository<FrequentContact, Long> {
    List<FrequentContact> findByOwnerUserIdOrderByCreatedAtDesc(Long ownerUserId);

    Optional<FrequentContact> findByOwnerUserIdAndEmail(Long ownerUserId, String email);

    void deleteByIdAndOwnerUserId(Long id, Long ownerUserId);
}
