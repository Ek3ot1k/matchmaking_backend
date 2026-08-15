package com.football.backend.dto;

import com.football.backend.model.TransactionStatus;

import java.sql.Timestamp;

public record TransactionDTO(Long id,
                             Long userId,
                             String chargeId,
                             Integer amount,
                             String currency,
                             TransactionStatus status,
                             Timestamp createdAt) {
}
