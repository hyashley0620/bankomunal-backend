package com.bankomunal.repository;

import com.bankomunal.entity.BackupRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface BackupRecordRepository extends JpaRepository<BackupRecord, Long> {

    List<BackupRecord> findAllByOrderByCreatedAtDesc();

    Optional<BackupRecord> findFirstByEstadoOrderByCreatedAtDesc(BackupRecord.Estado estado);

}
