# DATABASE.md — `nomi-backend`

PostgreSQL 16. El esquema lo gestiona **Flyway** (`src/main/resources/db/migration/`) y Hibernate
no lo toca (`ddl-auto: none`). La descripción columna por columna está en `nomi-docs`
(Arquitectura → Modelo de datos).

## 1. Tablas

| Área | Tablas |
|---|---|
| Usuarios y sesión | `users`, `refresh_tokens` |
| Catálogo | `stores`, `products`, `aulas` |
| Pedidos | `orders`, `order_items`, `order_status_history` |
| Pagos | `payments` |
| Favoritos y valoraciones | `favorite_products`, `favorite_stores`, `ratings`, `calificaciones_repartidor` |
| Repartidor | `repartidor_perfiles` |
| Campus | `universidades`, `campus`, `campus_mapas`, `puntos_entrega` |
| IA | `ai_recommendation_feedback` |

```text
users ─┬─< stores ─< products
       ├─< orders ─┬─< order_items          (copia de nombre y precio del producto)
       │           ├─< order_status_history
       │           ├── payments (1 por pedido)
       │           └── ratings (0..1)
       ├─< refresh_tokens
       └─< favorite_products / favorite_stores
orders >── aulas
```

## 2. Migraciones

| Versión | Cambio |
|---|---|
| V1–V8 | Tablas base: `users`, `aulas`, `stores`, `products`, `orders` + `order_items`, `payments`, `refresh_tokens`, `order_status_history` |
| V9 | Soft delete (`deleted_at`) en usuarios, tiendas y productos |
| V10 | Preferencias gastronómicas del usuario |
| V11 | Feedback de recomendaciones |
| V12 | Campos de repartidor en pedidos y usuarios |
| V13 | Perfil y calificaciones de repartidor |
| V14 | Universidades, campus, mapas y puntos de entrega |
| V15 | Índices de rendimiento, unicidades y columnas `actualizado_en` |
| V16 | Favoritos |
| V17 | Calificaciones de pedidos |
| V18 | Horario de las tiendas |
| V19 | `DEFAULT NOW()` en las marcas de tiempo |
| V20 | `CHECK` de estados válidos del pedido |
| V21 | `CHECK` de `budget_range` (`BAJO`, `MEDIO`, `ALTO`) |
| V22 | `products.etiquetas_dieteticas` (`TEXT[]`, por defecto vacío) con `CHECK` de valores válidos |
| V23 | Renombra la columna de comisión de la plataforma a `orders.comision_nomi` |
| V24 | `orders.pago_expira_en` (plazo para pagar; los pedidos `PENDIENTE` existentes reciben `creado_en + 15 min`) con índice parcial sobre los pendientes, y `payments.gateway_payment_id` |
| V25 | `payments.external_reference`: referencia única del checkout (`nomi-<pedido>-<uuid>`), con índice único parcial |

## 3. Reglas de datos

- **Dinero:** `NUMERIC(10,2)` en la base y `BigDecimal` en Java.
- **Snapshots:** `order_items` guarda el nombre y el precio del producto al comprar; cambiar el
  producto no altera pedidos pasados.
- **Importes del pedido:** `orders.total` es el subtotal de productos; propina, tarifa y comisión
  van en columnas propias. Los `DEFAULT` de `tarifa_servicio` y `comision_nomi` no son los valores
  reales: los decide `CreateOrderHandler`.
- **Stock:** se descuenta con un `UPDATE` condicional (`stock >= cantidad`), atómico frente a
  pedidos simultáneos, que recalcula `disponible`.
- **Salir de `PENDIENTE`:** el paso a `PREPARANDO` (pago aprobado) y la cancelación son `UPDATE`
  condicionales (`… WHERE status = 'PENDIENTE'`, `OrderJpaRepository.updateStatusIf` y
  `cancelIf`). Si webhook, cancelación y caducidad coinciden, solo uno cambia la fila; el stock se
  devuelve solo si la cancelación ganó.
- **Plazo para pagar:** `orders.pago_expira_en`. `NULL` en pedidos que ya no estaban pendientes al
  aplicar V24: esos nunca caducan.
- **Pagos:** `payments.external_id` es el checkout (preferencia), `external_reference` su
  referencia única en MercadoPago (`NULL` en los anteriores a V25, que se buscan por el id del
  pedido) y `gateway_payment_id` el pago de MercadoPago que decidió el estado; distingue un aviso
  repetido de un cobro duplicado.
- **Soft delete:** usuarios, tiendas y productos se marcan con `deleted_at`. El email es único
  sin distinguir mayúsculas entre usuarios no borrados (`uq_users_email_alive`).
- **Unicidades:** refresh token, `external_id` de pago, un favorito por usuario y producto o
  tienda, una calificación por pedido.
- **Checks:** estados del pedido, `budget_range`, `rating` entre 1 y 5, etiquetas dietéticas
  (`VEGETARIANO`, `VEGANO`, `SIN_GLUTEN`, `SIN_LACTOSA`).
- **Etiquetas dietéticas:** `products.etiquetas_dieteticas` lista las restricciones para las que
  el comercio declara apto el producto. Vacío significa "no apto para ninguna": los productos
  existentes antes de V22 no se recomiendan a estudiantes con restricciones hasta etiquetarlos.
  Un producto `VEGANO` cuenta también como vegetariano y sin lactosa (regla en
  `DietaryRestriction`, no en la base).
- **Fechas:** `TIMESTAMP` sin zona; la aplicación escribe con `LocalDateTime.now()` (hora del
  servidor) aunque Hibernate está configurado en UTC. Ver auditoría M7.

## 4. Cómo cambiar el esquema

1. Crea `V26__descripcion_en_snake_case.sql` (siguiente número libre).
2. Escribe SQL idempotente cuando sea razonable (`IF NOT EXISTS`, bloques `DO $$`).
3. **Nunca edites una migración ya aplicada**: `validate-on-migrate` hará fallar el arranque.
4. Actualiza la entidad JPA, el modelo de dominio, el adaptador y el mapper.
5. Arranca la app (Flyway aplica la migración) y corre los tests.
6. Actualiza este documento y el modelo de datos de `nomi-docs`.

## 5. Tareas de mantenimiento

| Tarea | Cuándo | Qué hace |
|---|---|---|
| `TokenCleanupScheduler` | Diario, 3:00 | Borra refresh tokens expirados y los revocados hace más de un día |
| `SoftDeleteCleanupScheduler` | Diario, 4:00 | Intenta borrar usuarios eliminados hace más de 7 días; falla si tienen pedidos (auditoría M5) |
| `PendingOrdersReconciliationScheduler` | Cada 60 s (`ORDER_RECONCILE_INTERVAL_MS`) | Confirma pedidos pendientes ya pagados en MercadoPago y cancela los vencidos sin pago, devolviendo el stock |
| `LatePaymentsRefundScheduler` | Cada 10 min (`ORDER_LATE_PAYMENT_CHECK_INTERVAL_MS`) | Reembolsa los pagos aprobados de pedidos cancelados en las últimas 72 h (`ORDER_LATE_PAYMENT_WINDOW_HOURS`) |

## 6. Datos de prueba

- `src/test/resources/db/seed-test-data.sql` para los tests de integración.
- Datos de desarrollo: ver "Datos de prueba" en `nomi-docs`.
