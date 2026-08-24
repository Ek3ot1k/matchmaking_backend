package com.football.backend.config;

import com.football.backend.bot.FootballWebhookBot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.updates.GetWebhookInfo;
import org.telegram.telegrambots.meta.api.objects.WebhookInfo;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

/**
 * Telegram хранит только один способ получения обновлений: webhook или long polling.
 * Поэтому production сам проверяет и восстанавливает webhook после каждого запуска.
 */
@Component
@Profile("prod")
public class TelegramWebhookRegistrar {

    private static final Logger log = LoggerFactory.getLogger(TelegramWebhookRegistrar.class);

    private final FootballWebhookBot bot;

    public TelegramWebhookRegistrar(FootballWebhookBot bot) {
        this.bot = bot;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void registerOnStartup() {
        ensureWebhook();
    }

    @Scheduled(
            initialDelayString = "${telegram.webhook.check-initial-delay-ms:30000}",
            fixedDelayString = "${telegram.webhook.check-interval-ms:60000}"
    )
    public void ensureWebhook() {
        String expectedUrl = bot.getSetWebhook().getUrl();

        try {
            WebhookInfo current = bot.execute(new GetWebhookInfo());
            if (expectedUrl.equals(current.getUrl())) {
                return;
            }

            bot.setWebhook(bot.getSetWebhook());
            log.info("Telegram webhook установлен: {}", expectedUrl);
        } catch (TelegramApiException exception) {
            log.error("Не удалось проверить или установить Telegram webhook", exception);
        }
    }
}
