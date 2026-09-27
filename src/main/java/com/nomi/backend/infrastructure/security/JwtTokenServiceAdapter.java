package com.nomi.backend.infrastructure.security;

import com.nomi.backend.domain.model.user.UserRole;
import com.nomi.backend.domain.port.out.TokenServicePort;
import com.nomi.backend.infrastructure.config.JwtConfig;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Emisión y validación de JWT firmados con HMAC-SHA256 (jjwt).
 *
 * <p>Los tokens no llevan {@code jti}: dos emitidos en el mismo segundo para el mismo usuario
 * son idénticos (ver la auditoría técnica, M3).
 */
@Component
public class JwtTokenServiceAdapter implements TokenServicePort {

    private final JwtConfig jwtConfig;
    private final SecretKey signingKey;

    public JwtTokenServiceAdapter(JwtConfig jwtConfig) {
        this.jwtConfig = jwtConfig;
        this.signingKey = Keys.hmacShaKeyFor(jwtConfig.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String generateAccessToken(Long userId, String email, UserRole role, String nombres) {
        return Jwts.builder()
                .subject(email)
                .claim("userId", userId)
                .claim("email", email)
                .claim("role", role.name())
                .claim("nombres", nombres)
                .issuer(jwtConfig.getIssuer())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtConfig.getExpiration()))
                .signWith(signingKey)
                .compact();
    }

    @Override
    public String generateRefreshToken(String email) {
        return Jwts.builder()
                .subject(email)
                .issuer(jwtConfig.getIssuer())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtConfig.getRefreshExpiration()))
                .signWith(signingKey)
                .compact();
    }

    @Override
    public boolean isTokenValid(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return !claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String extractEmail(String token) {
        Claims claims = extractAllClaims(token);
        String email = claims.get("email", String.class);
        return email != null ? email : claims.getSubject();
    }

    @Override
    public String extractRole(String token) {
        return extractAllClaims(token).get("role", String.class);
    }

    @Override
    public String extractNombres(String token) {
        return extractAllClaims(token).get("nombres", String.class);
    }

    @Override
    public Long extractUserId(String token) {
        Object userId = extractAllClaims(token).get("userId");
        return userId instanceof Number number ? number.longValue() : null;
    }

    @Override
    public long extractIssuedAtMillis(String token) {
        Date issuedAt = extractAllClaims(token).getIssuedAt();
        return issuedAt != null ? issuedAt.getTime() : 0L;
    }

    @Override
    public long getAccessTokenExpirationMillis() {
        return jwtConfig.getExpiration();
    }

    @Override
    public long getRefreshTokenExpirationMillis() {
        return jwtConfig.getRefreshExpiration();
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
