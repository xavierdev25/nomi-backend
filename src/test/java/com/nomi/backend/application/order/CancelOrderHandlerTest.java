package com.nomi.backend.application.order;

import com.nomi.backend.application.payment.OrderPaymentSettlement;
import com.nomi.backend.domain.exception.OrderAlreadyPaidException;
import com.nomi.backend.domain.exception.PaymentGatewayUnavailableException;
import com.nomi.backend.domain.model.order.Order;
import com.nomi.backend.domain.model.order.OrderItem;
import com.nomi.backend.domain.model.order.OrderStatus;
import com.nomi.backend.domain.model.payment.Payment;
import com.nomi.backend.domain.model.payment.PaymentStatus;
import com.nomi.backend.domain.port.in.order.CancelOrderUseCase.CancelOrderCommand;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Cancelación de un pedido pendiente. Antes de cancelar se consulta si ya está pagado: cancelar un
 * pedido pagado dejaría al estudiante sin pedido y sin su dinero.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CancelOrderHandler - Cancelación de pedidos")
class CancelOrderHandlerTest {

    @Mock private OrderRepositoryPort orderRepositoryPort;
    @Mock private PaymentRepositoryPort paymentRepositoryPort;
    @Mock private OrderPaymentSettlement settlement;
    @Mock private ProductRepositoryPort productRepositoryPort;
    @Mock private OrderHistoryPort orderHistoryPort;
    @Mock private BusinessMetricsPort metricsPort;
    @Mock private NotificationPort notificationPort;
    @Mock private PushNotificationPort pushNotificationPort;
    @Mock private PaymentGatewayPort paymentGatewayPort;

    private CancelOrderHandler handler;

    @BeforeEach
    void setUp() {
        // El cancelador es real: lo que importa es que devuelva el stock solo si ganó la cancelación.
        PendingOrderCanceller canceller = new PendingOrderCanceller(orderRepositoryPort, productRepositoryPort,
                orderHistoryPort, metricsPort, notificationPort, pushNotificationPort, paymentRepositoryPort,
                paymentGatewayPort);
        handler = new CancelOrderHandler(orderRepositoryPort, paymentRepositoryPort, settlement, canceller);
    }

    private static Order order(OrderStatus status) {
        return Order.builder()
                .id(3L).userId(5L).storeId(1L).status(status)
                .items(List.of(
                        OrderItem.builder().productId(10L).cantidad(2).productPrecio(BigDecimal.ONE).build(),
                        OrderItem.builder().productId(11L).cantidad(1).productPrecio(BigDecimal.ONE).build()))
                .pagoExpiraEn(LocalDateTime.now().plusMinutes(10))
                .build();
    }

    private static Payment checkout() {
        return Payment.builder().id(1L).orderId(3L).externalId("pref-1").status(PaymentStatus.PENDIENTE).build();
    }

    private static CancelOrderCommand command() {
        return new CancelOrderCommand(3L, 5L, "Me equivoqué de tienda");
    }

    @Test
    @DisplayName("Sin checkout: cancela y devuelve el stock de cada línea")
    void cancela_sin_checkout() {
        when(orderRepositoryPort.findByIdWithItems(3L))
                .thenReturn(Optional.of(order(OrderStatus.PENDIENTE)), Optional.of(order(OrderStatus.CANCELADO)));
        when(paymentRepositoryPort.findByOrderId(3L)).thenReturn(Optional.empty());
        when(orderRepositoryPort.cancelIfPending(eq(3L), eq("Me equivoqué de tienda"), eq(5L), any())).thenReturn(true);

        Order result = handler.execute(command());

        assertEquals(OrderStatus.CANCELADO, result.getStatus());
        verify(productRepositoryPort).incrementStock(10L, 2);
        verify(productRepositoryPort).incrementStock(11L, 1);
        verifyNoInteractions(paymentGatewayPort);
        verify(orderHistoryPort).record(3L, OrderStatus.CANCELADO, 5L, "Me equivoqué de tienda");
        verify(metricsPort).recordOrderCancelled();
        verifyNoInteractions(settlement);
    }

