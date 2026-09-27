package com.foodv.backend.domain.model.ai;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Valoración del usuario (me gusta / no me gusta) sobre un producto recomendado. Hay como
 * máximo una por usuario y producto.
 */
@Getter
@Builder
public class AiFeedback {
    private Long id;
    private Long userId;
    private Long productId;
    private Boolean liked;
    private String context;
    private LocalDateTime creadoEn;
}
