package com.nomi.backend.domain.port.out;

import com.nomi.backend.domain.common.PageQuery;
import com.nomi.backend.domain.common.PagedResult;
import com.nomi.backend.domain.model.order.Order;
import com.nomi.backend.domain.model.order.OrderStatus;

import java.time.LocalDateTime;
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

    /**
     * Ids de los pedidos cancelados desde {@code since} que tienen un checkout sin pago aprobado ni
     * reembolsado: en ellos todavía puede aprobarse un pago tardío que hay que devolver.
     */
    List<Long> findCancelledWithOpenCheckoutSince(LocalDateTime since);

    /**
     * Pasa el pedido de {@code PENDIENTE} a {@code PREPARANDO} de forma atómica.
     *
     * @return {@code false} si ya no estaba pendiente (otro proceso lo cambió antes)
     */
    boolean markPaidIfPending(Long orderId, LocalDateTime now);

    /**
     * Cancela el pedido de forma atómica si sigue {@code PENDIENTE}. No devuelve el stock.
     *
     * @param canceladoPor usuario que cancela, o {@code null} si lo cancela el sistema
     * @return {@code false} si ya no estaba pendiente (otro proceso lo cambió antes)
     */
    boolean cancelIfPending(Long orderId, String motivo, Long canceladoPor, LocalDateTime now);
}
