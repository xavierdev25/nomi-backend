package com.nomi.backend.application.rating;

import com.nomi.backend.domain.exception.AuthorizationException;
import com.nomi.backend.domain.exception.ResourceNotFoundException;
import com.nomi.backend.domain.model.order.Order;
import com.nomi.backend.domain.model.order.OrderStatus;
import com.nomi.backend.domain.model.rating.Rating;
import com.nomi.backend.domain.model.rating.StoreRatingSummary;
import com.nomi.backend.domain.port.in.rating.RatingUseCase;
import com.nomi.backend.domain.port.out.OrderRepositoryPort;
import com.nomi.backend.domain.port.out.RatingRepositoryPort;
import com.nomi.backend.domain.port.out.StoreRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Calificación de pedidos: solo el dueño, solo pedidos entregados y una vez por pedido.
 */
@Service
@RequiredArgsConstructor
public class RatingHandler implements RatingUseCase {

    private final RatingRepositoryPort ratingRepositoryPort;
    private final OrderRepositoryPort orderRepositoryPort;
    private final StoreRepositoryPort storeRepositoryPort;

    @Override
    @Transactional
    public Rating rateOrder(Long userId, Long orderId, Integer rating, String comentario) {
        validateRating(rating);
        validateComentario(comentario);

        Order order = orderRepositoryPort.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Orden no encontrada"));

        if (!order.getUserId().equals(userId)) {
            throw new AuthorizationException("Sólo el dueño de la orden puede calificarla");
        }

        if (order.getStatus() != OrderStatus.ENTREGADO) {
            throw new IllegalStateException("Sólo se puede calificar una orden entregada");
        }

        if (ratingRepositoryPort.existsByOrderId(orderId)) {
            throw new IllegalStateException("La orden ya fue calificada");
        }

        return ratingRepositoryPort.save(Rating.builder()
                .orderId(orderId)
                .userId(userId)
                .storeId(order.getStoreId())
                .rating(rating)
                .comentario(comentario)
                .creadoEn(LocalDateTime.now())
                .build());
    }

    @Override
    @Transactional(readOnly = true)
    public Rating findByOrderId(Long orderId) {
        return ratingRepositoryPort.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Calificación no encontrada"));
    }

    @Override
    @Transactional(readOnly = true)
    public StoreRatingSummary findStoreRatingSummary(Long storeId) {
        storeRepositoryPort.findById(storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Tienda no encontrada"));
        return ratingRepositoryPort.findStoreRatingSummary(storeId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Rating> findByStoreId(Long storeId) {
        storeRepositoryPort.findById(storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Tienda no encontrada"));
        return ratingRepositoryPort.findByStoreId(storeId);
    }

    private void validateRating(Integer rating) {
        if (rating == null || rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Rating debe estar entre 1 y 5");
        }
    }

    private void validateComentario(String comentario) {
        if (comentario != null && comentario.length() > 500) {
            throw new IllegalArgumentException("Comentario máximo 500 caracteres");
        }
    }
}
