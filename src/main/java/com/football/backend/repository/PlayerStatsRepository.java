package com.football.backend.repository;

import com.football.backend.dto.LeaderboardEntryDTO;
import com.football.backend.entity.PlayerStatsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Pageable;
import java.time.LocalDateTime;
import java.util.List;

public interface PlayerStatsRepository extends JpaRepository<PlayerStatsEntity, Long> {

    // Достаем статистику юзера вместе с данными матча, сортируем от новых к старым
    @Query("SELECT p FROM PlayerStatsEntity p JOIN FETCH p.match m " +
            "WHERE p.user.id = :userId AND m.status = 'COMPLETED' ORDER BY m.dateTime DESC")
    List<PlayerStatsEntity> findRecentMatchesByUserId(@Param("userId") Long userId);
    List<PlayerStatsEntity> findByMatchId(Long matchId);

    // Spring Data JPA сам преобразует Object[] в нужные типы
    // [0] - count(matches), [1] - sum(goals), [2] - sum(assists), [3] - sum(mvp), [4] - avg(rating)
    @Query("SELECT COUNT(s), SUM(s.goals), SUM(s.assists), SUM(s.mvpVotes), AVG(s.matchRating) " +
            "FROM PlayerStatsEntity s JOIN s.match m " +
            "WHERE s.user.id = :userId AND m.status = 'COMPLETED'")
    Object[] getAggregatedStatsByUserId(@Param("userId") Long userId);

    // Топ по голам
    @Query("SELECT new com.football.backend.dto.LeaderboardEntryDTO(" +
            "u.id, u.firstName, u.lastName, u.avatarUrl, cast(u.position as string), u.ovr, SUM(s.goals)) " +
            "FROM PlayerStatsEntity s JOIN s.user u JOIN s.match m " +
            "WHERE m.status = 'COMPLETED' " +
            "AND m.finishedAt >= :startDate AND m.finishedAt <= :endDate " +
            "GROUP BY u.id " +
            "ORDER BY SUM(s.goals) DESC")
    List<LeaderboardEntryDTO> getTopScorers(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable); // Pageable нужен, чтобы ограничить результат (например, ТОП-10)

    // Топ по ассистам
    @Query("SELECT new com.football.backend.dto.LeaderboardEntryDTO(" +
            "u.id, u.firstName, u.lastName, u.avatarUrl, cast(u.position as string), u.ovr, SUM(s.assists)) " +
            "FROM PlayerStatsEntity s JOIN s.user u JOIN s.match m " +
            "WHERE m.status = 'COMPLETED' " +
            "AND m.finishedAt >= :startDate AND m.finishedAt <= :endDate " +
            "GROUP BY u.id " +
            "ORDER BY SUM(s.assists) DESC")
    List<LeaderboardEntryDTO> getTopAssistant(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);

    // Топ по mvp
    @Query("SELECT new com.football.backend.dto.LeaderboardEntryDTO(" +
            "u.id, u.firstName, u.lastName, u.avatarUrl, cast(u.position as string), u.ovr, SUM(s.mvpVotes)) " +
            "FROM PlayerStatsEntity s JOIN s.user u JOIN s.match m " +
            "WHERE m.status = 'COMPLETED' " +
            "AND m.finishedAt >= :startDate AND m.finishedAt <= :endDate " +
            "GROUP BY u.id " +
            "ORDER BY SUM(s.mvpVotes) DESC")
    List<LeaderboardEntryDTO> getTopMVP(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);


    // Топ по Г+П
    @Query("SELECT new com.football.backend.dto.LeaderboardEntryDTO(" +
            "u.id, u.firstName, u.lastName, u.avatarUrl, cast(u.position as string), u.ovr, SUM(s.goals+s.assists)) " +
            "FROM PlayerStatsEntity s JOIN s.user u JOIN s.match m " +
            "WHERE m.status = 'COMPLETED' " +
            "AND m.finishedAt >= :startDate AND m.finishedAt <= :endDate " +
            "GROUP BY u.id " +
            "ORDER BY SUM(s.goals+s.assists) DESC")
    List<LeaderboardEntryDTO> getTopGA(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);

    // Топ по числу подтверждённых сыгранных матчей
    @Query("SELECT new com.football.backend.dto.LeaderboardEntryDTO(" +
            "u.id, u.firstName, u.lastName, u.avatarUrl, cast(u.position as string), u.ovr, COUNT(s)) " +
            "FROM PlayerStatsEntity s JOIN s.user u JOIN s.match m " +
            "WHERE m.status = 'COMPLETED' " +
            "AND m.finishedAt >= :startDate AND m.finishedAt <= :endDate " +
            "GROUP BY u.id " +
            "ORDER BY COUNT(s) DESC")
    List<LeaderboardEntryDTO> getTopByMatches(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);
}
