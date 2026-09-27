# AGENTS.md — `nomi-backend`

Instrucciones para agentes de IA (y personas) que trabajen en este repositorio.

## Qué es

API REST en Spring Boot 4 / Java 21 con arquitectura hexagonal. Es la autoridad de FoodV sobre
usuarios, sesiones, pedidos, stock, dinero y permisos. La consumen la app iOS (`nomi-ios`) y, en
sentido inverso, llama al servicio de IA (`nomi-ai-service`) y a MercadoPago.

## Antes de cambiar algo, lee

| Si vas a… | Lee primero |
|---|---|
| Añadir o cambiar un caso de uso | [docs/PRD.md](docs/PRD.md), [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) |
| Cambiar un endpoint, DTO o error | [docs/API.md](docs/API.md) (y avisa: la app iOS depende del contrato) |
| Cambiar el esquema | [docs/DATABASE.md](docs/DATABASE.md) |
| Tocar autenticación, permisos, pagos o datos personales | [docs/SECURITY.md](docs/SECURITY.md) |
| Cambiar configuración o variables de entorno | [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) |
| Escribir cualquier código | [docs/CODE_STYLE.md](docs/CODE_STYLE.md) |
| Añadir o cambiar tests | [docs/TESTING.md](docs/TESTING.md) |

Si la documentación contradice el código, señálalo antes de hacer un cambio grande.

## Comandos

```bash
docker compose up -d postgres redis   # dependencias locales
./mvnw spring-boot:run                # arrancar (carga .env automáticamente)
./mvnw test                           # tests
./mvnw verify                         # tests + cobertura (JaCoCo), lo que corre la CI
```

## Reglas

- **Respeta las capas.** `domain/` no importa Spring, JPA ni nada de `infrastructure/`. Los
  controladores no contienen lógica de negocio: delegan en un handler de `application/`.
- **El esquema solo cambia con una migración Flyway nueva** (`V23__…sql`). Nunca edites una
  migración ya aplicada.
- **Dinero con `BigDecimal`** y los importes siempre se calculan en el servidor.
- **Todo endpoint nuevo** lleva DTOs con `@Schema`, validación con Bean Validation, comprobación de
  ownership o rol y aparece correctamente en Swagger.
- **Cambios de contrato** (rutas, campos, códigos de estado): actualiza `docs/API.md` y avisa de
  su impacto en `nomi-ios`.
- **Todo cambio de lógica lleva test.** Corre la suite antes de terminar.
- **Documenta en español** y explica el porqué (ver CODE_STYLE.md).

## Nunca

- Escribir secretos reales en código, tests, documentación o `application*.yaml`: van en `.env` o
  en variables de entorno.
- Desactivar la verificación de firma del webhook, el filtro JWT o los checks de ownership para
  "hacer funcionar" algo.
- Registrar tokens, contraseñas o datos de pago en los logs.
- Cambiar el comportamiento del webhook de pagos sin tests por cada estado de MercadoPago.
- Hacer commit o push sin que te lo pidan.
