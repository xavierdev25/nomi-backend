package com.nomi.backend.application.payment;

import com.nomi.backend.application.payment.OrderPaymentSettlement.Outcome;
import com.nomi.backend.domain.exception.PaymentGatewayUnavailableException;
import com.nomi.backend.domain.model.order.Order;
import com.nomi.backend.domain.model.order.OrderStatus;
import com.nomi.backend.domain.model.payment.Payment;
import com.nomi.backend.domain.model.payment.PaymentStatus;
import com.nomi.backend.domain.port.out.BusinessMetricsPort;
import com.nomi.backend.domain.port.out.NotificationPort;
import com.nomi.backend.domain.port.out.OrderHistoryPort;
import com.nomi.backend.domain.port.out.OrderRepositoryPort;
import com.nomi.backend.domain.port.out.PaymentGatewayPort;
import com.nomi.backend.domain.port.out.PaymentGatewayPort.GatewayPayment;
import com.nomi.backend.domain.port.out.PaymentRepositoryPort;
import com.nomi.backend.domain.port.out.notification.PushNotificationPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Qué le pasa al pedido y al dinero cuando la pasarela informa un pago. Cubre los tres riesgos de
 * la auditoría: un pago pagado nunca se pierde, un pedido cancelado no se queda con el dinero y un
 * cobro duplicado se devuelve.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderPaymentSettlement - Aplicación de pagos a pedidos")
class OrderPaymentSettlementTest {

    private static final Long ORDER_ID = 3L;
    private static final Long USER_ID = 5L;

    @Mock private OrderRepositoryPort orderRepositoryPort;
    @Mock private PaymentRepositoryPort paymentRepositoryPort;
    @Mock private PaymentGatewayPort paymentGatewayPort;
    @Mock private OrderHistoryPort orderHistoryPort;
    @Mock private BusinessMetricsPort metricsPort;
    @Mock private NotificationPort notificationPort;
    @Mock private PushNotificationPort pushNotificationPort;

    @InjectMocks private OrderPaymentSettlement settlement;

    private static Payment record(PaymentStatus status, String gatewayPaymentId) {
        return Payment.builder()
                .id(7L).orderId(ORDER_ID).userId(USER_ID).amount(new BigDecimal("12.70"))
                .status(status).externalId("pref-1").gatewayPaymentId(gatewayPaymentId)
                .creadoEn(LocalDateTime.now()).actualizadoEn(LocalDateTime.now())
                .build();
    }

    private static GatewayPayment gateway(String id, PaymentStatus status) {
        return new GatewayPayment(id, status, String.valueOf(ORDER_ID), new BigDecimal("12.70"), null, null);
    }

    private static final String REFERENCE = "nomi-3-6f1c0f2e-6b1d-4a57-9c38-3f5e8a2d9b10";

    /** Checkout con referencia única, como los que se crean ahora. */
    private static Payment uniqueRecord(PaymentStatus status) {
        return Payment.builder()
                .id(7L).orderId(ORDER_ID).userId(USER_ID).amount(new BigDecimal("12.70"))
                .status(status).externalId("pref-1").externalReference(REFERENCE)
                .creadoEn(LocalDateTime.now()).actualizadoEn(LocalDateTime.now())
                .build();
    }

    private static Order order(OrderStatus status) {
        return Order.builder().id(ORDER_ID).userId(USER_ID).status(status).build();
    }

    private Payment savedPayment() {
        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepositoryPort).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("Encuentra el intento aprobado entre los intentos del checkout")
    void find_approved_payment() {
        when(paymentRepositoryPort.findByOrderId(ORDER_ID)).thenReturn(Optional.of(record(PaymentStatus.PENDIENTE, null)));
        when(paymentGatewayPort.findPaymentsByReference("3")).thenReturn(List.of(
                gateway("mp-1", PaymentStatus.RECHAZADO), gateway("mp-2", PaymentStatus.APROBADO)));

        assertEquals("mp-2", settlement.findApprovedPayment(ORDER_ID).orElseThrow().id());
    }

