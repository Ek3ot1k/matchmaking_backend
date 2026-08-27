package com.football.backend.repository;

import com.football.backend.entity.MatchChatMessageEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MatchChatMessageRepository extends JpaRepository<MatchChatMessageEntity, Long> {
    List<MatchChatMessageEntity> findByMatchIdOrderByCreatedAtDesc(Long matchId, Pageable pageable);
}
