# API.md — `nomi-backend`

La referencia completa, con esquemas y ejemplos, es **Swagger UI**:
`http://localhost:8080/api/swagger-ui.html` (JSON en `/api/api-docs`). Está cerrada en `prod`.
Este documento recoge las convenciones y lo que no se ve en Swagger.

## 1. Convenciones

- Base: `/api` (context path). JSON en ambos sentidos.
- Autenticación: `Authorization: Bearer <accessToken>` en todo salvo login, registro, refresh,
  webhook y la documentación.
- Nombres de campos en español cuando son del dominio (`nombre`, `precio`, `propina`, `aulaId`).
- Fechas: `LocalDateTime` sin zona (`2026-09-26T00:13:59.905824`), en hora del servidor.
- Dinero: números decimales en soles (PEN).
- Enums en mayúsculas: `ESTUDIANTE`, `PENDIENTE`, `COMIDA`…
- Cada respuesta lleva `X-Request-Id` para cruzarla con los logs.

### Paginación

Parámetros `page` (desde 0), `size` (por defecto 20; fuera de 1–200 vuelve a 20) y `sortBy`
(por defecto `id`). Respuesta:

```json
{ "content": [], "totalElements": 0, "totalPages": 0, "currentPage": 0, "pageSize": 20 }
```

## 2. Errores

Cuerpo común (`ApiError`):

```json
{ "timestamp": "…", "status": 400, "error": "Error de validación",
  "message": "Hay errores en los campos enviados",
  "fields": { "email": "El email no tiene un formato válido" } }
```

`fields` solo aparece en errores de validación. Algunas respuestas omiten campos (el login
inválido solo trae `error`), así que los clientes deben tratarlos todos como opcionales.

| Código | Cuándo |
|---|---|
| `400` | Validación, JSON inválido, parámetro faltante o de tipo incorrecto, regla de negocio (`IllegalArgumentException`: stock, propina, transición de estado…) |
| `401` | Sin token, token inválido, expirado o revocado; credenciales incorrectas |
| `403` | Autenticado sin permiso (rol u ownership) |
| `404` | Recurso inexistente |
| `409` | Conflicto de datos (duplicados) o estado no permitido (`IllegalStateException`) |
| `429` | Rate limit superado; incluye `Retry-After` |
| `500` | Error inesperado (sin detalles internos) |

> Los clientes renuevan la sesión solo ante `401`. Un `403` significa "no puedes", no "tu sesión
> expiró".

## 3. Endpoints por módulo

| Módulo | Endpoints | Quién |
|---|---|---|
| Autenticación | `POST /auth/login`, `/auth/register`, `/auth/refresh` (públicos), `POST /auth/logout` | Todos |
| Usuarios | `GET/PUT /users/me`, `PUT /users/me/password` | Autenticado |
| | `GET/PUT/DELETE /users/{id}` | El propio usuario o admin |
| | `GET/POST /users`, `GET /users/deleted`, `POST /users/{id}/restore` | Admin |
| Aulas | `GET /aulas`, `/aulas/activas`, `/aulas/{id}` | Autenticado |
| | `POST /aulas`, `PUT/DELETE /aulas/{id}` | Admin |
| Tiendas | `GET /stores`, `/stores/search`, `/stores/{id}` | Autenticado |
| | `POST /stores` | Comercio o admin |
| | `GET /stores/me`, `PUT/DELETE /stores/{id}` | Dueño o admin |
| | `GET /stores/admin` | Admin |
| Productos | `GET /products`, `/products/search`, `/products/{id}`, `/products/store/{storeId}`, `/products/categoria/{categoria}` | Autenticado |
| | `POST /products`, `PUT/DELETE /products/{id}` | Dueño de la tienda o admin |
| Imágenes | `POST /images/products/{productId}`, `POST /images/stores/{storeId}`, `DELETE /images?publicId=` | Dueño o admin |
| Pedidos | `POST /orders` | Estudiante o admin |
| | `GET /orders/me`, `GET /orders/{id}`, `GET /orders/{id}/history`, `PATCH /orders/{id}/cancel` | Con acceso al pedido |
| | `PATCH /orders/{id}/status` | Tienda, repartidor o admin con acceso (nunca estudiante) |
| | `GET /orders`, `/orders/user/{userId}`, `/orders/store/{storeId}`, `/orders/status/{status}` | Según rol y ownership |
| Pagos | `POST /payments`, `GET /payments/{id}`, `/payments/order/{orderId}`, `/payments/me` | Dueño del pedido o admin |
| | `GET /payments/user/{userId}` | El propio usuario o admin |
| | `POST /payments/webhook` | Público, con firma de MercadoPago |
| Favoritos | `GET/POST/DELETE /favorites/products[/{id}]`, `GET …/{id}/check`; ídem `/favorites/stores` | Autenticado (propios) |
| Calificaciones | `POST/GET /ratings/orders/{orderId}`, `GET /ratings/stores/{storeId}[/all]` | Autenticado |
| IA | `GET /ai/recommendations?maxRecommendations=5`, `POST /ai/recommendations/feedback` | Autenticado |
| Operación | `GET /actuator/health`, `/info`, `/prometheus` | Públicos fuera de `prod`; admin en `prod` |

