package com.football.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TelegramStarsClientTest {

    @Test
    void buildsStarsInvoiceWithoutLegacyProviderToken() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        TelegramStarsClient client = new TelegramStarsClient(
                objectMapper, mock(HttpClient.class), URI.create("https://example.test/createInvoiceLink")
        );

        JsonNode body = objectMapper.readTree(client.createRequestBody(42L));

        assertThat(body.path("currency").asText()).isEqualTo("XTR");
        assertThat(body.path("payload").asText()).isEqualTo("VIP_30_DAYS_42");
        assertThat(body.path("prices").get(0).path("amount").asInt()).isEqualTo(100);
        assertThat(body.has("provider_token")).isFalse();
    }
}
