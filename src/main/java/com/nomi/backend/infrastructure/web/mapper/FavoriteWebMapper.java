package com.nomi.backend.infrastructure.web.mapper;

import com.nomi.backend.domain.model.favorite.FavoriteProduct;
import com.nomi.backend.domain.model.favorite.FavoriteStore;
import com.nomi.backend.infrastructure.web.dto.favorite.FavoriteProductResponse;
import com.nomi.backend.infrastructure.web.dto.favorite.FavoriteStoreResponse;
import org.mapstruct.Mapper;

/**
 * Mapeo MapStruct entre favoritos del dominio y sus DTOs.
 */
@Mapper(componentModel = "spring")
public interface FavoriteWebMapper {

    FavoriteProductResponse toResponse(FavoriteProduct favorite);

    FavoriteStoreResponse toResponse(FavoriteStore favorite);
}
