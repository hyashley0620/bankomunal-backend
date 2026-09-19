package com.bankomunal.repository;

import com.bankomunal.entity.Poll;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PollRepository extends JpaRepository<Poll, Long> {
    List<Poll> findByGroupIdOrderByCreatedAtDesc(Long groupId);

    List<Poll> findByStatus(Poll.PollStatus status);

    List<Poll> findAllByOrderByCreatedAtDesc();
}
