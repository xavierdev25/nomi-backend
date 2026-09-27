package com.nomi.backend.infrastructure.web.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Credenciales de inicio de sesión. El email no distingue mayúsculas.")
public record LoginRequest(
        @Schema(description = "Email del usuario", example = "ana@ucv.edu.pe")
        @NotBlank @Email String email,
        @Schema(description = "Contraseña", example = "Segura123")
        @NotBlank String password
) {}
