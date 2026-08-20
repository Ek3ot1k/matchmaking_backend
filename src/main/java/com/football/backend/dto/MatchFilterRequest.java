package com.football.backend.dto;

import com.football.backend.model.MatchStatus; // Предполагаю, что у тебя есть такой Enum

public record MatchFilterRequest(
        MatchStatus status,
        String format,
        String location
) {}