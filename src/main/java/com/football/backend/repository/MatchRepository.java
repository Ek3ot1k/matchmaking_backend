package com.football.backend.repository;

import com.football.backend.entity.MatchEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

public interface MatchRepository extends JpaRepository<MatchEntity,Long>,
        JpaSpecificationExecutor<MatchEntity> {
}
