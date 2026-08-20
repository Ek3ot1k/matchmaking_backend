package com.football.backend.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import java.time.LocalDateTime;

public record UpdateMatchRequest(
        String location,

        @Future(message = "Дата должна быть в будущем")
        LocalDateTime dateTime,

        @Min(4) @Max(22)
        Integer maxPlayers
) {}