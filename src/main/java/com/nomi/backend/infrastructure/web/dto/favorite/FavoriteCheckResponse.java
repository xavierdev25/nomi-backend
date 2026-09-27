package com.nomi.backend.infrastructure.web.dto.favorite;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Si el recurso está en los favoritos del usuario.")
public record FavoriteCheckResponse(
        @Schema(description = "true si es favorito", example = "true")
        boolean favorite
) {}
