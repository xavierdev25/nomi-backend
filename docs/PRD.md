# PRD — Backend de Nomi

## 1. Propósito

Ser la fuente de verdad de Nomi: quién es cada usuario y qué puede hacer, qué se vende, cuánto
cuesta, qué stock queda, en qué estado está cada pedido y si se pagó. Los clientes (app iOS)
solo presentan y solicitan; toda regla de negocio se aplica aquí.

El contexto de producto (actores, ciclo del pedido, reglas) está en `nomi-docs`, sección
**Producto**.

## 2. Actores

| Rol | Puede |
|---|---|
| `ESTUDIANTE` | Ver catálogo, crear y pagar pedidos, cancelar los suyos en `PENDIENTE`, favoritos, calificar, recibir recomendaciones |
| `COMERCIO` | Gestionar su tienda y sus productos; avanzar el estado de los pedidos de su tienda |
| `REPARTIDOR` | Avanzar el estado de los pedidos que puede ver (el flujo de asignación está incompleto) |
| `ADMIN` | Todo, incluidos usuarios, aulas, tiendas y restauración de borrados |

## 3. Módulos y requisitos

### Autenticación y usuarios
- **RF-01** Registro con validación de nombres, email, contraseña fuerte, teléfono y
  restricciones alimentarias (`VEGETARIANO`, `VEGANO`, `SIN_GLUTEN`, `SIN_LACTOSA`, `NINGUNA`).
  El registro no inicia sesión.
- **RF-02** Login con email y contraseña → access token (24 h) y refresh token (7 días).
- **RF-03** Renovación con rotación: cada refresh revoca el token usado; reutilizar uno revocado
  revoca todas las sesiones del usuario.
- **RF-04** Logout que revoca el refresh token y pone el access token en blacklist.
- **RF-05** Bloqueo temporal tras 5 intentos fallidos de login (15 minutos).
- **RF-06** Perfil propio (`/users/me`), cambio de contraseña y soft delete con restauración por
  administrador.

### Catálogo
- **RF-07** Tiendas con dueño, horario y estado activo.
- **RF-08** Productos con categoría (`COMIDA`, `BEBIDA`, `SNACK`, `POSTRE`, `OTRO`), precio, stock,
  `activo`, `disponible` y **etiquetas dietéticas**: las restricciones para las que el comercio
  declara apto el producto. Sin etiquetas, no es apto para ninguna.
- **RF-09** Búsqueda paginada por texto, categoría y tienda.
- **RF-10** Aulas del campus (solo las activas se ofrecen al estudiante).
- **RF-11** Imágenes de productos y tiendas en Cloudinary.

### Pedidos
- **RF-12** Crear un pedido de una sola tienda activa, con productos disponibles y stock
  suficiente. El stock se **reserva al crear** con un descuento atómico.
- **RF-13** Importes calculados en el servidor: `total` (subtotal de productos), `propina`
  (máximo 50 % del subtotal), `tarifaServicio` S/ 0.50 y `comisionNomi` S/ 0.20.
- **RF-14** Código de confirmación por pedido.
- **RF-15** Máquina de estados: `PENDIENTE → PREPARANDO → LISTO_PARA_RECOGER → EN_CAMINO →
  ENTREGADO`, y `PENDIENTE → CANCELADO`. Ningún otro salto es válido.
- **RF-16** Solo `PENDIENTE` sin pago aprobado es cancelable; cancelar devuelve el stock. Si
  MercadoPago ya aprobó el pago, el pedido pasa a `PREPARANDO` en vez de cancelarse (`409`).
- **RF-17** El estudiante no cambia estados; tienda, repartidor y administrador sí, sobre los
  pedidos a los que tienen acceso y a partir de `PREPARANDO`. Nadie saca un pedido de `PENDIENTE`
  con `PATCH /status`: lo hacen el pago aprobado y la cancelación.
