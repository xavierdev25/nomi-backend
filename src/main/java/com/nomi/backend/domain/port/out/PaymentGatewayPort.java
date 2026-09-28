package com.nomi.backend.domain.port.out;

import com.nomi.backend.domain.exception.PaymentGatewayUnavailableException;
import com.nomi.backend.domain.model.payment.PaymentStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Pasarela de pagos (MercadoPago).
 *
 * <p>Nuestro registro de pago guarda el id de la preferencia (el checkout), no el de cada pago:
 * en un mismo checkout el estudiante puede hacer varios intentos. Por eso el estado real de un
 * pedido se obtiene buscando en la pasarela los pagos con su referencia externa (el id del
 * pedido). Las consultas que fallan lanzan {@link PaymentGatewayUnavailableException}: nunca se
 * inventa un estado.
 */
public interface PaymentGatewayPort {

    /**
     * @param expiresAt hasta cuándo se puede pagar; el checkout deja de aceptar pagos después
     */
    /**
     * @param returnUrl         adónde vuelve el navegador tras pagar
     * @param expiresAt         hora a la que el checkout deja de aceptar pagos, o {@code null}
     * @param externalReference referencia única del checkout ({@code CheckoutReference})
     */
    record PaymentRequest(Long orderId, Long userId, BigDecimal amount, String description,
                          String returnUrl, OffsetDateTime expiresAt, String externalReference) {}

    record PaymentResponse(String externalId, String paymentUrl, PaymentStatus status) {}

    /**
     * Un pago en la pasarela.
     *
     * @param id                id del pago (no el de la preferencia)
     * @param externalReference referencia del checkout, tal como se envió al crearlo
     * @param statusDetail      motivo que da la pasarela (por ejemplo, el de un rechazo)
     * @param createdAt         cuándo se creó el pago en la pasarela, si lo informa
     */
    record GatewayPayment(String id, PaymentStatus status, String externalReference,
                          BigDecimal amount, String statusDetail, OffsetDateTime createdAt) {}

    /**
     * Crea el checkout y devuelve su id y su URL.
     *
     * @throws IllegalStateException si la pasarela rechaza la operación
     */
    PaymentResponse createPayment(PaymentRequest request);

    /**
     * Un pago por su id (el que llega en el webhook), o vacío si la pasarela no lo conoce: por
     * ejemplo, el aviso de prueba del panel de MercadoPago. Reintentar no lo haría aparecer.
     *
     * @throws PaymentGatewayUnavailableException si no se pudo consultar
     */
    Optional<GatewayPayment> getPayment(String paymentId);

    /**
     * Todos los pagos con una referencia externa: los intentos de un checkout.
     *
     * @throws PaymentGatewayUnavailableException si no se pudo consultar
     */
    List<GatewayPayment> findPaymentsByReference(String externalReference);

    /**
     * Cierra un checkout: desde ahora no acepta pagos. Los intentos ya en curso pueden aprobarse
     * igual; de esos se encarga el reembolso de pagos tardíos.
     *
     * @param checkoutId id de la preferencia ({@code Payment.externalId})
     * @throws PaymentGatewayUnavailableException si la pasarela no lo cerró
     */
    void closeCheckout(String checkoutId);

    /**
     * Devuelve el importe completo de un pago.
     *
     * @throws PaymentGatewayUnavailableException si la pasarela no lo hizo
     */
    void refund(String paymentId);
}
