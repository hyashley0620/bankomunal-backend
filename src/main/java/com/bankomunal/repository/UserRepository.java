package com.bankomunal.repository;

import com.bankomunal.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByIdentificationNumber(String identificationNumber);

    List<User> findByStatus(User.UserStatus status);

    @Query("SELECT COALESCE(SUM(a.balance), 0) FROM Account a WHERE a.ownerUser.id = :userId AND a.status = com.bankomunal.entity.Account.AccountStatus.active")
    Optional<BigDecimal> sumBalanceByUserId(@Param("userId") Long userId);
}
