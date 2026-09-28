# TESTING.md — `nomi-backend`

## 1. Cómo correr los tests

```bash
./mvnw test      # tests
./mvnw verify    # tests + informe de cobertura JaCoCo (target/site/jacoco), lo que corre la CI
```

Una clase concreta: `./mvnw test -Dtest=OrderDomainServiceTest`.

### Requisitos

| Tipo | Necesita |
|---|---|
| Unitarios y de controlador | Nada |
| Integración con perfil `test` (`BackendApplicationTests`, `*RepositoryIntegrationTest`) | PostgreSQL en `localhost:5432`, base `nomi_db`, usuario `nomi_user` (contraseña por `DB_PASSWORD`), y Redis en `6380` |

Configuración en `src/test/resources/application-test.yaml` (sin caché, sin Docker Compose). En
local, si tu PostgreSQL de desarrollo está en otro puerto, levanta uno desechable en el 5432 para
los tests. Testcontainers está en el `pom.xml` pero aún no se usa (auditoría M11).

Para no ensuciar la base de desarrollo, usa un PostgreSQL desechable en otro puerto y apunta los
tests con `SPRING_DATASOURCE_URL`:

```bash
docker run --rm -d --name nomi-test-pg -p 55432:5432 -e POSTGRES_DB=nomi_db \
  -e POSTGRES_USER=nomi_user -e POSTGRES_PASSWORD="$DB_PASSWORD" postgres:16-alpine
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:55432/nomi_db ./mvnw test
docker stop nomi-test-pg
```

## 2. Tipos de test

| Tipo | Herramientas | Ejemplo |
|---|---|---|
| Dominio | JUnit 5 | `OrderDomainServiceTest` |
| Handler | JUnit 5 + Mockito (puertos simulados) | `CreateOrderHandlerTest`, `LoginHandlerTest` |
| Controlador | `@SpringBootTest` con las clases mínimas + MockMvc + Spring Security Test | `OrderControllerTest`, `AuthControllerTest`, `PaymentControllerTest` |
| Adaptador | `@SpringBootTest` acotado | `AiServiceAdapterTest` |
| Integración con BD | `@SpringBootTest` + perfil `test` + `@Sql` | `ProductRepositoryIntegrationTest` |
| Configuración | JUnit 5 con rutas temporales | `DotenvEnvironmentPostProcessorTest` |

## 3. Cobertura actual (169 tests)

