package com.football.backend.repository;

import com.football.backend.entity.VoteEntity;
import com.football.backend.model.VoteCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VoteRepository extends JpaRepository<VoteEntity, Long> {
    // Проверка, голосовал ли уже этот юзер в этой номинации в этом матче
    boolean existsByMatchIdAndVoterIdAndCategory(Long matchId, Long voterId, VoteCategory category);
    List<VoteEntity> findByMatchId(Long matchId);
}