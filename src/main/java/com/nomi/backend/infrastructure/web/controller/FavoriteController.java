package com.nomi.backend.infrastructure.web.controller;

import com.nomi.backend.domain.port.in.favorite.FavoriteProductUseCase;
import com.nomi.backend.domain.port.in.favorite.FavoriteStoreUseCase;
import com.nomi.backend.infrastructure.security.AuthenticatedUserResolver;
import com.nomi.backend.infrastructure.web.dto.favorite.FavoriteCheckResponse;
import com.nomi.backend.infrastructure.web.dto.favorite.FavoriteProductResponse;
import com.nomi.backend.infrastructure.web.dto.favorite.FavoriteStoreResponse;
import com.nomi.backend.infrastructure.web.dto.product.ProductResponse;
import com.nomi.backend.infrastructure.web.dto.store.StoreResponse;
import com.nomi.backend.infrastructure.web.mapper.FavoriteWebMapper;
import com.nomi.backend.infrastructure.web.mapper.ProductWebMapper;
import com.nomi.backend.infrastructure.web.mapper.StoreWebMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Productos y tiendas favoritos del usuario autenticado.
 */
@Tag(name = "Favoritos")
@RestController
@RequestMapping("/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteProductUseCase favoriteProductUseCase;
    private final FavoriteStoreUseCase favoriteStoreUseCase;
    private final AuthenticatedUserResolver currentUser;
    private final FavoriteWebMapper favoriteWebMapper;
    private final ProductWebMapper productWebMapper;
    private final StoreWebMapper storeWebMapper;

    @Operation(summary = "Agregar producto favorito")
    @PostMapping("/products/{productId}")
    public ResponseEntity<FavoriteProductResponse> addProductFavorite(@PathVariable Long productId) {
        Long userId = currentUser.currentUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(favoriteWebMapper.toResponse(favoriteProductUseCase.addProductFavorite(userId, productId)));
    }

    @Operation(summary = "Quitar producto favorito")
    @DeleteMapping("/products/{productId}")
    public ResponseEntity<Void> removeProductFavorite(@PathVariable Long productId) {
        favoriteProductUseCase.removeProductFavorite(currentUser.currentUserId(), productId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Listar productos favoritos")
    @GetMapping("/products")
    public ResponseEntity<List<ProductResponse>> findFavoriteProducts() {
        List<ProductResponse> favorites = favoriteProductUseCase.findFavoriteProducts(currentUser.currentUserId()).stream()
                .map(productWebMapper::toResponse)
                .toList();
        return ResponseEntity.ok(favorites);
    }

    @Operation(summary = "Verificar si un producto es favorito")
    @GetMapping("/products/{productId}/check")
    public ResponseEntity<FavoriteCheckResponse> checkProductFavorite(@PathVariable Long productId) {
        return ResponseEntity.ok(new FavoriteCheckResponse(
                favoriteProductUseCase.isProductFavorite(currentUser.currentUserId(), productId)));
    }

    @Operation(summary = "Agregar tienda favorita")
    @PostMapping("/stores/{storeId}")
    public ResponseEntity<FavoriteStoreResponse> addStoreFavorite(@PathVariable Long storeId) {
        Long userId = currentUser.currentUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(favoriteWebMapper.toResponse(favoriteStoreUseCase.addStoreFavorite(userId, storeId)));
    }

    @Operation(summary = "Quitar tienda favorita")
    @DeleteMapping("/stores/{storeId}")
    public ResponseEntity<Void> removeStoreFavorite(@PathVariable Long storeId) {
        favoriteStoreUseCase.removeStoreFavorite(currentUser.currentUserId(), storeId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Listar tiendas favoritas")
    @GetMapping("/stores")
    public ResponseEntity<List<StoreResponse>> findFavoriteStores() {
        List<StoreResponse> favorites = favoriteStoreUseCase.findFavoriteStores(currentUser.currentUserId()).stream()
                .map(storeWebMapper::toResponse)
                .toList();
        return ResponseEntity.ok(favorites);
    }

    @Operation(summary = "Verificar si una tienda es favorita")
    @GetMapping("/stores/{storeId}/check")
    public ResponseEntity<FavoriteCheckResponse> checkStoreFavorite(@PathVariable Long storeId) {
        return ResponseEntity.ok(new FavoriteCheckResponse(
                favoriteStoreUseCase.isStoreFavorite(currentUser.currentUserId(), storeId)));
    }
}
