package com.football.backend.dto;

import java.time.LocalDateTime;

public record MatchWaitlistDetailsDTO(
        Long id,
        Long userId,
        Long matchId,
        LocalDateTime joinedAt,
        UserDTO user
) {}
