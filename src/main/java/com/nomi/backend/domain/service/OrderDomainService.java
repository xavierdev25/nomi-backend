package com.nomi.backend.domain.service;

import com.nomi.backend.domain.model.order.Order;
import com.nomi.backend.domain.model.order.OrderStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Reglas de dominio de los pedidos que no pertenecen a una sola entidad.
 */
@Component
public class OrderDomainService {

    /**
     * Devuelve una copia del pedido con el nuevo estado.
     *
     * @throws IllegalArgumentException si la transición no está permitida por {@link OrderStatus#canTransitionTo}
     */
    public Order applyStatusTransition(Order order, OrderStatus newStatus) {
        if (!order.getStatus().canTransitionTo(newStatus)) {
            throw new IllegalArgumentException(
                    "Transición de estado inválida: " + order.getStatus() + " -> " + newStatus
            );
        }
        return Order.builder()
                .id(order.getId())
                .userId(order.getUserId())
                .storeId(order.getStoreId())
                .aulaId(order.getAulaId())
                .repartidorId(order.getRepartidorId())
                .items(order.getItems())
                .total(order.getTotal())
                .propina(order.getPropina())
                .tarifaServicio(order.getTarifaServicio())
                .comisionNomi(order.getComisionNomi())
                .status(newStatus)
                .notas(order.getNotas())
                .motivoCancelacion(order.getMotivoCancelacion())
                .canceladoPor(order.getCanceladoPor())
                .codigoConfirmacion(order.getCodigoConfirmacion())
                .fotoEntregaUrl(order.getFotoEntregaUrl())
                .creadoEn(order.getCreadoEn())
                .actualizadoEn(LocalDateTime.now())
                .build();
    }

    public boolean isCancellable(Order order) {
        return order.getStatus().canTransitionTo(OrderStatus.CANCELADO);
    }

    public boolean isTerminal(Order order) {
        return order.getStatus() == OrderStatus.ENTREGADO
                || order.getStatus() == OrderStatus.CANCELADO;
    }
}
