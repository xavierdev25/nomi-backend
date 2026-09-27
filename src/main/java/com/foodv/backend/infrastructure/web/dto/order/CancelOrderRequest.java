package com.foodv.backend.infrastructure.web.dto.order;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Cancelación de un pedido. El cuerpo es opcional.")
public record CancelOrderRequest(
        @Schema(description = "Motivo de la cancelación", example = "Ya no lo necesito")
        String motivo
) {}
