package com.nomi.backend.infrastructure.web.dto.rating;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Resumen de calificaciones de una tienda.")
public record StoreRatingSummaryResponse(
        @Schema(description = "Tienda", example = "1")
        Long storeId,
        @Schema(description = "Promedio de 1 a 5; null si no tiene calificaciones", example = "4.6")
        Double promedio,
        @Schema(description = "Número de calificaciones", example = "12")
        Long total
) {}
