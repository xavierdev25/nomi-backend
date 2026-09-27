package com.foodv.backend.infrastructure.web.dto.aula;

import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Alta de un aula.")
public record CreateAulaRequest(
        @Schema(description = "Código único del aula", example = "A-101")
        @NotBlank String codigo,
        @Schema(description = "Nombre visible", example = "Aula 101")
        @NotBlank String nombre,
        @Schema(description = "Piso", example = "1er piso")
        String piso,
        @Schema(description = "Pabellón", example = "Pabellón A")
        String pabellon
) {}
