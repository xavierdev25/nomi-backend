package com.nomi.backend.domain.port.in.auth;

/**
 * Emite un par de tokens nuevo a partir de un refresh token válido, rotándolo.
 */
public interface RefreshTokenUseCase {

    LoginUseCase.LoginResult execute(String refreshToken);
}
