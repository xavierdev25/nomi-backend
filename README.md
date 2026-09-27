# Nomi — Backend (`nomi-backend`)

API REST de **Nomi**, la plataforma de pedidos de comida dentro del campus (Universidad César
Vallejo, sede Lima Norte): los estudiantes piden a las tiendas de la universidad y reciben el
pedido en su aula. Este servicio es la autoridad del sistema: usuarios y sesiones, catálogo,
pedidos y stock, pagos con MercadoPago y el puente hacia el servicio de recomendaciones
(`nomi-ai-service`).

## Stack

| | |
|---|---|
| Lenguaje | Java 21 |
| Framework | Spring Boot 4.0.8 (Web MVC, Security 7, Data JPA, Validation, WebSocket, Actuator) |
| Arquitectura | Hexagonal (puertos y adaptadores) |
| Base de datos | PostgreSQL 16, migraciones con Flyway (V1–V22) |
| Caché y límites | Redis 7 (caché, rate limiting, blacklist de tokens, intentos de login) |
| Autenticación | JWT (JJWT 0.12.6), access + refresh token con rotación |
| Pagos | MercadoPago Checkout Pro con webhook firmado |
| Otros | Cloudinary (imágenes), Firebase Admin (push, opcional), Resilience4j (circuit breaker de IA), MapStruct, Lombok |
| Documentación API | springdoc-openapi 2.8.8 (Swagger UI) |
| Observabilidad | Actuator + Micrometer/Prometheus |

## Requisitos

- Java 21 (el Maven Wrapper `./mvnw` viene incluido).
- Docker, para PostgreSQL y Redis.
- Opcional: `nomi-ai-service` corriendo en `http://localhost:8001` para recomendaciones reales.
  Sin él, las recomendaciones devuelven una lista vacía (`FALLBACK`).

## Empezar

```bash
cp .env.example .env              # y rellenar los valores
docker compose up -d postgres redis
./mvnw spring-boot:run
```

- El `.env` de la raíz se carga solo al arrancar (`DotenvEnvironmentPostProcessor`). Las variables
  de entorno reales tienen prioridad sobre él.
- `docker compose up -d postgres redis` levanta solo las dependencias; úsalo si ejecutas el
  servicio de IA desde su repositorio (`uvicorn`, puerto 8001). `docker compose up -d` además
  construye y arranca `ai-service` desde `../nomi-ai-service` (el repositorio vecino).
- Con el perfil `dev`, Spring Boot también intenta levantar `docker-compose.yml` si no encuentra
  servicios corriendo; por eso conviene levantarlos antes a mano.
- PostgreSQL se publica en el puerto `DB_PORT` del `.env` y Redis en el `6380` del host.
- Flyway aplica las migraciones pendientes al arrancar.

| URL | |
|---|---|
| `http://localhost:8080/api` | API (context path `/api`) |
| `http://localhost:8080/api/swagger-ui.html` | Swagger UI (fuera de `prod`) |
| `http://localhost:8080/api/api-docs` | OpenAPI en JSON |
| `http://localhost:8080/api/actuator/health` | Estado del servicio |

## Tests

```bash
./mvnw test
```

68 tests: unitarios, de controlador con MockMvc y de integración. Los de integración (perfil
`test`) necesitan PostgreSQL en `localhost:5432` (base `nomi_db`, usuario `nomi_user`) y Redis
en `6380`. Detalle en [docs/TESTING.md](docs/TESTING.md).

## Estructura

```text
src/main/java/com/nomi/backend/
├── domain/            Núcleo sin Spring: modelos, puertos (in/out), servicios de dominio, excepciones
├── application/       Casos de uso: un handler por operación, agrupados por módulo
└── infrastructure/    Adaptadores
    ├── web/           Controladores REST, DTOs, mappers y manejo global de errores
    ├── persistence/   Entidades JPA, repositorios Spring Data y adaptadores de los puertos
    ├── security/      Filtro JWT, rate limiting, cabeceras, ownership, blacklist
    ├── config/        Seguridad, CORS, OpenAPI, Redis, WebSocket, Firebase, carga del .env
    ├── payment/       Adaptador de MercadoPago
    ├── ai/            Cliente de nomi-ai-service
    ├── notification/  WebSocket (STOMP) y Firebase Cloud Messaging
    ├── storage/       Cloudinary
    ├── scheduler/     Tareas programadas
    └── metrics/       Métricas de negocio
src/main/resources/db/migration/   Migraciones Flyway
bruno/                             Colección de peticiones (Bruno)
```

## Documentación

| Documento | Contenido |
|---|---|
| [docs/PRD.md](docs/PRD.md) | Qué debe hacer el backend: módulos, reglas y alcance |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Capas, flujo de una petición y decisiones |
| [docs/API.md](docs/API.md) | Convenciones de la API, errores y endpoints por módulo |
| [docs/DATABASE.md](docs/DATABASE.md) | Esquema, migraciones y reglas de datos |
| [docs/SECURITY.md](docs/SECURITY.md) | Autenticación, autorización, límites, pagos y secretos |
| [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) | Variables de entorno, perfiles, Docker y CI |
| [docs/CODE_STYLE.md](docs/CODE_STYLE.md) | Convenciones de código y de documentación |
| [docs/TESTING.md](docs/TESTING.md) | Cómo se prueba y qué está cubierto |
| [CHANGELOG.md](CHANGELOG.md) | Cambios relevantes |
| [AGENTS.md](AGENTS.md) | Instrucciones para agentes de IA |

La documentación general del sistema (producto, reglas de negocio, diagramas y auditoría
técnica) vive en el sitio Starlight de `nomi-docs`.

## Proyectos relacionados

- `nomi-ios` — app iOS del estudiante (SwiftUI).
- `nomi-ai-service` — servicio de recomendaciones (FastAPI).
- `nomi-docs` — documentación general (Starlight).
