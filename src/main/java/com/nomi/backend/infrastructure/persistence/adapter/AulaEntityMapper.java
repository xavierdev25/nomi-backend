package com.nomi.backend.infrastructure.persistence.adapter;

import com.nomi.backend.domain.model.aula.Aula;
import com.nomi.backend.infrastructure.persistence.entity.AulaEntity;
import org.mapstruct.Mapper;

/**
 * Mapeo MapStruct entre {@code Aula} y {@code AulaEntity}.
 */
@Mapper(componentModel = "spring")
public interface AulaEntityMapper {

    AulaEntity toEntity(Aula aula);

    Aula toDomain(AulaEntity entity);
}
