package com.foodv.backend.infrastructure.web.controller;

import com.foodv.backend.domain.common.PageQuery;
import com.foodv.backend.domain.common.PagedResult;
import com.foodv.backend.domain.model.product.Product;
import com.foodv.backend.domain.model.product.ProductCategory;
import com.foodv.backend.domain.model.user.User;
import com.foodv.backend.domain.model.user.UserRole;
import com.foodv.backend.domain.port.in.product.CreateProductUseCase;
import com.foodv.backend.domain.port.in.product.DeleteProductUseCase;
import com.foodv.backend.domain.port.in.product.FindProductUseCase;
import com.foodv.backend.domain.port.in.product.UpdateProductUseCase;
import com.foodv.backend.infrastructure.security.AuthenticatedUserResolver;
import com.foodv.backend.infrastructure.security.OwnershipService;
import com.foodv.backend.infrastructure.web.dto.common.PageResponse;
import com.foodv.backend.infrastructure.web.dto.product.CreateProductRequest;
import com.foodv.backend.infrastructure.web.dto.product.ProductResponse;
import com.foodv.backend.infrastructure.web.dto.product.UpdateProductRequest;
import com.foodv.backend.infrastructure.web.mapper.ProductWebMapper;
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

import java.math.BigDecimal;

/**
 * Catálogo: lectura para usuarios autenticados; alta, edición y baja para el comercio dueño
 * o un administrador.
 */
@Tag(name = "Productos")
@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final CreateProductUseCase createProductUseCase;
    private final FindProductUseCase findProductUseCase;
    private final UpdateProductUseCase updateProductUseCase;
    private final DeleteProductUseCase deleteProductUseCase;
    private final ProductWebMapper mapper;
    private final AuthenticatedUserResolver currentUser;
    private final OwnershipService ownershipService;

    @Operation(summary = "Crear producto en mi tienda (sólo COMERCIO/ADMIN)")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Producto creado"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "401", description = "No autenticado"),
            @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody CreateProductRequest request) {
        User me = currentUser.currentUserSummary();
        Long storeId = me.getRole() == UserRole.ADMIN
                ? ownershipService.resolveStoreIdForUser(me)
                : ownershipService.resolveStoreIdForUser(me);
        Product product = createProductUseCase.execute(mapper.toCommandWithStore(request, storeId));
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(product));
    }

    @Operation(summary = "Listar productos paginado")
    @GetMapping
    public ResponseEntity<PageResponse<ProductResponse>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy
    ) {
        return ResponseEntity.ok(PageResponse.from(
                findProductUseCase.findAllPaginated(new PageQuery(page, size, sortBy, true))
                        .map(mapper::toResponse)
        ));
    }

    @Operation(summary = "Obtener producto")
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> findById(@PathVariable Long id) {
        Product product = findProductUseCase.findById(id);
        return ResponseEntity.ok(mapper.toResponse(product));
    }

    @Operation(summary = "Productos por tienda paginado")
    @GetMapping("/store/{storeId}")
    public ResponseEntity<PageResponse<ProductResponse>> findByStoreId(
            @PathVariable Long storeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy
    ) {
        return ResponseEntity.ok(PageResponse.from(
                findProductUseCase.findByStoreIdPaginated(storeId, new PageQuery(page, size, sortBy, true))
                        .map(mapper::toResponse)
        ));
    }

    @Operation(summary = "Productos por categoría paginado")
    @GetMapping("/categoria/{categoria}")
    public ResponseEntity<PageResponse<ProductResponse>> findByCategoria(
            @PathVariable ProductCategory categoria,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy
    ) {
        return ResponseEntity.ok(PageResponse.from(
                findProductUseCase.findByCategoriaPaginated(categoria, new PageQuery(page, size, sortBy, true))
                        .map(mapper::toResponse)
        ));
    }

    @Operation(summary = "Actualizar producto (sólo dueño o ADMIN)")
    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> update(@PathVariable Long id,
                                                  @Valid @RequestBody UpdateProductRequest request) {
        ownershipService.requireProductOwnerOrAdmin(currentUser.currentUserSummary(), id);
        Product product = updateProductUseCase.execute(id, mapper.toCommand(request));
        return ResponseEntity.ok(mapper.toResponse(product));
    }

    @Operation(summary = "Eliminar producto (sólo dueño o ADMIN)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        ownershipService.requireProductOwnerOrAdmin(currentUser.currentUserSummary(), id);
        deleteProductUseCase.execute(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Buscar productos con filtros")
    @GetMapping("/search")
    public ResponseEntity<PageResponse<ProductResponse>> search(
            @RequestParam(required = false) String nombre,
            @RequestParam(required = false) ProductCategory categoria,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) BigDecimal precioMin,
            @RequestParam(required = false) BigDecimal precioMax,
            @RequestParam(required = false) Boolean disponible,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "nombre") String sortBy) {
        if (precioMin != null && precioMin.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("precioMin no puede ser negativo");
        }
        if (precioMax != null && precioMin != null && precioMax.compareTo(precioMin) < 0) {
            throw new IllegalArgumentException("precioMax debe ser >= precioMin");
        }
        PagedResult<Product> result = findProductUseCase.search(
                nombre, categoria, storeId, precioMin, precioMax, disponible,
                new PageQuery(page, size, sortBy, true));
        return ResponseEntity.ok(PageResponse.from(result.map(mapper::toResponse)));
    }
}
