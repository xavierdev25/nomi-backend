package com.nomi.backend.infrastructure.persistence.adapter;

import com.nomi.backend.domain.common.PageQuery;
import com.nomi.backend.domain.common.PagedResult;
import com.nomi.backend.domain.model.store.Store;
import com.nomi.backend.domain.port.out.StoreRepositoryPort;
import com.nomi.backend.infrastructure.common.PagingMapper;
import com.nomi.backend.infrastructure.persistence.repository.StoreJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Implementación JPA de {@code StoreRepositoryPort}. Excluye tiendas borradas y aplica el
 * borrado lógico.
 */
@Component
@RequiredArgsConstructor
public class StoreRepositoryAdapter implements StoreRepositoryPort {

    private final StoreJpaRepository jpaRepository;
    private final StoreEntityMapper mapper;

    @Override
    public Store save(Store store) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(store)));
    }

    @Override
    public Optional<Store> findById(Long id) {
        return jpaRepository.findByIdAndDeletedAtIsNull(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Store> findByOwnerId(Long ownerId) {
        return jpaRepository.findByOwnerIdAndDeletedAtIsNull(ownerId).map(mapper::toDomain);
    }

    @Override
    public boolean existsByOwnerId(Long ownerId) {
        return jpaRepository.findByOwnerIdAndDeletedAtIsNull(ownerId).isPresent();
    }

    @Override
    public List<Store> findAllById(List<Long> ids) {
        return jpaRepository.findByIdInAndDeletedAtIsNull(ids).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<Store> findAll() {
        return jpaRepository.findAllByDeletedAtIsNull().stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<Store> findAllActivas() {
        return jpaRepository.findByActivoTrueAndDeletedAtIsNull().stream().map(mapper::toDomain).toList();
    }

    @Override
    public PagedResult<Store> findAllPaginated(PageQuery query) {
        return PagingMapper.toDomain(
                jpaRepository.findAllByDeletedAtIsNull(PagingMapper.toPageable(query)),
                mapper::toDomain
        );
    }

    @Override
    public PagedResult<Store> findAllActivasPaginated(PageQuery query) {
        return PagingMapper.toDomain(
                jpaRepository.findByActivoTrueAndDeletedAtIsNull(PagingMapper.toPageable(query)),
                mapper::toDomain
        );
    }

    @Override
    public void deleteById(Long id) {
        jpaRepository.findByIdAndDeletedAtIsNull(id).ifPresent(entity -> {
            entity.setDeletedAt(LocalDateTime.now());
            jpaRepository.save(entity);
        });
    }

    @Override
    public PagedResult<Store> findByNombreContaining(String nombre, PageQuery query) {
        return PagingMapper.toDomain(
                jpaRepository.findByNombreContainingIgnoreCaseAndActivoTrueAndDeletedAtIsNull(
                        nombre == null ? "" : nombre,
                        PagingMapper.toPageable(query)
                ),
                mapper::toDomain
        );
    }
}
