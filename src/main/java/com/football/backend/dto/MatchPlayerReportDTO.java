package com.football.backend.dto;

import com.football.backend.model.Position;
import com.football.backend.model.TeamColor;

/** Снимок игрока и его статистики для отчёта по конкретному матчу. */
public record MatchPlayerReportDTO(
        Long userId,
        UserDTO user,
        TeamColor teamColor,
        Position position,
        Integer goals,
        Integer assists,
        Integer mvpVotes,
        Double matchRating
) {}
