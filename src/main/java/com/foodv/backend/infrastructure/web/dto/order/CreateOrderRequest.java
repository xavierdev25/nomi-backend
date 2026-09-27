package com.foodv.backend.infrastructure.web.dto.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Nuevo pedido. Importes y reserva de stock los calcula el servidor.")
public record CreateOrderRequest(
        @Schema(description = "Tienda; todos los productos deben ser de ella", example = "1")
        @NotNull Long storeId,
        @Schema(description = "Aula de entrega", example = "1")
        @NotNull Long aulaId,
        @Schema(description = "Productos y cantidades; un producto repetido se suma")
        @NotEmpty List<@Valid OrderItemRequest> items,
        @Schema(description = "Notas para el repartidor, incluido el punto de encuentro", example = "Punto de encuentro: la puerta del salón")
        String notas,
        @Schema(description = "Propina en soles, de 0 al 50 % del subtotal de productos; 0 si se omite", example = "2.00")
        BigDecimal propina
) {
    public CreateOrderRequest {
        if (propina == null) propina = BigDecimal.ZERO;
    }
}
