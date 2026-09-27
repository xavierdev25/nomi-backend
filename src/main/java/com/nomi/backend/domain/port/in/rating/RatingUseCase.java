package com.nomi.backend.domain.port.in.rating;

import com.nomi.backend.domain.model.rating.Rating;
import com.nomi.backend.domain.model.rating.StoreRatingSummary;

import java.util.List;

/**
 * Calificación de pedidos y resumen por tienda.
 */
public interface RatingUseCase {

    Rating rateOrder(Long userId, Long orderId, Integer rating, String comentario);

    Rating findByOrderId(Long orderId);

    StoreRatingSummary findStoreRatingSummary(Long storeId);

    List<Rating> findByStoreId(Long storeId);
}
