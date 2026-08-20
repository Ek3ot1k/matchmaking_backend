package com.football.backend.dto;

import java.time.LocalDateTime;

public record MatchRescheduledEvent(
        Long matchId,
        LocalDateTime oldTime,
        LocalDateTime newTime,
        String oldLocation,
        String newLocation
) {
}
