package com.football.backend.config;

import com.football.backend.bot.FootballMatchmakerBot;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Configuration
public class TelegramBotConfig {

    @Bean
    public TelegramBotsApi telegramBotsApi(FootballMatchmakerBot bot) throws TelegramApiException {
        // Создаем API для работы с ботами
        TelegramBotsApi api = new TelegramBotsApi(DefaultBotSession.class);

        // Регистрируем нашего бота, чтобы он начал long-polling (прослушивание)
        api.registerBot(bot);

        return api;
    }
}