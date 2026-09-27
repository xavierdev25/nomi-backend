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
| Integración con perfil `test` (`BackendApplicationTests`, `*RepositoryIntegrationTest`) | PostgreSQL en `localhost:5432`, base `foodv_db`, usuario `foodv_user` (contraseña por `DB_PASSWORD`), y Redis en `6380` |

Configuración en `src/test/resources/application-test.yaml` (sin caché, sin Docker Compose). En
local, si tu PostgreSQL de desarrollo está en otro puerto, levanta uno desechable en el 5432 para
los tests. Testcontainers está en el `pom.xml` pero aún no se usa (auditoría M11).

## 2. Tipos de test

| Tipo | Herramientas | Ejemplo |
|---|---|---|
| Dominio | JUnit 5 | `OrderDomainServiceTest` |
| Handler | JUnit 5 + Mockito (puertos simulados) | `CreateOrderHandlerTest`, `LoginHandlerTest` |
| Controlador | `@SpringBootTest` con las clases mínimas + MockMvc + Spring Security Test | `OrderControllerTest`, `AuthControllerTest`, `PaymentControllerTest` |
| Adaptador | `@SpringBootTest` acotado | `AiServiceAdapterTest` |
| Integración con BD | `@SpringBootTest` + perfil `test` + `@Sql` | `ProductRepositoryIntegrationTest` |
| Configuración | JUnit 5 con rutas temporales | `DotenvEnvironmentPostProcessorTest` |

## 3. Cobertura actual (68 tests)

| Suite | Tests | Cubre |
|---|---:|---|
| `OrderDomainServiceTest` | 12 | Máquina de estados |
| `OrderControllerTest` | 6 | Crear pedido por rol, ownership, 401 sin token o con token inválido |
| `AuthControllerTest` | 6 | Registro, validación (incluida una restricción no admitida), login, credenciales incorrectas, logout |
| `DietaryRestrictionTest` | 6 | Cumplimiento de restricciones con las etiquetas del producto (vegano ⇒ vegetariano y sin lactosa), restricciones desconocidas |
| `GetRecommendationsHandlerTest` | 4 | Solo candidatos aptos llegan a la IA; sin candidatos o con restricción desconocida no se llama; presupuesto sin `:` |
| `RecommendationCandidatesTest` | 4 | Filtros de restricciones, stock, tienda activa y "no me gusta"; los más pedidos primero y tope de 40 |
| `LoginHandlerTest` | 5 | Login correcto, usuario inexistente o inactivo, contraseña incorrecta, bloqueo |
| `CreateOrderHandlerTest` | 4 | Cálculo del total, usuario inexistente, tienda inactiva, stock insuficiente |
| `DotenvEnvironmentPostProcessorTest` | 4 | Carga del `.env` y prioridad frente al entorno |
| `ProductRepositoryIntegrationTest` | 4 | Consultas de productos |
| `PaymentControllerTest` | 3 | Webhook: firma válida, inválida y payload incompleto |
| `CreateUserHandlerTest` | 3 | Alta de usuarios |
| `UserRepositoryIntegrationTest` | 3 | Consultas de usuarios |
| `AiServiceAdapterTest` | 3 | Circuit breaker abierto → degradación sin llamar al servicio; los `4xx` no abren el circuito; cabecera `X-FoodV-User-Id` |
| `BackendApplicationTests` | 1 | Arranque del contexto |

**Sin cubrir** (prioridad alta, ver auditoría M11): `ProcessWebhookHandler` por cada estado de
MercadoPago, `CreatePaymentHandler`, cancelación, rotación de refresh tokens, ownership de
tiendas y productos, favoritos y calificaciones.

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
