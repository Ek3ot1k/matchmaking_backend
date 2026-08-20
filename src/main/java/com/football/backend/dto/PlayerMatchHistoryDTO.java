package com.football.backend.dto;

import java.time.LocalDateTime;

public record PlayerMatchHistoryDTO(
        Long matchId,
        LocalDateTime date,
        String location,
        Integer goals,
        Integer assists,
        Integer mvpVotes
) {}