package com.nomi.backend.application.payment;

import com.nomi.backend.domain.exception.AuthorizationException;
import com.nomi.backend.domain.model.order.Order;
import com.nomi.backend.domain.model.order.OrderStatus;
import com.nomi.backend.domain.model.payment.Payment;
import com.nomi.backend.domain.model.payment.PaymentStatus;
import com.nomi.backend.domain.model.user.User;
import com.nomi.backend.domain.model.user.UserRole;
import com.nomi.backend.domain.port.in.payment.CreatePaymentUseCase.CreatePaymentCommand;
import com.nomi.backend.domain.port.out.OrderRepositoryPort;
import com.nomi.backend.domain.port.out.PaymentGatewayPort;
import com.nomi.backend.domain.port.out.PaymentGatewayPort.PaymentRequest;
import com.nomi.backend.domain.port.out.PaymentGatewayPort.PaymentResponse;
import com.nomi.backend.domain.port.out.PaymentRepositoryPort;
import com.nomi.backend.domain.port.out.UserRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Creación del checkout: el monto lo calcula el servidor, solo se paga un pedido pendiente y en
 * plazo, y el checkout vence con el pedido.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CreatePaymentHandler - Checkout de MercadoPago")
class CreatePaymentHandlerTest {

    private static final String EMAIL = "estudiante@nomi.com";

    @Mock private PaymentRepositoryPort paymentRepositoryPort;
    @Mock private PaymentGatewayPort paymentGatewayPort;
    @Mock private OrderRepositoryPort orderRepositoryPort;
    @Mock private UserRepositoryPort userRepositoryPort;

    private CreatePaymentHandler handler;

    @BeforeEach
    void setUp() {
        handler = new CreatePaymentHandler(paymentRepositoryPort, paymentGatewayPort, orderRepositoryPort,
                userRepositoryPort, "https://api.nomi.test/api/payments/webhook");
        when(userRepositoryPort.findByEmail(EMAIL)).thenReturn(Optional.of(
                User.builder().id(5L).email(EMAIL).role(UserRole.ESTUDIANTE).activo(true).build()));
    }

    private static Order order(OrderStatus status, LocalDateTime pagoExpiraEn) {
        return Order.builder()
                .id(3L).userId(5L).storeId(1L).status(status)
                .total(new BigDecimal("10.00")).propina(new BigDecimal("2.00"))
                .tarifaServicio(new BigDecimal("0.50")).comisionNomi(new BigDecimal("0.20"))
                .pagoExpiraEn(pagoExpiraEn)
                .build();
    }

    private void stubCheckout() {
        when(paymentGatewayPort.createPayment(any()))
                .thenReturn(new PaymentResponse("pref-1", "https://mp/checkout", PaymentStatus.PENDIENTE));
        when(paymentRepositoryPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("Pedido pendiente y en plazo: monto del servidor y checkout que vence con el pedido")
    void crea_checkout_con_vencimiento() {
        LocalDateTime deadline = LocalDateTime.now().plusMinutes(10);
        when(orderRepositoryPort.findByIdWithItems(3L)).thenReturn(Optional.of(order(OrderStatus.PENDIENTE, deadline)));
        when(paymentRepositoryPort.findByOrderId(3L)).thenReturn(Optional.empty());
        stubCheckout();

        Payment payment = handler.execute(new CreatePaymentCommand(3L, EMAIL));

        ArgumentCaptor<PaymentRequest> request = ArgumentCaptor.forClass(PaymentRequest.class);
        verify(paymentGatewayPort).createPayment(request.capture());
        assertEquals(0, new BigDecimal("12.70").compareTo(request.getValue().amount()));
        assertEquals(deadline.atZone(ZoneId.systemDefault()).toOffsetDateTime(), request.getValue().expiresAt());
        assertTrue(request.getValue().externalReference().startsWith("nomi-3-"),
                "referencia única, no el id del pedido");
        assertEquals(request.getValue().externalReference(), payment.getExternalReference());
        assertEquals(PaymentStatus.PENDIENTE, payment.getStatus());
        assertEquals("pref-1", payment.getExternalId());
        assertEquals(0, new BigDecimal("12.70").compareTo(payment.getAmount()));
    }

    @Test
    @DisplayName("Un pedido anterior a la regla (sin plazo) crea un checkout sin vencimiento")
    void pedido_sin_plazo_crea_checkout_sin_vencimiento() {
        when(orderRepositoryPort.findByIdWithItems(3L)).thenReturn(Optional.of(order(OrderStatus.PENDIENTE, null)));
        when(paymentRepositoryPort.findByOrderId(3L)).thenReturn(Optional.empty());
        stubCheckout();

        handler.execute(new CreatePaymentCommand(3L, EMAIL));

        ArgumentCaptor<PaymentRequest> request = ArgumentCaptor.forClass(PaymentRequest.class);
        verify(paymentGatewayPort).createPayment(request.capture());
        assertNull(request.getValue().expiresAt());
    }

    @Test
    @DisplayName("No se paga un pedido que ya no está pendiente")
    void rechaza_pedido_no_pendiente() {
        when(orderRepositoryPort.findByIdWithItems(3L)).thenReturn(Optional.of(
                order(OrderStatus.CANCELADO, LocalDateTime.now().plusMinutes(10))));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> handler.execute(new CreatePaymentCommand(3L, EMAIL)));

        assertTrue(error.getMessage().contains("Cancelado"));
        verifyNoInteractions(paymentGatewayPort);
    }

    @Test
    @DisplayName("No se paga un pedido cuyo plazo ya venció")
    void rechaza_pedido_vencido() {
        when(orderRepositoryPort.findByIdWithItems(3L)).thenReturn(Optional.of(
                order(OrderStatus.PENDIENTE, LocalDateTime.now().minusSeconds(1))));

        assertThrows(IllegalStateException.class, () -> handler.execute(new CreatePaymentCommand(3L, EMAIL)));
        verifyNoInteractions(paymentGatewayPort);
        verify(paymentRepositoryPort, never()).save(any());
    }

    @Test
    @DisplayName("Un estudiante no puede pagar el pedido de otro")
    void rechaza_pedido_ajeno() {
        Order ajeno = Order.builder().id(3L).userId(99L).status(OrderStatus.PENDIENTE).build();
        when(orderRepositoryPort.findByIdWithItems(3L)).thenReturn(Optional.of(ajeno));

        assertThrows(AuthorizationException.class, () -> handler.execute(new CreatePaymentCommand(3L, EMAIL)));
        verifyNoInteractions(paymentGatewayPort);
    }

    @Test
    @DisplayName("Un pedido admite un único checkout")
    void rechaza_segundo_checkout() {
        when(orderRepositoryPort.findByIdWithItems(3L)).thenReturn(Optional.of(
                order(OrderStatus.PENDIENTE, LocalDateTime.now().plusMinutes(10))));
        when(paymentRepositoryPort.findByOrderId(3L)).thenReturn(Optional.of(Payment.builder().id(1L).build()));

        assertThrows(IllegalArgumentException.class, () -> handler.execute(new CreatePaymentCommand(3L, EMAIL)));
        verifyNoInteractions(paymentGatewayPort);
    }
}
