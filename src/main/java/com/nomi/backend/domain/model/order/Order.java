package com.nomi.backend.domain.model.order;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Pedido de un estudiante a una tienda, para entregar en un aula.
 *
 * <p>{@code total} es solo el subtotal de productos. Lo cobrado es
 * {@code total + propina + tarifaServicio + comisionNomi} (ver {@code CreatePaymentHandler}).
 * {@code codigoConfirmacion} son 4 dígitos que el estudiante muestra al recibir el pedido.
 *
 * <p>{@code pagoExpiraEn} es el plazo para pagar: si el pedido sigue en {@code PENDIENTE} sin pago
 * aprobado después de esa hora, se cancela y su stock vuelve a estar disponible.
 */
@Getter
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Order {

    @EqualsAndHashCode.Include
    private Long id;

    private Long userId;
    private Long storeId;
    private Long aulaId;
    private Long repartidorId;
    private List<OrderItem> items;
    private BigDecimal total;
    private BigDecimal propina;
    private BigDecimal tarifaServicio;
    private BigDecimal comisionNomi;
    private OrderStatus status;
    private String notas;
    private String motivoCancelacion;
    private Long canceladoPor;
    private String codigoConfirmacion;
    private String fotoEntregaUrl;
    private LocalDateTime creadoEn;
    private LocalDateTime actualizadoEn;
    private LocalDateTime pagoExpiraEn;

    /** Si está pendiente de pago y ya pasó el plazo para pagarlo. */
    public boolean isPaymentExpired(LocalDateTime now) {
        return status == OrderStatus.PENDIENTE && pagoExpiraEn != null && !now.isBefore(pagoExpiraEn);
    }

    /**
     * Segundos que le quedan al estudiante para pagar, o {@code null} si el pedido ya no está
     * pendiente o no tiene plazo. Se envía relativo (no una hora) porque las fechas del backend
     * no llevan zona horaria y los clientes las interpretan distinto.
     */
    public Long segundosParaPagar(LocalDateTime now) {
        if (status != OrderStatus.PENDIENTE || pagoExpiraEn == null) {
            return null;
        }
        return Math.max(0, Duration.between(now, pagoExpiraEn).getSeconds());
    }
}
