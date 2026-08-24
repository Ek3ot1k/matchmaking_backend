package com.football.backend.bot;

import com.football.backend.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updates.SetWebhook;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.webapp.WebAppInfo;
import org.telegram.telegrambots.starter.SpringWebhookBot;

import java.util.ArrayList;
import java.util.List;

@Component
@Profile("prod")
public class FootballWebhookBot extends SpringWebhookBot {

    private final String botUsername;
    private final String miniAppUrl;
    private final UserService userService;

    public FootballWebhookBot(
            @Value("${telegram.bot.token}") String botToken,
            @Value("${telegram.bot.username}") String botUsername,
            @Value("${telegram.webhook.url}") String webhookUrl,
            @Value("${telegram.miniapp.url}") String miniAppUrl,
            UserService userService) {
        super(SetWebhook.builder().url(webhookUrl).build(), botToken);
        this.botUsername = botUsername;
        this.miniAppUrl = miniAppUrl;
        this.userService = userService;
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public String getBotPath() {
        return "/api/v1/telegram/webhook";
    }

    @Override
    public BotApiMethod<?> onWebhookUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();

            if (messageText.startsWith("/start")) {
                Long telegramUserId = update.getMessage().getFrom().getId();
                String firstName = update.getMessage().getFrom().getFirstName();

                userService.registerOrUpdateTelegramUser(telegramUserId, firstName);

                SendMessage message = new SendMessage();
                message.setChatId(update.getMessage().getChatId().toString());
                message.setText(BotMessages.start(firstName));

                InlineKeyboardButton webAppButton = new InlineKeyboardButton();
                webAppButton.setText("⚽ Открыть Mini App");
                webAppButton.setWebApp(new WebAppInfo(miniAppUrl));

                List<InlineKeyboardButton> row = new ArrayList<>();
                row.add(webAppButton);
                List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
                keyboard.add(row);
                message.setReplyMarkup(new InlineKeyboardMarkup(keyboard));

                return message;
            }
        }
        return null;
    }
}
