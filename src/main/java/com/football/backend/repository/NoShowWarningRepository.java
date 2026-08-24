package com.football.backend.repository;

import com.football.backend.entity.NoShowWarningEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface NoShowWarningRepository extends JpaRepository<NoShowWarningEntity, Long> {
    boolean existsByMatchIdAndUserId(Long matchId, Long userId);
    long countByUserIdAndIssuedAtGreaterThanEqual(Long userId, LocalDateTime since);
}
