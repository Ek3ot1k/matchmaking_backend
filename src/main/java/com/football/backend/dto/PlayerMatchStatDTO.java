package com.football.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PlayerMatchStatDTO(
        @NotNull(message = "ID пользователя обязательно")
        Long userId,

        @Min(0) int goals,
        @Min(0) int assists,

        @Min(0) int mvpVotes,
        @Min(0) int fastestPlayerVotes
) {}