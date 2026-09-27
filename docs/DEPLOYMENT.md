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
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME` | No | `localhost`, `5432`, `foodv_db`, `foodv_user` | Conexión a PostgreSQL |
| `DB_PASSWORD` | Sí | — | Contraseña de PostgreSQL |
| `DB_POOL_SIZE` | No | `10` | Máximo de conexiones Hikari |
| `REDIS_HOST`, `REDIS_PORT` | No | `localhost`, `6379` (`6380` en `dev`) | Redis |
| `JWT_SECRET` | Sí | — | Secreto HMAC, **mínimo 32 bytes** |
| `JWT_EXPIRATION` | No | `86400000` (24 h) | Vida del access token, en ms |
| `JWT_REFRESH_EXPIRATION` | No | `604800000` (7 días) | Vida del refresh token, en ms |
| `JWT_ISSUER` | No | `foodv-backend` | Emisor del JWT |
| `MERCADOPAGO_ACCESS_TOKEN`, `MERCADOPAGO_PUBLIC_KEY` | Sí | — | Credenciales de MercadoPago (`TEST-…` fuera de producción) |
| `MERCADOPAGO_WEBHOOK_SECRET` | Sí | — | Secreto de la firma del webhook; sin él se rechazan todos los webhooks |
| `MERCADOPAGO_NOTIFICATION_URL` | No | `http://localhost:8080/api/payments/webhook` | URL pública del webhook |
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

`docker-compose.yml` define `postgres` (puerto `DB_PORT`), `redis` (`6380` en el host) y un
`ai-service` cuya ruta de build (`../foodv-ai-service`) no existe en este workspace. pgAdmin está
en `docker-compose.override.yml.example`.

## 3. Imagen Docker

```bash
docker build -t foodv-backend .
docker run --env-file .env -e SPRING_PROFILES_ACTIVE=prod -p 8080:8080 foodv-backend
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
- [ ] Riesgos C1 y A1–A5 de la auditoría técnica resueltos o aceptados.

## 6. Problemas frecuentes

| Síntoma | Causa |
|---|---|
| `JWT_SECRET debe tener al menos 32 bytes… Actual: 13` | La variable no se resolvió: falta en el entorno y en `.env` (13 es la longitud de `${JWT_SECRET}`). |
| El arranque intenta construir `ai-service` | Spring Boot levanta el compose al no encontrar servicios: arranca antes `postgres` y `redis`. |
| No conecta a Redis | En el host es `6380`; dentro de la red de Docker, `6379`. |
| El webhook no llega en local | MercadoPago necesita una URL pública: usa un túnel (ngrok o similar) en `MERCADOPAGO_NOTIFICATION_URL`. |
