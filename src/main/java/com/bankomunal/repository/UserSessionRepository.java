package com.bankomunal.repository;

import com.bankomunal.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    List<UserSession> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<UserSession> findByTokenHash(String tokenHash);
}