    @Test
    @DisplayName("Sin intentos aprobados no hay pago aprobado")
    void find_approved_payment_vacio() {
        when(paymentRepositoryPort.findByOrderId(ORDER_ID)).thenReturn(Optional.of(record(PaymentStatus.PENDIENTE, null)));
        when(paymentGatewayPort.findPaymentsByReference("3")).thenReturn(List.of(
                gateway("mp-1", PaymentStatus.RECHAZADO), gateway("mp-2", PaymentStatus.PENDIENTE)));

        assertTrue(settlement.findApprovedPayment(ORDER_ID).isEmpty());
    }

    @Test
    @DisplayName("Pago aprobado de un pedido pendiente: pasa a preparación y guarda el id del pago")
    void confirma_pedido_pendiente() {
        when(paymentRepositoryPort.findByOrderId(ORDER_ID)).thenReturn(Optional.of(record(PaymentStatus.PENDIENTE, null)));
        when(orderRepositoryPort.markPaidIfPending(eq(ORDER_ID), any())).thenReturn(true);

        Outcome outcome = settlement.settleApproved(ORDER_ID, gateway("mp-ok", PaymentStatus.APROBADO));

        assertEquals(Outcome.CONFIRMED, outcome);
        Payment saved = savedPayment();
        assertEquals(PaymentStatus.APROBADO, saved.getStatus());
        assertEquals("mp-ok", saved.getGatewayPaymentId());
        verify(orderHistoryPort).record(ORDER_ID, OrderStatus.PREPARANDO, null, "Pago aprobado en MercadoPago");
        verify(metricsPort).recordPaymentCompleted();
        verify(paymentGatewayPort, never()).refund(anyString());
    }

    @Test
    @DisplayName("Un fallo al notificar no deshace la confirmación")
    void confirma_aunque_falle_la_notificacion() {
        when(paymentRepositoryPort.findByOrderId(ORDER_ID)).thenReturn(Optional.of(record(PaymentStatus.PENDIENTE, null)));
        when(orderRepositoryPort.markPaidIfPending(eq(ORDER_ID), any())).thenReturn(true);
        doThrow(new RuntimeException("FCM caído")).when(pushNotificationPort).sendToUser(anyString(), anyString(), anyString());

        assertEquals(Outcome.CONFIRMED, settlement.settleApproved(ORDER_ID, gateway("mp-ok", PaymentStatus.APROBADO)));
    }

    @Test
    @DisplayName("Pago aprobado de un pedido ya cancelado: se reembolsa")
    void reembolsa_si_el_pedido_estaba_cancelado() {
        when(paymentRepositoryPort.findByOrderId(ORDER_ID)).thenReturn(Optional.of(record(PaymentStatus.PENDIENTE, null)));
        when(orderRepositoryPort.markPaidIfPending(eq(ORDER_ID), any())).thenReturn(false);
        when(orderRepositoryPort.findById(ORDER_ID)).thenReturn(Optional.of(order(OrderStatus.CANCELADO)));

        Outcome outcome = settlement.settleApproved(ORDER_ID, gateway("mp-ok", PaymentStatus.APROBADO));

        assertEquals(Outcome.REFUNDED, outcome);
        verify(paymentGatewayPort).refund("mp-ok");
        Payment saved = savedPayment();
        assertEquals(PaymentStatus.REEMBOLSADO, saved.getStatus());
        assertEquals("mp-ok", saved.getGatewayPaymentId());
        verify(orderHistoryPort, never()).record(anyLong(), any(), any(), anyString());
    }

