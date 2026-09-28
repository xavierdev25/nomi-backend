# SECURITY.md — `nomi-backend`

El backend es la frontera de confianza de Nomi: todo lo que llega de un cliente se valida aquí.

## 1. Autenticación

| Aspecto | Implementación |
|---|---|
| Contraseñas | BCrypt con coste 12. Nunca se devuelven ni se registran. |
| Access token | JWT firmado con HMAC (`JWT_SECRET`, mínimo 32 bytes; el arranque falla si es más corto). 24 h por defecto. |
| Refresh token | JWT de 7 días guardado en `refresh_tokens`. Rotación en cada uso; reutilizar uno revocado revoca todas las sesiones del usuario. |
| Logout | Revoca el refresh token y pone el access token en una blacklist en Redis durante la vida máxima de un access token. |
| Fuerza bruta | 5 logins fallidos bloquean el email 15 minutos (`RedisLoginAttemptAdapter`). |
| Respuestas | Sin sesión válida → `401` con `ApiError` (`SecurityConfig.writeUnauthorized`); sin permiso → `403`. |

## 2. Autorización

- Roles: `ESTUDIANTE`, `COMERCIO`, `REPARTIDOR`, `ADMIN`.
- Reglas por ruta en `SecurityConfig` (públicas: login, registro, refresh, webhook; todo lo demás
  requiere autenticación).
- **Ownership** en los controladores con `OwnershipService`: `requireSelfOrAdmin`,
  `requireStoreOwnerOrAdmin`, `requireProductOwnerOrAdmin`, `requireOrderAccess`. Todo endpoint
  que reciba un id de recurso ajeno debe pasar por uno de ellos.
- El estudiante nunca cambia el estado de un pedido.

## 3. Pagos

- El monto se calcula en el servidor; el cliente solo envía el id del pedido.
- Webhook de MercadoPago verificado con **HMAC-SHA256** sobre el manifiesto
  `id:…;request-id:…;ts:…;` con `MERCADOPAGO_WEBHOOK_SECRET`, comparación en tiempo constante y
  tolerancia de 5 minutos en `ts`. Sin cabeceras → 401; firma inválida → 403.
- Tras una notificación válida, el estado del pago se **consulta a MercadoPago**, no se toma del
  cuerpo recibido. El pedido lo cambia solo un pago aprobado; si la consulta falla no se decide
  nada (`503`/`500`) y se reintenta.
- Un pedido solo se paga mientras está `PENDIENTE` y en plazo; el checkout vence con él y se
  cierra al cancelar: nadie paga un pedido que ya no se va a entregar. Si aun así llega un pago aprobado para un pedido
  cancelado, o un segundo pago del mismo pedido, se reembolsa.
- Salir de `PENDIENTE` es una actualización condicional (`… WHERE status = 'PENDIENTE'`): webhook,
  cancelación y caducidad simultáneos no pueden cobrar sin entregar ni devolver stock dos veces.
- Ni tiendas ni repartidores pueden pasar un pedido a preparación sin pago ni cancelarlo con
  `PATCH /status`.
- Anti-acaparamiento: un pedido sin pagar caduca a los 15 minutos y cada estudiante tiene como
  mucho 2 sin pagar.

## 4. Protección de la API

| Medida | Detalle |
|---|---|
| Rate limiting | Redis, ventana de 1 minuto: 100 general, 30 autenticación, 60 pagos, 5 IA; `429` con `Retry-After`. |
| Cabeceras | `SecurityHeadersFilter`: `nosniff`, `X-Frame-Options: DENY`, HSTS, CSP, `Referrer-Policy: no-referrer`, `Permissions-Policy`, COOP/CORP y `Cache-Control: no-store`. |
| CORS | Orígenes desde `CORS_ALLOWED_ORIGINS` (WebSocket: `WS_ALLOWED_ORIGINS`). |
| Validación | Bean Validation en todos los DTOs de entrada; los errores no exponen trazas. |
| Errores | `GlobalExceptionHandler` devuelve mensajes controlados; un 500 nunca incluye la excepción. |
| Subidas | Máximo 10 MB por archivo. |
| Servicio de IA | Llamadas con `X-API-Key` (`AI_SERVICE_SECRET_KEY`), timeouts y circuit breaker. |

## 5. Perfiles

- Fuera de `prod`, Swagger y `actuator/health|info|prometheus` son públicos, y los logs pueden
  incluir SQL con parámetros (`TRACE`), lo que expone tokens y hashes en los logs.
- En `prod`, Swagger y actuator exigen `ADMIN`, y los logs bajan a `INFO`/`WARN`.
- **El perfil por defecto es `dev`**: todo despliegue debe fijar `SPRING_PROFILES_ACTIVE=prod`.

## 6. Secretos

- Los secretos viven en `.env` (ignorado por git) o en variables de entorno; `.env.example` solo
  tiene marcadores.
- Nunca escribas secretos reales en código, tests, `application*.yaml`, documentación ni logs.
- Las credenciales de MercadoPago de desarrollo deben ser de prueba (`TEST-…`).
- `firebase-credentials.json` está ignorado; el repositorio solo incluye un ejemplo.
- En CI, los secretos vienen de GitHub Secrets con marcadores de respaldo.

## 7. Riesgos abiertos

Detallados y priorizados en la auditoría técnica de `nomi-docs`:

| ID | Riesgo |
|---|---|
| A1 | Suscripciones WebSocket sin autorización |
| A4 | Perfil `dev` por defecto |
| A5 | Auto-registro como `COMERCIO`/`REPARTIDOR` sin verificación |
| M1 | Rate limit evadible (tokens falsos, `X-Forwarded-For`, límite de pagos inactivo) |
| M2 | Bloqueo de login usable como denegación de servicio |
| M3 | Tokens sin `jti` |

## 8. Checklist para cambios

- [ ] ¿El endpoint nuevo requiere autenticación y comprueba rol u ownership?
- [ ] ¿Valida toda la entrada con Bean Validation?
- [ ] ¿Algún importe se toma del cliente? (No debe.)
- [ ] ¿Se registra algún dato sensible en logs?
- [ ] ¿Hay test del caso no autorizado (401/403)?

## 9. Reportar un problema

En privado al responsable del proyecto, no en issues públicos.
