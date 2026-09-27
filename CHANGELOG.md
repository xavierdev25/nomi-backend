# Changelog — `nomi-backend`

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/). Las versiones
siguen [SemVer](https://semver.org/lang/es/). Los cambios nuevos van en **No publicado**.

## [No publicado]

### Añadido
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
  recomendaciones, circuit breaker con `4xx` y registro con restricción no válida (68 en total).

### Cambiado
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
