# Changelog — `nomi-backend`

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/). Las versiones
siguen [SemVer](https://semver.org/lang/es/). Los cambios nuevos van en **No publicado**.

## [No publicado]

### Añadido
- **Caducidad de pedidos sin pagar** (migración V24): un pedido `PENDIENTE` vence a los 15
  minutos (`ORDER_PAYMENT_WINDOW_MINUTES`) y se cancela solo, devolviendo el stock. Cada
  estudiante puede tener como mucho 2 sin pagar (`ORDER_MAX_PENDING_PER_USER`); el tercero
  responde `409`. `OrderResponse` incluye `pagoExpiraEn` y `segundosParaPagar`.
- **Conciliación con MercadoPago** cada minuto (`ORDER_RECONCILE_INTERVAL_MS`): confirma los
  pedidos pagados cuyo webhook no llegó y cancela los vencidos, siempre tras consultar el pago.
- **Reembolsos automáticos** de un pago aprobado para un pedido ya cancelado y de un segundo pago
  aprobado del mismo pedido. `payments.gateway_payment_id` guarda el pago que decidió el estado.
- El checkout de MercadoPago vence con el pedido y no ofrece medios diferidos (`ticket`, `atm`).
- `503 Pagos no disponibles` cuando MercadoPago no responde a una consulta.
- Referencia única por checkout en MercadoPago (`nomi-<pedido>-<uuid>`, migración V25).
- **Cancelar un pedido cierra su checkout** en MercadoPago, y una revisión cada 10 minutos
  (`ORDER_LATE_PAYMENT_CHECK_INTERVAL_MS`) **reembolsa los pagos aprobados de pedidos cancelados**
  en las últimas 72 h (`ORDER_LATE_PAYMENT_WINDOW_HOURS`), sin depender del webhook.
- Los errores de la API de MercadoPago se registran con su código y su cuerpo.
- 101 tests de pagos, webhook, cancelación, caducidad y actualizaciones condicionales (169 en
  total).
- Etiquetas dietéticas en los productos (`etiquetasDieteticas`, migración V22): restricciones
  para las que el comercio declara apto cada producto, con `CHECK` de valores válidos.
- Recomendaciones en dos etapas: el backend elige los candidatos de forma determinista
  (`RecommendationCandidates`: se pueden pedir, tienda activa, aptos para las restricciones del
  estudiante, sin "no me gusta", los más pedidos primero, como mucho 40) y el servicio de IA solo
  los ordena y explica. Sin candidatos, `generatedBy = "SIN_CANDIDATOS"` sin llamar a la IA.
- Cabecera `X-Nomi-User-Id` hacia el servicio de IA, para su límite de peticiones por estudiante.
- Documentación OpenAPI completa: descripción general, tags, `@Schema` en todos los DTOs y
  respuestas de error comunes (`400`, `401`, `403`, `429`, `500`) con el esquema `ApiError`.
- Javadoc en español de todo el código de producción.
- Documentación del repositorio: README, AGENTS y `docs/`.
- Tests: carga del `.env`, respuestas `401`, restricciones, selección de candidatos, handler de
  recomendaciones, circuit breaker con `4xx` y registro con restricción no válida.

### Cambiado
- **El checkout ya no envía `notification_url`.** MercadoPago firma esos avisos con una clave
  distinta de la del panel y nunca pasaban la verificación. El webhook se da de alta en el panel
  de MercadoPago de cada entorno; `MERCADOPAGO_NOTIFICATION_URL` queda solo como URL de retorno.
- **La marca es Nomi en todo el sistema.** Paquete Java `com.nomi.backend` y `groupId`
  `com.nomi`; nombre de la aplicación y emisor JWT por defecto `nomi-backend`; base de datos y
  usuario por defecto `nomi_db` / `nomi_user`; contenedores `nomi-postgres`, `nomi-redis` y
  `nomi-ai-service`; métricas `nomi.*`; carpetas de Cloudinary `nomi/…`.
- **Cambio de contrato:** la comisión de la plataforma se llama `comisionNomi` en la API y
  `comision_nomi` en la base (migración V23). Los clientes deben usar el nombre nuevo.
- Cabecera hacia el servicio de IA: `X-Nomi-User-Id`.
- El registro (y el alta por admin) solo admite restricciones `VEGETARIANO`, `VEGANO`,
  `SIN_GLUTEN`, `SIN_LACTOSA` o `NINGUNA`.
- El presupuesto se envía a la IA como `presupuesto medio` (sin `:`, que el servicio rechazaba) y
  la franja horaria va primero en las preferencias.
- El circuit breaker `aiService` ignora los `4xx` (`AiResilienceConfig`); el adaptador los
  registra como error con el código y el inicio del cuerpo.

### Corregido
- **El webhook cancelaba pedidos con pago pendiente, en revisión o cuya consulta fallaba**
  (auditoría C1). Ahora solo un pago aprobado cambia el pedido, un rechazo lo deja abierto para
  reintentar y un fallo de consulta responde error para que MercadoPago reenvíe.
- **Se podía cancelar un pedido ya pagado sin reembolso** (A3). La cancelación consulta
  MercadoPago: con un pago aprobado confirma el pedido y responde `409`.
- **Un pedido sin pagar retenía el stock para siempre** (A2): ver caducidad.
- `PATCH /orders/{id}/status` dejaba pasar un pedido `PENDIENTE` a `PREPARANDO` sin cobrar, o a
  `CANCELADO` sin devolver el stock. Ahora responde `409`: lo primero lo hace el pago aprobado y lo
  segundo `/cancel`.
- `POST /payments` creaba un checkout para pedidos cancelados o vencidos.
- **Un pago antiguo podía dar por pagado un pedido nuevo** (hallado con el sandbox real): la
  referencia del checkout era el id del pedido, que se repite entre bases de datos y entornos.
  Ahora es única y un pago solo cuenta si coinciden referencia y monto (y, en checkouts antiguos,
  si no es anterior al checkout); un pago ajeno tampoco se reembolsa como duplicado.
- La búsqueda de pagos de un checkout fallaba siempre: el SDK de MercadoPago necesita `offset`.
- **El webhook rechazaba (`400`) todas las notificaciones actuales de MercadoPago**: exigía
  `topic`, que solo envía el formato IPN antiguo, y los webhooks firmados usan `type=payment`. Se
  aceptan ambos y se ignoran los eventos que no son de pago. Un pago que MercadoPago no conoce
  (el aviso de prueba del panel) responde `200` en vez de reintentarse para siempre.
- El paso a preparación y la cancelación son actualizaciones condicionales: webhook, cancelación y
  caducidad simultáneos ya no pueden cobrar sin entregar ni devolver el stock dos veces.
- **Seguridad de dependencias** (el paso *Security scan* de la CI fallaba con 7 CVE CRITICAL y 37
  HIGH; Trivy da ahora 0): Spring Boot 4.0.6 → 4.0.8, Tomcat 11.0.26, HttpClient 5.6.4 /
  HttpCore 5.4.3, `firebase-admin` 9.4.2 → 9.11.0 (gRPC 1.83), y exclusión de las dependencias
  de build que el SDK de MercadoPago declaraba en tiempo de ejecución (plugin de Javadoc,
  Maven, Velocity, `commons-compress`).
- `aquasecurity/trivy-action` fijado a un commit (v0.36.0) en lugar de `@master`.
- Se recomendaban platos que violaban las restricciones del estudiante (por ejemplo Ají de
  Gallina a un vegetariano).
- Se enviaban a la IA productos agotados y de tiendas inactivas, y todo el catálogo (más de 200
  productos → `422` → sin recomendaciones).
- Entre las 11:00 y las 15:00 las recomendaciones fallaban siempre por `presupuesto:MEDIO`.
- Un `422` o `429` del servicio de IA abría el circuito y cortaba las recomendaciones de todos
  durante 30 s.
- Un token ausente, expirado o revocado respondía `403` en lugar de `401`, por lo que los clientes
  no renovaban la sesión. Ahora responde `401` con cuerpo `ApiError`.
- `PATCH /orders/{id}/cancel` y `GET /orders/{id}/history` respondían `500`
  (`LazyInitializationException`): los ítems del pedido se cargan con `@EntityGraph`.
- El `.env` no se cargaba (el post-procesador estaba registrado en un archivo que Spring Boot no
  lee) y su prioridad sobre las variables de entorno era la inversa. Ahora se registra en
  `META-INF/spring.factories` y el entorno manda.

## [1.0.0] — 2026-04-27

Primera versión completa.

### Añadido
- Módulos de usuarios, aulas, tiendas, productos (con búsqueda y filtros), pedidos con máquina
  de estados, pagos con MercadoPago y webhook firmado, notificaciones (WebSocket y FCM) y
  recomendaciones con IA con feedback.
- Seguridad: JWT con refresh tokens en base de datos y blacklist en Redis, RBAC y checks de
  ownership, rate limiting, bloqueo por intentos fallidos, cabeceras de seguridad.
- Soft delete, historial de estados, tareas de limpieza, perfiles `dev`/`prod`, caché Redis,
  métricas de negocio, CI con GitHub Actions.

### Cambios posteriores a 1.0.0 (sin versionar)
- Perfil y verificación de repartidor, universidades y campus, estado `LISTO_PARA_RECOGER`,
  cancelación solo en `PENDIENTE`, propina y tarifas en los pedidos (abril 2026).
- Logout con revocación (abril 2026).
- Favoritos y calificaciones, horario de tiendas (mayo 2026).
