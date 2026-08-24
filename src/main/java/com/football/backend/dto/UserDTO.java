package com.football.backend.dto;

import com.football.backend.model.Position;
import com.football.backend.model.Role;

import java.time.LocalDateTime;

public record UserDTO(
        Long id,
        Long telegramId,
        String username,
        String firstName,
        String lastName,
        Position position,
        Role role,
        boolean isVip,
        LocalDateTime vipUntil,
        Integer ovr,
        Integer pace,
        Integer shoot,
        Integer pass,
        Integer dribbling,
        Integer defend,
        Integer physic
) {}