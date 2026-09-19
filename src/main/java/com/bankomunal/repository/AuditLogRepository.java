package com.bankomunal.repository;

import com.bankomunal.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("SELECT a FROM AuditLog a ORDER BY a.createdAt DESC")
    List<AuditLog> findAllOrderByCreatedAtDesc();

    @Query("SELECT a FROM AuditLog a WHERE a.createdAt BETWEEN :inicio AND :fin ORDER BY a.createdAt DESC")
    List<AuditLog> findByDateRange(
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin);

    /**
     * Filtro por tipo de evento (para /api/admin/auditoria?tipo=LOGIN_SUCCESS)
     */
    @Query("SELECT a FROM AuditLog a WHERE a.eventType = :tipo ORDER BY a.createdAt DESC")
    List<AuditLog> findByEventType(@Param("tipo") String tipo);

    /**
     * Filtro combinado: rango de fecha + tipo de evento
     */
    @Query("SELECT a FROM AuditLog a WHERE a.createdAt BETWEEN :inicio AND :fin AND a.eventType = :tipo ORDER BY a.createdAt DESC")
    List<AuditLog> findByDateRangeAndTipo(
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin,
            @Param("tipo") String tipo);
}
