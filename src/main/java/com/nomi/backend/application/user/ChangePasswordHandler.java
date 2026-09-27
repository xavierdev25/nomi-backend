package com.nomi.backend.application.user;

import com.nomi.backend.domain.exception.ResourceNotFoundException;
import com.nomi.backend.domain.model.user.User;
import com.nomi.backend.domain.port.in.user.ChangePasswordUseCase;
import com.nomi.backend.domain.port.out.RefreshTokenStorePort;
import com.nomi.backend.domain.port.out.TokenBlacklistPort;
import com.nomi.backend.domain.port.out.UserRepositoryPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Cambia la contraseña tras verificar la actual y cierra todas las sesiones: revoca los
 * refresh tokens e invalida los access tokens emitidos antes del cambio.
 */
@Service
public class ChangePasswordHandler implements ChangePasswordUseCase {

    private final UserRepositoryPort userRepositoryPort;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenStorePort refreshTokenStorePort;
    private final TokenBlacklistPort tokenBlacklistPort;

    public ChangePasswordHandler(UserRepositoryPort userRepositoryPort,
                                 PasswordEncoder passwordEncoder,
                                 RefreshTokenStorePort refreshTokenStorePort,
                                 TokenBlacklistPort tokenBlacklistPort) {
        this.userRepositoryPort = userRepositoryPort;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenStorePort = refreshTokenStorePort;
        this.tokenBlacklistPort = tokenBlacklistPort;
    }

    @Override
    @Transactional
    public void execute(String email, String currentPassword, String newPassword) {
        if (currentPassword == null || newPassword == null || currentPassword.equals(newPassword)) {
            throw new IllegalArgumentException("La nueva contraseña debe ser distinta a la actual");
        }

        User user = userRepositoryPort.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new IllegalArgumentException("La contraseña actual es incorrecta");
        }

        User updated = User.builder()
                .id(user.getId())
                .nombres(user.getNombres())
                .apellidos(user.getApellidos())
                .email(user.getEmail())
                .password(passwordEncoder.encode(newPassword))
                .telefono(user.getTelefono())
                .role(user.getRole())
                .activo(user.isActivo())
                .creadoEn(user.getCreadoEn())
                .actualizadoEn(LocalDateTime.now())
                .preferences(user.getPreferences())
                .restrictions(user.getRestrictions())
                .budgetRange(user.getBudgetRange())
                .cuisineTypes(user.getCuisineTypes())
                .esRepartidor(user.getEsRepartidor())
                .campusId(user.getCampusId())
                .build();

        userRepositoryPort.save(updated);

        refreshTokenStorePort.revokeAllByUserId(user.getId());
        tokenBlacklistPort.invalidateAllSessionsBefore(user.getId(), System.currentTimeMillis());
    }
}
