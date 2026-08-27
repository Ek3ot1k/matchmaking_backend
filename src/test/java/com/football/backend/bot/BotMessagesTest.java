package com.football.backend.bot;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BotMessagesTest {

    @Test
    void startMessageExplainsTheMainGameFlowAndFitsTelegramLimit() {
        String message = BotMessages.start("amin");

        assertThat(message)
                .contains("МАТЧИ И ЗАПИСЬ", "КОМАНДЫ И ИГРА", "РЕЗУЛЬТАТ И ГОЛОСОВАНИЯ")
                .contains("70%", "15 минут", "FUT-КАРТОЧКА", "Накрутка статистики")
                .hasSizeLessThanOrEqualTo(4096);
    }
}
