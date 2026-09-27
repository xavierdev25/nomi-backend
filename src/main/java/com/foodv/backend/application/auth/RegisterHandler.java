package com.foodv.backend.application.auth;

import com.foodv.backend.domain.factory.UserFactory;
import com.foodv.backend.domain.model.user.User;
import com.foodv.backend.domain.model.user.UserRole;
import com.foodv.backend.domain.port.in.auth.RegisterUseCase;
import com.foodv.backend.domain.port.out.BusinessMetricsPort;
import com.foodv.backend.domain.port.out.UserRepositoryPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Auto-registro. Permite {@code ESTUDIANTE}, {@code REPARTIDOR} y {@code COMERCIO}, sin
 * verificación adicional para estos dos últimos (ver la auditoría técnica, A5).
 */
@Service
public class RegisterHandler implements RegisterUseCase {

    private static final Set<UserRole> SELF_REGISTRABLE_ROLES = Set.of(
            UserRole.ESTUDIANTE,
            UserRole.REPARTIDOR,
            UserRole.COMERCIO
    );

    private final UserRepositoryPort userRepositoryPort;
    private final PasswordEncoder passwordEncoder;
    private final BusinessMetricsPort metricsPort;

    public RegisterHandler(UserRepositoryPort userRepositoryPort,
                           PasswordEncoder passwordEncoder,
                           BusinessMetricsPort metricsPort) {
        this.userRepositoryPort = userRepositoryPort;
        this.passwordEncoder = passwordEncoder;
        this.metricsPort = metricsPort;
    }

    @Override
    @Transactional
    public User execute(RegisterCommand command) {
        UserRole requestedRole = command.role();
        if (requestedRole == null || !SELF_REGISTRABLE_ROLES.contains(requestedRole)) {
            throw new IllegalArgumentException(
                    "El rol solicitado no permite auto-registro. " +
                    "Roles válidos: ESTUDIANTE, REPARTIDOR, COMERCIO."
            );
        }

        String normalizedEmail = UserFactory.normalizeEmail(command.email());

        if (userRepositoryPort.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException("Email ya registrado");
        }

        User user = UserFactory.create(
                command.nombres(),
                command.apellidos(),
                normalizedEmail,
                passwordEncoder.encode(command.password()),
                command.telefono(),
                requestedRole,
                command.preferences(),
                command.restrictions(),
                command.budgetRange(),
                command.cuisineTypes()
        );

        User saved = userRepositoryPort.save(user);
        metricsPort.recordUserRegistered();
        return saved;
    }
}
