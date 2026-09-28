package com.nomi.backend.domain.model.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cómo cambia el registro de pago con los avisos de la pasarela: un pago aprobado no se pierde
 * por un aviso tardío de otro intento, y un aviso repetido no cambia nada.
 */
@DisplayName("Payment - Estado según la pasarela")
class PaymentTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 27, 12, 0);

    private static Payment payment(PaymentStatus status, String gatewayPaymentId) {
        return Payment.builder()
                .id(7L).orderId(3L).userId(5L).amount(new BigDecimal("12.70"))
                .status(status).externalId("pref-1").gatewayPaymentId(gatewayPaymentId)
                .paymentUrl("https://mp/checkout").failureReason("anterior")
                .creadoEn(NOW.minusMinutes(5)).actualizadoEn(NOW.minusMinutes(5))
                .build();
    }

    @Test
    @DisplayName("Mientras no hay pago aprobado, cualquier intento actualiza el registro")
    void pendiente_acepta_cualquier_intento() {
        Payment rechazado = payment(PaymentStatus.PENDIENTE, null)
                .withGatewayStatus("mp-1", PaymentStatus.RECHAZADO, "cc_rejected_insufficient_amount", NOW);

        assertEquals(PaymentStatus.RECHAZADO, rechazado.getStatus());
        assertEquals("mp-1", rechazado.getGatewayPaymentId());
        assertEquals("cc_rejected_insufficient_amount", rechazado.getFailureReason());
        assertEquals(NOW, rechazado.getActualizadoEn());
        assertEquals("pref-1", rechazado.getExternalId());

        Payment reintento = rechazado.withGatewayStatus("mp-2", PaymentStatus.PENDIENTE, null, NOW);
        assertEquals(PaymentStatus.PENDIENTE, reintento.getStatus());
        assertEquals("mp-2", reintento.getGatewayPaymentId());
        assertEquals("cc_rejected_insufficient_amount", reintento.getFailureReason(),
                "sin motivo nuevo se conserva el anterior");
    }

    @Test
    @DisplayName("Un aviso tardío de otro intento no pisa el pago aprobado")
    void aprobado_ignora_otros_intentos() {
        Payment aprobado = payment(PaymentStatus.APROBADO, "mp-ok");

        assertSame(aprobado, aprobado.withGatewayStatus("mp-viejo", PaymentStatus.RECHAZADO, "x", NOW));
        assertSame(aprobado, aprobado.withGatewayStatus("mp-viejo", PaymentStatus.REEMBOLSADO, "x", NOW));
    }

    @Test
    @DisplayName("El pago aprobado solo puede pasar a reembolsado, y solo por su propio aviso")
    void aprobado_solo_pasa_a_reembolsado() {
        Payment aprobado = payment(PaymentStatus.APROBADO, "mp-ok");

        assertSame(aprobado, aprobado.withGatewayStatus("mp-ok", PaymentStatus.RECHAZADO, null, NOW));
        assertSame(aprobado, aprobado.withGatewayStatus("mp-ok", PaymentStatus.PENDIENTE, null, NOW));
        assertEquals(PaymentStatus.REEMBOLSADO,
                aprobado.withGatewayStatus("mp-ok", PaymentStatus.REEMBOLSADO, "chargeback", NOW).getStatus());
    }

    @Test
    @DisplayName("Un aviso repetido del mismo pago y estado no cambia nada")
    void aviso_repetido_es_idempotente() {
        Payment aprobado = payment(PaymentStatus.APROBADO, "mp-ok");
        assertSame(aprobado, aprobado.withGatewayStatus("mp-ok", PaymentStatus.APROBADO, null, NOW));

        Payment rechazado = payment(PaymentStatus.RECHAZADO, "mp-1");
        assertSame(rechazado, rechazado.withGatewayStatus("mp-1", PaymentStatus.RECHAZADO, null, NOW));
    }

    @Test
    @DisplayName("Un registro aprobado sin id de pago (datos antiguos) adopta el id del aviso")
    void aprobado_antiguo_adopta_id() {
        Payment antiguo = payment(PaymentStatus.APROBADO, null);

        Payment completado = antiguo.withGatewayStatus("mp-ok", PaymentStatus.APROBADO, null, NOW);

        assertEquals(PaymentStatus.APROBADO, completado.getStatus());
        assertEquals("mp-ok", completado.getGatewayPaymentId());
    }

    @Test
    @DisplayName("Un reembolso es definitivo")
    void reembolsado_es_final() {
        Payment reembolsado = payment(PaymentStatus.REEMBOLSADO, "mp-ok");

        assertSame(reembolsado, reembolsado.withGatewayStatus("mp-ok", PaymentStatus.APROBADO, null, NOW));
        assertFalse(PaymentStatus.REEMBOLSADO.canBeReplacedBy(PaymentStatus.APROBADO));
        assertTrue(PaymentStatus.RECHAZADO.canBeReplacedBy(PaymentStatus.APROBADO));
        assertTrue(PaymentStatus.PENDIENTE.canBeReplacedBy(PaymentStatus.CANCELADO));
    }

    @Test
    @DisplayName("Los checkouts nuevos se buscan por su referencia única; los antiguos, por el pedido")
    void gateway_reference() {
        assertEquals("3", payment(PaymentStatus.PENDIENTE, null).gatewayReference());

        Payment unique = Payment.builder().orderId(3L).status(PaymentStatus.PENDIENTE)
                .externalReference("nomi-3-abc").build();
        assertEquals("nomi-3-abc", unique.gatewayReference());
        assertEquals("nomi-3-abc",
                unique.withGatewayStatus("mp-1", PaymentStatus.RECHAZADO, null, NOW).getExternalReference(),
                "la referencia se conserva al actualizar el estado");
    }

    @Test
    @DisplayName("Solo aprobado y reembolsado cuentan como pago resuelto")
    void is_settled() {
        assertTrue(payment(PaymentStatus.APROBADO, "a").isSettled());
        assertTrue(payment(PaymentStatus.REEMBOLSADO, "a").isSettled());
        assertFalse(payment(PaymentStatus.PENDIENTE, null).isSettled());
        assertFalse(payment(PaymentStatus.RECHAZADO, "a").isSettled());
        assertFalse(payment(PaymentStatus.CANCELADO, "a").isSettled());
    }
}
