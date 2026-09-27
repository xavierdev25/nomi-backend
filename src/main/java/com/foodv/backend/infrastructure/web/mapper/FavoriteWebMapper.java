package com.foodv.backend.infrastructure.web.mapper;

import com.foodv.backend.domain.model.favorite.FavoriteProduct;
import com.foodv.backend.domain.model.favorite.FavoriteStore;
import com.foodv.backend.infrastructure.web.dto.favorite.FavoriteProductResponse;
import com.foodv.backend.infrastructure.web.dto.favorite.FavoriteStoreResponse;
import org.mapstruct.Mapper;

/**
 * Mapeo MapStruct entre favoritos del dominio y sus DTOs.
 */
@Mapper(componentModel = "spring")
public interface FavoriteWebMapper {

    FavoriteProductResponse toResponse(FavoriteProduct favorite);

    FavoriteStoreResponse toResponse(FavoriteStore favorite);
}
