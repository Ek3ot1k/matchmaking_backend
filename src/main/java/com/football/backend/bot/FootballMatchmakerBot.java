package com.football.backend.bot;

import com.football.backend.service.PaymentService;
import com.football.backend.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.webapp.WebAppInfo;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.api.methods.AnswerPreCheckoutQuery;
import org.telegram.telegrambots.meta.api.objects.payments.PreCheckoutQuery;
import org.telegram.telegrambots.meta.api.objects.payments.SuccessfulPayment;

import java.util.ArrayList;
import java.util.List;

@Component
@Profile("local")
@ConditionalOnProperty(name = "telegram.long-polling.enabled", havingValue = "true")
public class FootballMatchmakerBot extends TelegramLongPollingBot {

    private final String botUsername;
    private final String miniAppUrl;
    private final UserService userService;
    private final PaymentService paymentService;

    public FootballMatchmakerBot(
            @Value("${telegram.bot.token}") String botToken,
            @Value("${telegram.bot.username}") String botUsername,
            @Value("${telegram.miniapp.url}") String miniAppUrl,
            UserService userService,
            PaymentService paymentService) {
        super(botToken);
        this.botUsername = botUsername;
        this.miniAppUrl = miniAppUrl;
        this.userService = userService;
        this.paymentService=paymentService;
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasPreCheckoutQuery()) {
            PreCheckoutQuery preCheckoutQuery = update.getPreCheckoutQuery();
            AnswerPreCheckoutQuery answer = new AnswerPreCheckoutQuery();
            answer.setPreCheckoutQueryId(preCheckoutQuery.getId());
            answer.setOk(true);

            try {
                execute(answer);
            } catch (TelegramApiException e) {
                System.err.println("Ошибка PreCheckoutQuery: " + e.getMessage());
            }
            return;
        }

        // Перехват успешной оплаты
        if (update.hasMessage() && update.getMessage().hasSuccessfulPayment()) {
            SuccessfulPayment payment = update.getMessage().getSuccessfulPayment();
            Long telegramUserId = update.getMessage().getFrom().getId();

            try {
                paymentService.processSuccessfulPayment(
                        telegramUserId,
                        payment.getTelegramPaymentChargeId(),
                        payment.getTotalAmount(),
                        payment.getCurrency(),
                        payment.getInvoicePayload()
                );

                SendMessage successMessage = new SendMessage();
                successMessage.setChatId(update.getMessage().getChatId().toString());
                successMessage.setText("🎉 Оплата прошла успешно! Ваш VIP-статус активирован на 30 дней.");
                execute(successMessage);
            } catch (Exception e) {
                System.err.println("Ошибка при зачислении VIP: " + e.getMessage());
            }
            return;
        }

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
        message.setText(BotMessages.start(firstName));

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
