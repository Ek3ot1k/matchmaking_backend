package com.football.backend.repository;

import com.football.backend.entity.MatchParticipantEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MatchParticipantRepository extends JpaRepository<MatchParticipantEntity,Long> {
    List<MatchParticipantEntity> findAllByMatchId(Long matchId);
    boolean existsByMatchIdAndUserId(Long matchId,Long userId);
}
