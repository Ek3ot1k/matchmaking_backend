package com.football.backend.dto;

import com.football.backend.model.MatchStatus;

import java.time.LocalDateTime;

public record MatchSummaryResponse(
        Long id,
        LocalDateTime dateTime,
        String location,
        MatchStatus status,
        Integer maxPlayers
        // Позже сюда добавим поле currentPlayers (сколько уже записалось)
) {}
