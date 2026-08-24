package com.football.backend.dto;

import com.football.backend.model.Position;

import java.time.LocalDateTime;

public record MatchWaitlistDetailsDTO(
        Long id,
        Long userId,
        Long matchId,
        Position position,
        LocalDateTime joinedAt,
        UserDTO user
) {}
