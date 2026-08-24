package com.football.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;

    public WebConfig(RateLimitInterceptor rateLimitInterceptor) {
        this.rateLimitInterceptor = rateLimitInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Вешаем лимит на самые уязвимые места: авторизацию и платежи
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/v1/auth/telegram")
                .addPathPatterns("/api/v1/payments/**");
    }
}