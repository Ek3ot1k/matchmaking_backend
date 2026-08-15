package com.football.backend.bot;

import com.football.backend.entity.UserEntity;
import com.football.backend.repository.UserRepository;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.api.objects.webapp.WebAppInfo;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.List;
import java.util.Optional;

@Component
public class UpdateConsumer implements LongPollingUpdateConsumer {
    private final TelegramClient telegramClient;
    private final UserRepository userRepository;

    public UpdateConsumer(@Value("${telegram.bot.token}") String token, UserRepository userRepository) {
        this.telegramClient = new OkHttpTelegramClient(token);
        this.userRepository = userRepository;
    }

    @SneakyThrows
    @Override
    public void consume(List<Update> updates) {
        for(Update update:updates){
            if (update.hasMessage() && update.getMessage().hasText()) {
                String text = update.getMessage().getText();
                Long chatId = update.getMessage().getChatId();

                // Обрабатываем команду /start
                if (text.equals("/start")) {
                    handleStartCommand(chatId, update.getMessage().getFrom().getFirstName());
                }
            }
        }
    }

    @SneakyThrows
    public void handleStartCommand(Long chatId,String firstName){
        Optional<UserEntity> existingUser=userRepository.findByTelegramId(chatId);

        if(existingUser.isEmpty()){
            UserEntity newUser=new UserEntity();
            newUser.setTelegramId(chatId);
            newUser.setUsername(firstName);
            newUser.setPosition("CM"); // Даем позицию по умолчанию, потом он сам поменяет в Mini App
            userRepository.save(newUser);
            System.out.println("Новый игрок добавлен в базу: " + firstName);
        }

        WebAppInfo webAppInfo=WebAppInfo.builder()
                .url("https://google.com")
                .build();

        InlineKeyboardButton webAppBtn=InlineKeyboardButton.builder()
                .text("⚽ Открыть профиль и матчмейкинг")
                .webApp(webAppInfo)
                .build();

        InlineKeyboardRow row=new InlineKeyboardRow(webAppBtn);
        InlineKeyboardMarkup keyboard=new InlineKeyboardMarkup(List.of(row));

        SendMessage message=SendMessage.builder()
                .chatId(chatId)
                .text("Привет, " + firstName + "! Готов выйти на поле? Жми кнопку ниже, чтобы открыть приложение.")
                .replyMarkup(keyboard)
                .build();

        telegramClient.execute(message);
    }
}
