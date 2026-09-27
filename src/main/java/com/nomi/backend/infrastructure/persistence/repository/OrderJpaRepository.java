package com.nomi.backend.infrastructure.persistence.repository;

import com.nomi.backend.domain.model.order.OrderStatus;
import com.nomi.backend.infrastructure.persistence.entity.OrderEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
}
