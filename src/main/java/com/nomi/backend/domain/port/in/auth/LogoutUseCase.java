package com.nomi.backend.domain.port.in.auth;

/**
 * Cierre de sesión: revoca los refresh tokens del usuario e invalida el access token.
 */
public interface LogoutUseCase {

    void execute(LogoutCommand command);

    record LogoutCommand(
            String accessToken,
            String refreshToken
    ) {}
}
