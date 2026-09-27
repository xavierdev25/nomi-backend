package com.nomi.backend.application.order;

import com.nomi.backend.domain.model.order.Order;
import com.nomi.backend.domain.model.order.OrderStatus;
import com.nomi.backend.domain.model.product.Product;
import com.nomi.backend.domain.model.product.ProductCategory;
import com.nomi.backend.domain.model.store.Store;
import com.nomi.backend.domain.model.user.User;
import com.nomi.backend.domain.model.user.UserRole;
import com.nomi.backend.domain.port.in.order.CreateOrderUseCase;
import com.nomi.backend.domain.port.out.*;
import com.nomi.backend.domain.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Creación de pedidos: cálculo de importes y validaciones de usuario, tienda y stock.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CreateOrderHandler - Creación de órdenes")
class CreateOrderHandlerTest {

    @Mock private OrderRepositoryPort orderRepositoryPort;
    @Mock private ProductRepositoryPort productRepositoryPort;
    @Mock private UserRepositoryPort userRepositoryPort;
    @Mock private StoreRepositoryPort storeRepositoryPort;
    @Mock private AulaRepositoryPort aulaRepositoryPort;
    @Mock private BusinessMetricsPort metricsPort;
    @Mock private SecureRandomPort secureRandomPort;

    @InjectMocks private CreateOrderHandler createOrderHandler;

    private User user;
    private Store store;
    private Product product;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L).email("xavier@nomi.com")
                .role(UserRole.ESTUDIANTE).activo(true)
                .creadoEn(LocalDateTime.now()).build();

        store = Store.builder()
                .id(1L).nombre("Pollería Test")
                .ownerId(2L).activo(true)
                .ownerRole(UserRole.COMERCIO)
                .creadoEn(LocalDateTime.now()).build();

        product = Product.builder()
                .id(1L).nombre("Pollo a la brasa")
                .precio(BigDecimal.valueOf(12.50))
                .stock(10).activo(true).disponible(true)
                .categoria(ProductCategory.COMIDA)
                .storeId(1L)
                .creadoEn(LocalDateTime.now()).build();
    }

    private CreateOrderUseCase.CreateOrderCommand buildCommand(Long userId, Long storeId, Long aulaId,
                                                               List<CreateOrderUseCase.OrderItemCommand> items, String notas) {
        return new CreateOrderUseCase.CreateOrderCommand(
                userId, storeId, aulaId, items, notas, BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Crear orden exitosamente calcula total correctamente")
    void crear_orden_calcula_total_correctamente() {
        when(userRepositoryPort.findById(1L)).thenReturn(Optional.of(user));
        when(storeRepositoryPort.findById(1L)).thenReturn(Optional.of(store));
        when(aulaRepositoryPort.findById(1L)).thenReturn(Optional.of(
                com.nomi.backend.domain.model.aula.Aula.builder()
                        .id(1L).codigo("A-101").nombre("Aula 101").activo(true).build()
        ));
        when(productRepositoryPort.findById(1L)).thenReturn(Optional.of(product));
        when(productRepositoryPort.decrementStock(anyLong(), anyInt())).thenReturn(1);
        when(orderRepositoryPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(secureRandomPort.generateConfirmationCode(anyInt())).thenReturn("1234");

        Order result = createOrderHandler.execute(buildCommand(
                1L, 1L, 1L,
                List.of(new CreateOrderUseCase.OrderItemCommand(1L, 2)),
                "Sin ají"
        ));

        assertNotNull(result);
        assertEquals(OrderStatus.PENDIENTE, result.getStatus());
        assertEquals(0, BigDecimal.valueOf(25.00).compareTo(result.getTotal()));
        assertEquals(1, result.getItems().size());
        verify(metricsPort).recordOrderCreated();
    }

    @Test
    @DisplayName("Crear orden falla si usuario no existe")
    void crear_orden_falla_usuario_no_existe() {
        when(userRepositoryPort.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                createOrderHandler.execute(buildCommand(
                        99L, 1L, 1L,
                        List.of(new CreateOrderUseCase.OrderItemCommand(1L, 1)),
                        null
                ))
        );
    }

    @Test
    @DisplayName("Crear orden falla si tienda no está activa")
    void crear_orden_falla_tienda_inactiva() {
        Store inactiveStore = Store.builder()
                .id(1L).nombre("Cerrado").ownerId(2L)
                .activo(false).ownerRole(UserRole.COMERCIO)
                .creadoEn(LocalDateTime.now()).build();

        when(userRepositoryPort.findById(1L)).thenReturn(Optional.of(user));
        when(storeRepositoryPort.findById(1L)).thenReturn(Optional.of(inactiveStore));

        assertThrows(IllegalArgumentException.class, () ->
                createOrderHandler.execute(buildCommand(
                        1L, 1L, 1L,
                        List.of(new CreateOrderUseCase.OrderItemCommand(1L, 1)),
                        null
                ))
        );
    }

    @Test
    @DisplayName("Crear orden falla si stock insuficiente")
    void crear_orden_falla_stock_insuficiente() {
        Product lowStockProduct = Product.builder()
                .id(1L).nombre("Pollo")
                .precio(BigDecimal.valueOf(12.50))
                .stock(1).activo(true).disponible(true)
                .categoria(ProductCategory.COMIDA)
                .storeId(1L)
                .creadoEn(LocalDateTime.now()).build();

        when(userRepositoryPort.findById(1L)).thenReturn(Optional.of(user));
        when(storeRepositoryPort.findById(1L)).thenReturn(Optional.of(store));
        when(aulaRepositoryPort.findById(1L)).thenReturn(Optional.of(
                com.nomi.backend.domain.model.aula.Aula.builder()
                        .id(1L).codigo("A-101").nombre("Aula 101").activo(true).build()
        ));
        when(productRepositoryPort.findById(1L)).thenReturn(Optional.of(lowStockProduct));

        assertThrows(IllegalArgumentException.class, () ->
                createOrderHandler.execute(buildCommand(
                        1L, 1L, 1L,
                        List.of(new CreateOrderUseCase.OrderItemCommand(1L, 5)),
                        null
                ))
        );
    }
}
