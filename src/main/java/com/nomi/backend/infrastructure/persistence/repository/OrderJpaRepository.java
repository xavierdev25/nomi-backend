package com.nomi.backend.infrastructure.persistence.repository;

import com.nomi.backend.domain.model.order.OrderStatus;
import com.nomi.backend.domain.model.payment.PaymentStatus;
import com.nomi.backend.infrastructure.persistence.entity.OrderEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio Spring Data de pedidos. Las consultas usan un entity graph sobre
 * {@code items} para cargar las líneas sin N+1.
 */
@Repository
public interface OrderJpaRepository extends JpaRepository<OrderEntity, Long> {

    @EntityGraph(attributePaths = "items")
    @Query("SELECT o FROM OrderEntity o WHERE o.id = :id")
    Optional<OrderEntity> findByIdWithItems(@Param("id") Long id);

    @EntityGraph(attributePaths = "items")
    List<OrderEntity> findByUserId(Long userId);

    @EntityGraph(attributePaths = "items")
    List<OrderEntity> findByStoreId(Long storeId);

    @EntityGraph(attributePaths = "items")
    List<OrderEntity> findByStatus(OrderStatus status);

    @EntityGraph(attributePaths = "items")
    Page<OrderEntity> findAll(Pageable pageable);

    @EntityGraph(attributePaths = "items")
    Page<OrderEntity> findByUserId(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = "items")
    Page<OrderEntity> findByStoreId(Long storeId, Pageable pageable);

    @EntityGraph(attributePaths = "items")
    Page<OrderEntity> findByStatus(OrderStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "items")
    List<OrderEntity> findByUserIdAndStatus(Long userId, OrderStatus status);

    /**
     * Cambia el estado solo si el pedido sigue en {@code from}. Devuelve las filas afectadas: 0
     * significa que otro proceso (webhook, caducidad, cancelación) cambió el pedido antes.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OrderEntity o SET o.status = :to, o.actualizadoEn = :now WHERE o.id = :id AND o.status = :from")
    int updateStatusIf(@Param("id") Long id,
                       @Param("from") OrderStatus from,
                       @Param("to") OrderStatus to,
                       @Param("now") LocalDateTime now);

    /** Cancela solo si el pedido sigue en {@code pending}; misma semántica que {@link #updateStatusIf}. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE OrderEntity o SET o.status = :cancelled, o.motivoCancelacion = :motivo, "
            + "o.canceladoPor = :canceladoPor, o.actualizadoEn = :now "
            + "WHERE o.id = :id AND o.status = :pending")
    int cancelIf(@Param("id") Long id,
                 @Param("pending") OrderStatus pending,
                 @Param("cancelled") OrderStatus cancelled,
                 @Param("motivo") String motivo,
                 @Param("canceladoPor") Long canceladoPor,
                 @Param("now") LocalDateTime now);

    /** Pedidos en {@code status} actualizados desde {@code since} con un pago cuyo estado no está en {@code settled}. */
    @Query("SELECT o.id FROM OrderEntity o WHERE o.status = :status AND o.actualizadoEn >= :since "
            + "AND EXISTS (SELECT p.id FROM PaymentEntity p WHERE p.orderId = o.id AND p.status NOT IN :settled) "
            + "ORDER BY o.id")
    List<Long> findIdsWithUnsettledPayment(@Param("status") OrderStatus status,
                                           @Param("since") LocalDateTime since,
                                           @Param("settled") List<PaymentStatus> settled);
}
