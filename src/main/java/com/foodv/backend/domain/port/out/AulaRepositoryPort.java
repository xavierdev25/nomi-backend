package com.foodv.backend.domain.port.out;

import com.foodv.backend.domain.model.aula.Aula;

import java.util.List;
import java.util.Optional;

/**
 * Persistencia de aulas.
 */
public interface AulaRepositoryPort {

    Aula save(Aula aula);

    Optional<Aula> findById(Long id);

    Optional<Aula> findByCodigo(String codigo);

    boolean existsByCodigo(String codigo);

    List<Aula> findAll();

    List<Aula> findAllActivas();

    void deleteById(Long id);
}
