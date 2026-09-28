# DEPLOYMENT.md — `nomi-backend`

## 1. Configuración

La configuración sale de `application.yaml` y del perfil activo, y los valores sensibles de
variables de entorno. En local, el archivo `.env` de la raíz se carga automáticamente con
**menor prioridad** que las variables de entorno reales: en un despliegue, manda el entorno.

### Variables de entorno

Los valores de ejemplo están en `.env.example`. **Nunca pongas valores reales en este documento
ni en el repositorio.**

| Variable | Obligatoria | Por defecto | Uso |
|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | En despliegue | `dev` | `dev` o `prod`. Fija `prod` fuera de local. |
| `SERVER_PORT` | No | `8080` | Puerto HTTP |
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME` | No | `localhost`, `5432`, `nomi_db`, `nomi_user` | Conexión a PostgreSQL |
| `DB_PASSWORD` | Sí | — | Contraseña de PostgreSQL |
| `DB_POOL_SIZE` | No | `10` | Máximo de conexiones Hikari |
| `REDIS_HOST`, `REDIS_PORT` | No | `localhost`, `6379` (`6380` en `dev`) | Redis |
| `JWT_SECRET` | Sí | — | Secreto HMAC, **mínimo 32 bytes** |
| `JWT_EXPIRATION` | No | `86400000` (24 h) | Vida del access token, en ms |
| `JWT_REFRESH_EXPIRATION` | No | `604800000` (7 días) | Vida del refresh token, en ms |
| `JWT_ISSUER` | No | `nomi-backend` | Emisor del JWT |
| `MERCADOPAGO_ACCESS_TOKEN`, `MERCADOPAGO_PUBLIC_KEY` | Sí | — | Credenciales de MercadoPago (`TEST-…` fuera de producción) |
| `MERCADOPAGO_WEBHOOK_SECRET` | Sí | — | Clave secreta del webhook configurado en el panel de MercadoPago (Tus integraciones → Webhooks) de la misma aplicación que las credenciales; sin ella se rechazan todos los avisos |
| `MERCADOPAGO_NOTIFICATION_URL` | No | `http://localhost:8080/api/payments/webhook` | URL de retorno tras pagar (hoy la del webhook, auditoría M8). **No** configura los avisos: esos se dan de alta en el panel |
| `ORDER_PAYMENT_WINDOW_MINUTES` | No | `15` | Minutos para pagar un pedido antes de que se cancele solo; también el vencimiento del checkout |
| `ORDER_MAX_PENDING_PER_USER` | No | `2` | Pedidos sin pagar (y en plazo) que puede tener un estudiante |
| `ORDER_RECONCILE_INTERVAL_MS` | No | `60000` | Cada cuánto se concilian los pedidos pendientes con MercadoPago |
| `ORDER_LATE_PAYMENT_CHECK_INTERVAL_MS` | No | `600000` | Cada cuánto se buscan pagos aprobados de pedidos ya cancelados para reembolsarlos |
| `ORDER_LATE_PAYMENT_WINDOW_HOURS` | No | `72` | Durante cuántas horas desde la cancelación se buscan esos pagos |
| `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET` | Sí | — | Imágenes |
| `AI_SERVICE_URL` | No | `http://localhost:8001` | URL de `nomi-ai-service` |
| `AI_SERVICE_SECRET_KEY` | Sí para IA | vacío | Debe coincidir con `API_SECRET_KEY` del servicio de IA |
| `CORS_ALLOWED_ORIGINS` | No | `http://localhost:3000,http://localhost:5173` | Orígenes REST |
| `WS_ALLOWED_ORIGINS` | No | igual que CORS | Orígenes WebSocket |
| `FIREBASE_ENABLED` | No | `false` | Activa las notificaciones push |
| `FIREBASE_CREDENTIALS_PATH`, `FIREBASE_PROJECT_ID` | Si Firebase está activo | vacío | Credenciales de Firebase |
| `LOG_LEVEL`, `JPA_SHOW_SQL`, `HIBERNATE_LOG_LEVEL` | No | `DEBUG`, `false`, `WARN` | Logs |

Las variables `PGADMIN_*`, `OLLAMA_HOST`, `ALLOWED_ORIGINS`, `GROQ_API_KEY` y
`AI_CACHE_TTL_SECONDS` del `.env.example` solo las usa Docker Compose.

### Perfiles

| | `dev` | `prod` |
|---|---|---|
| Swagger / OpenAPI | Público | Desactivado (y protegido con `ADMIN`) |
| Actuator | `health`, `info`, `metrics`, `prometheus` públicos | Solo `health` e `info`, con `ADMIN` |
| Logs | `DEBUG`, SQL con parámetros (`TRACE`) | `INFO`/`WARN` |
| Docker Compose | Spring Boot arranca `docker-compose.yml` si no está corriendo | Desactivado |

El perfil `test` (solo tests) está en `src/test/resources/application-test.yaml`.

## 2. Local

```bash
cp .env.example .env
docker compose up -d postgres redis
./mvnw spring-boot:run
```

