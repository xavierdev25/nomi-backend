package com.foodv.backend.infrastructure.web.dto.user;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Actualización parcial del perfil: los campos omitidos no cambian.")
public record UpdateUserRequest(
        @Schema(description = "Nombres, 2 a 60 caracteres")
        @Size(min = 2, max = 60) String nombres,
        @Schema(description = "Apellidos, 2 a 60 caracteres")
        @Size(min = 2, max = 60) String apellidos,
        @Schema(description = "6 a 20 dígitos, espacios o guiones, con + inicial opcional")
        @Pattern(regexp = "^[+]?[0-9\\s-]{6,20}$", message = "Teléfono inválido") String telefono
) {}
