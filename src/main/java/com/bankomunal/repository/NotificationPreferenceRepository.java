package com.bankomunal.repository;

import com.bankomunal.entity.NotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {

    /** Buscar preferencias por ID de usuario */
    Optional<NotificationPreference> findByUserId(Long userId);
}
