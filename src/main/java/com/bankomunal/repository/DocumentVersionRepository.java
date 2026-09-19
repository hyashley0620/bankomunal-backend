package com.bankomunal.repository;

import com.bankomunal.entity.DocumentVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, Long> {
    List<DocumentVersion> findByDocumentoIdOrderByVersionDesc(Long documentoId);

    Optional<DocumentVersion> findTopByDocumentoIdOrderByVersionDesc(Long documentoId);
}