- **RF-18** Historial de cambios de estado.
- **RF-18a** Plazo para pagar: un pedido `PENDIENTE` vence a los 15 minutos
  (`ORDER_PAYMENT_WINDOW_MINUTES`). Vencido sin pago aprobado, se cancela ("El pago no se completó
  a tiempo") y devuelve el stock. La respuesta incluye `pagoExpiraEn` y `segundosParaPagar`.
- **RF-18b** Un estudiante tiene como mucho 2 pedidos sin pagar y dentro de plazo
  (`ORDER_MAX_PENDING_PER_USER`); el tercero responde `409`.

### Pagos
- **RF-19** Un checkout por pedido, creado en MercadoPago Checkout Pro con el monto calculado en
  el servidor, solo para pedidos `PENDIENTE` y dentro de plazo. Vence con el pedido y excluye los
  medios diferidos (`ticket`, `atm`).
- **RF-20** Webhook firmado (HMAC-SHA256). El backend consulta el pago en MercadoPago (no confía
  en el aviso): aprobado → pedido `PREPARANDO`; rechazado, pendiente o en revisión → el pedido no
  cambia y se puede reintentar. Si la consulta falla, responde error para que MercadoPago reenvíe.
- **RF-20a** Conciliación cada minuto (`ORDER_RECONCILE_INTERVAL_MS`): confirma pedidos pagados
  cuyo webhook no llegó y cancela los vencidos, siempre tras consultar MercadoPago.
- **RF-20b** Reembolso automático de un pago aprobado para un pedido ya cancelado y de un segundo
  pago aprobado del mismo pedido. Cancelar cierra el checkout en MercadoPago, y una revisión cada
  10 minutos (`ORDER_LATE_PAYMENT_CHECK_INTERVAL_MS`) reembolsa los pagos que se aprueben durante
  las 72 h siguientes a la cancelación (`ORDER_LATE_PAYMENT_WINDOW_HOURS`), sin depender del
  webhook.
- **RF-21** Consulta del pago de un pedido y de los pagos propios.

### Favoritos, calificaciones e IA
- **RF-22** Favoritos de productos y tiendas, con comprobación individual.
- **RF-23** Una calificación (1–5) por pedido; resumen por tienda.
- **RF-24** Recomendaciones en dos etapas. El backend elige los **candidatos** de forma
  determinista: productos que se pueden pedir, de tiendas activas, aptos para todas las
  restricciones del estudiante según sus etiquetas, que no marcó como "no me gusta"; primero los
  que más pide, como mucho 40. `nomi-ai-service` solo los ordena y explica (de 1 a 20).
- **RF-25** Una recomendación nunca incluye un producto que no cumple una restricción del
  estudiante. Si no hay candidatos, o el estudiante tiene una restricción que no se puede
  comprobar, respuesta vacía con `generatedBy = "SIN_CANDIDATOS"` sin llamar al servicio de IA.
- **RF-26** Si el servicio de IA falla o rechaza la petición, respuesta vacía con
  `generatedBy = "FALLBACK"`, nunca un error.
- **RF-27** Feedback de recomendaciones; los productos con "no me gusta" dejan de recomendarse.

### Notificaciones
- **RF-28** Eventos por WebSocket (STOMP) y push con Firebase (desactivable).

## 4. Requisitos no funcionales

- **RNF-01** Errores con cuerpo uniforme (`ApiError`) y mensajes en español.
- **RNF-02** Rate limiting por minuto: 100 general, 30 autenticación, 60 pagos, 5 IA.
- **RNF-03** Sin estado de sesión en el servidor (JWT); secretos solo por entorno.
- **RNF-04** Esquema versionado con Flyway; nada de `ddl-auto`.
- **RNF-05** API documentada en OpenAPI; Swagger cerrado en producción.
- **RNF-06** Métricas de negocio en Prometheus.
- **RNF-07** Los cambios de estado que compiten (webhook, cancelación, caducidad) son
  actualizaciones condicionales sobre `status = 'PENDIENTE'`: solo uno gana, sin cobrar sin
  entregar ni devolver stock dos veces.

## 5. Fuera de alcance o incompleto (hoy)

- Asignación de repartidor y verificación del código de confirmación al entregar.
- Notificación de cambios de estado (el evento se publica, pero nadie lo escucha).
- Verificación de comercios y repartidores en el registro.

Los riesgos asociados están priorizados en la auditoría técnica de `nomi-docs`.
