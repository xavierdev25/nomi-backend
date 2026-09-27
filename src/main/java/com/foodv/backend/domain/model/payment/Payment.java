package com.foodv.backend.domain.model.payment;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Pago de un pedido en MercadoPago. Hay como máximo uno por pedido. {@code externalId} es el
 * id de la preferencia de MercadoPago y {@code paymentUrl} la URL del checkout.
 */
@Getter
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Payment {

    @EqualsAndHashCode.Include
    private Long id;

    private Long orderId;
    private Long userId;
    private BigDecimal amount;
    private PaymentStatus status;
    private String externalId;
    private String paymentUrl;
    private String failureReason;
    private LocalDateTime creadoEn;
    private LocalDateTime actualizadoEn;
}
