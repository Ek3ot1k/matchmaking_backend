package com.football.backend.repository;

import com.football.backend.entity.PlayerStatsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PlayerStatsRepository extends JpaRepository<PlayerStatsEntity, Long> {

    // Достаем статистику юзера вместе с данными матча, сортируем от новых к старым
    @Query("SELECT p FROM PlayerStatsEntity p JOIN FETCH p.match m WHERE p.user.id = :userId ORDER BY m.dateTime DESC")
    List<PlayerStatsEntity> findRecentMatchesByUserId(@Param("userId") Long userId);
}