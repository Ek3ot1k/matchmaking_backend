package com.football.backend.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import java.time.LocalDateTime;

public record UpdateMatchRequest(
        String location,

        @DecimalMin(value = "-90.0") @DecimalMax(value = "90.0")
        Double latitude,

        @DecimalMin(value = "-180.0") @DecimalMax(value = "180.0")
        Double longitude,

        @Future(message = "Дата должна быть в будущем")
        LocalDateTime dateTime,

        @Min(10) @Max(14)
        Integer maxPlayers,

        @Size(max = 500, message = "Ссылка на чат слишком длинная")
        String chatLink
) {}
