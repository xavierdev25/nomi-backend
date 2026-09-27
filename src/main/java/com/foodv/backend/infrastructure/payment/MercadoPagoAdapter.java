package com.foodv.backend.infrastructure.payment;

import com.foodv.backend.domain.model.payment.PaymentStatus;
import com.foodv.backend.domain.port.out.PaymentGatewayPort;
import com.mercadopago.client.preference.PreferenceBackUrlsRequest;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceItemRequest;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.resources.preference.Preference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Integración con MercadoPago: crea preferencias de pago, consulta su estado y reembolsa.
 * {@code refundPayment} existe pero ningún flujo lo invoca todavía.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MercadoPagoAdapter implements PaymentGatewayPort {

    private final MercadoPagoConfiguration mercadoPagoConfig;

    @Override
    public String getExternalReference(String paymentId) {
        try {
            com.mercadopago.client.payment.PaymentClient paymentClient =
                    new com.mercadopago.client.payment.PaymentClient();
            com.mercadopago.resources.payment.Payment payment =
                    paymentClient.get(Long.parseLong(paymentId));
            return payment.getExternalReference();
        } catch (Exception e) {
            log.warn("No se pudo obtener external_reference para paymentId={}: {}", paymentId, e.getMessage());
            return null;
        }
    }

    @Override
    public PaymentResponse createPayment(PaymentRequest request) {
        try {
            PreferenceClient preferenceClient = new PreferenceClient();

            PreferenceItemRequest item = PreferenceItemRequest.builder()
                    .title(request.description())
                    .quantity(1)
                    .currencyId("PEN")
                    .unitPrice(request.amount())
                    .build();

            // Las URLs de retorno apuntan al propio webhook: tras pagar, el navegador no vuelve a la app
            // (ver la auditoría técnica, M8).
            PreferenceBackUrlsRequest backUrls = PreferenceBackUrlsRequest.builder()
                    .success(request.notificationUrl())
                    .failure(request.notificationUrl())
                    .pending(request.notificationUrl())
                    .build();

            PreferenceRequest preferenceRequest = PreferenceRequest.builder()
                    .items(List.of(item))
                    .backUrls(backUrls)
                    // MercadoPago solo acepta URLs de notificación https; en desarrollo se omite.
                    .notificationUrl(
                            (request.notificationUrl() != null && request.notificationUrl().startsWith("https://"))
                                    ? request.notificationUrl()
                                    : null
                    )
                    .externalReference(request.orderId().toString())
                    .build();

            Preference preference = preferenceClient.create(preferenceRequest);

            return new PaymentResponse(preference.getId(), preference.getInitPoint(), PaymentStatus.PENDIENTE);
        } catch (MPApiException e) {
            log.error("MercadoPago API error para orden {}: status={} | body={}",
                    request.orderId(),
                    e.getStatusCode(),
                    e.getApiResponse() != null ? e.getApiResponse().getContent() : "sin body");
            throw new IllegalStateException("No fue posible crear el pago. Inténtalo más tarde.");
        } catch (Exception e) {
            log.error("Error inesperado creando pago para orden {}: {} | tipo: {}",
                    request.orderId(),
                    e.getMessage(),
                    e.getClass().getName());
            throw new IllegalStateException("No fue posible crear el pago. Inténtalo más tarde.");
        }
    }

    @Override
    public PaymentStatus getPaymentStatus(String externalId) {
        try {
            com.mercadopago.client.payment.PaymentClient paymentClient =
                    new com.mercadopago.client.payment.PaymentClient();
            com.mercadopago.resources.payment.Payment payment = paymentClient.get(Long.parseLong(externalId));

            return switch (payment.getStatus()) {
                case "approved" -> PaymentStatus.APROBADO;
                case "rejected" -> PaymentStatus.RECHAZADO;
                case "cancelled" -> PaymentStatus.CANCELADO;
                default -> PaymentStatus.PENDIENTE;
            };
        } catch (Exception e) {
            log.warn("No se pudo consultar status MercadoPago para id {}: {}", externalId, e.getMessage());
            return PaymentStatus.PENDIENTE;
        }
    }

    @Override
    public boolean refundPayment(String externalId) {
        try {
            com.mercadopago.client.payment.PaymentRefundClient refundClient =
                    new com.mercadopago.client.payment.PaymentRefundClient();
            refundClient.refund(Long.parseLong(externalId));
            return true;
        } catch (Exception e) {
            log.error("Refund MercadoPago falló para id {}: {}", externalId, e.getMessage());
            return false;
        }
    }
}
