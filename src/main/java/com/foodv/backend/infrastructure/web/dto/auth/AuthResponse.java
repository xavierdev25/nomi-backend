package com.foodv.backend.infrastructure.web.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Tokens de sesión.")
public record AuthResponse(
        @Schema(description = "JWT para la cabecera Authorization: Bearer")
        String accessToken,
        @Schema(description = "Token para renovar la sesión en /auth/refresh; se rota en cada uso")
        String refreshToken,
        @Schema(description = "Tipo de token", example = "Bearer")
        String tokenType,
        @Schema(description = "Duración del access token, en milisegundos", example = "86400000")
        long expiresIn
) {}
