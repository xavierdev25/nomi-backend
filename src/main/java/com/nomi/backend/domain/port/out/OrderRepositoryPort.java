package com.nomi.backend.domain.port.out;

import com.nomi.backend.domain.common.PageQuery;
import com.nomi.backend.domain.common.PagedResult;
import com.nomi.backend.domain.model.order.Order;
import com.nomi.backend.domain.model.order.OrderStatus;

import java.util.List;
import java.util.Optional;

/**
 * Persistencia de pedidos. Todas las lecturas devuelven el pedido con sus líneas cargadas.
 */
public interface OrderRepositoryPort {
    Order save(Order order);
    Optional<Order> findById(Long id);
    Optional<Order> findByIdWithItems(Long id);
    List<Order> findByUserId(Long userId);
    List<Order> findByStoreId(Long storeId);
    List<Order> findByStatus(OrderStatus status);
    List<Order> findAll();
    PagedResult<Order> findAllPaginated(PageQuery query);
    PagedResult<Order> findByUserIdPaginated(Long userId, PageQuery query);
    PagedResult<Order> findByStoreIdPaginated(Long storeId, PageQuery query);
    PagedResult<Order> findByStatusPaginated(OrderStatus status, PageQuery query);
    List<Order> findByUserIdAndStatus(Long userId, OrderStatus status);
}
