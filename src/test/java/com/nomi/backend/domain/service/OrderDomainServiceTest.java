package com.nomi.backend.domain.service;

import com.nomi.backend.domain.model.order.Order;
import com.nomi.backend.domain.model.order.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Máquina de estados del pedido: transiciones permitidas, prohibidas y estados finales.
 */
@DisplayName("OrderDomainService - Máquina de estados")
class OrderDomainServiceTest {

    private OrderDomainService service;
    private Order orderPendiente;

    @BeforeEach
    void setUp() {
        service = new OrderDomainService();
        orderPendiente = Order.builder()
                .id(1L)
                .userId(1L)
                .storeId(1L)
                .aulaId(1L)
                .items(List.of())
                .total(BigDecimal.valueOf(25.00))
                .status(OrderStatus.PENDIENTE)
                .notas("Test")
                .creadoEn(LocalDateTime.now())
                .actualizadoEn(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("PENDIENTE puede transicionar a PREPARANDO")
    void pendiente_to_preparando() {
        Order result = service.applyStatusTransition(orderPendiente, OrderStatus.PREPARANDO);
        assertEquals(OrderStatus.PREPARANDO, result.getStatus());
    }

    @Test
    @DisplayName("PENDIENTE puede transicionar a CANCELADO")
    void pendiente_to_cancelado() {
        Order result = service.applyStatusTransition(orderPendiente, OrderStatus.CANCELADO);
        assertEquals(OrderStatus.CANCELADO, result.getStatus());
    }

    @Test
    @DisplayName("PENDIENTE no puede transicionar a ENTREGADO")
    void pendiente_to_entregado_lanza_excepcion() {
        assertThrows(IllegalArgumentException.class, () ->
                service.applyStatusTransition(orderPendiente, OrderStatus.ENTREGADO)
        );
    }

    @Test
    @DisplayName("PREPARANDO puede transicionar a LISTO_PARA_RECOGER")
    void preparando_to_listo_para_recoger() {
        Order preparando = service.applyStatusTransition(orderPendiente, OrderStatus.PREPARANDO);
        Order result = service.applyStatusTransition(preparando, OrderStatus.LISTO_PARA_RECOGER);
        assertEquals(OrderStatus.LISTO_PARA_RECOGER, result.getStatus());
    }

    @Test
    @DisplayName("PREPARANDO no puede transicionar a EN_CAMINO directamente")
    void preparando_to_en_camino_lanza_excepcion() {
        Order preparando = service.applyStatusTransition(orderPendiente, OrderStatus.PREPARANDO);
        assertThrows(IllegalArgumentException.class, () ->
                service.applyStatusTransition(preparando, OrderStatus.EN_CAMINO)
        );
    }

    @Test
    @DisplayName("LISTO_PARA_RECOGER puede transicionar a EN_CAMINO")
    void listo_para_recoger_to_en_camino() {
        Order preparando = service.applyStatusTransition(orderPendiente, OrderStatus.PREPARANDO);
        Order listo = service.applyStatusTransition(preparando, OrderStatus.LISTO_PARA_RECOGER);
        Order result = service.applyStatusTransition(listo, OrderStatus.EN_CAMINO);
        assertEquals(OrderStatus.EN_CAMINO, result.getStatus());
    }

    @Test
    @DisplayName("EN_CAMINO puede transicionar a ENTREGADO")
    void en_camino_to_entregado() {
        Order preparando = service.applyStatusTransition(orderPendiente, OrderStatus.PREPARANDO);
        Order listo = service.applyStatusTransition(preparando, OrderStatus.LISTO_PARA_RECOGER);
        Order enCamino = service.applyStatusTransition(listo, OrderStatus.EN_CAMINO);
        Order result = service.applyStatusTransition(enCamino, OrderStatus.ENTREGADO);
        assertEquals(OrderStatus.ENTREGADO, result.getStatus());
    }

    @Test
    @DisplayName("ENTREGADO es estado terminal — no puede cambiar")
    void entregado_es_terminal() {
        Order preparando = service.applyStatusTransition(orderPendiente, OrderStatus.PREPARANDO);
        Order listo = service.applyStatusTransition(preparando, OrderStatus.LISTO_PARA_RECOGER);
        Order enCamino = service.applyStatusTransition(listo, OrderStatus.EN_CAMINO);
        Order entregado = service.applyStatusTransition(enCamino, OrderStatus.ENTREGADO);

        assertTrue(service.isTerminal(entregado));
        assertThrows(IllegalArgumentException.class, () ->
                service.applyStatusTransition(entregado, OrderStatus.CANCELADO)
        );
    }

    @Test
    @DisplayName("CANCELADO es estado terminal — no puede cambiar")
    void cancelado_es_terminal() {
        Order cancelado = service.applyStatusTransition(orderPendiente, OrderStatus.CANCELADO);
        assertTrue(service.isTerminal(cancelado));
        assertThrows(IllegalArgumentException.class, () ->
                service.applyStatusTransition(cancelado, OrderStatus.PREPARANDO)
        );
    }

    @Test
    @DisplayName("isCancellable retorna true solo para PENDIENTE")
    void is_cancellable() {
        assertTrue(service.isCancellable(orderPendiente));
    }

    @Test
    @DisplayName("isCancellable retorna false para PREPARANDO, EN_CAMINO y ENTREGADO")
    void is_not_cancellable() {
        Order preparando = service.applyStatusTransition(orderPendiente, OrderStatus.PREPARANDO);
        assertFalse(service.isCancellable(preparando));

        Order listo = service.applyStatusTransition(preparando, OrderStatus.LISTO_PARA_RECOGER);
        assertFalse(service.isCancellable(listo));

        Order enCamino = service.applyStatusTransition(listo, OrderStatus.EN_CAMINO);
        assertFalse(service.isCancellable(enCamino));
    }

    @Test
    @DisplayName("applyStatusTransition actualiza actualizadoEn")
    void actualiza_timestamp() {
        LocalDateTime antes = orderPendiente.getActualizadoEn();
        Order result = service.applyStatusTransition(orderPendiente, OrderStatus.PREPARANDO);
        assertNotNull(result.getActualizadoEn());
        assertTrue(result.getActualizadoEn().isAfter(antes) || result.getActualizadoEn().isEqual(antes));
    }
}
