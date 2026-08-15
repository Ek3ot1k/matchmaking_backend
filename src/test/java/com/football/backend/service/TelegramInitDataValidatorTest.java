package com.football.backend.service;

import com.football.backend.dto.TelegramUser;
import com.football.backend.exceptions.TelegramAuthenticationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TelegramInitDataValidatorTest {

    private static final String BOT_TOKEN = "123456:test-bot-token";
    private static final long TELEGRAM_ID = 123_456_789L;

    private TelegramInitDataValidator validator;

    @BeforeEach
    void setUp() {
        validator = new TelegramInitDataValidator(new ObjectMapper(), BOT_TOKEN);
    }

    @Test
    void returnsTelegramUserForValidSignedInitData() {
        String initData = signedInitData(Instant.now().getEpochSecond());

        TelegramUser user = validator.validate(initData);

        assertEquals(TELEGRAM_ID, user.id());
        assertEquals("goalkeeper", user.username());
        assertEquals("Ivan", user.first_name());
    }

    @Test
    void rejectsInitDataWithModifiedUser() {
        String initData = signedInitData(Instant.now().getEpochSecond())
                .replace("goalkeeper", "attacker");

        assertThrows(TelegramAuthenticationException.class,
                () -> validator.validate(initData));
    }

    @Test
    void rejectsExpiredInitData() {
        String initData = signedInitData(Instant.now().minusSeconds(301).getEpochSecond());

        assertThrows(TelegramAuthenticationException.class,
                () -> validator.validate(initData));
    }

    @Test
    void rejectsInitDataWithoutHash() {
        String initData = signedInitData(Instant.now().getEpochSecond());
        String initDataWithoutHash = initData.substring(0, initData.lastIndexOf("&hash="));

        assertThrows(TelegramAuthenticationException.class,
                () -> validator.validate(initDataWithoutHash));
    }

    @Test
    void rejectsMalformedInitData() {
        assertThrows(TelegramAuthenticationException.class,
                () -> validator.validate("this-is-not-a-query-string"));
    }

    private String signedInitData(long authDate) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("auth_date", String.valueOf(authDate));
        fields.put("query_id", "AAHj2xUAAAAA4-UmQ4xPHVfR");
        fields.put("user", "{\"id\":123456789,\"first_name\":\"Ivan\",\"last_name\":\"Petrov\",\"username\":\"goalkeeper\"}");

        String dataCheckString = fields.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("\n"));

        byte[] secretKey = hmac("WebAppData".getBytes(StandardCharsets.UTF_8),
                BOT_TOKEN.getBytes(StandardCharsets.UTF_8));
        String hash = toHex(hmac(secretKey, dataCheckString.getBytes(StandardCharsets.UTF_8)));

        return fields.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&")) + "&hash=" + hash;
    }

    private byte[] hmac(byte[] key, byte[] value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot prepare test data", exception);
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format("%02x", value));
        }
        return result.toString();
    }
}
