package com.nomi.backend.domain.model.order;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * Línea de un pedido. Nombre y precio se copian del producto al crear el pedido, para que un
 * cambio posterior del catálogo no altere pedidos existentes.
 */
@Getter
@Builder
public class OrderItem {

    private Long id;
    private Long productId;
    private String productNombre;
    private BigDecimal productPrecio;
    private Integer cantidad;
    private BigDecimal subtotal;
}
