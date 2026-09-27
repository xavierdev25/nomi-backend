package com.foodv.backend.application.user;

import com.foodv.backend.domain.model.user.BudgetRange;
import com.foodv.backend.domain.model.user.User;
import com.foodv.backend.domain.model.user.UserRole;
import com.foodv.backend.domain.port.in.user.CreateUserUseCase;
import com.foodv.backend.domain.port.out.BusinessMetricsPort;
import com.foodv.backend.domain.port.out.UserRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Alta de usuarios: la contraseña siempre se guarda hasheada y el email debe ser único.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CreateUserHandler - Creación de usuarios")
class CreateUserHandlerTest {

    @Mock private UserRepositoryPort userRepositoryPort;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private BusinessMetricsPort metricsPort;
    @InjectMocks private CreateUserHandler createUserHandler;

    private CreateUserUseCase.CreateUserCommand buildCommand(String email, String telefono) {
        return new CreateUserUseCase.CreateUserCommand(
                "Xavier", "David", email, "password123", telefono, UserRole.ESTUDIANTE,
                List.of("pollo", "arroz"), List.of(), BudgetRange.MEDIO, List.of("criolla")
        );
    }

    @Test
    @DisplayName("Crear usuario exitosamente hashea password")
    void crear_usuario_hashea_password() {
        when(userRepositoryPort.existsByEmail("nuevo@foodv.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashedPassword");
        when(userRepositoryPort.save(any())).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            return User.builder()
                    .id(1L).nombres(u.getNombres()).apellidos(u.getApellidos())
                    .email(u.getEmail()).password(u.getPassword())
                    .role(u.getRole()).activo(u.isActivo())
                    .creadoEn(u.getCreadoEn()).build();
        });

        User result = createUserHandler.execute(buildCommand("nuevo@foodv.com", "999999999"));

        assertNotNull(result);
        assertEquals("hashedPassword", result.getPassword());
        assertEquals("nuevo@foodv.com", result.getEmail());
        assertTrue(result.isActivo());
        assertNotNull(result.getCreadoEn());
    }

    @Test
    @DisplayName("Crear usuario falla si email ya existe")
    void crear_usuario_falla_email_duplicado() {
        when(userRepositoryPort.existsByEmail("existe@foodv.com")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () ->
                createUserHandler.execute(buildCommand("existe@foodv.com", null))
        );
        verify(userRepositoryPort, never()).save(any());
    }

    @Test
    @DisplayName("Crear usuario nunca guarda password en texto plano")
    void crear_usuario_no_guarda_password_plano() {
        when(userRepositoryPort.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("$2a$10$hashedValue");
        when(userRepositoryPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        User result = createUserHandler.execute(buildCommand("test@foodv.com", null));

        assertNotEquals("password123", result.getPassword());
        assertTrue(result.getPassword().startsWith("$2a$10$"));
    }
}
