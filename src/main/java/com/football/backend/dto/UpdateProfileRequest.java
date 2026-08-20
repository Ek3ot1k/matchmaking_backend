package com.football.backend.dto;

import com.football.backend.model.Position;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(min = 2, max = 50, message = "Имя должно быть от 2 до 50 символов")
        String username,
        Position position) {
}