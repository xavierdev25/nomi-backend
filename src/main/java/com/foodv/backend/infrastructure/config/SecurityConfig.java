package com.foodv.backend.infrastructure.config;

import com.foodv.backend.infrastructure.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * Seguridad HTTP: API sin estado con JWT Bearer, reglas de acceso por ruta y rol, y respuesta
 * {@code 401} ante falta de autenticación.
 *
 * <p>Las rutas públicas son login, registro, refresh, el webhook de pagos y el handshake de
 * WebSocket. El resto requiere autenticación, y además hay controles de ownership por recurso
 * en {@code OwnershipService}.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final Environment environment;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, Environment environment) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.environment = environment;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                        configureActuatorAccess(auth);
                        configureDocumentationAccess(auth);
                        auth

                        .requestMatchers(HttpMethod.POST, "/auth/login", "/auth/register", "/auth/refresh",
                                "/api/auth/login", "/api/auth/register", "/api/auth/refresh").permitAll()
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers("/error").permitAll()

                        .requestMatchers(HttpMethod.POST, "/auth/logout", "/api/auth/logout").authenticated()

                        .requestMatchers(HttpMethod.GET, "/users/me").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/users/me").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/users/me/password").authenticated()

                        .requestMatchers(HttpMethod.GET, "/payments/me").authenticated()

                        .requestMatchers(HttpMethod.GET, "/aulas/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/aulas/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/aulas/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/aulas/**").hasRole("ADMIN")

                        .requestMatchers("/users/deleted", "/users/*/restore").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/users").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/users").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/users/{id}").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/users/{id}").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/users/{id}").authenticated()

                        .requestMatchers(HttpMethod.GET, "/stores/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/stores/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/stores/**").hasAnyRole("ADMIN", "COMERCIO")
                        .requestMatchers(HttpMethod.PUT, "/stores/**").hasAnyRole("ADMIN", "COMERCIO")
                        .requestMatchers(HttpMethod.DELETE, "/stores/**").hasAnyRole("ADMIN", "COMERCIO")

                        .requestMatchers(HttpMethod.GET, "/products/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/products/**").hasAnyRole("ADMIN", "COMERCIO")
                        .requestMatchers(HttpMethod.PUT, "/products/**").hasAnyRole("ADMIN", "COMERCIO")
                        .requestMatchers(HttpMethod.DELETE, "/products/**").hasAnyRole("ADMIN", "COMERCIO")

                        .requestMatchers("/favorites/**").authenticated()

                        .requestMatchers(HttpMethod.GET, "/ratings/stores/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/ratings/orders/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/ratings/orders/**").authenticated()

                        .requestMatchers(HttpMethod.POST, "/orders").hasAnyRole("ESTUDIANTE", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/orders/**").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/orders/**").authenticated()

                        .requestMatchers(HttpMethod.POST, "/payments/webhook").permitAll()
                        .requestMatchers("/payments/**").authenticated()

                        .requestMatchers("/ai/**").authenticated()

                        .requestMatchers(HttpMethod.POST, "/images/**").hasAnyRole("ADMIN", "COMERCIO")
                        .requestMatchers(HttpMethod.DELETE, "/images/**").hasAnyRole("ADMIN", "COMERCIO")

                        .anyRequest().authenticated()
                        ;
                })
                .exceptionHandling(ex -> ex.authenticationEntryPoint(SecurityConfig::writeUnauthorized))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /**
     * Sin token, o con uno expirado, inválido o revocado, {@code JwtAuthenticationFilter} deja
     * pasar la petición como anónima. Sin un entry point explícito, Spring Security respondería
     * {@code 403} ({@code Http403ForbiddenEntryPoint}) y los clientes, que renuevan la sesión ante
     * un {@code 401}, nunca llegarían a usar el refresh token. {@code 403} queda para
     * "autenticado, pero sin permiso".
     */
    private static void writeUnauthorized(HttpServletRequest request,
                                          HttpServletResponse response,
                                          AuthenticationException ex) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"timestamp\":\"" + LocalDateTime.now() + "\",\"status\":401,"
                + "\"error\":\"No autenticado\","
                + "\"message\":\"Debes iniciar sesión para acceder a este recurso\"}");
    }

    /**
     * En {@code prod}, Actuator solo para administradores; en el resto de perfiles health, info y
     * prometheus son públicos.
     */
    private void configureActuatorAccess(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth) {
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            auth.requestMatchers("/actuator/**", "/api/actuator/**").hasRole("ADMIN");
            return;
        }
        auth.requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll();
    }

    /**
     * En {@code prod}, Swagger solo para administradores; en el resto de perfiles es público.
     */
    private void configureDocumentationAccess(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth) {
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            auth.requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/api-docs/**", "/v3/api-docs/**")
                    .hasRole("ADMIN");
            return;
        }
        auth.requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/api-docs/**", "/v3/api-docs/**").permitAll();
    }

    /**
     * bcrypt con coste 12.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
