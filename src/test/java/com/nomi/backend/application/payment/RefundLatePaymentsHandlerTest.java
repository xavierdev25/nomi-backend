package com.nomi.backend.application.payment;

import com.nomi.backend.application.payment.OrderPaymentSettlement.Outcome;
import com.nomi.backend.domain.model.order.Order;
import com.nomi.backend.domain.model.order.OrderStatus;
import com.nomi.backend.domain.model.payment.PaymentStatus;
import com.nomi.backend.domain.port.out.OrderRepositoryPort;
import com.nomi.backend.domain.port.out.PaymentGatewayPort.GatewayPayment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Un pago que se aprueba cuando el pedido ya está cancelado (pago en revisión al cancelar, o aviso
 * perdido) se reembolsa aunque el webhook no llegue.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RefundLatePaymentsHandler - Reembolso de pagos tardíos")
class RefundLatePaymentsHandlerTest {

    @Mock private OrderRepositoryPort orderRepositoryPort;
    @Mock private OrderPaymentSettlement settlement;

    @InjectMocks private RefundLatePaymentsHandler handler;

    private static final GatewayPayment APPROVED =
            new GatewayPayment("mp-tarde", PaymentStatus.APROBADO, "nomi-3-abc", new BigDecimal("12.70"), null, null);

    private void givenOrder(OrderStatus status) {
        when(orderRepositoryPort.findById(3L)).thenReturn(Optional.of(Order.builder().id(3L).status(status).build()));
    }

    @Test
    @DisplayName("Busca los pedidos cancelados con checkout abierto desde el inicio de la ventana")
    void busca_candidatos() {
        LocalDateTime since = LocalDateTime.of(2026, 9, 24, 12, 0);
        when(orderRepositoryPort.findCancelledWithOpenCheckoutSince(since)).thenReturn(List.of(3L, 8L));

        assertEquals(List.of(3L, 8L), handler.findCancelledOrdersToCheck(since));
    }

    @Test
    @DisplayName("Un pago aprobado de un pedido cancelado se reembolsa")
    void reembolsa_pago_tardio() {
        givenOrder(OrderStatus.CANCELADO);
        when(settlement.findApprovedPayment(3L)).thenReturn(Optional.of(APPROVED));
        when(settlement.settleApproved(3L, APPROVED)).thenReturn(Outcome.REFUNDED);

        assertTrue(handler.refundIfPaid(3L));
    }

    @Test
    @DisplayName("Sin pago aprobado no hace nada")
    void sin_pago_aprobado() {
        givenOrder(OrderStatus.CANCELADO);
        when(settlement.findApprovedPayment(3L)).thenReturn(Optional.empty());

        assertFalse(handler.refundIfPaid(3L));
        verify(settlement, never()).settleApproved(any(), any());
    }

    @Test
    @DisplayName("Un pedido que ya no está cancelado no se toca")
    void pedido_no_cancelado() {
        givenOrder(OrderStatus.PREPARANDO);

        assertFalse(handler.refundIfPaid(3L));
        verifyNoInteractions(settlement);
    }

    @Test
    @DisplayName("Un pago que no es de este checkout no cuenta como reembolsado")
    void pago_ajeno() {
        givenOrder(OrderStatus.CANCELADO);
        when(settlement.findApprovedPayment(3L)).thenReturn(Optional.of(APPROVED));
        when(settlement.settleApproved(3L, APPROVED)).thenReturn(Outcome.NOT_THIS_CHECKOUT);

        assertFalse(handler.refundIfPaid(3L));
    }
}
