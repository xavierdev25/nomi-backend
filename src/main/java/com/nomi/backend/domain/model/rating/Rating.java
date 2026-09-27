package com.nomi.backend.domain.model.rating;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Calificación (1 a 5) de un pedido entregado; una por pedido.
 */
@Getter
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Rating {

    @EqualsAndHashCode.Include
    private Long id;

    private Long orderId;
    private Long userId;
    private Long storeId;
    private Integer rating;
    private String comentario;
    private LocalDateTime creadoEn;
}
