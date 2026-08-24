package com.football.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PermanentBanRequest(
        @NotBlank(message = "Укажите причину блокировки")
        @Size(max = 1000, message = "Причина слишком длинная")
        String reason
) {}
