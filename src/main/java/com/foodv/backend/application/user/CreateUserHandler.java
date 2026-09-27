package com.foodv.backend.application.user;

import com.foodv.backend.domain.factory.UserFactory;
import com.foodv.backend.domain.model.user.User;
import com.foodv.backend.domain.port.in.user.CreateUserUseCase;
import com.foodv.backend.domain.port.out.BusinessMetricsPort;
import com.foodv.backend.domain.port.out.UserRepositoryPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alta de usuarios por un administrador; admite cualquier rol.
 */
@Service
public class CreateUserHandler implements CreateUserUseCase {

    private final UserRepositoryPort userRepositoryPort;
    private final PasswordEncoder passwordEncoder;
    private final BusinessMetricsPort metricsPort;

    public CreateUserHandler(UserRepositoryPort userRepositoryPort,
                             PasswordEncoder passwordEncoder,
                             BusinessMetricsPort metricsPort) {
        this.userRepositoryPort = userRepositoryPort;
        this.passwordEncoder = passwordEncoder;
        this.metricsPort = metricsPort;
    }

    @Override
    @Transactional
    public User execute(CreateUserCommand command) {
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
                command.role(),
                command.preferences(),
                command.restrictions(),
                command.budgetRange(),
                command.cuisineTypes()
        );

        User savedUser = userRepositoryPort.save(user);
        metricsPort.recordUserRegistered();
        return savedUser;
    }
}
