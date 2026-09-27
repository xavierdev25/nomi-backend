package com.foodv.backend.infrastructure.web.controller;

import com.foodv.backend.domain.port.in.rating.RatingUseCase;
import com.foodv.backend.infrastructure.security.AuthenticatedUserResolver;
import com.foodv.backend.infrastructure.web.dto.rating.CreateRatingRequest;
import com.foodv.backend.infrastructure.web.dto.rating.RatingResponse;
import com.foodv.backend.infrastructure.web.dto.rating.StoreRatingSummaryResponse;
import com.foodv.backend.infrastructure.web.mapper.RatingWebMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Calificación de pedidos y resumen de calificaciones por tienda.
 */
@Tag(name = "Calificaciones")
@RestController
@RequestMapping("/ratings")
@RequiredArgsConstructor
public class RatingController {

    private final RatingUseCase ratingUseCase;
    private final AuthenticatedUserResolver currentUser;
    private final RatingWebMapper ratingWebMapper;

    @Operation(summary = "Calificar una orden entregada")
    @PostMapping("/orders/{orderId}")
    public ResponseEntity<RatingResponse> rateOrder(
            @PathVariable Long orderId,
            @Valid @RequestBody CreateRatingRequest request) {
        Long userId = currentUser.currentUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ratingWebMapper.toResponse(
                        ratingUseCase.rateOrder(userId, orderId, request.rating(), request.comentario())));
    }

    @Operation(summary = "Obtener calificación de una orden")
    @GetMapping("/orders/{orderId}")
    public ResponseEntity<RatingResponse> findByOrderId(@PathVariable Long orderId) {
        return ResponseEntity.ok(ratingWebMapper.toResponse(ratingUseCase.findByOrderId(orderId)));
    }

    @Operation(summary = "Obtener rating promedio de una tienda")
    @GetMapping("/stores/{storeId}")
    public ResponseEntity<StoreRatingSummaryResponse> findStoreRatingSummary(@PathVariable Long storeId) {
        return ResponseEntity.ok(ratingWebMapper.toResponse(ratingUseCase.findStoreRatingSummary(storeId)));
    }

    @Operation(summary = "Listar todas las reseñas de una tienda")
    @GetMapping("/stores/{storeId}/all")
    public ResponseEntity<List<RatingResponse>> findByStoreId(@PathVariable Long storeId) {
        List<RatingResponse> ratings = ratingUseCase.findByStoreId(storeId).stream()
                .map(ratingWebMapper::toResponse)
                .toList();
        return ResponseEntity.ok(ratings);
    }
}
