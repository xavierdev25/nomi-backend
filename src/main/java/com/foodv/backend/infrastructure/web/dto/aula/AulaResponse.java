package com.foodv.backend.infrastructure.web.dto.aula;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Aula del campus donde se entregan los pedidos.")
public record AulaResponse(
        @Schema(description = "Id del aula", example = "1")
        Long id,
        @Schema(description = "Código único del aula", example = "A-101")
        String codigo,
        @Schema(description = "Nombre visible", example = "Aula 101")
        String nombre,
        @Schema(description = "Piso", example = "1er piso")
        String piso,
        @Schema(description = "Pabellón", example = "Pabellón A")
        String pabellon,
        @Schema(description = "Si se ofrece para pedidos nuevos", example = "true")
        boolean activo
) {}