    @Test
    @DisplayName("Si el reembolso falla, no se guarda nada y el error sube para reintentar")
    void reembolso_fallido_se_reintenta() {
        when(paymentRepositoryPort.findByOrderId(ORDER_ID)).thenReturn(Optional.of(record(PaymentStatus.PENDIENTE, null)));
        when(orderRepositoryPort.markPaidIfPending(eq(ORDER_ID), any())).thenReturn(false);
        when(orderRepositoryPort.findById(ORDER_ID)).thenReturn(Optional.of(order(OrderStatus.CANCELADO)));
        doThrow(new PaymentGatewayUnavailableException("timeout", null)).when(paymentGatewayPort).refund("mp-ok");

        assertThrows(PaymentGatewayUnavailableException.class,
                () -> settlement.settleApproved(ORDER_ID, gateway("mp-ok", PaymentStatus.APROBADO)));
        verify(paymentRepositoryPort, never()).save(any());
    }

    @Test
    @DisplayName("El mismo pago aprobado otra vez (webhook repetido) no hace nada")
    void aviso_repetido_es_idempotente() {
        when(paymentRepositoryPort.findByOrderId(ORDER_ID)).thenReturn(Optional.of(record(PaymentStatus.APROBADO, "mp-ok")));

        assertEquals(Outcome.ALREADY_CONFIRMED, settlement.settleApproved(ORDER_ID, gateway("mp-ok", PaymentStatus.APROBADO)));
        verify(orderRepositoryPort, never()).markPaidIfPending(anyLong(), any());
        verify(paymentGatewayPort, never()).refund(anyString());
        verify(paymentRepositoryPort, never()).save(any());
    }

    @Test
    @DisplayName("Un segundo pago aprobado del mismo pedido se reembolsa como duplicado")
    void reembolsa_pago_duplicado() {
        Payment aprobado = record(PaymentStatus.APROBADO, "mp-ok");
        when(paymentRepositoryPort.findByOrderId(ORDER_ID)).thenReturn(Optional.of(aprobado));

        Outcome outcome = settlement.settleApproved(ORDER_ID, gateway("mp-dup", PaymentStatus.APROBADO));

        assertEquals(Outcome.DUPLICATE_REFUNDED, outcome);
        verify(paymentGatewayPort).refund("mp-dup");
        verify(paymentGatewayPort, never()).refund("mp-ok");
        verify(paymentRepositoryPort, never()).save(any());
    }

    @Test
    @DisplayName("Un registro aprobado antiguo, sin id de pago, adopta el id en vez de reembolsar")
    void registro_antiguo_adopta_id() {
        when(paymentRepositoryPort.findByOrderId(ORDER_ID)).thenReturn(Optional.of(record(PaymentStatus.APROBADO, null)));

        Outcome outcome = settlement.settleApproved(ORDER_ID, gateway("mp-ok", PaymentStatus.APROBADO));

        assertEquals(Outcome.ALREADY_CONFIRMED, outcome);
        assertEquals("mp-ok", savedPayment().getGatewayPaymentId());
        verify(paymentGatewayPort, never()).refund(anyString());
    }

    @Test
    @DisplayName("Si el pedido ya avanzó sin reflejar el pago, solo se actualiza el registro")
    void pedido_avanzado_solo_actualiza_registro() {
        when(paymentRepositoryPort.findByOrderId(ORDER_ID)).thenReturn(Optional.of(record(PaymentStatus.PENDIENTE, null)));
        when(orderRepositoryPort.markPaidIfPending(eq(ORDER_ID), any())).thenReturn(false);
        when(orderRepositoryPort.findById(ORDER_ID)).thenReturn(Optional.of(order(OrderStatus.EN_CAMINO)));

        assertEquals(Outcome.ALREADY_CONFIRMED, settlement.settleApproved(ORDER_ID, gateway("mp-ok", PaymentStatus.APROBADO)));
        assertEquals(PaymentStatus.APROBADO, savedPayment().getStatus());
        verify(paymentGatewayPort, never()).refund(anyString());
    }

