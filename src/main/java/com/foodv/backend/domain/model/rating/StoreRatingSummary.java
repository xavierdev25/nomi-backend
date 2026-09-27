package com.foodv.backend.domain.model.rating;

import lombok.Builder;
import lombok.Getter;

/**
 * Promedio y número de calificaciones de una tienda.
 */
@Getter
@Builder
public class StoreRatingSummary {

    private Long storeId;
    private Double promedio;
    private Long total;
}
