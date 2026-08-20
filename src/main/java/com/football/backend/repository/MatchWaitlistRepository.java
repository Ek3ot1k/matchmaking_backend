package com.football.backend.repository;

import com.football.backend.entity.MatchWaitlistEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MatchWaitlistRepository extends JpaRepository<MatchWaitlistEntity,Long> {
    boolean existsByMatchIdAndUserId(Long matchId, Long userId);
    Optional<MatchWaitlistEntity> findFirstByMatchIdOrderByJoinedAtAsc(Long matchId);
}
