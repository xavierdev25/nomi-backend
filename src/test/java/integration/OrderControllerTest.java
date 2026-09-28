package integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nomi.backend.domain.common.PagedResult;
import com.nomi.backend.domain.model.order.Order;
import com.nomi.backend.domain.model.order.OrderItem;
import com.nomi.backend.domain.model.order.OrderStatus;
import com.nomi.backend.domain.model.user.UserRole;
import com.nomi.backend.domain.port.in.order.CancelOrderUseCase;
import com.nomi.backend.domain.port.in.order.CreateOrderUseCase;
import com.nomi.backend.domain.port.in.order.FindOrderUseCase;
import com.nomi.backend.domain.port.in.order.UpdateOrderStatusUseCase;
import com.nomi.backend.domain.port.out.OrderHistoryPort;
import com.nomi.backend.domain.port.out.TokenBlacklistPort;
import com.nomi.backend.domain.port.out.TokenServicePort;
import com.nomi.backend.domain.port.out.UserRepositoryPort;
import com.nomi.backend.infrastructure.config.JwtConfig;
import com.nomi.backend.infrastructure.config.SecurityConfig;
import com.nomi.backend.infrastructure.security.AuthenticatedUserResolver;
import com.nomi.backend.infrastructure.security.JwtAuthenticationFilter;
import com.nomi.backend.infrastructure.security.JwtTokenServiceAdapter;
import com.nomi.backend.infrastructure.security.OwnershipService;
import com.nomi.backend.infrastructure.web.controller.GlobalExceptionHandler;
import com.nomi.backend.infrastructure.web.controller.OrderController;
import com.nomi.backend.infrastructure.web.dto.common.PageResponse;
import com.nomi.backend.infrastructure.web.dto.order.OrderItemResponse;
import com.nomi.backend.infrastructure.web.dto.order.OrderResponse;
import com.nomi.backend.infrastructure.web.mapper.OrderWebMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Endpoints de pedidos con la cadena de seguridad real: roles, ownership y respuestas
 * {@code 401}/{@code 403}.
 */
