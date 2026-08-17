package com.football.backend.dto;

import java.time.LocalDateTime;

public record CreateMatchRequest(
        LocalDateTime dateTime,
        String location,
        Integer maxPlayers,
        Integer minPlayers,
        Integer duration,
        String description
) {}
