package com.nomi.backend.infrastructure.persistence.adapter;

import com.nomi.backend.domain.model.aula.Aula;
import com.nomi.backend.domain.port.out.AulaRepositoryPort;
import com.nomi.backend.infrastructure.persistence.repository.AulaJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Implementación JPA de {@code AulaRepositoryPort}.
 */
@Component
@RequiredArgsConstructor
public class AulaRepositoryAdapter implements AulaRepositoryPort {

    private final AulaJpaRepository jpaRepository;
    private final AulaEntityMapper mapper;

    @Override
    public Aula save(Aula aula) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(aula)));
    }

    @Override
    public Optional<Aula> findById(Long id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Aula> findByCodigo(String codigo) {
        return jpaRepository.findByCodigo(codigo).map(mapper::toDomain);
    }

    @Override
    public boolean existsByCodigo(String codigo) {
        return jpaRepository.existsByCodigo(codigo);
    }

    @Override
    public List<Aula> findAll() {
        return jpaRepository.findAll().stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<Aula> findAllActivas() {
        return jpaRepository.findByActivoTrue().stream().map(mapper::toDomain).toList();
    }

    @Override
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }
}
