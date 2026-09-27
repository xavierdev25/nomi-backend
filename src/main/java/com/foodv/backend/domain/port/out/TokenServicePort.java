package com.foodv.backend.domain.port.out;

import com.foodv.backend.domain.model.user.UserRole;

/**
 * Emisión y lectura de JWT. Los access tokens llevan {@code userId}, {@code email},
 * {@code role} y {@code nombres}; los refresh tokens solo el email como {@code subject}.
 */
public interface TokenServicePort {

    default String generateAccessToken(Long userId, String email, UserRole role) {
        return generateAccessToken(userId, email, role, null);
    }

    String generateAccessToken(Long userId, String email, UserRole role, String nombres);

    String generateRefreshToken(String email);

    /**
     * Firma correcta y no expirado. No consulta revocaciones: eso es {@link TokenBlacklistPort}.
     */
    boolean isTokenValid(String token);

    String extractEmail(String token);

    String extractRole(String token);

    String extractNombres(String token);

    Long extractUserId(String token);

    long extractIssuedAtMillis(String token);

    long getAccessTokenExpirationMillis();

    long getRefreshTokenExpirationMillis();
}
