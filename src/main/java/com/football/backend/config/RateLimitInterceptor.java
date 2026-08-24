package com.football.backend.config;

import com.football.backend.service.RateLimitingService;
import io.github.bucket4j.Bucket;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitingService rateLimitingService;

    public RateLimitInterceptor(RateLimitingService rateLimitingService) {
        this.rateLimitingService = rateLimitingService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // Получаем IP пользователя (учитываем, что может быть за прокси/nginx)
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddr();
        }

        Bucket tokenBucket = rateLimitingService.resolveBucket(ip);

        // Пытаемся забрать 1 токен
        if (tokenBucket.tryConsume(1)) {
            return true; // Токены есть, пропускаем запрос дальше
        } else {
            // Токены кончились, выдаем 429 Too Many Requests
            response.setStatus(429);
            response.getWriter().write("Too many requests. Please try again later.");
            return false;
        }
    }
}