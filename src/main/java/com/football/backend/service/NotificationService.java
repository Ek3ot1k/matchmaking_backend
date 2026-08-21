package com.football.backend.service;

import com.football.backend.bot.FootballMatchmakerBot;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;

@Service
public class NotificationService {
    // Используем @Lazy, чтобы избежать циклических зависимостей при старте Spring,
    // так как бот тоже инициализируется как бин.
    private final FootballMatchmakerBot telegramBot;

    public NotificationService(@Lazy FootballMatchmakerBot telegramBot) {
        this.telegramBot = telegramBot;
    }

    public void sendToUser(Long telegramId, String text){
        if(telegramId==null) return;

        SendMessage message=new SendMessage();
        message.setChatId(telegramId.toString());
        message.setText(text);

        try{
            telegramBot.execute(message);
        }catch (TelegramApiException e){
            System.err.println("Не удалось отправить уведомление пользователю " + telegramId + ": " + e.getMessage());
        }
    }

    public void sendToUsers(List<Long> telegramIds, String text){
        for(Long telegramId:telegramIds){
            sendToUser(telegramId,text);
        }
    }
}
