# ARCHITECTURE.md — `nomi-backend`

## 1. Contexto

```text
 App iOS ──REST (JWT)──►  nomi-backend  ──REST (X-API-Key)──► nomi-ai-service ──► Ollama / Groq
                             │    ▲
              PostgreSQL ◄───┤    └── webhook firmado ── MercadoPago
                   Redis ◄───┤
              Cloudinary ◄───┤
                Firebase ◄───┘
```

El backend es el único que escribe en la base de datos y el único que habla con MercadoPago y
con el servicio de IA.

## 2. Capas (hexagonal)

```text
infrastructure/web ──► domain/port/in (UseCase) ◄── application (Handler) ──► domain/port/out ◄── infrastructure/*
   (entrada)                                         (lógica de negocio)                      (salida: BD, pagos, IA…)
```

| Capa | Paquete | Contiene | Depende de |
|---|---|---|---|
| Dominio | `domain/` | Modelos inmutables (`@Builder`, `@Getter`), enums con reglas (`OrderStatus.canTransitionTo`), puertos `in` (interfaces `XxxUseCase` con sus comandos) y `out` (`XxxRepositoryPort`, `PaymentGatewayPort`…), `OrderDomainService`, excepciones | Solo Lombok y un `@Component` |
| Aplicación | `application/<módulo>/` | Un `XxxHandler` por caso de uso, que implementa el `UseCase` y orquesta puertos de salida | `domain/` |
| Infraestructura | `infrastructure/` | Controladores, DTOs y mappers (`web/`), JPA (`persistence/`), adaptadores externos, seguridad y configuración | Todo lo anterior |

Reglas:
- Los controladores validan la entrada, resuelven el usuario actual, comprueban ownership y
  delegan en un `UseCase`. No contienen reglas de negocio.
- Los handlers no conocen HTTP ni JPA: hablan con puertos.
- Los adaptadores de persistencia convierten entre entidades JPA y modelos de dominio.
- Los DTOs de la API son `record` con `@Schema` y Bean Validation; MapStruct los convierte.

## 3. Flujo de una petición

1. `RequestLoggingFilter` asigna un `X-Request-Id` (también en el MDC de los logs).
2. `RateLimitingFilter` cuenta la petición en Redis (ventana de 1 minuto).
3. `SecurityHeadersFilter` añade cabeceras defensivas.
4. `JwtAuthenticationFilter` valida el token, consulta la blacklist y carga el usuario.
5. `SecurityConfig` aplica las reglas por ruta; un fallo de autenticación responde **401**
   (`writeUnauthorized`), uno de permisos **403**.
6. El controlador comprueba ownership con `OwnershipService` y llama al `UseCase`.
7. `GlobalExceptionHandler` traduce excepciones a `ApiError`.

## 4. Flujos principales

**Crear pedido** (`CreateOrderHandler`, transaccional): valida tienda activa y productos
disponibles, comprueba el límite de pedidos sin pagar (`OrderPaymentPolicy`), suma cantidades
repetidas, descuenta stock con un `UPDATE … WHERE stock >= n` atómico, calcula importes, genera
el código de confirmación y guarda el pedido `PENDIENTE` con su plazo para pagar
(`pago_expira_en`).

**Pagar** (`CreatePaymentHandler`): comprueba dueño o admin, que el pedido siga `PENDIENTE` y en
plazo, y que no exista pago previo; calcula `total + propina + tarifa + comisión` y crea la
preferencia en MercadoPago, que vence con el pedido. Devuelve la URL de checkout.

**Liquidar un pago** (`OrderPaymentSettlement`): punto único que aplica al pedido lo que dice
MercadoPago, para que webhook, cancelación y conciliación decidan igual. Un pago aprobado pasa el
pedido a `PREPARANDO` con `markPaidIfPending` (`UPDATE … WHERE status = 'PENDIENTE'`); si el
pedido ya estaba cancelado, o ya tenía otro pago aprobado, reembolsa. Los demás estados solo
actualizan el registro de pago.

**Webhook** (`PaymentController` → `MercadoPagoSignatureVerifier` → `ProcessWebhookHandler`):
verifica la firma HMAC, consulta el pago en MercadoPago (`getPayment`) y delega en
`OrderPaymentSettlement`. Si MercadoPago falla, `PaymentGatewayUnavailableException` → error y
MercadoPago reenvía.

**Cancelar** (`CancelOrderHandler` → `PendingOrderCanceller`): si hay checkout, busca un pago
aprobado en MercadoPago; si lo hay, liquida y responde `OrderAlreadyPaidException` (409). Si no,
cancela con `cancelIfPending` (condicional), solo entonces devuelve el stock y, tras el commit,
cierra el checkout en MercadoPago (`closeCheckout`).

**Pagos tardíos** (`LatePaymentsRefundScheduler` → `RefundLatePaymentsHandler`): cada 10 minutos
revisa los pedidos cancelados en las últimas 72 h con un checkout sin pago aprobado; si aparece un
pago aprobado de ese checkout, `OrderPaymentSettlement` lo reembolsa.

**Caducidad y conciliación** (`PendingOrdersReconciliationScheduler` →
`ReconcilePendingOrdersHandler`): cada minuto revisa los pedidos `PENDIENTE` vencidos o con
checkout, cada uno en su transacción: confirma los pagados y cancela los vencidos sin pago.

