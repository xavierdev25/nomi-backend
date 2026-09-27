package com.nomi.backend.application.auth;

import com.nomi.backend.domain.exception.AuthenticationFailedException;
import com.nomi.backend.domain.model.user.User;
import com.nomi.backend.domain.port.in.auth.LoginUseCase;
import com.nomi.backend.domain.port.out.LoginAttemptPort;
import com.nomi.backend.domain.port.out.RefreshTokenStorePort;
import com.nomi.backend.domain.port.out.TokenServicePort;
import com.nomi.backend.domain.port.out.UserRepositoryPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Inicio de sesión.
 *
 * <p>Tras 5 intentos fallidos el email queda bloqueado 15 minutos. Email inexistente, contraseña
 * incorrecta y usuario inactivo dan el mismo error, para no revelar qué cuentas existen.
 */
@Service
public class LoginHandler implements LoginUseCase {

    private static final String GENERIC_ERROR = "Credenciales inválidas";

    private final UserRepositoryPort userRepositoryPort;
    private final PasswordEncoder passwordEncoder;
    private final TokenServicePort tokenServicePort;
    private final RefreshTokenStorePort refreshTokenStorePort;
    private final LoginAttemptPort loginAttemptPort;

    public LoginHandler(UserRepositoryPort userRepositoryPort,
                        PasswordEncoder passwordEncoder,
                        TokenServicePort tokenServicePort,
                        RefreshTokenStorePort refreshTokenStorePort,
                        LoginAttemptPort loginAttemptPort) {
        this.userRepositoryPort = userRepositoryPort;
        this.passwordEncoder = passwordEncoder;
        this.tokenServicePort = tokenServicePort;
        this.refreshTokenStorePort = refreshTokenStorePort;
        this.loginAttemptPort = loginAttemptPort;
    }

    @Override
    @Transactional
    public LoginResult execute(LoginCommand command) {
        String normalizedEmail = command.email() == null ? "" : command.email().trim().toLowerCase();

        if (loginAttemptPort.isBlocked(normalizedEmail)) {
            throw new IllegalArgumentException(
                    "Cuenta bloqueada temporalmente por demasiados intentos fallidos. Intenta de nuevo en 15 minutos."
            );
        }

        Optional<User> maybeUser = userRepositoryPort.findByEmail(normalizedEmail);
        if (maybeUser.isEmpty()) {
            loginAttemptPort.recordFailedAttempt(normalizedEmail);
            throw new AuthenticationFailedException(GENERIC_ERROR);
        }

        User user = maybeUser.get();

        if (!passwordEncoder.matches(command.password(), user.getPassword())) {
            loginAttemptPort.recordFailedAttempt(normalizedEmail);
            throw new AuthenticationFailedException(GENERIC_ERROR);
        }

        if (!user.isActivo()) {
            throw new AuthenticationFailedException(GENERIC_ERROR);
        }

        String accessToken = tokenServicePort.generateAccessToken(
                user.getId(),
                user.getEmail(),
                user.getRole(),
                user.getNombres()
        );
        String refreshToken = tokenServicePort.generateRefreshToken(user.getEmail());

        refreshTokenStorePort.save(
                refreshToken,
                user.getId(),
                LocalDateTime.now().plusSeconds(tokenServicePort.getRefreshTokenExpirationMillis() / 1000)
        );

        loginAttemptPort.resetAttempts(normalizedEmail);

        return new LoginResult(accessToken, refreshToken, "Bearer",
                tokenServicePort.getAccessTokenExpirationMillis());
    }
}
