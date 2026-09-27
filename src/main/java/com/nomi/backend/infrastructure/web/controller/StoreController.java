package com.nomi.backend.infrastructure.web.controller;

import com.nomi.backend.domain.common.PageQuery;
import com.nomi.backend.domain.model.store.Store;
import com.nomi.backend.domain.model.user.User;
import com.nomi.backend.domain.model.user.UserRole;
import com.nomi.backend.domain.port.in.store.CreateStoreUseCase;
import com.nomi.backend.domain.port.in.store.DeleteStoreUseCase;
import com.nomi.backend.domain.port.in.store.FindStoreUseCase;
import com.nomi.backend.domain.port.in.store.UpdateStoreUseCase;
import com.nomi.backend.domain.port.out.StoreRepositoryPort;
import com.nomi.backend.infrastructure.security.AuthenticatedUserResolver;
import com.nomi.backend.infrastructure.security.OwnershipService;
import com.nomi.backend.infrastructure.web.dto.common.PageResponse;
import com.nomi.backend.infrastructure.web.dto.store.CreateStoreRequest;
import com.nomi.backend.infrastructure.web.dto.store.StoreResponse;
import com.nomi.backend.infrastructure.web.dto.store.UpdateStoreRequest;
import com.nomi.backend.infrastructure.web.mapper.StoreWebMapper;
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
 * Tiendas: lectura para usuarios autenticados; alta para comercios y edición o baja para el
 * dueño o un administrador.
 */
@Tag(name = "Tiendas")
@RestController
@RequestMapping("/stores")
@RequiredArgsConstructor
public class StoreController {

    private final CreateStoreUseCase createStoreUseCase;
    private final FindStoreUseCase findStoreUseCase;
    private final UpdateStoreUseCase updateStoreUseCase;
    private final DeleteStoreUseCase deleteStoreUseCase;
    private final StoreWebMapper mapper;
    private final AuthenticatedUserResolver currentUser;
    private final OwnershipService ownershipService;
    private final StoreRepositoryPort storeRepositoryPort;

    @Operation(summary = "Crear tienda (sólo COMERCIO/ADMIN, asociada al usuario autenticado)")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Tienda creada"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "401", description = "No autenticado"),
            @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PostMapping
    public ResponseEntity<StoreResponse> create(@Valid @RequestBody CreateStoreRequest request) {
        User me = currentUser.currentUserSummary();
        if (me.getRole() != UserRole.COMERCIO && me.getRole() != UserRole.ADMIN) {
            throw new AccessDeniedException("Sólo COMERCIO o ADMIN pueden crear tiendas");
        }
        Store store = createStoreUseCase.execute(mapper.toCommandWithOwner(request, me.getId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(store));
    }

    @Operation(summary = "Listar tiendas activas paginado (público autenticado)")
    @GetMapping
    public ResponseEntity<PageResponse<StoreResponse>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy
    ) {
        return ResponseEntity.ok(PageResponse.from(
                storeRepositoryPort.findAllActivasPaginated(new PageQuery(page, size, sortBy, true))
                        .map(mapper::toResponse)
        ));
    }

    @Operation(summary = "Listar todas las tiendas (sólo ADMIN)")
    @GetMapping("/admin")
    public ResponseEntity<PageResponse<StoreResponse>> findAllAdmin(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy
    ) {
        if (currentUser.currentRole() != UserRole.ADMIN) {
            throw new AccessDeniedException("Sólo ADMIN");
        }
        return ResponseEntity.ok(PageResponse.from(
                storeRepositoryPort.findAllPaginated(new PageQuery(page, size, sortBy, true))
                        .map(mapper::toResponse)
        ));
    }

    @Operation(summary = "Obtener tienda por ID")
    @GetMapping("/{id}")
    public ResponseEntity<StoreResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(mapper.toResponse(findStoreUseCase.findById(id)));
    }

    @Operation(summary = "Mi tienda")
    @GetMapping("/me")
    public ResponseEntity<StoreResponse> findMine() {
        Store store = findStoreUseCase.findByOwnerId(currentUser.currentUserId());
        return ResponseEntity.ok(mapper.toResponse(store));
    }

    @Operation(summary = "Actualizar tienda (sólo dueño o ADMIN)")
    @PutMapping("/{id}")
    public ResponseEntity<StoreResponse> update(@PathVariable Long id,
                                                @Valid @RequestBody UpdateStoreRequest request) {
        ownershipService.requireStoreOwnerOrAdmin(currentUser.currentUserSummary(), id);
        Store store = updateStoreUseCase.execute(id, mapper.toCommand(request));
        return ResponseEntity.ok(mapper.toResponse(store));
    }

    @Operation(summary = "Eliminar tienda (sólo dueño o ADMIN)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        ownershipService.requireStoreOwnerOrAdmin(currentUser.currentUserSummary(), id);
        deleteStoreUseCase.execute(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Buscar tiendas por nombre")
    @GetMapping("/search")
    public ResponseEntity<PageResponse<StoreResponse>> search(
            @RequestParam(required = false, defaultValue = "") String nombre,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(PageResponse.from(
                storeRepositoryPort.findByNombreContaining(nombre, new PageQuery(page, size, "nombre", true))
                        .map(mapper::toResponse)
        ));
    }
}
