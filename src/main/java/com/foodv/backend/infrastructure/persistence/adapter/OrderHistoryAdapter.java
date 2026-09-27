package com.foodv.backend.infrastructure.persistence.adapter;

import com.foodv.backend.domain.model.order.OrderStatus;
import com.foodv.backend.domain.port.out.OrderHistoryPort;
import com.foodv.backend.infrastructure.persistence.entity.OrderStatusHistoryEntity;
import com.foodv.backend.infrastructure.persistence.repository.OrderStatusHistoryRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Implementación JPA de {@code OrderHistoryPort}; el historial se lee en orden cronológico.
 */
@Component
public class OrderHistoryAdapter implements OrderHistoryPort {

    private final OrderStatusHistoryRepository repository;

    public OrderHistoryAdapter(OrderStatusHistoryRepository repository) {
        this.repository = repository;
    }

    @Override
    public void record(Long orderId, OrderStatus status, Long changedBy, String notas) {
        repository.save(OrderStatusHistoryEntity.builder()
                .orderId(orderId)
                .status(status)
                .changedBy(changedBy)
                .notas(notas)
                .creadoEn(LocalDateTime.now())
                .build());
    }

    @Override
    public List<OrderHistoryEntry> findByOrderId(Long orderId) {
        return repository.findByOrderIdOrderByCreadoEnAsc(orderId).stream()
                .map(e -> new OrderHistoryEntry(
                        e.getId(), e.getOrderId(), e.getStatus(),
                        e.getChangedBy(), e.getNotas(), e.getCreadoEn()))
                .toList();
    }
}
