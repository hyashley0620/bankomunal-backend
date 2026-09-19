package com.bankomunal.repository;

import com.bankomunal.entity.GroupChatRead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface GroupChatReadRepository extends JpaRepository<GroupChatRead, Long> {
    Optional<GroupChatRead> findByGroupIdAndUserId(Long groupId, Long userId);

    List<GroupChatRead> findByUserId(Long userId);
}
