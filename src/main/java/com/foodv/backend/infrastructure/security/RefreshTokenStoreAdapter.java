package com.foodv.backend.infrastructure.security;

import com.foodv.backend.domain.port.out.RefreshTokenStorePort;
import com.foodv.backend.infrastructure.persistence.entity.RefreshTokenEntity;
import com.foodv.backend.infrastructure.persistence.repository.RefreshTokenRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Implementación JPA de {@code RefreshTokenStorePort}.
 */
@Component
public class RefreshTokenStoreAdapter implements RefreshTokenStorePort {

    private final RefreshTokenRepository repository;

    public RefreshTokenStoreAdapter(RefreshTokenRepository repository) {
        this.repository = repository;
    }

    @Override
    public void save(String token, Long userId, LocalDateTime expiresAt) {
        RefreshTokenEntity entity = RefreshTokenEntity.builder()
                .token(token)
                .userId(userId)
                .expiresAt(expiresAt)
                .revoked(false)
                .creadoEn(LocalDateTime.now())
                .build();
        repository.save(entity);
    }

    @Override
    public Optional<RefreshTokenInfo> findByToken(String token) {
        return repository.findByToken(token).map(this::toInfo);
    }

    @Override
    public void revokeByToken(String token) {
        repository.findByToken(token).ifPresent(entity -> {
            entity.setRevoked(true);
            repository.save(entity);
        });
    }

    @Override
    public void revokeAllByUserId(Long userId) {
        repository.revokeAllByUserId(userId);
    }

    private RefreshTokenInfo toInfo(RefreshTokenEntity entity) {
        return new RefreshTokenInfo(
                entity.getId(),
                entity.getToken(),
                entity.getUserId(),
                entity.getExpiresAt(),
                entity.isRevoked()
        );
    }
}
