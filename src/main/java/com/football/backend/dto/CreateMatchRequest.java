package com.football.backend.dto;

import jakarta.validation.constraints.*;

import java.time.LocalDateTime;

public record CreateMatchRequest(
        @NotBlank(message = "Формат матча обязателен: 5×5, 6×6 или 7×7")
        String format,

        @NotBlank(message = "Локация не может быть пустой")
        String location,

        @NotNull(message = "Укажите дату и время матча")
        @Future(message = "Нельзя создать матч в прошлом")
        LocalDateTime dateTime,

        @NotNull(message = "Укажите максимальное количество игроков")
        @Min(value = 10, message = "Минимум 10 игроков (5×5)")
        @Max(value = 14, message = "Максимум 14 игроков (7×7)")
        Integer maxPlayers
) {}
