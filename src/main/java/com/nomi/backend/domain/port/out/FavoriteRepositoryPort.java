package com.nomi.backend.domain.port.out;

import com.nomi.backend.domain.model.favorite.FavoriteProduct;
import com.nomi.backend.domain.model.favorite.FavoriteStore;

import java.util.List;
import java.util.Optional;

/**
 * Persistencia de favoritos.
 */
public interface FavoriteRepositoryPort {

    FavoriteProduct saveProductFavorite(FavoriteProduct favorite);

    FavoriteStore saveStoreFavorite(FavoriteStore favorite);

    Optional<FavoriteProduct> findProductFavorite(Long userId, Long productId);

    Optional<FavoriteStore> findStoreFavorite(Long userId, Long storeId);

    List<FavoriteProduct> findProductFavoritesByUserId(Long userId);

    List<FavoriteStore> findStoreFavoritesByUserId(Long userId);

    boolean existsProductFavorite(Long userId, Long productId);

    boolean existsStoreFavorite(Long userId, Long storeId);

    void deleteProductFavorite(Long userId, Long productId);

    void deleteStoreFavorite(Long userId, Long storeId);
}
