package com.football.backend.service;

import com.football.backend.bot.FootballMatchmakerBot;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.bots.DefaultAbsSender;

import java.util.List;

@Service
public class NotificationService {

    private final DefaultAbsSender telegramBot;

    public NotificationService(@Lazy FootballMatchmakerBot telegramBot) {
        this.telegramBot = telegramBot;
    }

    /**
     * @Async делает так, что этот метод работает в отдельном фоновом потоке
     * и не тормозит остальное приложение.
     */
    @Async
    public void sendToUser(Long telegramId, String text) {
        if (telegramId == null) return;

        SendMessage message = new SendMessage();
        message.setChatId(telegramId.toString());
        message.setText(text);

        // Ручной, но 100% надежный механизм Retry
        int maxRetries = 3;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                telegramBot.execute(message);
                return; // Успешно - выходим из метода
            } catch (TelegramApiException e) {
                if (attempt == maxRetries) {
                    System.err.println("❌ Ошибка отправки пуша юзеру " + telegramId + " после 3 попыток: " + e.getMessage());
                } else {
                    try {
                        Thread.sleep(2000L * attempt); // Ждем 2 сек, потом 4 сек перед повтором
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
    }

    @Async
    public void sendToUsers(List<Long> telegramIds, String text) {
        for (Long telegramId : telegramIds) {
            sendToUser(telegramId, text);
        }
    }
}