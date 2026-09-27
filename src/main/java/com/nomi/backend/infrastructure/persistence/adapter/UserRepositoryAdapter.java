package com.nomi.backend.infrastructure.persistence.adapter;

import com.nomi.backend.domain.common.PageQuery;
import com.nomi.backend.domain.common.PagedResult;
import com.nomi.backend.domain.model.user.User;
import com.nomi.backend.domain.port.out.UserRepositoryPort;
import com.nomi.backend.infrastructure.common.PagingMapper;
import com.nomi.backend.infrastructure.persistence.entity.UserEntity;
import com.nomi.backend.infrastructure.persistence.repository.UserJpaRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Implementación JPA de {@code UserRepositoryPort}. Excluye usuarios borrados, normaliza el
 * email en las búsquedas y aplica el borrado lógico.
 */
@Component
public class UserRepositoryAdapter implements UserRepositoryPort {

    private final UserJpaRepository jpaRepository;
    private final UserEntityMapper mapper;

    public UserRepositoryAdapter(UserJpaRepository jpaRepository, UserEntityMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public User save(User user) {
        UserEntity entity = mapper.toEntity(user);
        UserEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<User> findById(Long id) {
        return jpaRepository.findByIdAndDeletedAtIsNull(id)
                .map(mapper::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        if (email == null) return Optional.empty();
        return jpaRepository.findByEmailAndDeletedAtIsNull(email.toLowerCase().trim())
                .map(mapper::toDomain);
    }

    @Override
    public boolean existsByEmail(String email) {
        if (email == null) return false;
        return jpaRepository.existsByEmailAndDeletedAtIsNull(email.toLowerCase().trim());
    }

    @Override
    public List<User> findAll() {
        return jpaRepository.findAllByDeletedAtIsNull()
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public PagedResult<User> findAllPaginated(PageQuery query) {
        return PagingMapper.toDomain(
                jpaRepository.findAllByDeletedAtIsNull(PagingMapper.toPageable(query)),
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
    public Optional<User> findByIdWithPreferences(Long id) {
        return findById(id);
    }
}
