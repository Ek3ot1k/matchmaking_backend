package com.football.backend.controller;

import com.football.backend.bot.FootballWebhookBot;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.objects.Update;

@RestController
@Profile("prod")
public class WebhookController {

    private final FootballWebhookBot bot;

    public WebhookController(FootballWebhookBot bot) {
        this.bot = bot;
    }

    @PostMapping("/api/v1/telegram/webhook")
    public BotApiMethod<?> onUpdateReceived(@RequestBody Update update) {
        return bot.onWebhookUpdateReceived(update);
    }
}