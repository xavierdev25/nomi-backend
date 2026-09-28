package com.nomi.backend.domain.model.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Plazo para pagar un pedido y límite de pedidos sin pagar por estudiante.
 */
@DisplayName("OrderPaymentPolicy - Caducidad de pedidos sin pagar")
class OrderPaymentPolicyTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 27, 12, 0);

    private final OrderPaymentPolicy policy = new OrderPaymentPolicy(Duration.ofMinutes(15), 2);

    private static Order order(OrderStatus status, LocalDateTime pagoExpiraEn) {
        return Order.builder().id(1L).status(status).pagoExpiraEn(pagoExpiraEn).build();
    }

    @Test
    @DisplayName("El plazo se cuenta desde la creación")
    void deadline_desde_creacion() {
        assertEquals(NOW.plusMinutes(15), policy.deadlineFrom(NOW));
    }

    @Test
    @DisplayName("Rechaza un plazo nulo, cero o negativo y un límite menor que 1")
    void valida_configuracion() {
        assertThrows(IllegalArgumentException.class, () -> new OrderPaymentPolicy(null, 2));
        assertThrows(IllegalArgumentException.class, () -> new OrderPaymentPolicy(Duration.ZERO, 2));
        assertThrows(IllegalArgumentException.class, () -> new OrderPaymentPolicy(Duration.ofMinutes(-1), 2));
        assertThrows(IllegalArgumentException.class, () -> new OrderPaymentPolicy(Duration.ofMinutes(15), 0));
    }

    @Test
    @DisplayName("Permite otro pedido mientras no se alcance el límite de pendientes vigentes")
    void limite_de_pendientes() {
        Order vigente = order(OrderStatus.PENDIENTE, NOW.plusMinutes(5));

        assertTrue(policy.allowsAnotherPending(List.of(), NOW));
        assertTrue(policy.allowsAnotherPending(List.of(vigente), NOW));
        assertFalse(policy.allowsAnotherPending(List.of(vigente, vigente), NOW));
    }

    @Test
    @DisplayName("Los pedidos vencidos o que ya no están pendientes no cuentan")
    void vencidos_y_no_pendientes_no_cuentan() {
        Order vencido = order(OrderStatus.PENDIENTE, NOW.minusSeconds(1));
        Order preparando = order(OrderStatus.PREPARANDO, NOW.plusMinutes(5));

        assertTrue(policy.allowsAnotherPending(List.of(vencido, vencido, preparando), NOW));
    }

    @Test
    @DisplayName("Un pedido pendiente vence justo en su hora límite")
    void pedido_vence_en_la_hora_limite() {
        assertFalse(order(OrderStatus.PENDIENTE, NOW.plusSeconds(1)).isPaymentExpired(NOW));
        assertTrue(order(OrderStatus.PENDIENTE, NOW).isPaymentExpired(NOW));
        assertTrue(order(OrderStatus.PENDIENTE, NOW.minusMinutes(1)).isPaymentExpired(NOW));
    }

    @Test
    @DisplayName("Un pedido sin plazo (anterior a la regla) o ya pagado nunca vence")
    void sin_plazo_o_no_pendiente_no_vence() {
        assertFalse(order(OrderStatus.PENDIENTE, null).isPaymentExpired(NOW));
        assertFalse(order(OrderStatus.PREPARANDO, NOW.minusMinutes(1)).isPaymentExpired(NOW));
        assertFalse(order(OrderStatus.CANCELADO, NOW.minusMinutes(1)).isPaymentExpired(NOW));
    }

    @Test
    @DisplayName("Segundos para pagar: restantes, 0 si venció, null si no aplica")
    void segundos_para_pagar() {
        assertEquals(90L, order(OrderStatus.PENDIENTE, NOW.plusSeconds(90)).segundosParaPagar(NOW));
        assertEquals(0L, order(OrderStatus.PENDIENTE, NOW.minusMinutes(3)).segundosParaPagar(NOW));
        assertNull(order(OrderStatus.PENDIENTE, null).segundosParaPagar(NOW));
        assertNull(order(OrderStatus.PREPARANDO, NOW.plusMinutes(5)).segundosParaPagar(NOW));
    }
}
