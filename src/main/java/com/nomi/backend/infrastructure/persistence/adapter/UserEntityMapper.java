package com.nomi.backend.infrastructure.persistence.adapter;

import com.nomi.backend.domain.model.user.User;
import com.nomi.backend.infrastructure.persistence.entity.UserEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapeo MapStruct entre {@code User} y {@code UserEntity}. {@code deletedAt} no se copia desde
 * el dominio.
 */
@Mapper(componentModel = "spring")
public interface UserEntityMapper {

    @Mapping(target = "deletedAt", ignore = true)
    UserEntity toEntity(User user);

    User toDomain(UserEntity entity);
}
