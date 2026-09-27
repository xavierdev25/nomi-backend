package com.nomi.backend.application.user;

import com.nomi.backend.domain.port.in.user.DeleteUserUseCase;
import com.nomi.backend.domain.port.out.RefreshTokenStorePort;
import com.nomi.backend.domain.port.out.TokenBlacklistPort;
import com.nomi.backend.domain.port.out.UserRepositoryPort;
import com.nomi.backend.domain.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Borrado lógico de un usuario; revoca sus sesiones.
 */
@Service
public class DeleteUserHandler implements DeleteUserUseCase {

    private final UserRepositoryPort userRepositoryPort;
    private final RefreshTokenStorePort refreshTokenStorePort;
    private final TokenBlacklistPort tokenBlacklistPort;

    public DeleteUserHandler(UserRepositoryPort userRepositoryPort,
                             RefreshTokenStorePort refreshTokenStorePort,
                             TokenBlacklistPort tokenBlacklistPort) {
        this.userRepositoryPort = userRepositoryPort;
        this.refreshTokenStorePort = refreshTokenStorePort;
        this.tokenBlacklistPort = tokenBlacklistPort;
    }

    @Override
    @Transactional
    public void execute(Long id) {
        userRepositoryPort.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        refreshTokenStorePort.revokeAllByUserId(id);
        tokenBlacklistPort.invalidateAllSessionsBefore(id, System.currentTimeMillis());
        userRepositoryPort.deleteById(id);
    }
}
