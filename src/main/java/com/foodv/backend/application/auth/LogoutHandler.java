package com.foodv.backend.application.auth;

import com.foodv.backend.domain.port.in.auth.LogoutUseCase;
import com.foodv.backend.domain.port.out.RefreshTokenStorePort;
import com.foodv.backend.domain.port.out.TokenBlacklistPort;
import com.foodv.backend.domain.port.out.TokenServicePort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cierre de sesión: revoca el refresh token recibido y todos los del usuario, y pone el access
 * token en la lista negra hasta que expire.
 */
@Service
public class LogoutHandler implements LogoutUseCase {

    private final RefreshTokenStorePort refreshTokenStorePort;
    private final TokenBlacklistPort tokenBlacklistPort;
    private final TokenServicePort tokenServicePort;

    public LogoutHandler(RefreshTokenStorePort refreshTokenStorePort,
                         TokenBlacklistPort tokenBlacklistPort,
                         TokenServicePort tokenServicePort) {
        this.refreshTokenStorePort = refreshTokenStorePort;
        this.tokenBlacklistPort = tokenBlacklistPort;
        this.tokenServicePort = tokenServicePort;
    }

    @Override
    @Transactional
    public void execute(LogoutCommand command) {
        if (command.refreshToken() != null && !command.refreshToken().isBlank()) {
            refreshTokenStorePort.revokeByToken(command.refreshToken());
        }
        if (command.accessToken() != null && !command.accessToken().isBlank()) {
            Long userId = tokenServicePort.extractUserId(command.accessToken());
            if (userId != null) {
                refreshTokenStorePort.revokeAllByUserId(userId);
            }
            tokenBlacklistPort.blacklist(command.accessToken(),
                    tokenServicePort.getAccessTokenExpirationMillis());
        }
    }
}
