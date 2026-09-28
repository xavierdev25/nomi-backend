# Ejecutor del postest (`runner`)

Instrumenta, controla y registra la evaluación controlada de Nomi. **Nunca** ejecuta las acciones
de compra que se miden: eso lo hace una persona en la app. Diseño completo en
[../DISENO_POSTEST.md](../DISENO_POSTEST.md); guía del operador en
[../INSTRUCCIONES_SESION.md](../INSTRUCCIONES_SESION.md).

## Requisitos

- Python 3.10 o posterior. **Sin dependencias de terceros**: PostgreSQL se consulta con `psql`
  dentro del contenedor, y HTTP va con `urllib` y los certificados del sistema.
- Docker (OrbStack), con el contenedor `nomi-redis` del compose del backend creado al menos una
  vez.
- Ollama con el modelo `phi3`, y el entorno virtual de `nomi-ai-service` (`venv/`).
- `nomi-backend/.env` con las credenciales de prueba de MercadoPago.

## Uso

Desde esta carpeta:

```bash
# Entorno y datos
python3 postest.py entorno levantar          # PostgreSQL del postest, IA y backend
python3 postest.py entorno recrear --confirmar  # pg_dump y base vacía (antes de la ejecución oficial)
python3 postest.py seed cargar --catalogo C0 # cuentas (fijas + una por POST), estructura, catálogo y S0
python3 postest.py entorno verificar
python3 postest.py estado comparar S0

# Batería de validación (antes de J1)
python3 postest.py val ejecutar [--incluir-caducidad]
python3 postest.py val cerrar
python3 postest.py val persona VAL-006       # pago rechazado (OTHE) y reintento (APRO) en el mismo checkout
python3 postest.py val persona VAL-008       # pago APRO; el ejecutor cancela antes de la conciliación
python3 postest.py val persona VAL-018       # se registra como NO_VERIFICABLE_EN_SANDBOX
python3 postest.py val visual preparar       # estado de T2 para mirar VAL-019 a VAL-021 en la app
python3 postest.py val registrar VAL-019 --resultado PASA --evidencia "…"
python3 postest.py val visual cerrar
python3 postest.py val final                 # results/val/VAL_FINAL.json y .md

# Jornada
python3 postest.py jornada iniciar J1        # restaura T1 a S0 y comprueba el estado
python3 postest.py asignacion POST-001

# Por participante
python3 postest.py fam preparar PA01         # compra de familiarización con val.postest
python3 postest.py fam cerrar PA01
python3 postest.py ensayo preparar ENS-001 --escenario POST-001 --dispositivo simulador --conocia-nomi si
python3 postest.py ensayo iniciar ENS-001    # ENTER al decir «empieza» (--ahora para no esperar)
python3 postest.py ensayo cerrar ENS-001 [--no-completada] [--resultado …] [--notas "…"]
python3 postest.py ensayo reclasificar ENS-001 --resultado INVALIDA_PROTOCOLO --motivo "…"

python3 postest.py jornada cerrar J1         # pg_dump en results/respaldos/
python3 postest.py exportar --modo ensayo    # results/ensayos/<fecha>/
python3 postest.py entorno detener

# Pruebas del propio ejecutor (sin tocar la base ni results/)
python3 -m unittest discover -s tests
```

`post preparar|iniciar|cerrar|reclasificar POST-0XX` y `exportar --modo oficial` son los mismos
comandos para la muestra O2 y están **bloqueados** (ver «Garantías del entorno»).

### Matriz de análisis

`exportar --modo oficial` escribe, además de `postest_O2.*` (todos los registros, como evidencia),
la matriz para el análisis estadístico `postest_O2_analisis.csv` / `.json`. Solo contiene registros
`EXITOSA` o `FALLIDA_SISTEMA`, uno por POST, y **solo se escribe si supera todas estas
comprobaciones** (si no, queda únicamente `validacion_analisis.json` con los errores):

- 30 filas, POST-001 a POST-030 exactamente una vez, 30 `participant_id` distintos (PA01…PA30);
- ningún `NO_INICIADA`, `INVALIDA_PROTOCOLO`, `INVALIDA_ENTORNO`, ni registros de ensayo,
  familiarización o VAL; sin pedidos ni pagos repetidos;
- todas las filas con el catálogo cargado;
- D1 presente en las compras exitosas y reproducible (`mercadopago_approved_at − user_started_at`),
  y ausente en las no exitosas;
