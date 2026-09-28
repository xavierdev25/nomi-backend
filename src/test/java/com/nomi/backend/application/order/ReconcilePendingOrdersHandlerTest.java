package com.nomi.backend.application.order;

import com.nomi.backend.application.payment.OrderPaymentSettlement;
import com.nomi.backend.domain.exception.PaymentGatewayUnavailableException;
import com.nomi.backend.domain.model.order.Order;
import com.nomi.backend.domain.model.order.OrderItem;
import com.nomi.backend.domain.model.order.OrderStatus;
import com.nomi.backend.domain.model.payment.Payment;
import com.nomi.backend.domain.model.payment.PaymentStatus;
import com.nomi.backend.domain.port.in.order.ReconcilePendingOrdersUseCase.Result;
import com.nomi.backend.domain.port.out.BusinessMetricsPort;
import com.nomi.backend.domain.port.out.NotificationPort;
import com.nomi.backend.domain.port.out.OrderHistoryPort;
import com.nomi.backend.domain.port.out.OrderRepositoryPort;
import com.nomi.backend.domain.port.out.PaymentGatewayPort;
import com.nomi.backend.domain.port.out.PaymentGatewayPort.GatewayPayment;
import com.nomi.backend.domain.port.out.PaymentRepositoryPort;
import com.nomi.backend.domain.port.out.ProductRepositoryPort;
import com.nomi.backend.domain.port.out.notification.PushNotificationPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * Caducidad de pedidos sin pagar y conciliación con MercadoPago: un pedido vencido se cancela y
 * devuelve su stock, salvo que en realidad esté pagado (webhook retrasado o perdido).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReconcilePendingOrdersHandler - Caducidad y conciliación")
class ReconcilePendingOrdersHandlerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 27, 12, 0);

    @Mock private OrderRepositoryPort orderRepositoryPort;
    @Mock private PaymentRepositoryPort paymentRepositoryPort;
    @Mock private OrderPaymentSettlement settlement;
    @Mock private ProductRepositoryPort productRepositoryPort;
    @Mock private OrderHistoryPort orderHistoryPort;
    @Mock private BusinessMetricsPort metricsPort;
    @Mock private NotificationPort notificationPort;
    @Mock private PushNotificationPort pushNotificationPort;
    @Mock private PaymentGatewayPort paymentGatewayPort;

    private ReconcilePendingOrdersHandler handler;

    @BeforeEach
    void setUp() {
        PendingOrderCanceller canceller = new PendingOrderCanceller(orderRepositoryPort, productRepositoryPort,
                orderHistoryPort, metricsPort, notificationPort, pushNotificationPort, paymentRepositoryPort,
                paymentGatewayPort);
        handler = new ReconcilePendingOrdersHandler(orderRepositoryPort, paymentRepositoryPort, settlement, canceller);
    }

    private static Order order(long id, OrderStatus status, LocalDateTime pagoExpiraEn) {
        return Order.builder()
                .id(id).userId(5L).storeId(1L).status(status).pagoExpiraEn(pagoExpiraEn)
                .items(List.of(OrderItem.builder().productId(10L).cantidad(2).build()))
                .build();
    }

    private static Payment checkout(long orderId) {
        return Payment.builder().id(1L).orderId(orderId).status(PaymentStatus.PENDIENTE).build();
    }

    @Test
    @DisplayName("Revisa los vencidos y los que tienen checkout; ignora los recientes sin checkout")
    void selecciona_pedidos_a_revisar() {
        when(orderRepositoryPort.findByStatus(OrderStatus.PENDIENTE)).thenReturn(List.of(
                order(9L, OrderStatus.PENDIENTE, NOW.minusMinutes(1)),
                order(4L, OrderStatus.PENDIENTE, NOW.plusMinutes(5)),
                order(6L, OrderStatus.PENDIENTE, NOW.plusMinutes(5)),
                order(2L, OrderStatus.PENDIENTE, null)));
        when(paymentRepositoryPort.findByOrderId(4L)).thenReturn(Optional.of(checkout(4L)));
        when(paymentRepositoryPort.findByOrderId(6L)).thenReturn(Optional.empty());
        when(paymentRepositoryPort.findByOrderId(2L)).thenReturn(Optional.empty());

        assertEquals(List.of(4L, 9L), handler.findOrdersToReconcile(NOW, 10));
        assertEquals(List.of(4L), handler.findOrdersToReconcile(NOW, 1));
    }

    @Test
    @DisplayName("Un pedido vencido pero pagado se confirma, no se cancela")
    void vencido_pero_pagado_se_confirma() {
        GatewayPayment approved = new GatewayPayment("mp-ok", PaymentStatus.APROBADO, "3", new BigDecimal("12.70"), null, null);
        when(orderRepositoryPort.findByIdWithItems(3L)).thenReturn(Optional.of(order(3L, OrderStatus.PENDIENTE, NOW.minusMinutes(1))));
        when(paymentRepositoryPort.findByOrderId(3L)).thenReturn(Optional.of(checkout(3L)));
        when(settlement.findApprovedPayment(3L)).thenReturn(Optional.of(approved));

        assertEquals(Result.PAID, handler.reconcile(3L, NOW));
        verify(settlement).settleApproved(3L, approved);
        verify(orderRepositoryPort, never()).cancelIfPending(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("Un pedido vencido sin pago se cancela como sistema y devuelve el stock")
    void vencido_sin_pago_caduca() {
        when(orderRepositoryPort.findByIdWithItems(3L)).thenReturn(Optional.of(order(3L, OrderStatus.PENDIENTE, NOW.minusMinutes(1))));
        when(paymentRepositoryPort.findByOrderId(3L)).thenReturn(Optional.of(checkout(3L)));
        when(settlement.findApprovedPayment(3L)).thenReturn(Optional.empty());
        when(orderRepositoryPort.cancelIfPending(eq(3L), eq(ReconcilePendingOrdersHandler.EXPIRED_REASON), isNull(), any()))
                .thenReturn(true);

        assertEquals(Result.EXPIRED, handler.reconcile(3L, NOW));
        verify(productRepositoryPort).incrementStock(10L, 2);
        verify(orderHistoryPort).record(3L, OrderStatus.CANCELADO, null, ReconcilePendingOrdersHandler.EXPIRED_REASON);
        verifyNoInteractions(paymentGatewayPort); // el checkout venció con el pedido: no hay que cerrarlo
    }

    @Test
    @DisplayName("Un pedido vencido que nunca abrió el checkout caduca sin consultar MercadoPago")
    void vencido_sin_checkout_no_consulta_gateway() {
        when(orderRepositoryPort.findByIdWithItems(3L)).thenReturn(Optional.of(order(3L, OrderStatus.PENDIENTE, NOW.minusMinutes(1))));
        when(paymentRepositoryPort.findByOrderId(3L)).thenReturn(Optional.empty());
        when(orderRepositoryPort.cancelIfPending(eq(3L), any(), isNull(), any())).thenReturn(true);

        assertEquals(Result.EXPIRED, handler.reconcile(3L, NOW));
        verifyNoInteractions(settlement);
    }

    @Test
    @DisplayName("Un pedido con checkout aún en plazo sigue pendiente")
    void en_plazo_sigue_pendiente() {
        when(orderRepositoryPort.findByIdWithItems(3L)).thenReturn(Optional.of(order(3L, OrderStatus.PENDIENTE, NOW.plusMinutes(5))));
        when(paymentRepositoryPort.findByOrderId(3L)).thenReturn(Optional.of(checkout(3L)));
        when(settlement.findApprovedPayment(3L)).thenReturn(Optional.empty());

        assertEquals(Result.STILL_PENDING, handler.reconcile(3L, NOW));
        verify(orderRepositoryPort, never()).cancelIfPending(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("Si MercadoPago no responde, el pedido no se cancela y se reintenta después")
    void gateway_caido_no_cancela() {
        when(orderRepositoryPort.findByIdWithItems(3L)).thenReturn(Optional.of(order(3L, OrderStatus.PENDIENTE, NOW.minusMinutes(1))));
        when(paymentRepositoryPort.findByOrderId(3L)).thenReturn(Optional.of(checkout(3L)));
        when(settlement.findApprovedPayment(3L)).thenThrow(new PaymentGatewayUnavailableException("timeout", null));

        assertThrows(PaymentGatewayUnavailableException.class, () -> handler.reconcile(3L, NOW));
        verify(orderRepositoryPort, never()).cancelIfPending(anyLong(), any(), any(), any());
        verify(productRepositoryPort, never()).incrementStock(anyLong(), anyInt());
    }

    @Test
    @DisplayName("Un pedido que ya no está pendiente se salta")
    void no_pendiente_se_salta() {
        when(orderRepositoryPort.findByIdWithItems(3L)).thenReturn(Optional.of(order(3L, OrderStatus.PREPARANDO, NOW.minusMinutes(1))));

        assertEquals(Result.SKIPPED, handler.reconcile(3L, NOW));
        verifyNoInteractions(settlement, paymentRepositoryPort);
    }

    @Test
    @DisplayName("Si el webhook lo confirmó en paralelo, la caducidad no devuelve el stock")
    void carrera_con_el_webhook() {
        when(orderRepositoryPort.findByIdWithItems(3L)).thenReturn(Optional.of(order(3L, OrderStatus.PENDIENTE, NOW.minusMinutes(1))));
        when(paymentRepositoryPort.findByOrderId(3L)).thenReturn(Optional.empty());
        when(orderRepositoryPort.cancelIfPending(eq(3L), any(), isNull(), any())).thenReturn(false);

        assertEquals(Result.SKIPPED, handler.reconcile(3L, NOW));
        verify(productRepositoryPort, never()).incrementStock(anyLong(), anyInt());
    }
}
