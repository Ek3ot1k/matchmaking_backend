package com.football.backend.controller;

import com.football.backend.dto.TelegramAuthRequest;
import com.football.backend.dto.TelegramUser;
import com.football.backend.entity.UserEntity;
import com.football.backend.security.JWTUtil;
import com.football.backend.service.AuthService;
import com.football.backend.service.TelegramInitDataValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@Slf4j
public class AuthController {

    private final AuthService authService;
    private final JWTUtil jwtUtil;
    private final TelegramInitDataValidator validator;

    public AuthController(AuthService authService,
                          JWTUtil jwtUtil,
                          TelegramInitDataValidator validator) {
        this.authService = authService;
        this.jwtUtil = jwtUtil;
        this.validator = validator;
    }

    @PostMapping("/telegram")
    public ResponseEntity<Map<String,String>> authenticateTelegramUser(@RequestBody TelegramAuthRequest request){
        log.info("Попытка входа юзера в Telegram");

        TelegramUser telegramUser = validator.validate(request.initData());

        UserEntity user = authService.authenticateOrRegister(
                telegramUser.id(),
                telegramUser.username()
        );

        String token = jwtUtil.generateToken(user.getId());

        log.info("Telegram юзер с id={} авторизован", telegramUser.id());
        return ResponseEntity.ok(Map.of("jwt-token", token));
    }
}