- D2 (`order_success`) en todas las filas y coherente con `execution_result`;
- D3 en todas las filas, medido con la API de la app (con C0 y con C1 se puede medir) y con
  `accessibility_rate = accessible_products / total_products_offered × 100`.

### Cuentas de cada observación

Cada observación usa una cuenta **sin historial**, porque el backend envía a la IA los productos
que la cuenta ya compró:

| Observación | Cuenta | La crea |
|---|---|---|
| POST-0NN, intento 1 | `estNN` | `seed cargar` |
| POST-0NN, intento k ≥ 2 | `estNNik` | `preparar` |
| ENS-0NN | `ens0NN` (`ens0NNik` si se repite) | `preparar` |

Todas tienen el perfil del escenario (`restrictions` y `budget_range` de la asignación).
`preparar` rechaza la cuenta si ya tiene pedidos, favoritos o «no me gusta».

### Qué hace cada paso de una observación

| Comando | Qué hace |
|---|---|
| `preparar` | Comprueba que T1 está en el estado esperado (Sₖ), que la cuenta no tiene pedidos, favoritos ni «no me gusta», que T2 y T3 están despublicados y que el backend usa `nomi_postest`. Si algo falla escribe un registro `NO_INICIADA`. Si no, vacía la caché de la IA, mide el desfase de reloj con MercadoPago y guarda la foto «antes» |
| `iniciar` | Registra `user_started_at`, mide D3 con la cuenta del escenario y captura las recomendaciones que vio el participante: repite la petición de la app dentro de la caché de 300 s y comprueba el «Cache HIT» en el log de la IA |
| `cerrar` | Espera a que el pedido se confirme, se cancele o venza (plazo + 2 min, o `--espera`). Lee el pago en la base y en MercadoPago por `external_reference`, espera hasta 60 s la consulta de la app al pedido confirmado, avanza la logística simulada como comercio, guarda la foto «después» (nuevo estado esperado), comprueba el stock y escribe el registro con las 74 columnas de la plantilla |

Interrumpir `cerrar` mientras espera (Ctrl+C) no cambia nada: la observación sigue abierta y se
puede volver a cerrar. Una observación preparada pero no iniciada se puede volver a preparar; una
que ya tiene registro, no: el intento siguiente lleva `--intento 2`.

`reclasificar` corrige un registro cerrado cuando se comprueba que la causa fue ajena a Nomi:
solo de `FALLIDA_SISTEMA` a `INVALIDA_PROTOCOLO` o `INVALIDA_ENTORNO`, nunca hacia `EXITOSA`, y con
`--motivo` obligatorio. El registro guarda la clasificación original, el motivo y la fecha
(`reclasificacion`), la nota se añade a `notes`, y el cambio aparece en el resumen, en el
manifiesto y en `runtime/reclasificaciones.jsonl`.

## Estado de la implementación

| Comando | Estado |
|---|---|
| `entorno levantar / verificar / detener` | Implementado y probado |
| `entorno recrear --confirmar` | Implementado y probado el 27-09-2026 (lo lanzó el investigador; respaldo previo en `results/respaldos/`) |
| `seed cargar --catalogo C0` | Implementado y probado con una cuenta por POST: 30 cuentas `estNN` con su perfil, más `val` y `val2`. Espera y reintenta si el backend limita los registros por minuto (HTTP 429). C1 cuando exista `seed/catalogo_C1.json` |
| `estado foto / restaurar / comparar` | Implementado y probado |
| `jornada iniciar / cerrar` | Implementado y probado |
| `asignacion` | Implementado |
| `val ejecutar / cerrar` | Implementado y probado, también sobre la base recreada (16 PASA; los pedidos quedan solo en `val`). Pendientes de persona: VAL-006, 008, 018 y la parte visual de 019–021. VAL-004 (espera real de 15 min) solo con `--incluir-caducidad` |
| `ensayo preparar / iniciar / cerrar` | Implementado y probado con C0: ENS-001 (pago del ensayo no hecho a tiempo; reclasificado a `INVALIDA_PROTOCOLO`), ENS-002 y ENS-003 (compras completas, `EXITOSA`). Hechos con `est01`, antes del cambio a una cuenta por observación. Con la base recreada y cuentas propias: ENS-004 (`ens004`, pago no hecho a tiempo; reclasificado a `INVALIDA_PROTOCOLO`) y ENS-005 (`ens005`, perfil vegano de POST-005, compra completa `EXITOSA`; pagado con el saldo de la cuenta de prueba, que el resumen señala) |
| `ensayo reclasificar` | Implementado y probado (ENS-001, y rechazo sin motivo, desde `EXITOSA` y en modo oficial) |
| `fam preparar / cerrar` | Implementado |
| `exportar --modo ensayo` | Implementado y probado (CSV/JSON, manifiesto, resumen y control de completitud) |
| Matriz de análisis (`exportar --modo oficial`) | Implementada; reglas probadas con 21 pruebas unitarias sobre registros ficticios en memoria (`tests/test_analisis.py`) y rechazo comprobado con los registros reales de ensayo. Sin datos oficiales todavía |
| `val persona / visual / registrar / final` | Implementado; ver `results/val/VAL_FINAL.md` |
| `post preparar / iniciar / cerrar`, `exportar --modo oficial` | Implementados con el mismo motor que el ensayo. **Bloqueados** sin `AUTORIZACION_OFICIAL.json` válida |

