package com.football.backend.dto;

public record TelegramUser(
        Long id,
        String username,
        String first_name,
        String last_name
) {}
