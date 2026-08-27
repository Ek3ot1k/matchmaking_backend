package com.football.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateTeamFormationRequest(
        @NotBlank(message = "Выберите схему")
        @Size(max = 20, message = "Некорректная схема")
        String formation
) {}
