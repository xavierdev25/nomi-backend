package com.nomi.backend.infrastructure.security;

import com.nomi.backend.domain.model.user.User;
import com.nomi.backend.domain.model.user.UserRole;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Acceso al usuario autenticado de la petición en curso.
 */
@Component
public class AuthenticatedUserResolver {

    public User currentUser() {
        return currentUserSummary();
    }

    /**
     * Usuario construido solo con los datos del token (id, email, rol, nombres). Apellidos,
     * teléfono y preferencias no están: {@code GET /users/me} devuelve este resumen (ver la
     * auditoría técnica, M6).
     *
     * @throws org.springframework.security.access.AccessDeniedException si no hay usuario autenticado
     */
    public User currentUserSummary() {
        AuthenticatedUserPrincipal principal = currentPrincipal();
        return User.builder()
                .id(principal.userId())
                .nombres(principal.nombres())
                .email(principal.email())
                .role(principal.role())
                .activo(true)
                .build();
    }

    public Long currentUserId() {
        return currentPrincipal().userId();
    }

    public String currentEmail() {
        return currentPrincipal().email();
    }

    public UserRole currentRole() {
        return currentPrincipal().role();
    }

    private AuthenticatedUserPrincipal currentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("No autenticado");
        }
        if (auth.getPrincipal() instanceof AuthenticatedUserPrincipal principal) {
            return principal;
        }
        throw new AccessDeniedException("No autenticado");
    }
}
