package com.football.backend.dto;

import com.football.backend.model.Role;

import java.time.LocalDateTime;
import java.util.List;

public record UserProfileDTO(
        Long userId,
        String username,
        String firstName,
        String lastName,
        String avatarUrl,
        String position,
        Role role,
        Boolean isVip,
        LocalDateTime vipUntil,

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
        Double averageRating,
        List<PlayerMatchHistoryDTO> recentMatches
) {}
