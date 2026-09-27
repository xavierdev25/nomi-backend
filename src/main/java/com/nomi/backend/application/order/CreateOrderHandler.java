package com.nomi.backend.application.order;

import com.nomi.backend.domain.exception.ResourceNotFoundException;
import com.nomi.backend.domain.model.order.Order;
import com.nomi.backend.domain.model.order.OrderItem;
import com.nomi.backend.domain.model.order.OrderStatus;
import com.nomi.backend.domain.model.product.Product;
import com.nomi.backend.domain.model.store.Store;
import com.nomi.backend.domain.port.in.order.CreateOrderUseCase;
import com.nomi.backend.domain.port.out.AulaRepositoryPort;
import com.nomi.backend.domain.port.out.BusinessMetricsPort;
import com.nomi.backend.domain.port.out.OrderRepositoryPort;
import com.nomi.backend.domain.port.out.ProductRepositoryPort;
import com.nomi.backend.domain.port.out.SecureRandomPort;
import com.nomi.backend.domain.port.out.StoreRepositoryPort;
import com.nomi.backend.domain.port.out.UserRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Crea un pedido en {@code PENDIENTE}.
 *
 * <p>Reglas: tienda activa, aula existente, todos los productos de esa tienda, activos,
 * disponibles y con stock. El stock se reserva al crear el pedido, antes de pagar. Importes:
 * {@code total} = subtotal de productos; propina de 0 al 50 % del subtotal; tarifa de servicio
 * y comisión fijas.
 */
@Service
@RequiredArgsConstructor
public class CreateOrderHandler implements CreateOrderUseCase {

    private static final BigDecimal TARIFA_SERVICIO = new BigDecimal("0.50");
    private static final BigDecimal COMISION_NOMI = new BigDecimal("0.20");
    private static final int MAX_PROPINA_RATIO_PERCENT = 50;

    private final OrderRepositoryPort orderRepositoryPort;
    private final ProductRepositoryPort productRepositoryPort;
    private final UserRepositoryPort userRepositoryPort;
    private final StoreRepositoryPort storeRepositoryPort;
    private final AulaRepositoryPort aulaRepositoryPort;
    private final BusinessMetricsPort metricsPort;
    private final SecureRandomPort secureRandomPort;

    @Override
    @Transactional
    public Order execute(CreateOrderCommand command) {
        Store store = validateParticipants(command);
        List<OrderItem> orderItems = buildAndValidateItems(command, store);
        Money totals = calculateTotals(orderItems, command.propina());
        Order savedOrder = orderRepositoryPort.save(buildOrder(command, orderItems, totals));
        metricsPort.recordOrderCreated();
        return savedOrder;
    }

    private Store validateParticipants(CreateOrderCommand command) {
        userRepositoryPort.findById(command.userId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        Store store = storeRepositoryPort.findById(command.storeId())
                .orElseThrow(() -> new ResourceNotFoundException("Tienda no encontrada"));

        if (!store.isActivo()) {
            throw new IllegalArgumentException("La tienda no está activa");
        }

        aulaRepositoryPort.findById(command.aulaId())
                .orElseThrow(() -> new ResourceNotFoundException("Aula no encontrada"));

        return store;
    }

    private List<OrderItem> buildAndValidateItems(CreateOrderCommand command, Store store) {
        if (command.items() == null || command.items().isEmpty()) {
            throw new IllegalArgumentException("La orden debe tener al menos un item");
        }

        Map<Long, Integer> aggregatedQuantities = new HashMap<>();
        for (OrderItemCommand item : command.items()) {
            if (item.cantidad() == null || item.cantidad() < 1) {
                throw new IllegalArgumentException("Cantidad inválida para producto " + item.productId());
            }
            // El mismo producto en varias líneas se suma antes de validar el stock.
            aggregatedQuantities.merge(item.productId(), item.cantidad(), Integer::sum);
        }

        List<OrderItem> orderItems = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : aggregatedQuantities.entrySet()) {
            Long productId = entry.getKey();
            int cantidad = entry.getValue();

            Product product = productRepositoryPort.findById(productId)
                    .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado: " + productId));

            if (!product.getStoreId().equals(store.getId())) {
                throw new IllegalArgumentException(
                        "El producto " + product.getNombre() + " no pertenece a la tienda seleccionada");
            }
            if (!product.isActivo() || !product.isDisponible()) {
                throw new IllegalArgumentException("Producto no disponible: " + product.getNombre());
            }
            if (product.getStock() < cantidad) {
                throw new IllegalArgumentException("Stock insuficiente para: " + product.getNombre());
            }

            // Descuento atómico: si otro pedido se llevó el stock entre la lectura y aquí, falla en vez de
            // dejarlo negativo.
            int updated = productRepositoryPort.decrementStock(productId, cantidad);
            if (updated == 0) {
                throw new IllegalArgumentException(
                        "No fue posible reservar stock para: " + product.getNombre() +
                        ". Inténtalo de nuevo.");
            }

            BigDecimal subtotal = product.getPrecio().multiply(BigDecimal.valueOf(cantidad));
            orderItems.add(OrderItem.builder()
                    .productId(product.getId())
                    .productNombre(product.getNombre())
                    .productPrecio(product.getPrecio())
                    .cantidad(cantidad)
                    .subtotal(subtotal)
                    .build());
        }
        return orderItems;
    }

    private Money calculateTotals(List<OrderItem> orderItems, BigDecimal requestedTip) {
        BigDecimal totalProductos = orderItems.stream()
                .map(OrderItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal propina = requestedTip == null ? BigDecimal.ZERO : requestedTip;
        if (propina.signum() < 0) {
            throw new IllegalArgumentException("La propina no puede ser negativa");
        }
        BigDecimal maxPropina = totalProductos
                .multiply(BigDecimal.valueOf(MAX_PROPINA_RATIO_PERCENT))
                .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
        if (propina.compareTo(maxPropina) > 0) {
            throw new IllegalArgumentException("La propina excede el máximo permitido");
        }
        return new Money(totalProductos, propina, TARIFA_SERVICIO, COMISION_NOMI);
    }

    private Order buildOrder(CreateOrderCommand command, List<OrderItem> orderItems, Money totals) {
        return Order.builder()
                .userId(command.userId())
                .storeId(command.storeId())
                .aulaId(command.aulaId())
                .items(orderItems)
                .total(totals.totalProductos())
                .propina(totals.propina())
                .tarifaServicio(totals.tarifaServicio())
                .comisionNomi(totals.comisionNomi())
                .codigoConfirmacion(secureRandomPort.generateConfirmationCode(4))
                .status(OrderStatus.PENDIENTE)
                .notas(command.notas())
                .creadoEn(LocalDateTime.now())
                .actualizadoEn(LocalDateTime.now())
                .build();
    }

    private record Money(
            BigDecimal totalProductos,
            BigDecimal propina,
            BigDecimal tarifaServicio,
            BigDecimal comisionNomi
    ) {}
}
