package com.bankomunal.repository;

import com.bankomunal.entity.PointsTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PointsTransactionRepository extends JpaRepository<PointsTransaction, Long> {

    List<PointsTransaction> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Query("SELECT COALESCE(SUM(p.puntos),0) FROM PointsTransaction p WHERE p.user.id = :uid")
    Integer sumPuntosByUserId(@Param("uid") Long userId);

    @Query("SELECT COALESCE(SUM(p.puntos),0) FROM PointsTransaction p " +
            "WHERE p.user.id = :uid AND p.tipo = 'CANJEADO'")
    Integer sumCanjeadosByUserId(@Param("uid") Long userId);

    @Query("SELECT COALESCE(SUM(p.puntos),0) FROM PointsTransaction p " +
            "WHERE p.user.id = :uid AND p.tipo = 'GANADO'")
    Integer sumGanadosByUserId(@Param("uid") Long userId);
}
