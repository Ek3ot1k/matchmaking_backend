package com.football.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
public class RedisConfig {

    @Bean
    public RedisCacheConfiguration cacheConfiguration() {
        return RedisCacheConfiguration.defaultCacheConfig()
                // Устанавливаем время жизни кэша (например, 1 час).
                .entryTtl(Duration.ofHours(1))
                // Не кэшируем null (защита от ошибок)
                .disableCachingNullValues()
                // Ключи в Redis будут обычными строками
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                // А вот сами данные (наши DTO) будут сохраняться в красивый JSON
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(org.springframework.data.redis.serializer.RedisSerializer.json()));
    }
}