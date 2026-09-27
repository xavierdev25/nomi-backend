package com.foodv.backend.infrastructure.config;

import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.time.Duration;
import java.util.Map;

/**
 * Caché en Redis con TTL de 5 minutos para los modelos de lectura ({@code products},
 * {@code stores}).
 */
@Configuration
public class RedisCacheConfig {

    private static final Duration READ_MODEL_TTL = Duration.ofMinutes(5);

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(READ_MODEL_TTL)
                .disableCachingNullValues();

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(Map.of(
                        "products", defaultConfig.entryTtl(READ_MODEL_TTL),
                        "stores", defaultConfig.entryTtl(READ_MODEL_TTL)
                ))
                .build();
    }
}