## 4. Contratos que no son obvios

- **`POST /auth/register`** no devuelve tokens: hay que hacer login después. `restrictions` solo
  admite `VEGETARIANO`, `VEGANO`, `SIN_GLUTEN`, `SIN_LACTOSA` o `NINGUNA` (otro valor: 400 con el
  campo `restrictions[i]`).
- **Productos**: `etiquetasDieteticas` (en creación, edición y respuesta) son las restricciones
  para las que el comercio declara apto el producto (`VEGETARIANO`, `VEGANO`, `SIN_GLUTEN`,
  `SIN_LACTOSA`). En la edición, enviarlas reemplaza las anteriores y omitirlas no las cambia.
  Un producto sin etiquetas no se recomienda a estudiantes con restricciones.
- **`POST /auth/refresh`** rota el par. El refresh token usado queda revocado; reutilizarlo
  revoca todas las sesiones del usuario. Cualquier fallo responde 400.
- **`GET /users/me`** devuelve los datos contenidos en el token: apellidos, teléfono y
  preferencias llegan `null`.
- **`OrderResponse.total`** es el subtotal de productos. Lo cobrado es
  `total + propina + tarifaServicio + comisionFoodv`.
- **Propina**: no negativa y como máximo el 50 % del subtotal (redondeo half-up a 2 decimales).
- **Un pago por pedido**: un segundo `POST /payments` responde 400. Consultar primero
  `GET /payments/order/{orderId}` (404 si no existe).
- **Cancelar**: solo en `PENDIENTE`; devuelve el stock.
- **Recomendaciones**: nunca fallan y nunca incluyen un producto que no cumpla las restricciones
  del estudiante (se filtra en el backend antes de llamar al modelo). `generatedBy` indica el
  origen: el modelo (`phi3`, `groq/…`), `SIN_CANDIDATOS` (ningún producto cumple los filtros; no
  se llama a la IA) o `FALLBACK` (la IA falló). Límite de 5 peticiones por minuto.
- **Webhook**: MercadoPago envía `x-signature` y `x-request-id`. Sin cabeceras responde 401 y con
  una firma inválida, 403.

## 5. Tiempo real

WebSocket STOMP en `/api/ws`. El `CONNECT` exige `Authorization: Bearer`. Destinos:
`/topic/order/{id}`, `/topic/user/{id}`, `/topic/store/{id}`. Las suscripciones no validan
ownership (auditoría A1): no los uses para datos sensibles hasta corregirlo.

## 6. Cambiar el contrato

1. Cambia el DTO (`record` con `@Schema` y validaciones) y el mapper.
2. Comprueba en Swagger que el esquema y los errores salen bien.
3. Actualiza este documento y la sección de API de `nomi-docs`.
4. Revisa el impacto en `nomi-ios` (`docs/API.md` de ese repo).
