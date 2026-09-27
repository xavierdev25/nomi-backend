package com.foodv.backend.infrastructure.web.dto.order;

import com.foodv.backend.domain.model.order.OrderStatus;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Nuevo estado de un pedido.")
public record UpdateOrderStatusRequest(
        @Schema(description = "Debe ser una transición válida desde el estado actual", example = "PREPARANDO")
        @NotNull OrderStatus status
) {}
