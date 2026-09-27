package com.foodv.backend.infrastructure.web.mapper;

import com.foodv.backend.domain.model.user.User;
import com.foodv.backend.domain.port.in.user.CreateUserUseCase;
import com.foodv.backend.domain.port.in.user.UpdateUserUseCase;
import com.foodv.backend.infrastructure.web.dto.user.CreateUserRequest;
import com.foodv.backend.infrastructure.web.dto.user.UpdateUserRequest;
import com.foodv.backend.infrastructure.web.dto.user.UserResponse;
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
