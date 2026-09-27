package com.nomi.backend.infrastructure.web.dto.common;

import com.nomi.backend.domain.common.PagedResult;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Página de resultados.")
public record PageResponse<T>(
        @Schema(description = "Elementos de la página")
        List<T> content,
        @Schema(description = "Total de elementos en todas las páginas", example = "42")
        long totalElements,
        @Schema(description = "Total de páginas", example = "3")
        int totalPages,
        @Schema(description = "Número de página, desde 0", example = "0")
        int currentPage,
        @Schema(description = "Tamaño de página", example = "20")
        int pageSize
) {
    public static <T> PageResponse<T> from(PagedResult<T> page) {
        return new PageResponse<>(
                page.content(),
                page.totalElements(),
                page.totalPages(),
                page.currentPage(),
                page.pageSize()
        );
    }
}
