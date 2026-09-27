package com.foodv.backend.infrastructure.web.mapper;

import com.foodv.backend.domain.model.rating.Rating;
import com.foodv.backend.domain.model.rating.StoreRatingSummary;
import com.foodv.backend.infrastructure.web.dto.rating.RatingResponse;
import com.foodv.backend.infrastructure.web.dto.rating.StoreRatingSummaryResponse;
import org.mapstruct.Mapper;

/**
 * Mapeo MapStruct entre DTOs web y el dominio de calificaciones.
 */
@Mapper(componentModel = "spring")
public interface RatingWebMapper {

    RatingResponse toResponse(Rating rating);

    StoreRatingSummaryResponse toResponse(StoreRatingSummary summary);
}
