package com.nomi.backend.infrastructure.persistence.adapter;

import com.nomi.backend.domain.model.favorite.FavoriteProduct;
import com.nomi.backend.domain.model.favorite.FavoriteStore;
import com.nomi.backend.domain.port.out.FavoriteRepositoryPort;
import com.nomi.backend.infrastructure.persistence.repository.FavoriteProductJpaRepository;
import com.nomi.backend.infrastructure.persistence.repository.FavoriteStoreJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Implementación JPA de {@code FavoriteRepositoryPort}.
 */
@Component
@RequiredArgsConstructor
public class FavoriteRepositoryAdapter implements FavoriteRepositoryPort {

    private final FavoriteProductJpaRepository favoriteProductJpaRepository;
    private final FavoriteStoreJpaRepository favoriteStoreJpaRepository;
    private final FavoriteProductEntityMapper favoriteProductMapper;
    private final FavoriteStoreEntityMapper favoriteStoreMapper;

    @Override
    public FavoriteProduct saveProductFavorite(FavoriteProduct favorite) {
        return favoriteProductMapper.toDomain(favoriteProductJpaRepository.save(favoriteProductMapper.toEntity(favorite)));
    }

    @Override
    public FavoriteStore saveStoreFavorite(FavoriteStore favorite) {
        return favoriteStoreMapper.toDomain(favoriteStoreJpaRepository.save(favoriteStoreMapper.toEntity(favorite)));
    }

    @Override
    public Optional<FavoriteProduct> findProductFavorite(Long userId, Long productId) {
        return favoriteProductJpaRepository.findByUserIdAndProductId(userId, productId)
                .map(favoriteProductMapper::toDomain);
    }

    @Override
    public Optional<FavoriteStore> findStoreFavorite(Long userId, Long storeId) {
        return favoriteStoreJpaRepository.findByUserIdAndStoreId(userId, storeId)
                .map(favoriteStoreMapper::toDomain);
    }

    @Override
    public List<FavoriteProduct> findProductFavoritesByUserId(Long userId) {
        return favoriteProductJpaRepository.findByUserIdOrderByCreadoEnDesc(userId).stream()
                .map(favoriteProductMapper::toDomain)
                .toList();
    }

    @Override
    public List<FavoriteStore> findStoreFavoritesByUserId(Long userId) {
        return favoriteStoreJpaRepository.findByUserIdOrderByCreadoEnDesc(userId).stream()
                .map(favoriteStoreMapper::toDomain)
                .toList();
    }

    @Override
    public boolean existsProductFavorite(Long userId, Long productId) {
        return favoriteProductJpaRepository.existsByUserIdAndProductId(userId, productId);
    }

    @Override
    public boolean existsStoreFavorite(Long userId, Long storeId) {
        return favoriteStoreJpaRepository.existsByUserIdAndStoreId(userId, storeId);
    }

    @Override
    public void deleteProductFavorite(Long userId, Long productId) {
        favoriteProductJpaRepository.deleteByUserIdAndProductId(userId, productId);
    }

    @Override
    public void deleteStoreFavorite(Long userId, Long storeId) {
        favoriteStoreJpaRepository.deleteByUserIdAndStoreId(userId, storeId);
    }
}
