package com.foodv.backend.infrastructure.persistence.adapter;

import com.foodv.backend.domain.model.store.Store;
import com.foodv.backend.infrastructure.persistence.entity.StoreEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapeo MapStruct entre {@code Store} y {@code StoreEntity}. {@code deletedAt} no se copia
 * desde el dominio.
 */
@Mapper(componentModel = "spring")
public interface StoreEntityMapper {

    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "horarioApertura", source = "horarioApertura")
    @Mapping(target = "horarioCierre", source = "horarioCierre")
    StoreEntity toEntity(Store store);

    @Mapping(target = "horarioApertura", source = "horarioApertura")
    @Mapping(target = "horarioCierre", source = "horarioCierre")
    Store toDomain(StoreEntity entity);
}
