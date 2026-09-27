package com.nomi.backend.domain.port.in.auth;

/**
 * Inicio de sesión con email y contraseña. Devuelve un access token y un refresh token.
 */
public interface LoginUseCase {

    LoginResult execute(LoginCommand command);

    record LoginCommand(
            String email,
            String password
    ) {}

    record LoginResult(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn
    ) {}
}
