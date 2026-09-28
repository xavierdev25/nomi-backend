package com.nomi.backend.infrastructure.persistence.repository;

import com.nomi.backend.domain.model.order.Order;
import com.nomi.backend.domain.model.order.OrderStatus;
import com.nomi.backend.domain.model.payment.PaymentStatus;
import com.nomi.backend.domain.port.out.OrderRepositoryPort;
import com.nomi.backend.infrastructure.persistence.entity.AulaEntity;
import com.nomi.backend.infrastructure.persistence.entity.OrderEntity;
import com.nomi.backend.infrastructure.persistence.entity.PaymentEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cambios de estado condicionales contra PostgreSQL real. Son los que deciden quién gana cuando el
 * webhook, la cancelación y la caducidad llegan a la vez: solo el primero cambia el pedido. Necesita
 * PostgreSQL en {@code localhost:5432}.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Sql(scripts = "/db/seed-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@DisplayName("OrderRepositoryAdapter - Cambios de estado condicionales")
class OrderRepositoryIntegrationTest {

    @Autowired private OrderRepositoryPort orderRepositoryPort;
    @Autowired private OrderJpaRepository orderJpaRepository;
    @Autowired private UserJpaRepository userJpaRepository;
    @Autowired private StoreJpaRepository storeJpaRepository;
    @Autowired private AulaJpaRepository aulaJpaRepository;
    @Autowired private PaymentJpaRepository paymentJpaRepository;

    private Long orderId;
    private LocalDateTime deadline;

    @BeforeEach
    void setUp() {
        Long userId = userJpaRepository.findByEmailAndDeletedAtIsNull("owner@test.com").orElseThrow().getId();
        Long storeId = storeJpaRepository.findAll().getFirst().getId();
        Long aulaId = aulaJpaRepository.save(AulaEntity.builder()
                .codigo("T-" + UUID.randomUUID().toString().substring(0, 8))
                .nombre("Aula de prueba").activo(true).build()).getId();
        // Precisión de microsegundos, como la guarda PostgreSQL.
        deadline = LocalDateTime.now().plusMinutes(15).truncatedTo(ChronoUnit.MICROS);

        this.userId = userId;
        this.storeId = storeId;
        this.aulaId = aulaId;
        orderId = newOrder(OrderStatus.PENDIENTE, LocalDateTime.now());
    }

    private Long userId;
    private Long storeId;
    private Long aulaId;

    private Long newOrder(OrderStatus status, LocalDateTime actualizadoEn) {
        return orderJpaRepository.save(OrderEntity.builder()
                .userId(userId).storeId(storeId).aulaId(aulaId)
                .total(new BigDecimal("10.00")).status(status)
                .pagoExpiraEn(deadline)
                .creadoEn(actualizadoEn).actualizadoEn(actualizadoEn)
                .build()).getId();
    }

    private void newPayment(Long order, PaymentStatus status) {
        paymentJpaRepository.save(PaymentEntity.builder()
                .orderId(order).userId(userId).amount(new BigDecimal("10.70")).status(status)
                .externalId("pref-" + UUID.randomUUID()).externalReference("nomi-" + order + "-" + UUID.randomUUID())
                .creadoEn(LocalDateTime.now()).actualizadoEn(LocalDateTime.now())
                .build());
    }

    @Test
    @DisplayName("Encuentra los pedidos cancelados recientes con un pago sin aprobar ni reembolsar")
    void cancelados_con_checkout_abierto() {
        LocalDateTime now = LocalDateTime.now();
        Long abierto = newOrder(OrderStatus.CANCELADO, now);
        newPayment(abierto, PaymentStatus.PENDIENTE);
        Long reembolsado = newOrder(OrderStatus.CANCELADO, now);
        newPayment(reembolsado, PaymentStatus.REEMBOLSADO);
        Long sinCheckout = newOrder(OrderStatus.CANCELADO, now);
        Long antiguo = newOrder(OrderStatus.CANCELADO, now.minusDays(5));
        newPayment(antiguo, PaymentStatus.PENDIENTE);
        newPayment(orderId, PaymentStatus.PENDIENTE); // pendiente, no cancelado

        List<Long> found = orderRepositoryPort.findCancelledWithOpenCheckoutSince(now.minusHours(72));

        assertTrue(found.contains(abierto));
        assertFalse(found.contains(reembolsado));
        assertFalse(found.contains(sinCheckout));
        assertFalse(found.contains(antiguo));
        assertFalse(found.contains(orderId));
    }

    private Order reload() {
        return orderRepositoryPort.findById(orderId).orElseThrow();
    }

    @Test
    @DisplayName("Guarda y devuelve el plazo de pago")
    void persiste_plazo_de_pago() {
        assertEquals(deadline, reload().getPagoExpiraEn());
    }

    @Test
    @DisplayName("Marcar como pagado solo funciona una vez y solo desde PENDIENTE")
    void marcar_pagado_una_sola_vez() {
        assertTrue(orderRepositoryPort.markPaidIfPending(orderId, LocalDateTime.now()));
        assertFalse(orderRepositoryPort.markPaidIfPending(orderId, LocalDateTime.now()));
        assertEquals(OrderStatus.PREPARANDO, reload().getStatus());
    }

    @Test
    @DisplayName("Un pedido ya pagado no se puede cancelar")
    void pagado_no_se_cancela() {
        orderRepositoryPort.markPaidIfPending(orderId, LocalDateTime.now());

        assertFalse(orderRepositoryPort.cancelIfPending(orderId, "Caducó", null, LocalDateTime.now()));
        assertEquals(OrderStatus.PREPARANDO, reload().getStatus());
    }

    @Test
    @DisplayName("Un pedido cancelado guarda el motivo y ya no se puede marcar como pagado")
    void cancelado_no_se_paga() {
        assertTrue(orderRepositoryPort.cancelIfPending(orderId, "El pago no se completó a tiempo", null,
                LocalDateTime.now()));
        assertFalse(orderRepositoryPort.markPaidIfPending(orderId, LocalDateTime.now()));

        Order order = reload();
        assertEquals(OrderStatus.CANCELADO, order.getStatus());
        assertEquals("El pago no se completó a tiempo", order.getMotivoCancelacion());
        assertNull(order.getCanceladoPor());
    }
}
