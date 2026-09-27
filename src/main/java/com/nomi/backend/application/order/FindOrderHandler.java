package com.nomi.backend.application.order;

import com.nomi.backend.domain.common.PageQuery;
import com.nomi.backend.domain.common.PagedResult;
import com.nomi.backend.domain.model.order.Order;
import com.nomi.backend.domain.model.order.OrderStatus;
import com.nomi.backend.domain.model.store.Store;
import com.nomi.backend.domain.model.user.User;
import com.nomi.backend.domain.model.user.UserRole;
import com.nomi.backend.domain.exception.AuthorizationException;
import com.nomi.backend.domain.port.in.order.FindOrderUseCase;
import com.nomi.backend.domain.port.out.OrderRepositoryPort;
import com.nomi.backend.domain.port.out.StoreRepositoryPort;
import com.nomi.backend.domain.port.out.UserRepositoryPort;
import com.nomi.backend.domain.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consulta de pedidos con control de acceso: el dueño del pedido, la tienda que lo recibe, el
 * repartidor asignado o un administrador.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FindOrderHandler implements FindOrderUseCase {

    private final OrderRepositoryPort orderRepositoryPort;
    private final UserRepositoryPort userRepositoryPort;
    private final StoreRepositoryPort storeRepositoryPort;

    @Override
    public Order findById(Long id) {
        return orderRepositoryPort.findByIdWithItems(id)
                .orElseThrow(() -> new ResourceNotFoundException("Orden no encontrada"));
    }

    @Override
    public Order findByIdForUser(Long orderId, String email) {
        Order order = orderRepositoryPort.findByIdWithItems(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Orden no encontrada"));

        User user = userRepositoryPort.findByEmail(email)
                .orElseThrow(() -> new AuthorizationException("No autenticado"));

        if (!canAccess(user, order)) {
            throw new AuthorizationException("No tienes permiso para ver esta orden");
        }
        return order;
    }

    /**
     * "Mis pedidos" según el rol: un administrador ve todos, un comercio los de su tienda y el
     * resto los propios.
     */
    @Override
    public PagedResult<Order> findForUser(String email, PageQuery query) {
        User user = userRepositoryPort.findByEmail(email)
                .orElseThrow(() -> new AuthorizationException("No autenticado"));

        if (user.getRole() == UserRole.ADMIN) {
            return orderRepositoryPort.findAllPaginated(query);
        }
        if (user.getRole() == UserRole.COMERCIO) {
            Long storeId = storeRepositoryPort.findByOwnerId(user.getId())
                    .map(Store::getId)
                    .orElseThrow(() -> new AuthorizationException("No tienes una tienda asociada"));
            return orderRepositoryPort.findByStoreIdPaginated(storeId, query);
        }
        return orderRepositoryPort.findByUserIdPaginated(user.getId(), query);
    }

    @Override
    public PagedResult<Order> findAllPaginated(PageQuery query) {
        return orderRepositoryPort.findAllPaginated(query);
    }

    @Override
    public PagedResult<Order> findByUserIdPaginated(Long userId, PageQuery query) {
        return orderRepositoryPort.findByUserIdPaginated(userId, query);
    }

    @Override
    public PagedResult<Order> findByStoreIdPaginated(Long storeId, PageQuery query) {
        return orderRepositoryPort.findByStoreIdPaginated(storeId, query);
    }

    @Override
    public PagedResult<Order> findByStatusPaginated(OrderStatus status, PageQuery query) {
        return orderRepositoryPort.findByStatusPaginated(status, query);
    }

    private boolean canAccess(User user, Order order) {
        if (user.getRole() == UserRole.ADMIN) return true;
        if (order.getUserId() != null && order.getUserId().equals(user.getId())) return true;
        if (user.getRole() == UserRole.COMERCIO) {
            return storeRepositoryPort.findByOwnerId(user.getId())
                    .map(s -> s.getId().equals(order.getStoreId()))
                    .orElse(false);
        }
        if (user.getRole() == UserRole.REPARTIDOR) {
            return user.getId().equals(order.getRepartidorId());
        }
        return false;
    }
}
