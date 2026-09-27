package com.foodv.backend.infrastructure.web.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Cambio de contraseña. Cierra todas las sesiones del usuario.")
public record ChangePasswordRequest(
    @Schema(description = "Contraseña actual")
    @NotBlank String currentPassword,
    @NotBlank
    @Size(min = 8, max = 72)
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
        message = "La contraseña debe tener al menos una mayúscula, una minúscula y un número"
    )
    @Schema(description = "Nueva contraseña: 8 a 72 caracteres, con mayúscula, minúscula y número, distinta de la actual")
    String newPassword
) {}
