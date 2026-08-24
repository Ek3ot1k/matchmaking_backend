package com.football.backend.service;

import com.football.backend.dto.TelegramUser;
import com.football.backend.entity.UserEntity;
import com.football.backend.model.Position;
import com.football.backend.model.Role;
import com.football.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class AuthService {
    private final UserRepository userRepository;

    @Value("${telegram.bot.token}") // Токен твоего бота из properties
    private String botToken;

    @Value("${app.admin.telegram-ids:}")
    private String adminTelegramIds;

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * ГЛАВНЫЙ МЕТОД: Проверяет подлинность данных от Телеграма.
     * ПРАВИЛО: НИКОГДА не логируем initData в консоль!
     */
    public boolean validateTelegramInitData(String initData) {
        try {
            // Парсим строку на ключ-значение
            Map<String, String> dataMap = Arrays.stream(initData.split("&"))
                    .map(param -> param.split("=", 2))
                    .collect(Collectors.toMap(arr -> arr[0], arr -> arr.length > 1 ? arr[1] : ""));

            String hash = dataMap.remove("hash");
            if (hash == null) return false;

            // Сортируем ключи в алфавитном порядке и склеиваем
            String dataCheckString = dataMap.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .collect(Collectors.joining("\n"));

            // Генерируем Secret Key: HMAC256 от bot_token со строкой "WebAppData"
            Mac sha256HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec("WebAppData".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256HMAC.init(secretKey);
            byte[] secretKeyBytes = sha256HMAC.doFinal(botToken.getBytes(StandardCharsets.UTF_8));

            // Хешируем dataCheckString полученным ключом
            SecretKeySpec dataKey = new SecretKeySpec(secretKeyBytes, "HmacSHA256");
            sha256HMAC.init(dataKey);
            byte[] calculatedHash = sha256HMAC.doFinal(dataCheckString.getBytes(StandardCharsets.UTF_8));

            // Сравниваем хэши (переводим байты в HEX)
            StringBuilder hexString = new StringBuilder();
            for (byte b : calculatedHash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }

            return hexString.toString().equals(hash);
        } catch (Exception e) {
            return false;
        }
    }

    @Transactional
    public UserEntity authenticateOrRegister(TelegramUser tgUser) {
        Optional<UserEntity> existingUserOpt = userRepository.findByTelegramId(tgUser.id());

        if (existingUserOpt.isPresent()) {
            UserEntity user = existingUserOpt.get();
            // Синхронизируем данные с актуальным профилем Телеграма
            user.setFirstName(tgUser.first_name());
            user.setLastName(tgUser.last_name());
            user.setUsername(tgUser.username());
            user.setAvatarUrl(tgUser.photo_url());
            if (isConfiguredAdmin(tgUser.id())) {
                user.setRole(Role.ADMIN);
            }

            return userRepository.save(user);
        }

        // Если это абсолютно новый игрок
        UserEntity newUser = UserEntity.builder()
                .telegramId(tgUser.id())
                .firstName(tgUser.first_name())
                .lastName(tgUser.last_name())
                .username(tgUser.username())
                .avatarUrl(tgUser.photo_url())
                .position(Position.UNKNOWN)
                .role(isConfiguredAdmin(tgUser.id()) ? Role.ADMIN : Role.USER)
                .build();

        return userRepository.save(newUser);
    }

    private boolean isConfiguredAdmin(Long telegramId) {
        if (telegramId == null || adminTelegramIds == null || adminTelegramIds.isBlank()) return false;
        return Arrays.stream(adminTelegramIds.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .anyMatch(value -> value.equals(String.valueOf(telegramId)));
    }
}
