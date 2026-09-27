package com.nomi.backend.infrastructure.web.mapper;

import com.nomi.backend.domain.model.user.User;
import com.nomi.backend.domain.port.in.user.CreateUserUseCase;
import com.nomi.backend.domain.port.in.user.UpdateUserUseCase;
import com.nomi.backend.infrastructure.web.dto.user.CreateUserRequest;
import com.nomi.backend.infrastructure.web.dto.user.UpdateUserRequest;
import com.nomi.backend.infrastructure.web.dto.user.UserResponse;
import org.mapstruct.Mapper;

/**
 * Mapeo MapStruct entre DTOs web y el dominio de usuarios.
 */
@Mapper(componentModel = "spring")
public interface UserWebMapper {

    UserResponse toResponse(User user);

    CreateUserUseCase.CreateUserCommand toCommand(CreateUserRequest request);

    UpdateUserUseCase.UpdateUserCommand toCommand(UpdateUserRequest request);
}
