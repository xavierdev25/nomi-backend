package com.nomi.backend.infrastructure.persistence.adapter;

import com.nomi.backend.domain.model.rating.Rating;
import com.nomi.backend.infrastructure.persistence.entity.RatingEntity;
import org.mapstruct.Mapper;

/**
 * Mapeo MapStruct entre {@code Rating} y {@code RatingEntity}.
 */
@Mapper(componentModel = "spring")
public interface RatingEntityMapper {

    RatingEntity toEntity(Rating rating);

    Rating toDomain(RatingEntity entity);
}
