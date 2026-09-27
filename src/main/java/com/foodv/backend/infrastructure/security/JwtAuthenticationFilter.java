package com.foodv.backend.infrastructure.security;

import com.foodv.backend.domain.model.user.UserRole;
import com.foodv.backend.domain.port.out.TokenBlacklistPort;
import com.foodv.backend.domain.port.out.TokenServicePort;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Autentica cada petición con el access token de la cabecera {@code Authorization: Bearer}.
 *
 * <p>Un token ausente, inválido, expirado, revocado o emitido antes de un cierre de sesiones
 * global no corta la petición: sigue como anónima y la autorización de la ruta decide
 * (responde {@code 401}, ver {@code SecurityConfig}).
 */
@Order(2)
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final TokenServicePort tokenServicePort;
    private final TokenBlacklistPort tokenBlacklistPort;

    public JwtAuthenticationFilter(TokenServicePort tokenServicePort,
                                   TokenBlacklistPort tokenBlacklistPort) {
        this.tokenServicePort = tokenServicePort;
        this.tokenBlacklistPort = tokenBlacklistPort;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        if (tokenBlacklistPort.isBlacklisted(token) || !tokenServicePort.isTokenValid(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        Long userId;
        String email;
        String nombres;
        UserRole role;
        long issuedAtMillis;
        try {
            userId = tokenServicePort.extractUserId(token);
            email = tokenServicePort.extractEmail(token);
            nombres = tokenServicePort.extractNombres(token);
            role = UserRole.valueOf(tokenServicePort.extractRole(token));
            issuedAtMillis = tokenServicePort.extractIssuedAtMillis(token);
        } catch (Exception e) {
            filterChain.doFilter(request, response);
            return;
        }

        if (userId == null || tokenBlacklistPort.isUserSessionInvalidated(userId, issuedAtMillis)) {
            filterChain.doFilter(request, response);
            return;
        }

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        new AuthenticatedUserPrincipal(userId, email, role, nombres),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))
                );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }
}
