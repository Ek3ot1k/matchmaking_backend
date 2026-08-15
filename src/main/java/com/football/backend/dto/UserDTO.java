package com.football.backend.dto;

public record UserDTO(Long id,
                      Long telegramId,
                      String username,
                      String role,
                      String position,
                      Integer ovr,
                      Integer pace,
                      Integer shoot,
                      Integer pass,
                      Integer dribbling,
                      Integer defend,
                      Integer physic,
                      boolean isVip) {
}
