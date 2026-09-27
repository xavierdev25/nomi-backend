package com.nomi.backend.infrastructure.web.controller;

import com.nomi.backend.domain.common.PageQuery;
import com.nomi.backend.domain.common.PagedResult;
import com.nomi.backend.domain.model.order.Order;
import com.nomi.backend.domain.model.order.OrderStatus;
import com.nomi.backend.domain.model.user.User;
import com.nomi.backend.domain.model.user.UserRole;
import com.nomi.backend.domain.port.in.order.CancelOrderUseCase;
import com.nomi.backend.domain.port.in.order.CreateOrderUseCase;
import com.nomi.backend.domain.port.in.order.FindOrderUseCase;
import com.nomi.backend.domain.port.in.order.UpdateOrderStatusUseCase;
import com.nomi.backend.domain.port.out.OrderHistoryPort;
import com.nomi.backend.infrastructure.security.AuthenticatedUserResolver;
import com.nomi.backend.infrastructure.security.OwnershipService;
import com.nomi.backend.infrastructure.web.dto.common.PageResponse;
import com.nomi.backend.infrastructure.web.dto.order.CancelOrderRequest;
import com.nomi.backend.infrastructure.web.dto.order.CreateOrderRequest;
import com.nomi.backend.infrastructure.web.dto.order.OrderResponse;
import com.nomi.backend.infrastructure.web.dto.order.OrderStatusHistoryResponse;
import com.nomi.backend.infrastructure.web.dto.order.UpdateOrderStatusRequest;
import com.nomi.backend.infrastructure.web.mapper.OrderWebMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

/**
 * Pedidos: creación (estudiantes), consulta con control de acceso, avance de estado (tienda,
 * repartidor o administrador) y cancelación.
 */
@Tag(name = "Órdenes")
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final CreateOrderUseCase createOrderUseCase;
    private final FindOrderUseCase findOrderUseCase;
    private final UpdateOrderStatusUseCase updateOrderStatusUseCase;
    private final CancelOrderUseCase cancelOrderUseCase;
    private final OrderHistoryPort orderHistoryPort;
    private final OrderWebMapper mapper;
    private final AuthenticatedUserResolver currentUser;
    private final OwnershipService ownershipService;

    @Operation(summary = "Crear orden")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Orden creada"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "401", description = "No autenticado"),
            @ApiResponse(responseCode = "404", description = "Recurso no encontrado")
    })
    @PostMapping
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        Long userId = currentUser.currentUserId();
        Order order = createOrderUseCase.execute(mapper.toCommandWithUser(request, userId));
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(order));
    }

    @Operation(summary = "Listar órdenes (sólo ADMIN)")
    @GetMapping
    public ResponseEntity<PageResponse<OrderResponse>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy
    ) {
        User me = currentUser.currentUserSummary();
        if (me.getRole() != UserRole.ADMIN) {
            throw new AccessDeniedException("Sólo ADMIN puede listar todas las órdenes");
        }
        PagedResult<Order> page1 = findOrderUseCase.findAllPaginated(new PageQuery(page, size, sortBy, true));
        return ResponseEntity.ok(mapper.toPageResponse(page1));
    }

    @Operation(summary = "Listar mis órdenes (filtra por rol)")
    @GetMapping("/me")
    public ResponseEntity<PageResponse<OrderResponse>> findMine(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy
    ) {
        String email = currentUser.currentEmail();
        return ResponseEntity.ok(mapper.toPageResponse(
                findOrderUseCase.findForUser(email, new PageQuery(page, size, sortBy, false))
        ));
    }

    @Operation(summary = "Obtener orden con verificación de ownership")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Orden encontrada"),
            @ApiResponse(responseCode = "401", description = "No autenticado"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado"),
            @ApiResponse(responseCode = "404", description = "Orden no encontrada")
    })
    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> findById(@PathVariable Long id) {
        String email = currentUser.currentEmail();
        return ResponseEntity.ok(mapper.toResponse(findOrderUseCase.findByIdForUser(id, email)));
    }

    @Operation(summary = "Órdenes por usuario (sólo el propio o ADMIN)")
    @GetMapping("/user/{userId}")
    public ResponseEntity<PageResponse<OrderResponse>> findByUserId(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy
    ) {
        ownershipService.requireSelfOrAdmin(currentUser.currentUserSummary(), userId);
        return ResponseEntity.ok(mapper.toPageResponse(
                findOrderUseCase.findByUserIdPaginated(userId, new PageQuery(page, size, sortBy, false))
        ));
    }

    @Operation(summary = "Órdenes por tienda (sólo dueño o ADMIN)")
    @GetMapping("/store/{storeId}")
    public ResponseEntity<PageResponse<OrderResponse>> findByStoreId(
            @PathVariable Long storeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy
    ) {
        ownershipService.requireStoreOwnerOrAdmin(currentUser.currentUserSummary(), storeId);
        return ResponseEntity.ok(mapper.toPageResponse(
                findOrderUseCase.findByStoreIdPaginated(storeId, new PageQuery(page, size, sortBy, false))
        ));
    }

    @Operation(summary = "Órdenes por estado (sólo ADMIN)")
    @GetMapping("/status/{status}")
    public ResponseEntity<PageResponse<OrderResponse>> findByStatus(
            @PathVariable OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy
    ) {
        if (currentUser.currentRole() != UserRole.ADMIN) {
            throw new AccessDeniedException("Sólo ADMIN puede listar por estado");
        }
        return ResponseEntity.ok(mapper.toPageResponse(
                findOrderUseCase.findByStatusPaginated(status, new PageQuery(page, size, sortBy, false))
        ));
    }

    @Operation(summary = "Cambiar estado de orden")
    @PatchMapping("/{id}/status")
    public ResponseEntity<OrderResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateOrderStatusRequest request) {
        User me = currentUser.currentUserSummary();
        if (me.getRole() == UserRole.ESTUDIANTE) {
            throw new AccessDeniedException("Estudiantes no pueden cambiar estado de órdenes");
        }
        ownershipService.requireOrderAccess(me, id);
        Order order = updateOrderStatusUseCase.execute(id, request.status(), me.getId());
        return ResponseEntity.ok(mapper.toResponse(order));
    }

    @Operation(summary = "Cancelar orden")
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<OrderResponse> cancel(
            @PathVariable Long id,
            @RequestBody(required = false) CancelOrderRequest request) {
        User me = currentUser.currentUserSummary();
        ownershipService.requireOrderAccess(me, id);
        String motivo = request != null ? request.motivo() : null;
        Order order = cancelOrderUseCase.execute(
                new CancelOrderUseCase.CancelOrderCommand(id, me.getId(), motivo));
        return ResponseEntity.ok(mapper.toResponse(order));
    }

    @Operation(summary = "Historial de estados (sólo participantes)")
    @GetMapping("/{id}/history")
    public ResponseEntity<java.util.List<OrderStatusHistoryResponse>> getHistory(@PathVariable Long id) {
        ownershipService.requireOrderAccess(currentUser.currentUserSummary(), id);
        java.util.List<OrderStatusHistoryResponse> result = orderHistoryPort.findByOrderId(id).stream()
                .map(h -> new OrderStatusHistoryResponse(
                        h.id(), h.orderId(), h.status(), h.changedBy(), h.notas(), h.creadoEn()))
                .toList();
        return ResponseEntity.ok(result);
    }
}
