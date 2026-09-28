package com.nomi.backend.application.payment;

import com.nomi.backend.domain.exception.PaymentGatewayUnavailableException;
import com.nomi.backend.domain.model.payment.PaymentStatus;
import com.nomi.backend.domain.port.in.payment.ProcessWebhookUseCase.WebhookEvent;
import com.nomi.backend.domain.port.out.PaymentGatewayPort;
import com.nomi.backend.domain.port.out.PaymentGatewayPort.GatewayPayment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/**
 * El webhook consulta el pago en MercadoPago y delega en {@link OrderPaymentSettlement}. Si la
 * consulta falla, el error sube para que MercadoPago reenvíe el aviso: asumir "pendiente" podría
 * cancelar un pedido pagado.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProcessWebhookHandler - Webhook de MercadoPago")
class ProcessWebhookHandlerTest {

    @Mock private PaymentGatewayPort paymentGatewayPort;
    @Mock private OrderPaymentSettlement settlement;

    @InjectMocks private ProcessWebhookHandler handler;

    private static GatewayPayment payment(PaymentStatus status, String externalReference) {
        return new GatewayPayment("mp-1", status, externalReference, new BigDecimal("12.70"), null, null);
    }

    @Test
    @DisplayName("Un pago aprobado se aplica al pedido de su referencia externa")
    void aprobado_se_liquida() {
        GatewayPayment approved = payment(PaymentStatus.APROBADO, "3");
        when(paymentGatewayPort.getPayment("mp-1")).thenReturn(Optional.of(approved));

        handler.execute(new WebhookEvent("mp-1", "payment.updated", null));

        verify(settlement).settleApproved(3L, approved);
        verify(settlement, never()).recordNotApproved(any(), any());
    }

    @Test
    @DisplayName("Con la referencia única del checkout también se encuentra el pedido")
    void referencia_unica_se_resuelve() {
        GatewayPayment approved = payment(PaymentStatus.APROBADO, "nomi-3-6f1c0f2e-6b1d-4a57-9c38-3f5e8a2d9b10");
        when(paymentGatewayPort.getPayment("mp-1")).thenReturn(Optional.of(approved));

        handler.execute(new WebhookEvent("mp-1", "payment.updated", null));

        verify(settlement).settleApproved(3L, approved);
    }

    @Test
    @DisplayName("Un pago no aprobado solo se registra")
    void no_aprobado_se_registra() {
        GatewayPayment rejected = payment(PaymentStatus.RECHAZADO, "3");
        when(paymentGatewayPort.getPayment("mp-1")).thenReturn(Optional.of(rejected));

        handler.execute(new WebhookEvent("mp-1", "payment.updated", null));

        verify(settlement).recordNotApproved(3L, rejected);
        verify(settlement, never()).settleApproved(any(), any());
    }

    @Test
    @DisplayName("Si MercadoPago no responde, el error sube y no se toca nada")
    void gateway_caido_propaga_error() {
        when(paymentGatewayPort.getPayment("mp-1"))
                .thenThrow(new PaymentGatewayUnavailableException("timeout", null));

        assertThrows(PaymentGatewayUnavailableException.class,
                () -> handler.execute(new WebhookEvent("mp-1", "payment.updated", null)));
        verifyNoInteractions(settlement);
    }

    @Test
    @DisplayName("Un pago sin referencia a un pedido de Nomi se ignora")
    void referencia_invalida_se_ignora() {
        when(paymentGatewayPort.getPayment("mp-1")).thenReturn(Optional.of(payment(PaymentStatus.APROBADO, "otra-app-99")));

        handler.execute(new WebhookEvent("mp-1", "payment.updated", null));

        verifyNoInteractions(settlement);
    }

    @Test
    @DisplayName("Un pago que MercadoPago no conoce (aviso de prueba del panel) se ignora sin error")
    void pago_desconocido_se_ignora() {
        when(paymentGatewayPort.getPayment("123456")).thenReturn(Optional.empty());

        handler.execute(new WebhookEvent("123456", "payment.updated", null));

        verifyNoInteractions(settlement);
    }
}
