package com.football.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendMatchChatMessageRequest(
        @NotBlank(message = "Сообщение не может быть пустым")
        @Size(max = 500, message = "Сообщение не может быть длиннее 500 символов")
        String text
) {}
