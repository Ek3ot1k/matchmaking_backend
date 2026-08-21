package com.football.backend.bot;

import com.football.backend.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.webapp.WebAppInfo;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.List;

@Component
public class FootballMatchmakerBot extends TelegramLongPollingBot {

    private final String botUsername;
    private final String miniAppUrl;
    private final UserService userService;

    public FootballMatchmakerBot(
            @Value("${telegram.bot.token}") String botToken,
            @Value("${telegram.bot.username}") String botUsername,
            @Value("${telegram.miniapp.url}") String miniAppUrl,
            UserService userService) {
        super(botToken);
        this.botUsername = botUsername;
        this.miniAppUrl = miniAppUrl;
        this.userService = userService;
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();

            Long chatId = update.getMessage().getChatId();
            Long telegramUserId = update.getMessage().getFrom().getId();
            String firstName = update.getMessage().getFrom().getFirstName();

            if (messageText.equals("/start")) {
                handleStartCommand(chatId, telegramUserId, firstName);
            }
        }
    }

    private void handleStartCommand(Long chatId, Long telegramUserId, String firstName) {
        userService.registerOrUpdateTelegramUser(telegramUserId, firstName);

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("Привет, " + firstName + "! Добро пожаловать в сервис футбольных матчей. Нажми на кнопку ниже, чтобы открыть приложение 👇");

        InlineKeyboardButton webAppButton = new InlineKeyboardButton();
        webAppButton.setText("⚽ Открыть Mini App");
        webAppButton.setWebApp(new WebAppInfo(miniAppUrl));

        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(webAppButton);

        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        keyboard.add(row);

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(keyboard);
        message.setReplyMarkup(markup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            System.err.println("Ошибка при отправке сообщения: " + e.getMessage());
        }
    }
}