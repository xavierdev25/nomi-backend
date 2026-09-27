# PRD — Backend de FoodV

## 1. Propósito

Ser la fuente de verdad de FoodV: quién es cada usuario y qué puede hacer, qué se vende, cuánto
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
  (máximo 50 % del subtotal), `tarifaServicio` S/ 0.50 y `comisionFoodv` S/ 0.20.
- **RF-14** Código de confirmación por pedido.
- **RF-15** Máquina de estados: `PENDIENTE → PREPARANDO → LISTO_PARA_RECOGER → EN_CAMINO →
  ENTREGADO`, y `PENDIENTE → CANCELADO`. Ningún otro salto es válido.
- **RF-16** Solo `PENDIENTE` es cancelable; cancelar devuelve el stock.
- **RF-17** El estudiante no cambia estados; tienda, repartidor y administrador sí, sobre los
  pedidos a los que tienen acceso.
- **RF-18** Historial de cambios de estado.

### Pagos
- **RF-19** Un pago por pedido, creado en MercadoPago Checkout Pro con el monto calculado en el
  servidor.
- **RF-20** Webhook firmado (HMAC-SHA256): pago aprobado → pedido `PREPARANDO`; rechazado →
  pedido cancelado y stock devuelto.
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

## 5. Fuera de alcance o incompleto (hoy)

- Asignación de repartidor y verificación del código de confirmación al entregar.
- Expiración de pedidos sin pagar (el stock reservado no se libera solo).
- Reembolsos automáticos.
- Notificación de cambios de estado (el evento se publica, pero nadie lo escucha).
- Verificación de comercios y repartidores en el registro.

Los riesgos asociados están priorizados en la auditoría técnica de `nomi-docs`.
