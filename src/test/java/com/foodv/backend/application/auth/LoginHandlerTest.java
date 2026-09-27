package com.foodv.backend.application.auth;

import com.foodv.backend.domain.exception.AuthenticationFailedException;
import com.foodv.backend.domain.model.user.User;
import com.foodv.backend.domain.model.user.UserRole;
import com.foodv.backend.domain.port.in.auth.LoginUseCase;
import com.foodv.backend.domain.port.out.LoginAttemptPort;
import com.foodv.backend.domain.port.out.RefreshTokenStorePort;
import com.foodv.backend.domain.port.out.TokenServicePort;
import com.foodv.backend.domain.port.out.UserRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Reglas del login: tokens en el caso feliz, error genérico ante credenciales inválidas o
 * usuario inactivo, registro de intentos fallidos y bloqueo.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LoginHandler - Autenticación")
class LoginHandlerTest {

    @Mock private UserRepositoryPort userRepositoryPort;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private TokenServicePort tokenServicePort;
    @Mock private RefreshTokenStorePort refreshTokenStorePort;
    @Mock private LoginAttemptPort loginAttemptPort;

    @InjectMocks private LoginHandler loginHandler;

    private User activeUser;

    @BeforeEach
    void setUp() {
        activeUser = User.builder()
                .id(1L)
                .nombres("Xavier")
                .apellidos("David")
                .email("xavier@foodv.com")
                .password("hashedPassword")
                .role(UserRole.ADMIN)
                .activo(true)
                .creadoEn(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("Login exitoso retorna tokens")
    void login_exitoso_retorna_tokens() {
        when(loginAttemptPort.isBlocked(anyString())).thenReturn(false);
        when(userRepositoryPort.findByEmail("xavier@foodv.com")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("password123", "hashedPassword")).thenReturn(true);
        when(tokenServicePort.generateAccessToken(anyLong(), anyString(), any(), anyString())).thenReturn("accessToken");
        when(tokenServicePort.generateRefreshToken(anyString())).thenReturn("refreshToken");
        when(tokenServicePort.getAccessTokenExpirationMillis()).thenReturn(86400000L);
        when(tokenServicePort.getRefreshTokenExpirationMillis()).thenReturn(604800000L);

        LoginUseCase.LoginResult result = loginHandler.execute(
                new LoginUseCase.LoginCommand("xavier@foodv.com", "password123")
        );

        assertNotNull(result);
        assertEquals("accessToken", result.accessToken());
        assertEquals("refreshToken", result.refreshToken());
        assertEquals("Bearer", result.tokenType());
        verify(refreshTokenStorePort).save(eq("refreshToken"), eq(1L), any());
        verify(loginAttemptPort).resetAttempts("xavier@foodv.com");
    }

    @Test
    @DisplayName("Login fallido — usuario no existe lanza excepción genérica")
    void login_usuario_no_existe_lanza_excepcion() {
        when(loginAttemptPort.isBlocked(anyString())).thenReturn(false);
        when(userRepositoryPort.findByEmail("noexiste@foodv.com")).thenReturn(Optional.empty());

        AuthenticationFailedException ex = assertThrows(AuthenticationFailedException.class, () ->
                loginHandler.execute(new LoginUseCase.LoginCommand("noexiste@foodv.com", "password123"))
        );
        assertTrue(ex.getMessage().toLowerCase().contains("credenciales"));
        verify(loginAttemptPort).recordFailedAttempt("noexiste@foodv.com");
    }

    @Test
    @DisplayName("Login fallido — password incorrecto registra intento fallido")
    void login_password_incorrecto_registra_intento() {
        when(loginAttemptPort.isBlocked(anyString())).thenReturn(false);
        when(userRepositoryPort.findByEmail("xavier@foodv.com")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("wrongpassword", "hashedPassword")).thenReturn(false);

        assertThrows(AuthenticationFailedException.class, () ->
                loginHandler.execute(new LoginUseCase.LoginCommand("xavier@foodv.com", "wrongpassword"))
        );
        verify(loginAttemptPort).recordFailedAttempt("xavier@foodv.com");
    }

    @Test
    @DisplayName("Login bloqueado — demasiados intentos fallidos")
    void login_bloqueado_lanza_excepcion() {
        when(loginAttemptPort.isBlocked("xavier@foodv.com")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                loginHandler.execute(new LoginUseCase.LoginCommand("xavier@foodv.com", "password123"))
        );
        assertTrue(ex.getMessage().toLowerCase().contains("bloqueada"));
        verifyNoInteractions(userRepositoryPort);
    }

    @Test
    @DisplayName("Login fallido — usuario inactivo lanza excepción")
    void login_usuario_inactivo_lanza_excepcion() {
        User inactiveUser = User.builder()
                .id(2L)
                .email("inactivo@foodv.com")
                .password("hashedPassword")
                .role(UserRole.ESTUDIANTE)
                .activo(false)
                .creadoEn(LocalDateTime.now())
                .build();

        when(loginAttemptPort.isBlocked(anyString())).thenReturn(false);
        when(userRepositoryPort.findByEmail("inactivo@foodv.com")).thenReturn(Optional.of(inactiveUser));
        when(passwordEncoder.matches("password123", "hashedPassword")).thenReturn(true);

        assertThrows(AuthenticationFailedException.class, () ->
                loginHandler.execute(new LoginUseCase.LoginCommand("inactivo@foodv.com", "password123"))
        );
    }
}
