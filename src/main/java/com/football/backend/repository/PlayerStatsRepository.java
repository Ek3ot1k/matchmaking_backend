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
    List<PlayerStatsEntity> findByMatchId(Long matchId);

    // Spring Data JPA сам преобразует Object[] в нужные типы
    // [0] - count(matches), [1] - sum(goals), [2] - sum(assists), [3] - sum(mvp), [4] - avg(rating)
    @Query("SELECT COUNT(s), SUM(s.goals), SUM(s.assists), SUM(s.mvpVotes), AVG(s.matchRating) " +
            "FROM PlayerStatsEntity s WHERE s.user.id = :userId")
    Object[] getAggregatedStatsByUserId(@Param("userId") Long userId);
}