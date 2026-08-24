package com.football.backend.dto;

public record LeaderboardEntryDTO(
        Long userId,
        String firstName,
        String lastName,
        String avatarUrl,
        String position,
        Integer ovr, // Чтобы выводить бейджик рейтинга рядом с именем
        Number value // Универсальное поле (тут может лежать кол-во голов, ассистов или средняя оценка)
) {}
