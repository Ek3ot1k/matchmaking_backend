package com.football.backend.repository;

import com.football.backend.entity.TransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TransactionRepository extends JpaRepository<TransactionEntity, Long> {
    boolean existsByTelegramChargeId(String telegramChargeId);
}