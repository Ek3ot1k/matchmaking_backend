package com.football.backend.dto;

import com.football.backend.model.ResultVoteDecision;

import java.time.LocalDateTime;

public record AdminResultVoteDTO(
        Long userId,
        String playerName,
        ResultVoteDecision decision,
        String reason,
        LocalDateTime votedAt
) {}
