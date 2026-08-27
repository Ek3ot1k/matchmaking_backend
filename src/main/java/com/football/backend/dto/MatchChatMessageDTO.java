package com.football.backend.dto;

import java.time.LocalDateTime;

public record MatchChatMessageDTO(
        Long id,
        Long userId,
        String displayName,
        String avatarUrl,
        boolean vip,
        String text,
        LocalDateTime createdAt
) {}
