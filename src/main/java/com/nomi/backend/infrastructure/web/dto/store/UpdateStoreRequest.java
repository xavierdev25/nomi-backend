package com.nomi.backend.infrastructure.web.dto.store;

import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Edición de una tienda.")
public record UpdateStoreRequest(
        @Schema(description = "Nombre", example = "Sabores UCV")
        @NotBlank String nombre,
        @Schema(description = "Descripción; se conserva si se omite")
        String descripcion,
        @Schema(description = "Teléfono; se conserva si se omite")
        String telefono
) {}
