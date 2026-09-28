package com.nomi.backend.infrastructure.payment;

import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.payment.PaymentRefundClient;
import com.mercadopago.client.preference.PreferenceBackUrlsRequest;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceItemRequest;
import com.mercadopago.client.preference.PreferencePaymentMethodsRequest;
import com.mercadopago.client.preference.PreferencePaymentTypeRequest;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.net.MPResultsResourcesPage;
import com.mercadopago.net.MPSearchRequest;
import com.mercadopago.resources.payment.Payment;
import com.mercadopago.resources.preference.Preference;
import com.nomi.backend.domain.exception.PaymentGatewayUnavailableException;
import com.nomi.backend.domain.model.payment.PaymentStatus;
import com.nomi.backend.domain.port.out.PaymentGatewayPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Integración con MercadoPago Checkout Pro: crea el checkout de un pedido, consulta pagos y
 * reembolsa.
 *
 * <p>El checkout caduca a la misma hora que el plazo de pago del pedido y excluye los medios de
 * pago diferidos (efectivo en agentes, cajeros): tardan horas en confirmarse y el pedido caduca
 * antes. Cualquier fallo al consultar o reembolsar lanza {@link PaymentGatewayUnavailableException}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MercadoPagoAdapter implements PaymentGatewayPort {

    /** Tipos de pago de MercadoPago que se confirman horas o días después. */
    private static final List<String> DEFERRED_PAYMENT_TYPES = List.of("ticket", "atm");

    private final MercadoPagoConfiguration mercadoPagoConfig;

    @Override
    public PaymentResponse createPayment(PaymentRequest request) {
        try {
            Preference preference = new PreferenceClient().create(preferenceFor(request));
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
    public Optional<GatewayPayment> getPayment(String paymentId) {
        try {
            return Optional.of(toGatewayPayment(new PaymentClient().get(Long.parseLong(paymentId))));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Id de pago inválido: " + paymentId);
        } catch (MPApiException e) {
            if (e.getStatusCode() == 404) {
                return Optional.empty();
            }
            log.warn("No se pudo consultar el pago {} en MercadoPago: status={}", paymentId, e.getStatusCode());
            throw new PaymentGatewayUnavailableException("No se pudo consultar el pago en MercadoPago", e);
        } catch (Exception e) {
            log.warn("No se pudo consultar el pago {} en MercadoPago: {}", paymentId, e.getMessage());
            throw new PaymentGatewayUnavailableException("No se pudo consultar el pago en MercadoPago", e);
        }
    }

    @Override
    public List<GatewayPayment> findPaymentsByReference(String externalReference) {
        try {
            MPResultsResourcesPage<Payment> page = new PaymentClient().search(searchByReference(externalReference));
            List<Payment> results = page.getResults() != null ? page.getResults() : List.of();
            return results.stream().map(MercadoPagoAdapter::toGatewayPayment).toList();
        } catch (Exception e) {
            log.warn("No se pudieron buscar los pagos del checkout {} en MercadoPago: {}", externalReference, e.getMessage());
            throw new PaymentGatewayUnavailableException("No se pudo consultar el pago en MercadoPago", e);
        }
    }

    @Override
    public void closeCheckout(String checkoutId) {
        try {
            new PreferenceClient().update(checkoutId, closingRequest(OffsetDateTime.now()));
            log.info("Checkout {} cerrado en MercadoPago", checkoutId);
        } catch (Exception e) {
            log.warn("No se pudo cerrar el checkout {} en MercadoPago: {}", checkoutId, describe(e));
            throw new PaymentGatewayUnavailableException("No se pudo cerrar el checkout en MercadoPago", e);
        }
    }

    /** Actualización parcial que hace vencer la preferencia en {@code now}. */
    static PreferenceRequest closingRequest(OffsetDateTime now) {
        return PreferenceRequest.builder()
                .expires(true)
                .expirationDateTo(now)
                .build();
    }

    @Override
    public void refund(String paymentId) {
        try {
            new PaymentRefundClient().refund(Long.parseLong(paymentId));
            log.info("Pago {} reembolsado en MercadoPago", paymentId);
        } catch (Exception e) {
            log.error("No se pudo reembolsar el pago {} en MercadoPago: {}", paymentId, describe(e));
            throw new PaymentGatewayUnavailableException("No se pudo reembolsar el pago en MercadoPago", e);
        }
    }

    /**
     * Preferencia (checkout) de un pedido.
     *
     * <p>No lleva {@code notification_url}: MercadoPago firma los avisos enviados a esa URL con una
     * clave distinta de la del panel, así que nunca pasan la verificación de firma y solo generan
     * reintentos rechazados (comprobado en el sandbox). El webhook se configura en el panel de
     * MercadoPago de cada entorno, y la conciliación cubre los avisos que no lleguen.
     *
     * <p>Las URLs de retorno apuntan al propio webhook: tras pagar, el navegador no vuelve a la app
     * (ver la auditoría técnica, M8).
     */
    static PreferenceRequest preferenceFor(PaymentRequest request) {
        PreferenceItemRequest item = PreferenceItemRequest.builder()
                .title(request.description())
                .quantity(1)
                .currencyId("PEN")
                .unitPrice(request.amount())
                .build();

        PreferenceBackUrlsRequest backUrls = PreferenceBackUrlsRequest.builder()
                .success(request.returnUrl())
                .failure(request.returnUrl())
                .pending(request.returnUrl())
                .build();

        PreferencePaymentMethodsRequest paymentMethods = PreferencePaymentMethodsRequest.builder()
                .excludedPaymentTypes(DEFERRED_PAYMENT_TYPES.stream()
                        .map(type -> PreferencePaymentTypeRequest.builder().id(type).build())
                        .toList())
                .build();

        PreferenceRequest.PreferenceRequestBuilder preference = PreferenceRequest.builder()
                .items(List.of(item))
                .backUrls(backUrls)
                .paymentMethods(paymentMethods)
                .externalReference(request.externalReference());
        if (request.expiresAt() != null) {
            preference.expires(true).expirationDateTo(request.expiresAt());
        }
        return preference.build();
    }

    /**
     * Búsqueda de los pagos de un checkout. El {@code offset} es obligatorio aunque sea 0: el SDK lo
     * añade a la consulta aunque falte y, con {@code null}, falla al construir la URL.
     */
    static MPSearchRequest searchByReference(String externalReference) {
        return MPSearchRequest.builder()
                .filters(Map.of("external_reference", externalReference))
                .limit(50)
                .offset(0)
                .build();
    }

    /** El mensaje de {@code MPApiException} es genérico ("Api error"); el motivo está en el código y el cuerpo. */
    private static String describe(Exception e) {
        if (e instanceof MPApiException api) {
            String body = api.getApiResponse() != null ? api.getApiResponse().getContent() : null;
            return "status=" + api.getStatusCode() + " body=" + body;
        }
        return e.getMessage();
    }

    private static GatewayPayment toGatewayPayment(Payment payment) {
        return new GatewayPayment(
                String.valueOf(payment.getId()),
                mapStatus(payment.getStatus()),
                payment.getExternalReference(),
                payment.getTransactionAmount(),
                payment.getStatusDetail(),
                payment.getDateCreated()
        );
    }

    /**
     * Estados de MercadoPago a los nuestros. Los que todavía pueden cambiar ({@code pending},
     * {@code in_process}, {@code authorized}, {@code in_mediation}) cuentan como pendientes; los
     * devueltos o contracargados, como reembolsados.
     */
    static PaymentStatus mapStatus(String mercadoPagoStatus) {
        if (mercadoPagoStatus == null) {
            return PaymentStatus.PENDIENTE;
        }
        return switch (mercadoPagoStatus) {
            case "approved" -> PaymentStatus.APROBADO;
            case "rejected" -> PaymentStatus.RECHAZADO;
            case "cancelled" -> PaymentStatus.CANCELADO;
            case "refunded", "charged_back" -> PaymentStatus.REEMBOLSADO;
            default -> PaymentStatus.PENDIENTE;
        };
    }
}
