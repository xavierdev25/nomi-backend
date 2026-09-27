package com.foodv.backend.domain.port.in.user;

import com.foodv.backend.domain.model.user.BudgetRange;
import com.foodv.backend.domain.model.user.User;
import com.foodv.backend.domain.model.user.UserRole;

import java.util.List;

/**
 * Alta de usuarios por un administrador (cualquier rol).
 */
public interface CreateUserUseCase {

    User execute(CreateUserCommand command);

    record CreateUserCommand(
            String nombres,
            String apellidos,
            String email,
            String password,
            String telefono,
            UserRole role,
            List<String> preferences,
            List<String> restrictions,
            BudgetRange budgetRange,
            List<String> cuisineTypes
    ) {}

}
