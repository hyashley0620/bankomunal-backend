package com.bankomunal.repository;

import com.bankomunal.entity.CommunityPostReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CommunityPostReportRepository extends JpaRepository<CommunityPostReport, Long> {
    boolean existsByPostIdAndReportedById(Long postId, Long reportedById);

    long countByPostId(Long postId);

    List<CommunityPostReport> findAllByOrderByCreatedAtDesc();

    void deleteByPostId(Long postId);
}