## Garantías del entorno

- **Base aislada.** El backend se arranca contra `nomi_postest` (contenedor `nomi-postest-pg`,
  puerto 55433, volumen `nomi-postest-data`). La base de desarrollo no se usa.
- **Integración de Docker Compose desactivada** (`SPRING_DOCKER_COMPOSE_ENABLED=false`). Con ella
  activa (perfil `dev`), Spring Boot conecta con la base del compose y esa conexión tiene
  prioridad sobre `SPRING_DATASOURCE_URL`. Se comprobó el 27-09-2026: sin desactivarla, el backend
  se conectaba a la base de desarrollo.
- **Salvaguarda.** Tras arrancar, el ejecutor lee en el log a qué base se conectó el backend. Si no
  es `nomi_postest`, lo detiene y aborta. `entorno verificar` repite la comprobación.
- **Configuración por defecto.** El backend y la IA se arrancan sin ajustes para el experimento;
  lo único que cambia es la base de datos.
- **Secretos.** Las contraseñas de las cuentas sintéticas se generan al cargar el seed y se guardan
  en `../.secrets/cuentas.env` (no versionado, permisos 600). Nunca se imprimen.
- **Bloqueo.** La ejecución oficial exige `../AUTORIZACION_OFICIAL.json`, creado solo por el
  investigador, con la decisión sobre O1 y el hash del catálogo cargado.

## Archivos que genera

| Ruta | Contenido | ¿Versionado? |
|---|---|---|
| `../runtime/entorno.json` | Procesos arrancados y commits de los tres repos | No |
| `../runtime/backend.log`, `../runtime/ai.log` | Logs del backend y de la IA (fuente de varios eventos) | No |
| `../runtime/catalogo_congelado.json` | Versión y hash del catálogo cargado | No |
| `../runtime/estado/*.json` | Fotos del estado de T1 (S0, Sₖ) | No |
| `../.secrets/cuentas.env` | Contraseñas de las cuentas sintéticas | **Nunca** |
| `../runtime/sesiones/*.json` | Estado de cada observación (preparada, iniciada, cerrada) | No |
| `../runtime/registros/{ensayo,oficial}/*.json` | Un registro por observación e intento | No |
| `../runtime/fam.jsonl` | Bitácora de las compras de familiarización | No |
| `../runtime/reclasificaciones.jsonl` | Bitácora de reclasificaciones (registro, desde, a, motivo, fecha) | No |
| `../results/val/` | Resultados de la batería VAL | Sí |
| `../results/ensayos/<fecha>/` | Exportación de los ensayos técnicos | No |
| `../results/<fecha>/` | Exportación oficial (`postest_O2.*`, manifiesto, resumen) | Sí |
| `../results/<fecha>/postest_O2_analisis.{csv,json}` | Matriz de análisis: solo si supera la validación | Sí |
| `../results/<fecha>/validacion_analisis.json` | Resultado de la validación de la matriz (siempre) | Sí |
| `../results/val/val_persona.jsonl`, `VAL_FINAL.{json,md}`, `evidencia/` | VAL con persona, consolidado final y capturas | Sí |
| `../results/respaldos/` | `pg_dump` de la base al cerrar cada jornada y antes de recrearla; se archiva aparte como evidencia | No |
