package com.foodv.backend.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

/**
 * Límite de peticiones por minuto en Redis (válido con varias réplicas): 5 para IA, 30 para
 * autenticación, 60 para pagos y 100 para el resto.
 *
 * <p>Limitaciones conocidas (auditoría técnica, M1): la clave es el hash de la cabecera
 * {@code Authorization} aunque el token no sea válido, se confía en {@code X-Forwarded-For} y
 * el límite de pagos no aplica porque la URI incluye el prefijo {@code /api}.
 */
@Order(1)
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final StringRedisTemplate redisTemplate;

    public RateLimitingFilter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String clientKey = getClientKey(request);
        Bucket bucket = bucketFor(request);

        String redisKey = "rl:" + bucket.name + ":" + clientKey;
        Long count = redisTemplate.opsForValue().increment(redisKey);
        if (count != null && count == 1L) {
            redisTemplate.expire(redisKey, WINDOW);
        }

        if (count != null && count > bucket.limit) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", String.valueOf(WINDOW.getSeconds()));
            response.getWriter().write(
                    "{\"status\":429,\"error\":\"Demasiadas peticiones\",\"message\":\"" + bucket.message + "\"}"
            );
            return;
        }

        filterChain.doFilter(request, response);
    }

    private Bucket bucketFor(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri.contains("/ai/")) {
            return new Bucket("ai", 5, "Has excedido el límite de 5 recomendaciones por minuto.");
        }
        if (uri.contains("/auth/login") || uri.contains("/auth/register") || uri.contains("/auth/refresh")) {
            return new Bucket("auth", 30, "Demasiados intentos de autenticación. Espera un minuto.");
        }
        if (uri.startsWith("/payments")) {
            return new Bucket("pay", 60, "Demasiadas peticiones de pago.");
        }
        return new Bucket("gen", 100, "Has excedido el límite de 100 peticiones por minuto.");
    }

    private String getClientKey(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return "u:" + sha256(authHeader.substring(7));
        }
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) {
            int comma = ip.indexOf(',');
            ip = comma > 0 ? ip.substring(0, comma).trim() : ip.trim();
        } else {
            ip = request.getRemoteAddr();
        }
        return "ip:" + ip;
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(input.getBytes())).substring(0, 32);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(input.hashCode());
        }
    }

    private record Bucket(String name, int limit, String message) {}
}