@SpringBootTest(
        classes = {
                OrderController.class,
                GlobalExceptionHandler.class,
                SecurityConfig.class,
                JwtAuthenticationFilter.class,
                JwtTokenServiceAdapter.class,
                AuthenticatedUserResolver.class,
                OrderControllerTest.TestBeans.class
        },
        properties = {
                "jwt.secret=test_jwt_secret_placeholder_minimum_32_bytes_long_value",
                "jwt.expiration=86400000",
                "jwt.refresh-expiration=604800000",
                "jwt.issuer=nomi-backend-test"
        }
)
@ImportAutoConfiguration({
        JacksonAutoConfiguration.class,
        HttpMessageConvertersAutoConfiguration.class,
        WebMvcAutoConfiguration.class
})
class OrderControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private TokenServicePort tokenServicePort;
    @Autowired private CreateOrderUseCase createOrderUseCase;
    @Autowired private FindOrderUseCase findOrderUseCase;

    @org.junit.jupiter.api.BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    void create_order_estudiante_returns_201() throws Exception {
        when(createOrderUseCase.execute(any())).thenReturn(order(1L));

        mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + token(UserRole.ESTUDIANTE, 1L, "owner@nomi.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createOrderBody())))
                .andExpect(status().isCreated());
    }

    @Test
    void create_order_repartidor_returns_403() throws Exception {
        mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + token(UserRole.REPARTIDOR, 2L, "rider@nomi.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createOrderBody())))
                .andExpect(status().isForbidden());
    }

    @Test
    void find_order_owner_returns_200() throws Exception {
        when(findOrderUseCase.findByIdForUser(10L, "owner@nomi.com")).thenReturn(order(10L));

        mockMvc.perform(get("/orders/10")
                        .header("Authorization", "Bearer " + token(UserRole.ESTUDIANTE, 1L, "owner@nomi.com")))
                .andExpect(status().isOk());
    }

    @Test
    void find_order_non_owner_returns_403() throws Exception {
        doThrow(new AccessDeniedException("No tienes permiso para ver esta orden"))
                .when(findOrderUseCase).findByIdForUser(10L, "other@nomi.com");

        mockMvc.perform(get("/orders/10")
                        .header("Authorization", "Bearer " + token(UserRole.ESTUDIANTE, 2L, "other@nomi.com")))
                .andExpect(status().isForbidden());
    }

    @Test
    void find_order_without_token_returns_401() throws Exception {
        mockMvc.perform(get("/orders/10"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Mismo camino que un token expirado: el filtro JWT lo descarta y la petición sigue como
     * anónima. Los clientes renuevan la sesión ante {@code 401}, no ante {@code 403}.
     */
    @Test
    void find_order_with_invalid_token_returns_401() throws Exception {
        mockMvc.perform(get("/orders/10")
                        .header("Authorization", "Bearer token.invalido.firma"))
                .andExpect(status().isUnauthorized());
    }

    private String token(UserRole role, Long userId, String email) {
        return tokenServicePort.generateAccessToken(userId, email, role);
    }

    private Map<String, Object> createOrderBody() {
        return Map.of(
                "storeId", 1L,
                "aulaId", 1L,
                "items", List.of(Map.of("productId", 1L, "cantidad", 1)),
                "notas", "Sin cebolla",
                "propina", BigDecimal.ZERO
        );
    }

    private Order order(Long id) {
        return Order.builder()
                .id(id)
                .userId(1L)
                .storeId(1L)
                .aulaId(1L)
                .items(List.of(OrderItem.builder()
                        .id(1L)
                        .productId(1L)
                        .productNombre("Menu")
                        .productPrecio(BigDecimal.TEN)
                        .cantidad(1)
                        .subtotal(BigDecimal.TEN)
                        .build()))
                .total(BigDecimal.TEN)
                .propina(BigDecimal.ZERO)
                .tarifaServicio(BigDecimal.ZERO)
                .comisionNomi(BigDecimal.ZERO)
                .status(OrderStatus.PENDIENTE)
                .creadoEn(LocalDateTime.now())
                .build();
    }

    @TestConfiguration
    static class TestBeans {
        @Bean CreateOrderUseCase createOrderUseCase() { return Mockito.mock(CreateOrderUseCase.class); }
        @Bean FindOrderUseCase findOrderUseCase() { return Mockito.mock(FindOrderUseCase.class); }
        @Bean UpdateOrderStatusUseCase updateOrderStatusUseCase() { return Mockito.mock(UpdateOrderStatusUseCase.class); }
        @Bean CancelOrderUseCase cancelOrderUseCase() { return Mockito.mock(CancelOrderUseCase.class); }
        @Bean OrderHistoryPort orderHistoryPort() { return Mockito.mock(OrderHistoryPort.class); }
        @Bean TokenBlacklistPort tokenBlacklistPort() { return Mockito.mock(TokenBlacklistPort.class); }
        @Bean UserRepositoryPort userRepositoryPort() { return Mockito.mock(UserRepositoryPort.class); }
        @Bean OwnershipService ownershipService() { return Mockito.mock(OwnershipService.class); }

        @Bean
        JwtConfig jwtConfig() {
            JwtConfig jwtConfig = new JwtConfig();
            jwtConfig.setSecret("test_jwt_secret_placeholder_minimum_32_bytes_long_value");
            jwtConfig.setExpiration(86_400_000L);
            jwtConfig.setRefreshExpiration(604_800_000L);
            jwtConfig.setIssuer("nomi-backend-test");
            return jwtConfig;
        }

        @Bean
        OrderWebMapper orderWebMapper() {
            return new OrderWebMapper() {
                @Override
                public OrderResponse toResponse(Order order) {
                    return new OrderResponse(
                            order.getId(), order.getUserId(), order.getStoreId(), order.getAulaId(),
                            order.getRepartidorId(),
                            order.getItems() == null ? List.of() : order.getItems().stream().map(this::toItemResponse).toList(),
                            order.getTotal(), order.getPropina(), order.getTarifaServicio(), order.getComisionNomi(),
                            order.getStatus(), order.getStatus().enEspanol(), order.getNotas(),
                            order.getMotivoCancelacion(), order.getCanceladoPor(), order.getCodigoConfirmacion(),
                            order.getFotoEntregaUrl(), order.getPagoExpiraEn(),
                            order.segundosParaPagar(java.time.LocalDateTime.now()),
                            order.getCreadoEn(), order.getActualizadoEn()
                    );
                }

                @Override
                public OrderItemResponse toItemResponse(OrderItem item) {
                    return new OrderItemResponse(item.getId(), item.getProductId(), item.getProductNombre(),
                            item.getProductPrecio(), item.getCantidad(), item.getSubtotal());
                }

                @Override
                public CreateOrderUseCase.CreateOrderCommand toCommand(com.nomi.backend.infrastructure.web.dto.order.CreateOrderRequest request) {
                    return toCommandWithUser(request, null);
                }

                @Override
                public CreateOrderUseCase.OrderItemCommand toItemCommand(com.nomi.backend.infrastructure.web.dto.order.OrderItemRequest request) {
                    return new CreateOrderUseCase.OrderItemCommand(request.productId(), request.cantidad());
                }

                @Override
                public PageResponse<OrderResponse> toPageResponse(PagedResult<Order> page) {
                    return OrderWebMapper.super.toPageResponse(page);
                }
            };
        }
    }
}
