package com.football.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Небольшой клиент актуального Telegram Bot API для Stars.
 * Используем прямой запрос, потому что подключенная telegrambots 6.9 предшествует
 * Stars и валидирует createInvoiceLink по старым правилам провайдера.
 */
@Component
public class TelegramStarsClient {
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final URI createInvoiceUri;

    @Autowired
    public TelegramStarsClient(ObjectMapper objectMapper,
                               @Value("${telegram.bot.token}") String botToken) {
        this(objectMapper, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build(),
                URI.create("https://api.telegram.org/bot" + botToken + "/createInvoiceLink"));
    }

    TelegramStarsClient(ObjectMapper objectMapper, HttpClient httpClient, URI createInvoiceUri) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.createInvoiceUri = createInvoiceUri;
    }

    public String createVipInvoice(Long userId) {
        try {
            String body = createRequestBody(userId);
            HttpRequest request = HttpRequest.newBuilder(createInvoiceUri)
                    .timeout(Duration.ofSeconds(12))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode json = objectMapper.readTree(response.body());
            if (response.statusCode() / 100 != 2 || !json.path("ok").asBoolean(false)) {
                String description = json.path("description").asText("Telegram отклонил запрос");
                throw new IllegalStateException("Не удалось открыть оплату: " + description);
            }
            String invoiceLink = json.path("result").asText("");
            if (invoiceLink.isBlank()) throw new IllegalStateException("Telegram не вернул ссылку на оплату");
            return invoiceLink;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Создание оплаты было прервано");
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Не удалось связаться с Telegram для создания оплаты");
        }
    }

    String createRequestBody(Long userId) throws Exception {
        // provider_token намеренно отсутствует: для XTR он не должен отправляться.
        return objectMapper.writeValueAsString(Map.of(
                "title", "VIP на 30 дней",
                "description", "Золотая FUT-карта, VIP-корона и расширенная статистика",
                "payload", "VIP_30_DAYS_" + userId,
                "currency", "XTR",
                "prices", List.of(Map.of("label", "VIP 30 дней", "amount", 100))
        ));
    }
}