    @Test
    @DisplayName("Un pago aprobado sin registro local se ignora")
    void sin_registro_no_hace_nada() {
        when(paymentRepositoryPort.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());

        assertEquals(Outcome.NO_PAYMENT_RECORD, settlement.settleApproved(ORDER_ID, gateway("mp-ok", PaymentStatus.APROBADO)));
        verifyNoInteractions(orderRepositoryPort, paymentGatewayPort);
    }

    @Test
    @DisplayName("Un rechazo actualiza el registro y avisa, pero no cancela el pedido")
    void rechazo_no_cancela_el_pedido() {
        when(paymentRepositoryPort.findByOrderId(ORDER_ID)).thenReturn(Optional.of(record(PaymentStatus.PENDIENTE, null)));

        settlement.recordNotApproved(ORDER_ID, new GatewayPayment("mp-1", PaymentStatus.RECHAZADO,
                String.valueOf(ORDER_ID), new BigDecimal("12.70"), "cc_rejected_other_reason", null));

        Payment saved = savedPayment();
        assertEquals(PaymentStatus.RECHAZADO, saved.getStatus());
        assertEquals("cc_rejected_other_reason", saved.getFailureReason());
        verify(metricsPort).recordPaymentFailed();
        verify(notificationPort).notifyUser(eq(USER_ID), any());
        verifyNoInteractions(orderRepositoryPort);
    }

    @Test
    @DisplayName("Un pago pendiente o en revisión solo actualiza el registro")
    void pendiente_solo_actualiza_registro() {
        when(paymentRepositoryPort.findByOrderId(ORDER_ID)).thenReturn(Optional.of(record(PaymentStatus.RECHAZADO, "mp-1")));

        settlement.recordNotApproved(ORDER_ID, gateway("mp-2", PaymentStatus.PENDIENTE));

        assertEquals(PaymentStatus.PENDIENTE, savedPayment().getStatus());
        verify(metricsPort, never()).recordPaymentFailed();
        verifyNoInteractions(orderRepositoryPort);
    }

    @Test
    @DisplayName("Un rechazo tardío de otro intento no toca un pago ya aprobado")
    void rechazo_tardio_no_pisa_aprobado() {
        when(paymentRepositoryPort.findByOrderId(ORDER_ID)).thenReturn(Optional.of(record(PaymentStatus.APROBADO, "mp-ok")));

        settlement.recordNotApproved(ORDER_ID, gateway("mp-viejo", PaymentStatus.RECHAZADO));

        verify(paymentRepositoryPort, never()).save(any());
        verify(metricsPort, never()).recordPaymentFailed();
    }

    // Un pago antiguo con la misma referencia (otra base de datos u otro entorno que usó la misma
    // cuenta de MercadoPago) daba por pagado un pedido que nadie pagó.

    @Test
    @DisplayName("Checkout antiguo: un pago anterior al checkout no confirma el pedido")
    void pago_anterior_al_checkout_se_ignora() {
        when(paymentRepositoryPort.findByOrderId(ORDER_ID)).thenReturn(Optional.of(record(PaymentStatus.PENDIENTE, null)));
        GatewayPayment old = new GatewayPayment("mp-mayo", PaymentStatus.APROBADO, String.valueOf(ORDER_ID),
                new BigDecimal("12.70"), null, OffsetDateTime.now().minusMonths(4));

        assertEquals(Outcome.NOT_THIS_CHECKOUT, settlement.settleApproved(ORDER_ID, old));
        verify(orderRepositoryPort, never()).markPaidIfPending(anyLong(), any());
        verify(paymentGatewayPort, never()).refund(anyString());
        verify(paymentRepositoryPort, never()).save(any());
    }

