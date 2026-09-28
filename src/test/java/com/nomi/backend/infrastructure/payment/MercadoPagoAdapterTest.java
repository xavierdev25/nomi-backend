package com.nomi.backend.infrastructure.payment;

import com.mercadopago.client.preference.PreferencePaymentTypeRequest;
import com.mercadopago.client.preference.PreferenceRequest;
import com.nomi.backend.domain.model.payment.PaymentStatus;
import com.nomi.backend.domain.port.out.PaymentGatewayPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Traducción de los estados de MercadoPago. Lo que importa: solo {@code approved} confirma un
 * pedido, y un estado que aún puede cambiar nunca se toma como definitivo.
 */
@DisplayName("MercadoPagoAdapter - Estados de pago")
class MercadoPagoAdapterTest {

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "approved, APROBADO",
            "rejected, RECHAZADO",
            "cancelled, CANCELADO",
            "refunded, REEMBOLSADO",
            "charged_back, REEMBOLSADO",
            "pending, PENDIENTE",
            "in_process, PENDIENTE",
            "authorized, PENDIENTE",
            "in_mediation, PENDIENTE",
            "estado_nuevo, PENDIENTE"
    })
    void mapea_estados(String mercadoPago, PaymentStatus expected) {
        assertEquals(expected, MercadoPagoAdapter.mapStatus(mercadoPago));
    }

    @Test
    @DisplayName("Sin estado cuenta como pendiente")
    void sin_estado_es_pendiente() {
        assertEquals(PaymentStatus.PENDIENTE, MercadoPagoAdapter.mapStatus(null));
    }

    /** Sin {@code offset}, el SDK construía la consulta con un valor nulo y toda búsqueda fallaba. */
    @Test
    @DisplayName("La búsqueda de pagos de un checkout genera una consulta válida")
    void busqueda_por_referencia_es_valida() {
        Map<String, Object> parameters = MercadoPagoAdapter.searchByReference("nomi-12-abc").getParameters();

        assertEquals("nomi-12-abc", parameters.get("external_reference"));
        assertEquals(0, parameters.get("offset"));
        assertEquals(50, parameters.get("limit"));
        assertTrue(parameters.values().stream().allMatch(Objects::nonNull));
    }

    @Test
    @DisplayName("El checkout vence con el pedido, excluye medios diferidos y no lleva notification_url")
    void preferencia_del_pedido() {
        OffsetDateTime expiresAt = OffsetDateTime.parse("2026-09-27T12:09:02-05:00");
        PaymentGatewayPort.PaymentRequest request = new PaymentGatewayPort.PaymentRequest(
                14L, 5L, new BigDecimal("14.70"), "Pago Nomi - Orden #14",
                "https://api.nomi.test/api/payments/webhook", expiresAt, "nomi-14-abc");

        PreferenceRequest preference = MercadoPagoAdapter.preferenceFor(request);

        assertNull(preference.getNotificationUrl(), "sus avisos llegan con otra firma y se rechazarían");
        assertEquals("nomi-14-abc", preference.getExternalReference());
        assertEquals(Boolean.TRUE, preference.getExpires());
        assertEquals(expiresAt, preference.getExpirationDateTo());
        assertEquals(List.of("ticket", "atm"), preference.getPaymentMethods().getExcludedPaymentTypes().stream()
                .map(PreferencePaymentTypeRequest::getId).toList());
        assertEquals(0, new BigDecimal("14.70").compareTo(preference.getItems().getFirst().getUnitPrice()));
        assertEquals("PEN", preference.getItems().getFirst().getCurrencyId());
    }

    @Test
    @DisplayName("Un pedido sin plazo crea un checkout sin vencimiento")
    void preferencia_sin_plazo() {
        PaymentGatewayPort.PaymentRequest request = new PaymentGatewayPort.PaymentRequest(
                14L, 5L, new BigDecimal("14.70"), "Pago Nomi - Orden #14", null, null, "nomi-14-abc");

        assertNull(MercadoPagoAdapter.preferenceFor(request).getExpires());
    }

    @Test
    @DisplayName("Cerrar un checkout lo hace vencer ahora, sin tocar el resto de la preferencia")
    void cerrar_checkout() {
        OffsetDateTime now = OffsetDateTime.parse("2026-09-27T12:30:00-05:00");

        PreferenceRequest closing = MercadoPagoAdapter.closingRequest(now);

        assertEquals(Boolean.TRUE, closing.getExpires());
        assertEquals(now, closing.getExpirationDateTo());
        assertNull(closing.getItems(), "actualización parcial: no reemplaza los ítems");
        assertNull(closing.getExternalReference());
    }
}
