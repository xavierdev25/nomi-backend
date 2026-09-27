package com.foodv.backend.application.favorite;

import com.foodv.backend.domain.model.favorite.FavoriteProduct;
import com.foodv.backend.domain.model.favorite.FavoriteStore;
import com.foodv.backend.domain.model.product.Product;
import com.foodv.backend.domain.model.store.Store;
import com.foodv.backend.domain.port.in.favorite.FavoriteProductUseCase;
import com.foodv.backend.domain.port.in.favorite.FavoriteStoreUseCase;
import com.foodv.backend.domain.port.out.FavoriteRepositoryPort;
import com.foodv.backend.domain.port.out.ProductRepositoryPort;
import com.foodv.backend.domain.port.out.StoreRepositoryPort;
import com.foodv.backend.domain.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Favoritos de productos y tiendas. Agregar un favorito que ya existe es idempotente.
 */
@Service
@RequiredArgsConstructor
public class FavoriteHandler implements FavoriteProductUseCase, FavoriteStoreUseCase {

    private final FavoriteRepositoryPort favoriteRepositoryPort;
    private final ProductRepositoryPort productRepositoryPort;
    private final StoreRepositoryPort storeRepositoryPort;

    @Override
    @Transactional
    public FavoriteProduct addProductFavorite(Long userId, Long productId) {
        productRepositoryPort.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado"));

        return favoriteRepositoryPort.findProductFavorite(userId, productId)
                .orElseGet(() -> favoriteRepositoryPort.saveProductFavorite(FavoriteProduct.builder()
                        .userId(userId)
                        .productId(productId)
                        .creadoEn(LocalDateTime.now())
                        .build()));
    }

    @Override
    @Transactional
    public void removeProductFavorite(Long userId, Long productId) {
        favoriteRepositoryPort.deleteProductFavorite(userId, productId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> findFavoriteProducts(Long userId) {
        List<Long> productIds = favoriteRepositoryPort.findProductFavoritesByUserId(userId).stream()
                .map(FavoriteProduct::getProductId)
                .toList();
        return productRepositoryPort.findAllById(productIds);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isProductFavorite(Long userId, Long productId) {
        return favoriteRepositoryPort.existsProductFavorite(userId, productId);
    }

    @Override
    @Transactional
    public FavoriteStore addStoreFavorite(Long userId, Long storeId) {
        storeRepositoryPort.findById(storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Tienda no encontrada"));

        return favoriteRepositoryPort.findStoreFavorite(userId, storeId)
                .orElseGet(() -> favoriteRepositoryPort.saveStoreFavorite(FavoriteStore.builder()
                        .userId(userId)
                        .storeId(storeId)
                        .creadoEn(LocalDateTime.now())
                        .build()));
    }

    @Override
    @Transactional
    public void removeStoreFavorite(Long userId, Long storeId) {
        favoriteRepositoryPort.deleteStoreFavorite(userId, storeId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Store> findFavoriteStores(Long userId) {
        List<Long> storeIds = favoriteRepositoryPort.findStoreFavoritesByUserId(userId).stream()
                .map(FavoriteStore::getStoreId)
                .toList();
        return storeRepositoryPort.findAllById(storeIds);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isStoreFavorite(Long userId, Long storeId) {
        return favoriteRepositoryPort.existsStoreFavorite(userId, storeId);
    }
}
