package com.foodv.backend.infrastructure.web.dto.order;

import java.math.BigDecimal;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Línea de un pedido.")
public record OrderItemResponse(
        @Schema(description = "Id de la línea", example = "2")
        Long id,
        @Schema(description = "Producto", example = "4")
        Long productId,
        @Schema(description = "Nombre del producto al momento de pedir", example = "Chicha Morada")
        String productNombre,
        @Schema(description = "Precio unitario al momento de pedir", example = "4.00")
        BigDecimal productPrecio,
        @Schema(description = "Unidades", example = "1")
        Integer cantidad,
        @Schema(description = "productPrecio × cantidad", example = "4.00")
        BigDecimal subtotal
) {}
