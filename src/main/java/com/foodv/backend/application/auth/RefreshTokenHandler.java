package com.foodv.backend.application.auth;

import com.foodv.backend.domain.model.user.User;
import com.foodv.backend.domain.port.in.auth.LoginUseCase;
import com.foodv.backend.domain.port.in.auth.RefreshTokenUseCase;
import com.foodv.backend.domain.port.out.RefreshTokenStorePort;
import com.foodv.backend.domain.port.out.TokenServicePort;
import com.foodv.backend.domain.port.out.UserRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Rotación de refresh token: cada uso revoca el token recibido y emite un par nuevo.
 *
 * <p>Si llega un token ya revocado se asume robo (alguien lo reutilizó) y se revocan todas las
 * sesiones del usuario. Todos los fallos responden 400.
 */
@Service
public class RefreshTokenHandler implements RefreshTokenUseCase {

    private final TokenServicePort tokenServicePort;
    private final UserRepositoryPort userRepositoryPort;
    private final RefreshTokenStorePort refreshTokenStorePort;

    public RefreshTokenHandler(TokenServicePort tokenServicePort,
                               UserRepositoryPort userRepositoryPort,
                               RefreshTokenStorePort refreshTokenStorePort) {
        this.tokenServicePort = tokenServicePort;
        this.userRepositoryPort = userRepositoryPort;
        this.refreshTokenStorePort = refreshTokenStorePort;
    }

    @Override
    @Transactional
    public LoginUseCase.LoginResult execute(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank() || !tokenServicePort.isTokenValid(refreshToken)) {
            throw new IllegalArgumentException("Refresh token inválido");
        }

        RefreshTokenStorePort.RefreshTokenInfo info = refreshTokenStorePort.findByToken(refreshToken)
                .orElseThrow(() -> new IllegalArgumentException("Refresh token inválido"));

        if (info.revoked()) {
            refreshTokenStorePort.revokeAllByUserId(info.userId());
            throw new IllegalArgumentException("Refresh token revocado");
        }

        if (info.expiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Refresh token expirado");
        }

        refreshTokenStorePort.revokeByToken(refreshToken);

        String email = tokenServicePort.extractEmail(refreshToken);
        User user = userRepositoryPort.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        if (!user.isActivo()) {
            throw new IllegalArgumentException("Usuario inactivo");
        }

        String newAccessToken = tokenServicePort.generateAccessToken(
                user.getId(),
                user.getEmail(),
                user.getRole(),
                user.getNombres()
        );
        String newRefreshToken = tokenServicePort.generateRefreshToken(user.getEmail());

        refreshTokenStorePort.save(
                newRefreshToken,
                user.getId(),
                LocalDateTime.now().plusSeconds(tokenServicePort.getRefreshTokenExpirationMillis() / 1000)
        );

        return new LoginUseCase.LoginResult(newAccessToken, newRefreshToken, "Bearer",
                tokenServicePort.getAccessTokenExpirationMillis());
    }
}
