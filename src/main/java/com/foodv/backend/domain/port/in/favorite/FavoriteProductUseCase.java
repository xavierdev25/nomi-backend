package com.foodv.backend.domain.port.in.favorite;

import com.foodv.backend.domain.model.favorite.FavoriteProduct;
import com.foodv.backend.domain.model.product.Product;

import java.util.List;

/**
 * Gestión de productos favoritos de un usuario.
 */
public interface FavoriteProductUseCase {

    FavoriteProduct addProductFavorite(Long userId, Long productId);

    void removeProductFavorite(Long userId, Long productId);

    List<Product> findFavoriteProducts(Long userId);

    boolean isProductFavorite(Long userId, Long productId);
}
