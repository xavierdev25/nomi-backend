package com.foodv.backend.infrastructure.web.dto.order;

import com.foodv.backend.domain.model.order.OrderStatus;
import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Cambio de estado de un pedido.")
public record OrderStatusHistoryResponse(
        @Schema(description = "Id de la entrada", example = "1")
        Long id,
        @Schema(description = "Pedido", example = "2")
        Long orderId,
        @Schema(description = "Estado al que pasó", example = "PREPARANDO")
        OrderStatus status,
        @Schema(description = "Usuario que hizo el cambio", example = "3")
        Long changedBy,
        @Schema(description = "Descripción del cambio", example = "Estado actualizado a En preparación")
        String notas,
        @Schema(description = "Fecha del cambio (hora local del servidor, sin zona)")
        LocalDateTime creadoEn
) {}
