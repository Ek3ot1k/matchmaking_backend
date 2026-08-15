package com.football.backend.controller;

import com.football.backend.dto.TelegramAuthRequest;
import com.football.backend.entity.UserEntity;
import com.football.backend.security.JWTUtil;
import com.football.backend.service.AuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@Slf4j
@CrossOrigin(origins = "*")
public class AuthController {

    private final AuthService authService;
    private final JWTUtil jwtUtil;

    public AuthController(AuthService authService, JWTUtil jwtUtil) {
        this.authService = authService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/telegram")
    public ResponseEntity<Map<String,String>> authenticateTelegramUser(
            @RequestBody TelegramAuthRequest request
            ){
        try {
            log.info("Попытка входа Telegram юзера: {}", request.username());

            UserEntity user = authService
                    .authenticateOrRegister(request.telegramId(), request.username());
            String token = jwtUtil.generateToken(user.getUsername());

            log.info("Пользователь {} успешно авторизован", user.getUsername());
            return ResponseEntity.ok(Map.of("jwt-token", token));
        }catch (Exception e){
            log.error("Ошибка авторизации пользователя {}: {}", request.username(), e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "Auth failed"));
        }
    }
}
