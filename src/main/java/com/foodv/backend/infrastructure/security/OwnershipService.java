package com.foodv.backend.infrastructure.security;

import com.foodv.backend.domain.model.order.Order;
import com.foodv.backend.domain.model.product.Product;
import com.foodv.backend.domain.model.store.Store;
import com.foodv.backend.domain.model.user.User;
import com.foodv.backend.domain.model.user.UserRole;
import com.foodv.backend.domain.port.out.OrderRepositoryPort;
import com.foodv.backend.domain.port.out.ProductRepositoryPort;
import com.foodv.backend.domain.port.out.StoreRepositoryPort;
import com.foodv.backend.domain.port.out.UserRepositoryPort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Controles de ownership contra el acceso a recursos ajenos (IDOR). Todo endpoint que reciba
 * el id de un recurso de otro usuario debe pasar por aquí antes de actuar.
 */
@Component
public class OwnershipService {

    private final UserRepositoryPort userRepositoryPort;
    private final StoreRepositoryPort storeRepositoryPort;
    private final ProductRepositoryPort productRepositoryPort;
    private final OrderRepositoryPort orderRepositoryPort;

    public OwnershipService(UserRepositoryPort userRepositoryPort,
                            StoreRepositoryPort storeRepositoryPort,
                            ProductRepositoryPort productRepositoryPort,
                            OrderRepositoryPort orderRepositoryPort) {
        this.userRepositoryPort = userRepositoryPort;
        this.storeRepositoryPort = storeRepositoryPort;
        this.productRepositoryPort = productRepositoryPort;
        this.orderRepositoryPort = orderRepositoryPort;
    }

    public boolean isAdmin(User user) {
        return user.getRole() == UserRole.ADMIN;
    }

    public void requireSelfOrAdmin(User current, Long targetUserId) {
        if (current == null) throw new AccessDeniedException("No autenticado");
        if (isAdmin(current)) return;
        if (current.getId().equals(targetUserId)) return;
        throw new AccessDeniedException("No tienes permiso para acceder a este recurso");
    }

    public void requireStoreOwnerOrAdmin(User current, Long storeId) {
        if (current == null) throw new AccessDeniedException("No autenticado");
        if (isAdmin(current)) return;
        Store store = storeRepositoryPort.findById(storeId)
                .orElseThrow(() -> new AccessDeniedException("Tienda no encontrada"));
        if (current.getRole() == UserRole.COMERCIO && store.getOwnerId().equals(current.getId())) return;
        throw new AccessDeniedException("No tienes permiso sobre esta tienda");
    }

    public void requireProductOwnerOrAdmin(User current, Long productId) {
        if (current == null) throw new AccessDeniedException("No autenticado");
        if (isAdmin(current)) return;
        Product product = productRepositoryPort.findById(productId)
                .orElseThrow(() -> new AccessDeniedException("Producto no encontrado"));
        Store store = storeRepositoryPort.findById(product.getStoreId())
                .orElseThrow(() -> new AccessDeniedException("Tienda no encontrada"));
        if (current.getRole() == UserRole.COMERCIO && store.getOwnerId().equals(current.getId())) return;
        throw new AccessDeniedException("No tienes permiso sobre este producto");
    }

    public Order requireOrderAccess(User current, Long orderId) {
        if (current == null) throw new AccessDeniedException("No autenticado");
        Order order = orderRepositoryPort.findById(orderId)
                .orElseThrow(() -> new AccessDeniedException("Orden no encontrada"));
        if (canAccessOrder(current, order)) return order;
        throw new AccessDeniedException("No tienes permiso sobre esta orden");
    }

    /**
     * Acceden a un pedido: un administrador, su dueño, el comercio de la tienda que lo recibe y
     * el repartidor asignado.
     */
    public boolean canAccessOrder(User current, Order order) {
        if (current == null || order == null) return false;
        if (isAdmin(current)) return true;
        if (order.getUserId() != null && order.getUserId().equals(current.getId())) return true;
        if (current.getRole() == UserRole.COMERCIO) {
            return storeRepositoryPort.findByOwnerId(current.getId())
                    .map(s -> s.getId().equals(order.getStoreId()))
                    .orElse(false);
        }
        if (current.getRole() == UserRole.REPARTIDOR) {
            return current.getId().equals(order.getRepartidorId());
        }
        return false;
    }

    /**
     * Tienda del usuario autenticado.
     *
     * @throws org.springframework.security.access.AccessDeniedException si no tiene tienda
     */
    public Long resolveStoreIdForUser(User current) {
        if (current == null) throw new AccessDeniedException("No autenticado");
        return storeRepositoryPort.findByOwnerId(current.getId())
                .map(Store::getId)
                .orElseThrow(() -> new AccessDeniedException("No tienes una tienda asociada"));
    }

    public boolean userExists(Long id) {
        return userRepositoryPort.findById(id).isPresent();
    }
}
