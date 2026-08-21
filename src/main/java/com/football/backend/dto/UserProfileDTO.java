package com.football.backend.dto;

public record UserProfileDTO(
        Long userId,
        String firstName,
        String lastName,
        String position,

        // Скиллы
        Integer ovr,
        Integer pac,
        Integer sho,
        Integer pas,
        Integer dri,
        Integer def,
        Integer phy,

        // Статистика за всю историю
        Integer totalMatches,
        Integer totalGoals,
        Integer totalAssists,
        Integer totalMvp,
        Double averageRating
) {}