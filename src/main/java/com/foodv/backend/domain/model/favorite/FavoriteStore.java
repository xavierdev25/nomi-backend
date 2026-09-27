package com.foodv.backend.domain.model.favorite;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Tienda marcada como favorita por un usuario.
 */
@Getter
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class FavoriteStore {

    @EqualsAndHashCode.Include
    private Long id;

    private Long userId;
    private Long storeId;
    private LocalDateTime creadoEn;
}