    @Test
    @DisplayName("Con checkout sin pago aprobado: consulta MercadoPago, cancela y cierra el checkout")
    void cancela_con_checkout_sin_pagar() {
        when(orderRepositoryPort.findByIdWithItems(3L))
                .thenReturn(Optional.of(order(OrderStatus.PENDIENTE)), Optional.of(order(OrderStatus.CANCELADO)));
        when(paymentRepositoryPort.findByOrderId(3L)).thenReturn(Optional.of(checkout()));
        when(settlement.findApprovedPayment(3L)).thenReturn(Optional.empty());
        when(orderRepositoryPort.cancelIfPending(eq(3L), anyString(), eq(5L), any())).thenReturn(true);

        assertEquals(OrderStatus.CANCELADO, handler.execute(command()).getStatus());
        verify(settlement, never()).settleApproved(anyLong(), any());
        verify(paymentGatewayPort).closeCheckout("pref-1");
    }

    @Test
    @DisplayName("Si MercadoPago no cierra el checkout, el pedido queda cancelado igual")
    void fallo_al_cerrar_checkout_no_impide_cancelar() {
        when(orderRepositoryPort.findByIdWithItems(3L))
                .thenReturn(Optional.of(order(OrderStatus.PENDIENTE)), Optional.of(order(OrderStatus.CANCELADO)));
        when(paymentRepositoryPort.findByOrderId(3L)).thenReturn(Optional.of(checkout()));
        when(settlement.findApprovedPayment(3L)).thenReturn(Optional.empty());
        when(orderRepositoryPort.cancelIfPending(eq(3L), anyString(), eq(5L), any())).thenReturn(true);
        doThrow(new PaymentGatewayUnavailableException("timeout", null)).when(paymentGatewayPort).closeCheckout("pref-1");

        assertEquals(OrderStatus.CANCELADO, handler.execute(command()).getStatus());
        verify(productRepositoryPort).incrementStock(10L, 2);
    }

    @Test
    @DisplayName("Si ya estaba pagado, confirma el pedido y no lo cancela")
    void no_cancela_pedido_pagado() {
        GatewayPayment approved = new GatewayPayment("mp-ok", PaymentStatus.APROBADO, "3", new BigDecimal("12.70"), null, null);
        when(orderRepositoryPort.findByIdWithItems(3L)).thenReturn(Optional.of(order(OrderStatus.PENDIENTE)));
        when(paymentRepositoryPort.findByOrderId(3L)).thenReturn(Optional.of(
                Payment.builder().id(1L).orderId(3L).status(PaymentStatus.PENDIENTE).build()));
        when(settlement.findApprovedPayment(3L)).thenReturn(Optional.of(approved));

        assertThrows(OrderAlreadyPaidException.class, () -> handler.execute(command()));

        verify(settlement).settleApproved(3L, approved);
        verify(orderRepositoryPort, never()).cancelIfPending(anyLong(), any(), any(), any());
        verify(productRepositoryPort, never()).incrementStock(anyLong(), anyInt());
    }

    @Test
    @DisplayName("Si MercadoPago no responde, no cancela a ciegas")
    void no_cancela_si_gateway_no_responde() {
        when(orderRepositoryPort.findByIdWithItems(3L)).thenReturn(Optional.of(order(OrderStatus.PENDIENTE)));
        when(paymentRepositoryPort.findByOrderId(3L)).thenReturn(Optional.of(
                Payment.builder().id(1L).orderId(3L).status(PaymentStatus.PENDIENTE).build()));
        when(settlement.findApprovedPayment(3L)).thenThrow(new PaymentGatewayUnavailableException("timeout", null));

        assertThrows(PaymentGatewayUnavailableException.class, () -> handler.execute(command()));
        verify(orderRepositoryPort, never()).cancelIfPending(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("Si el pago se confirmó justo antes, no devuelve el stock")
    void carrera_con_el_webhook_no_devuelve_stock() {
        when(orderRepositoryPort.findByIdWithItems(3L)).thenReturn(Optional.of(order(OrderStatus.PENDIENTE)));
        when(paymentRepositoryPort.findByOrderId(3L)).thenReturn(Optional.empty());
        when(orderRepositoryPort.cancelIfPending(eq(3L), anyString(), eq(5L), any())).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> handler.execute(command()));
        verify(productRepositoryPort, never()).incrementStock(anyLong(), anyInt());
        verify(orderHistoryPort, never()).record(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("Solo se cancela un pedido pendiente")
    void rechaza_pedido_no_pendiente() {
        when(orderRepositoryPort.findByIdWithItems(3L)).thenReturn(Optional.of(order(OrderStatus.PREPARANDO)));

        assertThrows(IllegalArgumentException.class, () -> handler.execute(command()));
        verifyNoInteractions(settlement);
        verify(orderRepositoryPort, never()).cancelIfPending(anyLong(), any(), any(), any());
    }
}
