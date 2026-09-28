package com.nomi.backend.infrastructure.web.dto.order;

import com.nomi.backend.domain.model.order.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Pedido.")
public record OrderResponse(
        @Schema(description = "Id del pedido", example = "2")
        Long id,
        @Schema(description = "Estudiante que pidió", example = "5")
        Long userId,
        @Schema(description = "Tienda", example = "1")
        Long storeId,
        @Schema(description = "Aula de entrega", example = "1")
        Long aulaId,
        @Schema(description = "Repartidor asignado, si lo hay")
        Long repartidorId,
        @Schema(description = "Líneas del pedido")
        List<OrderItemResponse> items,
        @Schema(description = "Subtotal de productos. Lo cobrado es total + propina + tarifaServicio + comisionNomi", example = "10.00")
        BigDecimal total,
        @Schema(description = "Propina para el repartidor", example = "2.00")
        BigDecimal propina,
        @Schema(description = "Tarifa de servicio fija", example = "0.50")
        BigDecimal tarifaServicio,
        @Schema(description = "Comisión fija de Nomi", example = "0.20")
        BigDecimal comisionNomi,
        @Schema(description = "Estado del pedido", example = "PENDIENTE")
        OrderStatus status,
        @Schema(description = "Estado en texto legible", example = "Pendiente")
        String statusDescripcion,
        @Schema(description = "Notas para el repartidor", example = "Punto de encuentro: la puerta del salón")
        String notas,
        @Schema(description = "Motivo, si fue cancelado")
        String motivoCancelacion,
        @Schema(description = "Usuario que lo canceló")
        Long canceladoPor,
        @Schema(description = "Código de 4 dígitos que el estudiante muestra al recibir el pedido", example = "5369")
        String codigoConfirmacion,
        @Schema(description = "Foto de la entrega (todavía sin uso)")
        String fotoEntregaUrl,
        @Schema(description = "Hasta cuándo se puede pagar (hora local del servidor, sin zona). Después el pedido se cancela y el stock vuelve a la tienda; null en pedidos anteriores a esta regla",
                example = "2026-09-26T00:28:59.905824")
        LocalDateTime pagoExpiraEn,
        @Schema(description = "Segundos que quedan para pagar, calculados por el servidor para no depender del reloj ni de la zona del teléfono. Solo en pedidos PENDIENTE con plazo; 0 si ya venció",
                example = "840")
        Long segundosParaPagar,
        @Schema(description = "Fecha de creación (hora local del servidor, sin zona)", example = "2026-09-26T00:13:59.905824")
        LocalDateTime creadoEn,
        @Schema(description = "Última modificación (hora local del servidor, sin zona)", example = "2026-09-26T00:13:59.905828")
        LocalDateTime actualizadoEn
) {}
