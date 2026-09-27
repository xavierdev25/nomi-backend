package com.foodv.backend.domain.port.out;

import com.foodv.backend.domain.model.payment.PaymentStatus;

import java.math.BigDecimal;

/**
 * Pasarela de pagos (MercadoPago).
 */
public interface PaymentGatewayPort {

    record PaymentRequest(Long orderId, Long userId, BigDecimal amount, String description, String notificationUrl) {}

    record PaymentResponse(String externalId, String paymentUrl, PaymentStatus status) {}

    /**
     * Crea la preferencia de pago y devuelve su id y la URL del checkout.
     *
     * @throws IllegalStateException si la pasarela rechaza la operación
     */
    PaymentResponse createPayment(PaymentRequest request);

    /**
     * Estado actual de un pago en la pasarela. Si la consulta falla devuelve {@code PENDIENTE}:
     * quien llama no puede distinguir un pago pendiente de un fallo de consulta.
     */
    PaymentStatus getPaymentStatus(String externalId);

    boolean refundPayment(String externalId);

    /**
     * Referencia externa del pago en la pasarela (el id del pedido), o {@code null} si no se pudo consultar.
     */
    String getExternalReference(String paymentId);
}
