package com.football.backend.repository;

import com.football.backend.entity.MatchEntity;
import com.football.backend.model.MatchStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MatchRepository extends JpaRepository<MatchEntity,Long>,
        JpaSpecificationExecutor<MatchEntity> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MatchEntity m where m.id = :id")
    Optional<MatchEntity> findByIdForUpdate(@Param("id") Long id);
    List<MatchEntity> findByStatusAndVotingClosedFalseAndFinishedAtBefore(MatchStatus status, LocalDateTime cutoffTime);

    List<MatchEntity> findByStatus(MatchStatus status);

    List<MatchEntity> findByStatusAndResultVotingEndsAtLessThanEqual(
            MatchStatus status,
            LocalDateTime cutoffTime
    );

    List<MatchEntity> findByStatusInOrderByResultVotingEndsAtAsc(List<MatchStatus> statuses);

    @Query("select m from MatchEntity m " +
            "where m.id <> :excludedId and m.status in :statuses " +
            "and m.dateTime < :windowEnd and m.dateTime > :windowStart")
    List<MatchEntity> findVenueConflictCandidates(
            @Param("excludedId") Long excludedId,
            @Param("statuses") List<MatchStatus> statuses,
            @Param("windowStart") LocalDateTime windowStart,
            @Param("windowEnd") LocalDateTime windowEnd
    );
}
