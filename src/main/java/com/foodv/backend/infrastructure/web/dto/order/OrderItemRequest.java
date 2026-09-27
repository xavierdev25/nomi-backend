package com.foodv.backend.infrastructure.web.dto.order;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Producto y cantidad de un pedido nuevo.")
public record OrderItemRequest(
        @Schema(description = "Producto", example = "4")
        @NotNull Long productId,
        @Schema(description = "Unidades, mínimo 1", example = "2")
        @NotNull @Min(1) Integer cantidad
) {}
