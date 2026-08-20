package com.football.backend.dto;

import jakarta.validation.constraints.*;

import java.time.LocalDateTime;

public record CreateMatchRequest(
        @NotBlank(message = "Формат матча обязателен (например, 5х5)")
        String format,

        @NotBlank(message = "Локация не может быть пустой")
        String location,

        @NotNull(message = "Укажите дату и время матча")
        @Future(message = "Нельзя создать матч в прошлом")
        LocalDateTime dateTime,

        @NotNull(message = "Укажите максимальное количество игроков")
        @Min(value = 4, message = "Минимум 4 игрока (2х2)")
        @Max(value = 22, message = "Максимум 22 игрока (11х11)")
        Integer maxPlayers
) {}