| Suite | Tests | Cubre |
|---|---:|---|
| `OrderPaymentSettlementTest` | 21 | Pago aprobado → preparación; pedido ya cancelado o pago duplicado → reembolso; aviso repetido sin efecto; registro antiguo sin id de pago; un rechazo no cancela; si el reembolso falla no se guarda nada; un pago de otro checkout (referencia, monto o fecha anterior) no confirma ni se reembolsa |
| `OrderDomainServiceTest` | 13 | Máquina de estados; la transición conserva el plazo de pago |
| `MercadoPagoAdapterTest` | 15 | Estados de MercadoPago → estados de pago (solo `approved` confirma); consulta de búsqueda válida; el checkout vence con el pedido, excluye medios diferidos y no lleva `notification_url` |
| `CheckoutReferenceTest` | 3 | Referencia única por checkout; pedido desde referencias nuevas y antiguas |
| `ReconcilePendingOrdersHandlerTest` | 8 | Qué pedidos se revisan; vencido pero pagado se confirma; vencido sin pago caduca y devuelve stock; MercadoPago caído no cancela; carrera con el webhook |
| `OrderPaymentPolicyTest` | 7 | Plazo para pagar, límite de pedidos sin pagar, vencimiento exacto y segundos restantes |
| `PaymentTest` | 8 | Un aviso tardío de otro intento no pisa un pago aprobado; idempotencia; el reembolso es final; referencia de búsqueda |
| `CreateOrderHandlerTest` | 7 | Cálculo del total, usuario inexistente, tienda inactiva, stock insuficiente, plazo de pago, límite de pedidos sin pagar (sin tocar el stock) y los vencidos no cuentan |
| `CancelOrderHandlerTest` | 7 | Sin checkout; con checkout sin pagar (y lo cierra); si cerrar falla, cancela igual; ya pagado → confirma y `409`; MercadoPago caído → no cancela; carrera con el webhook → no devuelve stock |
| `RefundLatePaymentsHandlerTest` | 5 | Reembolsa el pago aprobado de un pedido cancelado; sin pago aprobado, pedido no cancelado o pago ajeno → nada |
| `CreatePaymentHandlerTest` | 6 | Monto del servidor y checkout que vence con el pedido; pedido no pendiente, vencido o ajeno; segundo checkout |
| `ProcessWebhookHandlerTest` | 6 | Aprobado → liquida; no aprobado → solo registra; MercadoPago caído → error; referencia ajena; pago desconocido (aviso de prueba) → se ignora |
| `UpdateOrderStatusHandlerTest` | 4 | No saca un pedido de `PENDIENTE` (ni a preparación ni a cancelado); avance normal |
| `OrderRepositoryIntegrationTest` | 5 | Pagar y cancelar condicionales contra PostgreSQL: solo gana el primero; pedidos cancelados recientes con checkout abierto |
| `OrderControllerTest` | 6 | Crear pedido por rol, ownership, 401 sin token o con token inválido |
| `AuthControllerTest` | 6 | Registro, validación (incluida una restricción no admitida), login, credenciales incorrectas, logout |
| `DietaryRestrictionTest` | 6 | Cumplimiento de restricciones con las etiquetas del producto (vegano ⇒ vegetariano y sin lactosa), restricciones desconocidas |
| `GetRecommendationsHandlerTest` | 4 | Solo candidatos aptos llegan a la IA; sin candidatos o con restricción desconocida no se llama; presupuesto sin `:` |
| `RecommendationCandidatesTest` | 4 | Filtros de restricciones, stock, tienda activa y "no me gusta"; los más pedidos primero y tope de 40 |
| `LoginHandlerTest` | 5 | Login correcto, usuario inexistente o inactivo, contraseña incorrecta, bloqueo |
| `DotenvEnvironmentPostProcessorTest` | 4 | Carga del `.env` y prioridad frente al entorno |
| `ProductRepositoryIntegrationTest` | 4 | Consultas de productos |
| `PaymentControllerTest` | 5 | Webhook: firma válida e inválida, formato actual (`type=payment`) y antiguo (`topic`), eventos que no son de pago ignorados, sin tipo de evento → 400 |
| `CreateUserHandlerTest` | 3 | Alta de usuarios |
| `UserRepositoryIntegrationTest` | 3 | Consultas de usuarios |
| `AiServiceAdapterTest` | 3 | Circuit breaker abierto → degradación sin llamar al servicio; los `4xx` no abren el circuito; cabecera `X-Nomi-User-Id` |
| `BackendApplicationTests` | 1 | Arranque del contexto |

**Sin cubrir** (ver auditoría M11): rotación de refresh tokens, ownership de tiendas y
productos, favoritos y calificaciones. El ciclo de pago está probado con MercadoPago simulado;
falta probarlo contra el sandbox real.

## 4. Cómo escribir un test

- Nombre del método en `snake_case` con acción, condición y resultado
  (`find_order_non_owner_returns_403`, `login_bloqueado_lanza_excepcion`), y `@DisplayName` en
  español cuando ayude.
- Un comportamiento por test; *arrange / act / assert* separados.
- Los handlers se prueban con puertos simulados (Mockito), sin Spring.
- Los controladores se prueban por HTTP con MockMvc y la cadena de seguridad real: incluye siempre
  el caso sin permiso (`403`) y sin sesión (`401`).
- Nunca uses credenciales reales: los tests usan marcadores (`TEST-placeholder`,
  `test_jwt_secret_placeholder…`).
- Todo bug corregido lleva un test que lo reproduce.

## 5. Regla

Todo cambio de lógica lleva test y la suite completa debe pasar antes de dar el cambio por
terminado. Los cambios en pagos o en la máquina de estados requieren tests de cada rama.
