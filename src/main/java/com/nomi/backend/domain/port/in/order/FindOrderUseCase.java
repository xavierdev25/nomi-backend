package com.nomi.backend.domain.port.in.order;

import com.nomi.backend.domain.common.PageQuery;
import com.nomi.backend.domain.common.PagedResult;
import com.nomi.backend.domain.model.order.Order;
import com.nomi.backend.domain.model.order.OrderStatus;

/**
 * Consulta de pedidos, con control de acceso en las variantes {@code ForUser}.
 */
public interface FindOrderUseCase {

    Order findById(Long id);

    PagedResult<Order> findAllPaginated(PageQuery query);

    PagedResult<Order> findByUserIdPaginated(Long userId, PageQuery query);

    PagedResult<Order> findByStoreIdPaginated(Long storeId, PageQuery query);

    PagedResult<Order> findByStatusPaginated(OrderStatus status, PageQuery query);

    Order findByIdForUser(Long orderId, String email);

    PagedResult<Order> findForUser(String email, PageQuery query);
}
