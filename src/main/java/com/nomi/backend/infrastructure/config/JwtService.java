package com.nomi.backend.infrastructure.config;

import com.nomi.backend.domain.model.user.UserRole;
import com.nomi.backend.domain.port.out.TokenServicePort;
import org.springframework.stereotype.Service;

/**
 * Envoltorio heredado de {@code TokenServicePort}. No se usa: el código nuevo debe inyectar el
 * puerto directamente.
 */
@Service
public class JwtService {

    private final TokenServicePort tokenServicePort;

    public JwtService(TokenServicePort tokenServicePort) {
        this.tokenServicePort = tokenServicePort;
    }

    public String generateAccessToken(Long userId, String email, UserRole role) {
        return tokenServicePort.generateAccessToken(userId, email, role);
    }

    public String generateAccessToken(Long userId, String email, UserRole role, String nombres) {
        return tokenServicePort.generateAccessToken(userId, email, role, nombres);
    }

    public String generateRefreshToken(String email) {
        return tokenServicePort.generateRefreshToken(email);
    }

    public boolean isTokenValid(String token) {
        return tokenServicePort.isTokenValid(token);
    }

    public String extractEmail(String token) {
        return tokenServicePort.extractEmail(token);
    }

    public String extractRole(String token) {
        return tokenServicePort.extractRole(token);
    }

    public String extractNombres(String token) {
        return tokenServicePort.extractNombres(token);
    }

    public Long extractUserId(String token) {
        return tokenServicePort.extractUserId(token);
    }
}
