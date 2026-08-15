package com.football.backend.service;

import tools.jackson.core.JacksonException;
import com.football.backend.dto.TelegramUser;
import com.football.backend.exceptions.TelegramAuthenticationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TelegramInitDataValidator {

    private static final long MAX_AGE_SECONDS = 300;

    private final String botToken;
    private final ObjectMapper objectMapper;

    public TelegramInitDataValidator(
            ObjectMapper objectMapper,
            @Value("${telegram.bot.token}") String botToken
    ) {
        this.objectMapper = objectMapper;
        this.botToken = botToken;
    }

    public TelegramUser validate(String initData) {
        if (initData == null || initData.isBlank()) {
            throw new TelegramAuthenticationException("Empty initData");
        }
        try {
            Map<String, String> data = Arrays.stream(initData.split("&"))
                    .map(pair -> pair.split("=", 2))
                    .collect(Collectors.toMap(
                            pair -> URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                            pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8)
                    ));

            String receivedHash = data.remove("hash");
            String userJson = data.get("user");

            if (userJson == null || userJson.isBlank()) {
                throw new TelegramAuthenticationException("Missing user");
            }
            if (receivedHash == null) {
                throw new TelegramAuthenticationException("Missing hash");
            }

            long authDate = Long.parseLong(data.get("auth_date"));
            long now = Instant.now().getEpochSecond();

            if (authDate > now + 30 || now - authDate > MAX_AGE_SECONDS) {
                throw new TelegramAuthenticationException("Expired initData");
            }

            String dataCheckString = data.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .collect(Collectors.joining("\n"));


            Mac mac = Mac.getInstance("HmacSHA256");

            mac.init(new SecretKeySpec(
                    "WebAppData".getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            ));
            byte[] secretKey = mac.doFinal(botToken.getBytes(StandardCharsets.UTF_8));

            mac.init(new SecretKeySpec(secretKey, "HmacSHA256"));
            byte[] expectedHash = mac.doFinal(
                    dataCheckString.getBytes(StandardCharsets.UTF_8)
            );

            byte[] actualHash = HexFormat.of().parseHex(receivedHash);

            if (!MessageDigest.isEqual(expectedHash, actualHash)) {
                throw new TelegramAuthenticationException("Invalid Telegram signature");
            }

            return objectMapper.readValue(userJson, TelegramUser.class);
        }catch (TelegramAuthenticationException e){
            throw e;
        } catch (IllegalArgumentException | ArrayIndexOutOfBoundsException | IllegalStateException e){
            throw new TelegramAuthenticationException("Malformed Telegram initData");
        }catch (GeneralSecurityException | JacksonException e) {
            throw new TelegramAuthenticationException("Invalid Telegram initData");
        }
    }
}
