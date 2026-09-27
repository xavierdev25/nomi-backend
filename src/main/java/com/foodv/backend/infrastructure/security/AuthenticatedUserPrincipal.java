package com.foodv.backend.infrastructure.security;

import com.foodv.backend.domain.model.user.UserRole;
import org.springframework.security.core.AuthenticatedPrincipal;

/**
 * Usuario autenticado reconstruido desde los claims del JWT, sin consultar la base de datos.
 */
public record AuthenticatedUserPrincipal(
        Long userId,
        String email,
        UserRole role,
        String nombres
) implements AuthenticatedPrincipal {

    @Override
    public String getName() {
        return email;
    }
}
