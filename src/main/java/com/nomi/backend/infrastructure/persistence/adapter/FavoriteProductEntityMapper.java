package com.nomi.backend.infrastructure.persistence.adapter;

import com.nomi.backend.domain.model.favorite.FavoriteProduct;
import com.nomi.backend.infrastructure.persistence.entity.FavoriteProductEntity;
import org.mapstruct.Mapper;

/**
 * Mapeo MapStruct entre {@code FavoriteProduct} y su entidad.
 */
@Mapper(componentModel = "spring")
public interface FavoriteProductEntityMapper {

    FavoriteProductEntity toEntity(FavoriteProduct favorite);

    FavoriteProduct toDomain(FavoriteProductEntity entity);
}
