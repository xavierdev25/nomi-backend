package com.nomi.backend.domain.model.order;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Pedido de un estudiante a una tienda, para entregar en un aula.
 *
 * <p>{@code total} es solo el subtotal de productos. Lo cobrado es
 * {@code total + propina + tarifaServicio + comisionNomi} (ver {@code CreatePaymentHandler}).
 * {@code codigoConfirmacion} son 4 dígitos que el estudiante muestra al recibir el pedido.
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
}
