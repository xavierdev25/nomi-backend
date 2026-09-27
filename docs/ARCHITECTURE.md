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
disponibles, suma cantidades repetidas, descuenta stock con un `UPDATE … WHERE stock >= n`
atómico, calcula importes, genera el código de confirmación y guarda el pedido `PENDIENTE`.

**Pagar** (`CreatePaymentHandler`): comprueba dueño o admin y que no exista pago previo, calcula
`total + propina + tarifa + comisión` y crea la preferencia en MercadoPago. Devuelve la URL de
checkout.

**Webhook** (`PaymentController` → `MercadoPagoSignatureVerifier` → `ProcessWebhookHandler`):
verifica la firma HMAC, consulta el pago en MercadoPago y actualiza pago y pedido.

**Cambiar estado** (`UpdateOrderStatusHandler`): `OrderDomainService.applyStatusTransition`
valida la transición con `OrderStatus.canTransitionTo`, se guarda el historial y se publica
`OrderStatusChangedEvent` (hoy sin oyentes).

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
| Tareas programadas | `TokenCleanupScheduler` (3:00) y `SoftDeleteCleanupScheduler` (4:00) |
| Métricas | `BusinessMetricsService` (`nomi.orders.*`, `nomi.payments.*`, `nomi.users.registered`, `nomi.stores.created`) |
| Tiempo real | STOMP sobre `/ws`, prefijos `/topic` y `/app`; `WebSocketAuthChannelInterceptor` autentica el `CONNECT` |
| Push | `FcmNotificationAdapter`, activo solo con `FIREBASE_ENABLED=true` |
| OpenAPI | `OpenApiConfig`: descripción, tags, esquema JWT y respuestas de error comunes con `ApiError` |

## 6. Decisiones

| Decisión | Motivo |
|---|---|
| Arquitectura hexagonal | Aislar las reglas de negocio de Spring y de los proveedores (MercadoPago, IA, Cloudinary) para poder probarlas y sustituirlos. |
| Stock reservado al crear el pedido | Evitar vender lo que no hay entre crear y pagar; el coste es que un pedido sin pagar retiene stock. |
| Importes solo en el servidor | El cliente no es de confianza. |
| Refresh tokens con rotación y detección de reuso | Limitar el daño de un refresh token robado. |
| Fallo de IA → `FALLBACK` | Las recomendaciones son accesorias: nunca deben romper la app. |
| Restricciones filtradas en el backend, no por el LLM | El modelo no sabe qué lleva cada plato; solo los datos del comercio (etiquetas) lo dicen. El LLM ordena y explica candidatos que ya cumplen. |
| Los `4xx` de la IA no abren el circuit breaker | Un error de contrato o de cuota no es una caída; si contara, cortaría las recomendaciones de todos 30 s. |
| `.env` con prioridad menor que el entorno | En despliegues manda el entorno; el `.env` es comodidad local. |

## 7. Deuda conocida

La auditoría técnica de `nomi-docs` detalla los riesgos abiertos. Los de arquitectura más
relevantes: el builder de `Order` se copia en varios sitios en lugar de métodos de dominio (M9),
eventos de estado sin oyentes (M14), historial incompleto (M13) y fechas `LocalDateTime` sin zona
(M7).
