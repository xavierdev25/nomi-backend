package com.foodv.backend.domain.port.in.favorite;

import com.foodv.backend.domain.model.favorite.FavoriteStore;
import com.foodv.backend.domain.model.store.Store;

import java.util.List;

/**
 * Gestión de tiendas favoritas de un usuario.
 */
public interface FavoriteStoreUseCase {

    FavoriteStore addStoreFavorite(Long userId, Long storeId);

    void removeStoreFavorite(Long userId, Long storeId);

    List<Store> findFavoriteStores(Long userId);

    boolean isStoreFavorite(Long userId, Long storeId);
}
