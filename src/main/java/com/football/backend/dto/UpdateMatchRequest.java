package com.football.backend.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import java.time.LocalDateTime;

public record UpdateMatchRequest(
        String location,

        @Future(message = "Дата должна быть в будущем")
        LocalDateTime dateTime,

        @Min(10) @Max(14)
        Integer maxPlayers
) {}
