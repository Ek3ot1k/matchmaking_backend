package com.football.backend.dto;

public record TelegramAuthRequest(
        Long telegramId,
        String username,
        String initData // В будущем пригодится для валидации подписи Telegram
) {
}
