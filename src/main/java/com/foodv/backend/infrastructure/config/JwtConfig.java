package com.foodv.backend.infrastructure.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;

/**
 * Configuración de JWT ({@code jwt.*}), validada al arrancar: la aplicación no inicia con un
 * secreto de menos de 32 bytes, expiraciones inválidas o sin emisor.
 */
@Configuration
@ConfigurationProperties(prefix = "jwt")
@Getter
@Setter
public class JwtConfig {

    private static final int MIN_SECRET_BYTES = 32;

    private String secret;
    private long expiration;
    private long refreshExpiration;
    private String issuer;

    @PostConstruct
    public void validate() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT_SECRET no configurado. Define la variable de entorno JWT_SECRET."
            );
        }
        int len = secret.getBytes(StandardCharsets.UTF_8).length;
        if (len < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET debe tener al menos " + MIN_SECRET_BYTES + " bytes (256 bits). Actual: " + len
            );
        }
        if (expiration <= 0) {
            throw new IllegalStateException("JWT_EXPIRATION debe ser positivo");
        }
        if (refreshExpiration <= expiration) {
            throw new IllegalStateException(
                    "JWT_REFRESH_EXPIRATION debe ser mayor que JWT_EXPIRATION"
            );
        }
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalStateException("JWT_ISSUER no configurado");
        }
    }
}
