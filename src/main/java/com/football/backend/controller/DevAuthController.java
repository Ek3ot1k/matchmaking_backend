package com.football.backend.controller;

import com.football.backend.entity.UserEntity;
import com.football.backend.model.Position;
import com.football.backend.model.Role;
import com.football.backend.repository.UserRepository;
import com.football.backend.security.JWTUtil;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@Profile("local")
public class DevAuthController {

    private final JWTUtil jwtUtil;
    private final UserRepository userRepository;

    public DevAuthController(JWTUtil jwtUtil, UserRepository userRepository) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
    }

    @GetMapping("/dev-token")
    public ResponseEntity<Map<String, String>> getDevToken(
            @RequestParam(defaultValue = "111222333") Long telegramId,
            @RequestParam(defaultValue = "ADMIN") Role role) {

        // Ищем или создаем фейкового юзера для тестов
        UserEntity user = userRepository.findByTelegramId(telegramId)
                .orElseGet(() -> {
                    UserEntity newUser = UserEntity.builder()
                            .telegramId(telegramId)
                            .username("dev_tester")
                            .firstName("Dev")
                            .lastName("User")
                            .position(Position.UNKNOWN)
                            .role(role)
                            .build();
                    return userRepository.save(newUser);
                });

        String token = jwtUtil.generateToken(user.getId());

        return ResponseEntity.ok(Map.of("jwt-token", token));
    }
}