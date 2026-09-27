package com.nomi.backend.domain.port.in.favorite;

import com.nomi.backend.domain.model.favorite.FavoriteStore;
import com.nomi.backend.domain.model.store.Store;

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
