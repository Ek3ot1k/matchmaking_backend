package com.football.backend.repository;

import com.football.backend.entity.MatchResultVoteEntity;
import com.football.backend.model.ResultVoteDecision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MatchResultVoteRepository extends JpaRepository<MatchResultVoteEntity, Long> {
    Optional<MatchResultVoteEntity> findByMatchIdAndUserId(Long matchId, Long userId);
    List<MatchResultVoteEntity> findByMatchIdOrderByUpdatedAtAsc(Long matchId);
    long countByMatchId(Long matchId);
    long countByMatchIdAndDecision(Long matchId, ResultVoteDecision decision);
    void deleteByMatchId(Long matchId);
}
