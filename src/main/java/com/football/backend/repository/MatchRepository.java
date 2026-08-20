package com.football.backend.repository;

import com.football.backend.entity.MatchEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

public interface MatchRepository extends JpaRepository<MatchEntity,Long>,
        JpaSpecificationExecutor<MatchEntity> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MatchEntity m where m.id = :id")
    Optional<MatchEntity> findByIdForUpdate(@Param("id") Long id);
}
