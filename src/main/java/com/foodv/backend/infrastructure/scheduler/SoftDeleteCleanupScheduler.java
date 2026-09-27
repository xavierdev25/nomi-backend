package com.foodv.backend.infrastructure.scheduler;

import com.foodv.backend.infrastructure.persistence.repository.ProductJpaRepository;
import com.foodv.backend.infrastructure.persistence.repository.StoreJpaRepository;
import com.foodv.backend.infrastructure.persistence.repository.UserJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Tarea diaria (4:00) que elimina definitivamente los usuarios borrados hace más de 7 días.
 *
 * <p>Las claves foráneas no tienen {@code ON DELETE}: con usuarios que tienen pedidos, pagos o
 * tokens la transacción falla (ver la auditoría técnica, M5).
 */
@Component
public class SoftDeleteCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(SoftDeleteCleanupScheduler.class);
    private static final int DAYS_BEFORE_PERMANENT_DELETE = 7;

    private final UserJpaRepository userJpaRepository;
    private final ProductJpaRepository productJpaRepository;
    private final StoreJpaRepository storeJpaRepository;

    public SoftDeleteCleanupScheduler(UserJpaRepository userJpaRepository,
                                      ProductJpaRepository productJpaRepository,
                                      StoreJpaRepository storeJpaRepository) {
        this.userJpaRepository = userJpaRepository;
        this.productJpaRepository = productJpaRepository;
        this.storeJpaRepository = storeJpaRepository;
    }

    @Scheduled(cron = "0 0 4 * * *")
    @Transactional
    public void cleanupSoftDeleted() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(DAYS_BEFORE_PERMANENT_DELETE);

        userJpaRepository.findAllByDeletedAtIsNotNull().stream()
                .filter(u -> u.getDeletedAt() != null && u.getDeletedAt().isBefore(cutoff))
                .forEach(userJpaRepository::delete);

        log.info("Limpieza de registros eliminados completada — cutoff: {}", cutoff);
    }
}
