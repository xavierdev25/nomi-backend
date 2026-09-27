package com.nomi.backend.infrastructure.web.dto.store;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Alta de la tienda del comercio autenticado (una por usuario).")
public record CreateStoreRequest(
        @NotBlank(message = "Nombre obligatorio")
        @Size(min = 2, max = 100, message = "Nombre debe tener entre 2 y 100 caracteres")
        @Schema(description = "Nombre, 2 a 100 caracteres", example = "Sabores UCV")
        String nombre,

        @Size(max = 500, message = "Descripción no puede exceder 500 caracteres")
        @Schema(description = "Descripción, hasta 500 caracteres", example = "Comida casera y rápida para estudiantes")
        String descripcion,

        @Pattern(regexp = "^[+]?[0-9\\s-]{6,20}$", message = "Teléfono inválido")
        @Schema(description = "Teléfono de contacto", example = "+51 987 654 321")
        String telefono
) {}
