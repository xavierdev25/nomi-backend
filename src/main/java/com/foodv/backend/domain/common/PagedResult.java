package com.foodv.backend.domain.common;

import java.util.List;
import java.util.function.Function;

/**
 * Página de resultados del dominio. {@code currentPage} empieza en 0.
 */
public record PagedResult<T>(
        List<T> content,
        long totalElements,
        int totalPages,
        int currentPage,
        int pageSize
) {

    /**
     * Transforma el contenido conservando los datos de paginación.
     */
    public <R> PagedResult<R> map(Function<T, R> mapper) {
        return new PagedResult<>(
                content.stream().map(mapper).toList(),
                totalElements,
                totalPages,
                currentPage,
                pageSize
        );
    }

    public boolean isEmpty() {
        return content == null || content.isEmpty();
    }
}
