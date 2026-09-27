package com.foodv.backend.domain.port.in.auth;

import com.foodv.backend.domain.model.user.BudgetRange;
import com.foodv.backend.domain.model.user.User;
import com.foodv.backend.domain.model.user.UserRole;

import java.util.List;

/**
 * Auto-registro público de usuarios.
 */
public interface RegisterUseCase {

    User execute(RegisterCommand command);

    record RegisterCommand(
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