    @Test
    @DisplayName("Checkout antiguo: un pago posterior al checkout sí lo confirma")
    void pago_posterior_al_checkout_confirma() {
        when(paymentRepositoryPort.findByOrderId(ORDER_ID)).thenReturn(Optional.of(record(PaymentStatus.PENDIENTE, null)));
        when(orderRepositoryPort.markPaidIfPending(eq(ORDER_ID), any())).thenReturn(true);
        GatewayPayment recent = new GatewayPayment("mp-ok", PaymentStatus.APROBADO, String.valueOf(ORDER_ID),
                new BigDecimal("12.70"), null, OffsetDateTime.now());

        assertEquals(Outcome.CONFIRMED, settlement.settleApproved(ORDER_ID, recent));
    }

    @Test
    @DisplayName("Un pago con otro monto no confirma el pedido")
    void monto_distinto_se_ignora() {
        when(paymentRepositoryPort.findByOrderId(ORDER_ID)).thenReturn(Optional.of(uniqueRecord(PaymentStatus.PENDIENTE)));
        GatewayPayment wrongAmount = new GatewayPayment("mp-x", PaymentStatus.APROBADO, REFERENCE,
                new BigDecimal("6.00"), null, OffsetDateTime.now());

        assertEquals(Outcome.NOT_THIS_CHECKOUT, settlement.settleApproved(ORDER_ID, wrongAmount));
        verify(orderRepositoryPort, never()).markPaidIfPending(anyLong(), any());
    }

    @Test
    @DisplayName("Con referencia única, un pago con la referencia antigua del pedido se ignora")
    void referencia_ajena_se_ignora() {
        when(paymentRepositoryPort.findByOrderId(ORDER_ID)).thenReturn(Optional.of(uniqueRecord(PaymentStatus.PENDIENTE)));

        assertEquals(Outcome.NOT_THIS_CHECKOUT, settlement.settleApproved(ORDER_ID, gateway("mp-viejo", PaymentStatus.APROBADO)));
        verify(orderRepositoryPort, never()).markPaidIfPending(anyLong(), any());
    }

    @Test
    @DisplayName("Un pago ajeno nunca se reembolsa como duplicado")
    void pago_ajeno_no_se_reembolsa_como_duplicado() {
        when(paymentRepositoryPort.findByOrderId(ORDER_ID)).thenReturn(Optional.of(record(PaymentStatus.APROBADO, "mp-ok")));
        GatewayPayment old = new GatewayPayment("mp-mayo", PaymentStatus.APROBADO, String.valueOf(ORDER_ID),
                new BigDecimal("6.00"), null, OffsetDateTime.now().minusMonths(4));

        assertEquals(Outcome.NOT_THIS_CHECKOUT, settlement.settleApproved(ORDER_ID, old));
        verify(paymentGatewayPort, never()).refund(anyString());
    }

    @Test
    @DisplayName("Busca por la referencia única y descarta los pagos ajenos")
    void find_approved_payment_descarta_ajenos() {
        when(paymentRepositoryPort.findByOrderId(ORDER_ID)).thenReturn(Optional.of(uniqueRecord(PaymentStatus.PENDIENTE)));
        when(paymentGatewayPort.findPaymentsByReference(REFERENCE)).thenReturn(List.of(
                new GatewayPayment("mp-x", PaymentStatus.APROBADO, REFERENCE, new BigDecimal("6.00"), null, null),
                new GatewayPayment("mp-ok", PaymentStatus.APROBADO, REFERENCE, new BigDecimal("12.70"), null, null)));

        assertEquals("mp-ok", settlement.findApprovedPayment(ORDER_ID).orElseThrow().id());
    }

    @Test
    @DisplayName("Un rechazo de otro checkout no toca el registro")
    void rechazo_ajeno_no_toca_el_registro() {
        when(paymentRepositoryPort.findByOrderId(ORDER_ID)).thenReturn(Optional.of(uniqueRecord(PaymentStatus.PENDIENTE)));

        settlement.recordNotApproved(ORDER_ID, gateway("mp-viejo", PaymentStatus.RECHAZADO));

        verify(paymentRepositoryPort, never()).save(any());
    }
}
