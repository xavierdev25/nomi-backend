package com.nomi.backend.infrastructure.persistence.entity;

import com.nomi.backend.domain.model.order.OrderStatus;
import jakarta.persistence.*;
import lombok.*;
import java.util.ArrayList;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Tabla {@code orders}. Las líneas ({@code order_items}) se cargan de forma perezosa: las
 * consultas del repositorio usan un entity graph para traerlas juntas.
 */
@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "aula_id", nullable = false)
    private Long aulaId;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @JoinColumn(name = "order_id", nullable = false)
    @org.hibernate.annotations.BatchSize(size = 50)
    @Builder.Default
    private List<OrderItemEntity> items = new ArrayList<>();

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    private String notas;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    @Column(name = "actualizado_en")
    private LocalDateTime actualizadoEn;

    @Column(name = "repartidor_id")
    private Long repartidorId;

    @Column(name = "motivo_cancelacion")
    private String motivoCancelacion;

    @Column(name = "cancelado_por")
    private Long canceladoPor;

    @Column(name = "propina", precision = 10, scale = 2)
    private BigDecimal propina;

    @Column(name = "tarifa_servicio", precision = 10, scale = 2)
    private BigDecimal tarifaServicio;

    @Column(name = "comision_nomi", precision = 10, scale = 2)
    private BigDecimal comisionNomi;

    @Column(name = "codigo_confirmacion", length = 4)
    private String codigoConfirmacion;

    @Column(name = "foto_entrega_url")
    private String fotoEntregaUrl;

    @Column(name = "pago_expira_en")
    private LocalDateTime pagoExpiraEn;
}