`docker-compose.yml` define `postgres` (puerto `DB_PORT`), `redis` (`6380` en el host) y
`ai-service`, que se construye desde `../nomi-ai-service` (el repositorio vecino) y usa el Ollama
del host. pgAdmin está en `docker-compose.override.yml.example`.

## 3. Imagen Docker

```bash
docker build -t nomi-backend .
docker run --env-file .env -e SPRING_PROFILES_ACTIVE=prod -p 8080:8080 nomi-backend
```

El `Dockerfile` compila con Maven (sin tests) y ejecuta el JAR con JRE 21 Alpine como usuario sin
privilegios, con `HEALTHCHECK` en `/api/actuator/health`. No fija el perfil: pásalo siempre.

## 4. Integración continua

`.github/workflows/ci.yml`, en cada push a `main`/`develop` y en PRs a `main`:

1. **Tests**: `./mvnw verify` con PostgreSQL 16 y Redis 7 como servicios; publica los reportes.
2. **Build**: empaqueta el JAR.
3. **Docker build**: construye la imagen.
4. **Security scan**: Trivy sobre la imagen; falla con vulnerabilidades `CRITICAL` o `HIGH`.

Los secretos de CI (`CI_DB_PASSWORD`, `CI_JWT_SECRET`, `CI_MERCADOPAGO_*`, `CI_CLOUDINARY_*`) vienen
de GitHub Secrets y tienen marcadores de respaldo.

## 5. Checklist de producción

- [ ] `SPRING_PROFILES_ACTIVE=prod`.
- [ ] `JWT_SECRET` aleatorio de al menos 32 bytes, distinto por entorno.
- [ ] Credenciales de MercadoPago de producción y `MERCADOPAGO_NOTIFICATION_URL` pública con HTTPS.
- [ ] `MERCADOPAGO_WEBHOOK_SECRET` configurado.
- [ ] PostgreSQL y Redis gestionados, con contraseña y sin exposición pública.
- [ ] CORS limitado a los orígenes reales.
- [ ] HTTPS terminado en el proxy; rate limiting ajustado a la IP real (auditoría M1).
- [ ] Riesgos A1, A4 y A5 de la auditoría técnica resueltos o aceptados.
- [ ] Ciclo de pago probado con credenciales de sandbox: pago aprobado, rechazo y reintento,
      caducidad y reembolso de un pago que llega tarde.
- [ ] **Reembolso real probado** con credenciales de producción y un pago pequeño: en el sandbox la
      API de reembolsos responde `401 Unauthorized use of live credentials`.
- [ ] **Webhook dado de alta en el panel de MercadoPago** de la aplicación de producción:
      URL `https://<dominio>/api/payments/webhook`, evento **Pagos**, y su clave secreta en
      `MERCADOPAGO_WEBHOOK_SECRET`. El checkout no envía `notification_url`: MercadoPago firma esos
      avisos con otra clave y se rechazarían (comprobado en el sandbox el 27-09-2026; los avisos del
      panel sí validan). Probar con "Simular" (debe responder 200) y con un pago real (200 en los
      logs, no 403).
- [ ] Con varias instancias, la conciliación corre en todas: es seguro, pero multiplica las
      consultas a MercadoPago; valorar un bloqueo distribuido (ShedLock) si crece la carga.

## 6. Problemas frecuentes

| Síntoma | Causa |
|---|---|
| `JWT_SECRET debe tener al menos 32 bytes… Actual: 13` | La variable no se resolvió: falta en el entorno y en `.env` (13 es la longitud de `${JWT_SECRET}`). |
| El arranque construye `ai-service` o choca con el puerto 8001 | Spring Boot levanta todo el compose si no encuentra servicios corriendo. Si ejecutas la IA con `uvicorn`, arranca antes solo `postgres` y `redis`. |
| No conecta a Redis | En el host es `6380`; dentro de la red de Docker, `6379`. |
| El webhook no llega en local | MercadoPago necesita una URL pública: abre un túnel (`ngrok http 8080`) y da de alta `https://<túnel>/api/payments/webhook` en el panel (Tus integraciones → Webhooks, evento Pagos), con su clave en `MERCADOPAGO_WEBHOOK_SECRET`. Sin túnel, la conciliación confirma el pedido en menos de un minuto consultando MercadoPago. |
| "Simular" del panel responde `503` y el aviso no aparece en ngrok | La URL o la aplicación del panel no son las correctas: tiene que ser la misma aplicación que las credenciales del `.env`. |
| `No se pudo reembolsar el pago … status=401 … Unauthorized use of live credentials` cada 10 min | Límite del sandbox: con credenciales de prueba MercadoPago no permite reembolsos por API. No se guarda nada y se reintenta durante la ventana de 72 h. |
| Los avisos llegan pero responden `403` | La clave de `MERCADOPAGO_WEBHOOK_SECRET` no es la del webhook del panel de esa aplicación, o el backend no se reinició tras cambiarla (se lee al arrancar). |
| `Pedido N: MercadoPago no respondió, se revisará en la siguiente pasada` cada minuto | Credenciales de MercadoPago inválidas o placeholder: los pedidos con checkout no caducan hasta que MercadoPago responda (a propósito, para no cancelar un pedido pagado). |
