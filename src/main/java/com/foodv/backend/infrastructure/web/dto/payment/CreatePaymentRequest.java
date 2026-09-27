package com.foodv.backend.infrastructure.web.dto.payment;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Pago de un pedido. El monto se calcula en el servidor.")
public record CreatePaymentRequest(
        @NotNull(message = "orderId es obligatorio")
        @Positive(message = "orderId debe ser positivo")
        @Schema(description = "Pedido a pagar", example = "2")
        Long orderId
) {}