**Cambiar estado** (`UpdateOrderStatusHandler`): rechaza sacar un pedido de `PENDIENTE` (eso lo
hacen el pago y la cancelación); `OrderDomainService.applyStatusTransition` valida la transición
con `OrderStatus.canTransitionTo`, se guarda el historial y se publica `OrderStatusChangedEvent`
(hoy sin oyentes).

**Recomendaciones** (`GetRecommendationsHandler` → `AiServiceAdapter`), en dos etapas:

1. **Candidatos, en el backend y deterministas** (`RecommendationCandidates`, dominio): productos
   que se pueden pedir, de tiendas activas, aptos para todas las restricciones del estudiante
   según sus etiquetas dietéticas (`DietaryRestriction.isSatisfiedBy`), sin "no me gusta";
   primero los más pedidos, como mucho 40. Una restricción guardada que no se reconoce, o
   ningún candidato, responde `SIN_CANDIDATOS` sin llamar a la IA.
2. **Ranking y explicación, en el servicio de IA**: se envían los candidatos con las
   preferencias (franja horaria primero), `X-API-Key` y `X-Nomi-User-Id`, con timeouts de 3 s
   (conexión) y 10 s (lectura) y un circuit breaker de Resilience4j (`aiService`). Cualquier
   fallo devuelve `FALLBACK`; los `4xx` no abren el circuito (`AiResilienceConfig`).

**Sesión** (`LoginHandler`, `RefreshTokenHandler`, `LogoutHandler`): refresh tokens guardados en
la tabla `refresh_tokens`, rotación en cada uso y revocación masiva si se reutiliza uno revocado.

## 5. Transversales

| Aspecto | Implementación |
|---|---|
| Configuración | `application.yaml` + perfiles `dev`/`prod`/`test`; `.env` cargado por `DotenvEnvironmentPostProcessor` (registrado en `META-INF/spring.factories`) |
| Caché | Redis, TTL 5 min; caché `products` invalidada al crear/editar/borrar |
| Tareas programadas | `TokenCleanupScheduler` (3:00), `SoftDeleteCleanupScheduler` (4:00), `PendingOrdersReconciliationScheduler` (cada `nomi.orders.reconcile-interval-ms`, 60 s) y `LatePaymentsRefundScheduler` (cada `nomi.orders.late-payment-check-interval-ms`, 10 min) |
| Métricas | `BusinessMetricsService` (`nomi.orders.*`, `nomi.payments.*`, `nomi.users.registered`, `nomi.stores.created`) |
| Tiempo real | STOMP sobre `/ws`, prefijos `/topic` y `/app`; `WebSocketAuthChannelInterceptor` autentica el `CONNECT` |
| Push | `FcmNotificationAdapter`, activo solo con `FIREBASE_ENABLED=true` |
| OpenAPI | `OpenApiConfig`: descripción, tags, esquema JWT y respuestas de error comunes con `ApiError` |

## 6. Decisiones

| Decisión | Motivo |
|---|---|
| Arquitectura hexagonal | Aislar las reglas de negocio de Spring y de los proveedores (MercadoPago, IA, Cloudinary) para poder probarlas y sustituirlos. |
| Stock reservado al crear el pedido | Evitar vender lo que no hay entre crear y pagar. Para que un pedido sin pagar no lo retenga, vence a los 15 minutos y cada estudiante tiene como mucho 2 sin pagar. |
| El pedido lo cambia un pago aprobado consultado en MercadoPago | El aviso del webhook puede llegar tarde, repetido o no llegar; la conciliación y la cancelación consultan la misma fuente y aplican la misma regla (`OrderPaymentSettlement`). |
| Cambios de estado condicionales (`… WHERE status = 'PENDIENTE'`) | Webhook, cancelación y caducidad pueden coincidir; sin bloqueos, solo el primero cambia el pedido y el stock se devuelve una vez. |
| Ante un fallo de MercadoPago, no decidir | Cancelar a ciegas puede dejar un pago sin pedido; el webhook, la cancelación y la caducidad se reintentan. |
| Importes solo en el servidor | El cliente no es de confianza. |
| Refresh tokens con rotación y detección de reuso | Limitar el daño de un refresh token robado. |
| Fallo de IA → `FALLBACK` | Las recomendaciones son accesorias: nunca deben romper la app. |
| Restricciones filtradas en el backend, no por el LLM | El modelo no sabe qué lleva cada plato; solo los datos del comercio (etiquetas) lo dicen. El LLM ordena y explica candidatos que ya cumplen. |
| Los `4xx` de la IA no abren el circuit breaker | Un error de contrato o de cuota no es una caída; si contara, cortaría las recomendaciones de todos 30 s. |
| `.env` con prioridad menor que el entorno | En despliegues manda el entorno; el `.env` es comodidad local. |

## 7. Deuda conocida

La auditoría técnica de `nomi-docs` detalla los riesgos abiertos. Los de arquitectura más
relevantes: `applyStatusTransition` copia el builder de `Order` campo a campo (M9), eventos de
estado sin oyentes (M14), el historial no registra la creación (M13) y fechas `LocalDateTime` sin
zona (M7).
