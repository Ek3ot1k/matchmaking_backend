package com.football.backend.repository;

import com.football.backend.entity.MatchParticipantEntity;
import com.football.backend.model.ParticipantStatus;
import com.football.backend.model.TeamColor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

@Repository
public interface MatchParticipantRepository extends JpaRepository<MatchParticipantEntity,Long> {
    List<MatchParticipantEntity> findAllByMatchId(Long matchId);
    boolean existsByMatchIdAndUserId(Long matchId,Long userId);
    Optional<MatchParticipantEntity> findByMatchIdAndUserId(Long matchId, Long userId);
    List<MatchParticipantEntity> findByMatchId(Long matchId);
    List<MatchParticipantEntity> findByMatchIdAndStatusNot(Long matchId, ParticipantStatus status);
    boolean existsByMatchIdAndTeamColorNot(Long matchId, TeamColor color);

    @Query("select p from MatchParticipantEntity p join fetch p.match m join fetch p.user where p.user.id = :userId " +
            "and m.status = 'COMPLETED' and p.status <> 'NO_SHOW' order by m.dateTime desc")
    List<MatchParticipantEntity> findCompletedByUserIdOrderByMatchDateDesc(@Param("userId") Long userId);
}
