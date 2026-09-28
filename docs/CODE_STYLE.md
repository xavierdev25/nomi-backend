# CODE_STYLE.md — `nomi-backend`

## 1. Idioma

- Clases, métodos y variables técnicas en inglés (`CreateOrderHandler`, `findById`).
- Términos del dominio en español, igual que en la base de datos y la API (`propina`, `aula`,
  `tarifaServicio`, `OrderStatus.EN_CAMINO`).
- Mensajes de error, Javadoc, comentarios y documentación en español.

## 2. Estructura por capa

| Elemento | Dónde | Convención |
|---|---|---|
| Caso de uso (entrada) | `domain/port/in/<módulo>/XxxUseCase` | Interfaz con `record XxxCommand(...)` anidado y un método `execute` |
| Puerto de salida | `domain/port/out/XxxPort` | Interfaz; nombres `XxxRepositoryPort`, `XxxGatewayPort`… |
| Modelo de dominio | `domain/model/<módulo>/` | Inmutable: `@Getter` + `@Builder`, sin anotaciones JPA |
| Handler | `application/<módulo>/XxxHandler` | `@Service`, `@RequiredArgsConstructor`, implementa el `UseCase` |
| Controlador | `infrastructure/web/controller/XxxController` | `@RestController`, `@Tag`, `@Operation`; delega en `UseCase` |
| DTO | `infrastructure/web/dto/<módulo>/XxxRequest` / `XxxResponse` | `record` con `@Schema` y Bean Validation |
| Mapper web | `infrastructure/web/mapper/XxxWebMapper` | MapStruct |
| Persistencia | `infrastructure/persistence/{entity,repository,adapter}` | `XxxEntity`, `XxxJpaRepository`, `XxxRepositoryAdapter` |

`domain/` no importa Spring (salvo el `@Component` de `OrderDomainService`), JPA ni nada de
`infrastructure/`.

## 3. Reglas de código

- Inyección por constructor (`@RequiredArgsConstructor` o constructor explícito); nada de
  `@Autowired` en campos.
- `@Transactional` en los handlers que escriben más de una cosa.
- Dinero con `BigDecimal` y `RoundingMode` explícito; nunca `double`.
- `Optional` para resultados que pueden no existir; no devuelvas `null` desde un puerto.
- Consultas con relaciones perezosas que se usan fuera de la transacción: `@EntityGraph` o
  `JOIN FETCH` (con `open-in-view: false`, acceder a una relación no cargada lanza
  `LazyInitializationException`).
- No captures `Exception` para ignorarla. Si un `catch` vacío es intencional, un comentario explica
  por qué.
- Logs con SLF4J (`@Slf4j`) y parámetros `{}`; nunca tokens, contraseñas ni cuerpos de pago.

## 4. Errores

| Situación | Lanza | HTTP |
|---|---|---|
| Regla de negocio o entrada inválida | `IllegalArgumentException` | 400 |
| Recurso inexistente | `ResourceNotFoundException` | 404 |
| Sin permiso | `AuthorizationException` / `AccessDeniedException` | 403 |
| Credenciales inválidas | `AuthenticationFailedException` | 401 |
| Estado que impide la operación | `IllegalStateException` | 409 |

Los mensajes se muestran al usuario: en español, claros y sin detalles internos.

## 5. Javadoc y comentarios

Los comentarios explican **por qué**, no qué.

- Javadoc en clases de dominio, handlers, adaptadores y configuración: qué responsabilidad tienen
  y qué decisiones o limitaciones no se ven en el código.
- En métodos, solo si el comportamiento no es evidente: efectos secundarios, excepciones,
  concurrencia, contrato con un tercero.
- Formato: `{@code ...}` para código, `<p>` para separar párrafos, `@throws` cuando la excepción es
  parte del contrato.
- Los DTOs se documentan con `@Schema` (descripción y ejemplo), no con Javadoc: es lo que ve
  Swagger.
- Las limitaciones conocidas se enlazan con su ID de la auditoría (`ver la auditoría técnica, M4`).
- Sin código comentado ni comentarios de historial ("antes…", "fix…").

```java
/**
 * Avanza el estado de un pedido, registra el cambio en el historial y publica
 * {@link OrderStatusChangedEvent}.
 *
 * <p>No verifica el código de confirmación al marcar {@code ENTREGADO}: el repartidor puede
 * cerrar un pedido sin haberlo entregado (ver la auditoría técnica, M4).
 */
```

## 6. Formato

- Sangría de 4 espacios; líneas de hasta ~120 caracteres (algunas firmas antiguas lo superan).
- Imports explícitos en código nuevo; quedan comodines heredados (`org.springframework.web.bind.annotation.*`,
  `jakarta.validation.constraints.*`) que no hace falta tocar.
- Un `record` o clase pública por archivo (salvo los `record` de comando anidados en el `UseCase`).

## 7. SQL y migraciones

- Una migración por cambio lógico, con nombre descriptivo en `snake_case`.
- Palabras clave en mayúsculas, identificadores en `snake_case`.
- Constraints con nombre (`chk_…`, `uq_…`, `idx_…`).
