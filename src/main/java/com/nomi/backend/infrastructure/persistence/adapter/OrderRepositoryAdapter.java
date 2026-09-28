package com.nomi.backend.infrastructure.persistence.adapter;

import com.nomi.backend.domain.common.PageQuery;
import com.nomi.backend.domain.common.PagedResult;
import com.nomi.backend.domain.model.order.Order;
import com.nomi.backend.domain.model.order.OrderStatus;
import com.nomi.backend.domain.model.payment.PaymentStatus;
import com.nomi.backend.domain.port.out.OrderRepositoryPort;
import com.nomi.backend.infrastructure.common.PagingMapper;
import com.nomi.backend.infrastructure.persistence.repository.OrderJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Implementación JPA de {@code OrderRepositoryPort}.
 */
@Component
@RequiredArgsConstructor
public class OrderRepositoryAdapter implements OrderRepositoryPort {

    private final OrderJpaRepository jpaRepository;
    private final OrderEntityMapper mapper;

    @Override
    public Order save(Order order) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(order)));
    }

    /**
     * Carga las líneas en la misma consulta. El {@code Order} de dominio siempre incluye sus
     * líneas; con el {@code findById} de JPA la colección quedaba perezosa y, leída fuera de una
     * transacción (por ejemplo en {@code OwnershipService}), lanzaba
     * {@code LazyInitializationException}: cancelar y consultar el historial respondían 500.
     */
    @Override
    public Optional<Order> findById(Long id) {
        return jpaRepository.findByIdWithItems(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Order> findByIdWithItems(Long id) {
        return jpaRepository.findByIdWithItems(id).map(mapper::toDomain);
    }

    @Override
    public List<Order> findByUserId(Long userId) {
        return jpaRepository.findByUserId(userId).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<Order> findByStoreId(Long storeId) {
        return jpaRepository.findByStoreId(storeId).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<Order> findByStatus(OrderStatus status) {
        return jpaRepository.findByStatus(status).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<Order> findAll() {
        return jpaRepository.findAll().stream().map(mapper::toDomain).toList();
    }

    @Override
    public PagedResult<Order> findAllPaginated(PageQuery query) {
        return PagingMapper.toDomain(
                jpaRepository.findAll(PagingMapper.toPageable(query)),
                mapper::toDomain
        );
    }

    @Override
    public PagedResult<Order> findByUserIdPaginated(Long userId, PageQuery query) {
        return PagingMapper.toDomain(
                jpaRepository.findByUserId(userId, PagingMapper.toPageable(query)),
                mapper::toDomain
        );
    }

    @Override
    public PagedResult<Order> findByStoreIdPaginated(Long storeId, PageQuery query) {
        return PagingMapper.toDomain(
                jpaRepository.findByStoreId(storeId, PagingMapper.toPageable(query)),
                mapper::toDomain
        );
    }

    @Override
    public PagedResult<Order> findByStatusPaginated(OrderStatus status, PageQuery query) {
        return PagingMapper.toDomain(
                jpaRepository.findByStatus(status, PagingMapper.toPageable(query)),
                mapper::toDomain
        );
    }

    @Override
    public List<Order> findByUserIdAndStatus(Long userId, OrderStatus status) {
        return jpaRepository.findByUserIdAndStatus(userId, status).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<Long> findCancelledWithOpenCheckoutSince(LocalDateTime since) {
        return jpaRepository.findIdsWithUnsettledPayment(OrderStatus.CANCELADO, since,
                List.of(PaymentStatus.APROBADO, PaymentStatus.REEMBOLSADO));
    }

    @Override
    public boolean markPaidIfPending(Long orderId, LocalDateTime now) {
        return jpaRepository.updateStatusIf(orderId, OrderStatus.PENDIENTE, OrderStatus.PREPARANDO, now) == 1;
    }

    @Override
    public boolean cancelIfPending(Long orderId, String motivo, Long canceladoPor, LocalDateTime now) {
        return jpaRepository.cancelIf(orderId, OrderStatus.PENDIENTE, OrderStatus.CANCELADO,
                motivo, canceladoPor, now) == 1;
    }
}
