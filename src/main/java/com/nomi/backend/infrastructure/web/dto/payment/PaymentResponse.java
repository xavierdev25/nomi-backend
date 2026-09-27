package com.nomi.backend.infrastructure.web.dto.payment;

import com.nomi.backend.domain.model.payment.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Pago de un pedido en MercadoPago.")
public record PaymentResponse(
        @Schema(description = "Id del pago", example = "9")
        Long id,
        @Schema(description = "Pedido pagado", example = "2")
        Long orderId,
        @Schema(description = "Estudiante", example = "5")
        Long userId,
        @Schema(description = "Monto: total + propina + tarifa de servicio + comisión", example = "12.70")
        BigDecimal amount,
        @Schema(description = "Estado; lo actualiza el webhook de MercadoPago", example = "PENDIENTE")
        PaymentStatus status,
        @Schema(description = "Id de la preferencia en MercadoPago")
        String externalId,
        @Schema(description = "URL del checkout de MercadoPago")
        String paymentUrl,
        @Schema(description = "Motivo del rechazo, si lo hubo")
        String failureReason,
        @Schema(description = "Fecha de creación (hora local del servidor, sin zona)")
        LocalDateTime creadoEn,
        @Schema(description = "Última actualización (hora local del servidor, sin zona)")
        LocalDateTime actualizadoEn
) {}
