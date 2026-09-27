package com.foodv.backend.infrastructure.common;

import com.foodv.backend.domain.common.PageQuery;
import com.foodv.backend.domain.common.PagedResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.function.Function;

/**
 * Conversión entre la paginación del dominio ({@code PageQuery}/{@code PagedResult}) y la de
 * Spring Data.
 */
public final class PagingMapper {

    private PagingMapper() {}

    public static Pageable toPageable(PageQuery query) {
        Sort sort = query.ascending()
                ? Sort.by(query.sortBy()).ascending()
                : Sort.by(query.sortBy()).descending();
        return PageRequest.of(query.page(), query.size(), sort);
    }

    public static <S, T> PagedResult<T> toDomain(Page<S> page, Function<S, T> mapper) {
        return new PagedResult<>(
                page.getContent().stream().map(mapper).toList(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber(),
                page.getSize()
        );
    }
}
