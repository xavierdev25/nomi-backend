package com.nomi.backend.application.order;

import com.nomi.backend.domain.model.order.Order;
import com.nomi.backend.domain.model.order.OrderStatus;
import com.nomi.backend.domain.port.out.BusinessMetricsPort;
import com.nomi.backend.domain.port.out.OrderHistoryPort;
import com.nomi.backend.domain.port.out.OrderRepositoryPort;
import com.nomi.backend.domain.service.OrderDomainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Cambio de estado por tienda, repartidor o administrador. Los dos cambios que salen de
 * {@code PENDIENTE} no se permiten por aquí: preparar sin pago aprobado o cancelar sin devolver
 * el stock.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UpdateOrderStatusHandler - Cambio de estado")
class UpdateOrderStatusHandlerTest {

    @Mock private OrderRepositoryPort orderRepositoryPort;
    @Mock private OrderHistoryPort orderHistoryPort;
    @Mock private BusinessMetricsPort metricsPort;
    @Mock private ApplicationEventPublisher eventPublisher;

    private UpdateOrderStatusHandler handler;

    @BeforeEach
    void setUp() {
        handler = new UpdateOrderStatusHandler(orderRepositoryPort, orderHistoryPort, metricsPort, eventPublisher,
                new OrderDomainService());
    }

    private void givenOrder(OrderStatus status) {
        when(orderRepositoryPort.findById(3L)).thenReturn(Optional.of(
                Order.builder().id(3L).userId(5L).storeId(1L).status(status).build()));
    }

    @Test
    @DisplayName("Un pedido pendiente no pasa a preparación sin pago aprobado")
    void pendiente_no_pasa_a_preparacion() {
        givenOrder(OrderStatus.PENDIENTE);

        assertThrows(IllegalStateException.class, () -> handler.execute(3L, OrderStatus.PREPARANDO, 2L));
        verify(orderRepositoryPort, never()).save(any());
    }

    @Test
    @DisplayName("Cancelar se hace con /cancel, que devuelve el stock")
    void cancelar_no_se_hace_por_aqui() {
        givenOrder(OrderStatus.PENDIENTE);

        assertThrows(IllegalStateException.class, () -> handler.execute(3L, OrderStatus.CANCELADO, 2L));
        verify(orderRepositoryPort, never()).save(any());
        verifyNoInteractions(metricsPort);
    }

    @Test
    @DisplayName("Un pedido pagado avanza con normalidad y queda en el historial")
    void preparando_avanza() {
        givenOrder(OrderStatus.PREPARANDO);
        when(orderRepositoryPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Order result = handler.execute(3L, OrderStatus.LISTO_PARA_RECOGER, 2L);

        assertEquals(OrderStatus.LISTO_PARA_RECOGER, result.getStatus());
        verify(orderHistoryPort).record(3L, OrderStatus.LISTO_PARA_RECOGER, 2L, "Estado actualizado a Listo para recoger");
        verify(eventPublisher).publishEvent(any(UpdateOrderStatusHandler.OrderStatusChangedEvent.class));
    }

    @Test
    @DisplayName("Entregar un pedido cuenta como pedido completado")
    void entregado_cuenta_como_completado() {
        givenOrder(OrderStatus.EN_CAMINO);
        when(orderRepositoryPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        handler.execute(3L, OrderStatus.ENTREGADO, 2L);

        verify(metricsPort).recordOrderCompleted();
    }
}
