package com.bankomunal.repository;

import com.bankomunal.entity.CourseProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface CourseProgressRepository extends JpaRepository<CourseProgress, Long> {
    List<CourseProgress> findByUserId(Long userId);

    List<CourseProgress> findByUserIdAndCertificadoTrue(Long userId);

    Optional<CourseProgress> findByUserIdAndCourseId(Long userId, String courseId);
}
