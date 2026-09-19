package com.bankomunal.repository;

import com.bankomunal.entity.PollVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PollVoteRepository extends JpaRepository<PollVote, Long> {
    boolean existsByPollIdAndUserId(Long pollId, Long userId);

    long countByOptionId(Long optionId);

    List<PollVote> findByPollId(Long pollId);

    /**
     * Retorna todos los votos emitidos por un usuario (para mostrar sus votos al
     * cargar)
     */
    List<PollVote> findByUserId(Long userId);

    /** Retorna el voto de un usuario en una encuesta específica, si existe */
    @Query("SELECT v FROM PollVote v WHERE v.poll.id = :pollId AND v.user.id = :userId")
    java.util.Optional<PollVote> findByPollIdAndUserId(@Param("pollId") Long pollId, @Param("userId") Long userId);
}
