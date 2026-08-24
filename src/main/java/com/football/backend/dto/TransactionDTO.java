package com.football.backend.dto;

import com.football.backend.model.TransactionStatus;
import java.time.LocalDateTime;

public record TransactionDTO(
        Long id,
        Long userId,
        String firstName,
        String telegramUsername,
        String telegramChargeId,
        Integer amount,
        String currency,
        TransactionStatus status,
        LocalDateTime createdAt
) {}