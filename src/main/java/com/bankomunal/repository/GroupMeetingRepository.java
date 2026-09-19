package com.bankomunal.repository;

import com.bankomunal.entity.GroupMeeting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface GroupMeetingRepository extends JpaRepository<GroupMeeting, Long> {

    List<GroupMeeting> findAllByOrderByFechaAsc();
}
