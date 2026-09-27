package com.foodv.backend.infrastructure.security;

import com.foodv.backend.domain.port.out.TokenBlacklistPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Lista negra de access tokens en Redis. Un token revocado se guarda hasta su expiración, y un
 * cierre de sesiones global guarda por 30 días la marca de tiempo antes de la cual todos los
 * tokens del usuario son inválidos.
 */
@Service
public class TokenBlacklistService implements TokenBlacklistPort {

    private static final String TOKEN_PREFIX = "blacklist:token:";
    private static final String USER_CUTOFF_PREFIX = "blacklist:user:";

    private final StringRedisTemplate redisTemplate;

    public TokenBlacklistService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void blacklist(String token, long expirationMillis) {
        if (token == null || token.isBlank()) return;
        long ttl = Math.max(expirationMillis, 1L);
        redisTemplate.opsForValue().set(
                TOKEN_PREFIX + token,
                "revoked",
                Duration.ofMillis(ttl)
        );
    }

    @Override
    public boolean isBlacklisted(String token) {
        if (token == null || token.isBlank()) return false;
        return Boolean.TRUE.equals(redisTemplate.hasKey(TOKEN_PREFIX + token));
    }

    @Override
    public void invalidateAllSessionsBefore(Long userId, long timestampMillis) {
        if (userId == null) return;
        redisTemplate.opsForValue().set(
                USER_CUTOFF_PREFIX + userId,
                String.valueOf(timestampMillis),
                Duration.ofDays(30)
        );
    }

    @Override
    public boolean isUserSessionInvalidated(Long userId, long tokenIssuedAtMillis) {
        if (userId == null) return false;
        String cutoff = redisTemplate.opsForValue().get(USER_CUTOFF_PREFIX + userId);
        if (cutoff == null) return false;
        try {
            return tokenIssuedAtMillis < Long.parseLong(cutoff);
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
