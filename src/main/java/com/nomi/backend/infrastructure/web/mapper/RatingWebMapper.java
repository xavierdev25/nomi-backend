package com.nomi.backend.infrastructure.web.mapper;

import com.nomi.backend.domain.model.rating.Rating;
import com.nomi.backend.domain.model.rating.StoreRatingSummary;
import com.nomi.backend.infrastructure.web.dto.rating.RatingResponse;
import com.nomi.backend.infrastructure.web.dto.rating.StoreRatingSummaryResponse;
import org.mapstruct.Mapper;

/**
 * Mapeo MapStruct entre DTOs web y el dominio de calificaciones.
 */
@Mapper(componentModel = "spring")
public interface RatingWebMapper {

    RatingResponse toResponse(Rating rating);

    StoreRatingSummaryResponse toResponse(StoreRatingSummary summary);
}
