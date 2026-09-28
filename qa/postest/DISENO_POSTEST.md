# Diseño del postest (O2): evaluación controlada del prototipo Nomi mediante escenarios de compra

| | |
|---|---|
| **Estado** | Versión 0.4, aprobada como base. **Ejecución oficial bloqueada** hasta decidir sobre O1 (§18). Ningún POST oficial ejecutado |
| **Fecha** | 27-09-2026 |
| **Naturaleza de O2** | Evaluación controlada del prototipo Nomi mediante escenarios de compra. **No** son datos de operación real del quiosco del ISTPC |
| **Diseño de investigación** | Preexperimental de un solo grupo con pretest y postest (G O1 X O2) |
| **Unidad de análisis** | Registro del proceso de compra |
| **Tamaño de la muestra O2** | 30 registros (POST-001 a POST-030), muestreo no probabilístico por conveniencia |
| **Participantes** | 30 personas × 1 observación (PA01 a PA30) |
| **Datos O1 (pretest)** | `PENDIENTE_DE_VERIFICAR` ([DEPENDENCIAS_O1.md](DEPENDENCIAS_O1.md)) |
| **Catálogo y D3 inicial** | `PENDIENTE_DE_CALIBRACION_O1` (§6) |
| **Lugar de la investigación** | Instituto Superior Tecnológico Público Chancay (ISTPC) |

> Los usuarios, productos, precios, stock y pedidos de O2 son **datos sintéticos de prueba**.
> Emulan las condiciones de un quiosco del ISTPC, pero no son información real de la institución,
> no representan transacciones reales del ISTPC y no reproducen el catálogo del pretest.

### Documentos

| Documento | Contenido |
|---|---|
| `DISENO_POSTEST.md` | Este documento |
| [seed/DISENO_SEED.md](seed/DISENO_SEED.md) | Diseño del seed: estructura definitiva, catálogo provisional C0 y calibración con O1 |
| [PROTOCOLO_PARTICIPANTES.md](PROTOCOLO_PARTICIPANTES.md) | Lo que se explica y se pide a cada participante |
| [CONSENTIMIENTO_INFORMADO.md](CONSENTIMIENTO_INFORMADO.md) | Consentimiento informado |
| [scenarios/asignacion_POST.csv](scenarios/asignacion_POST.csv) | Asignación PA01–PA30 → POST-001–POST-030, con franja, consigna, cuenta, aula, punto y tarjeta |
| [INSTRUCCIONES_SESION.md](INSTRUCCIONES_SESION.md) | Guía del operador |
| [plantillas/postest_O2_plantilla.csv](plantillas/postest_O2_plantilla.csv) | Estructura final del CSV (solo cabecera) |
| [DEPENDENCIAS_O1.md](DEPENDENCIAS_O1.md) | Todo lo que depende de recuperar O1 |

### Cambios respecto a la versión 0.3

| Punto | Cambio |
|---|---|
| Participantes | **30 participantes × 1 observación** (antes 10 × 3). Cada registro procede de una persona distinta: desaparecen la dependencia intrapersonal y el aprendizaje entre POST |
| Asignación | PA01–PA10 en J1, PA11–PA20 en J2 y PA21–PA30 en J3. No se congela definitivamente mientras O1 pueda exigir un emparejamiento por persona |
| Franjas | Ventanas de la evaluación controlada: J1 09:00–10:30, J2 12:00–14:00, J3 16:00–17:30. Si O1 trae sus franjas, se intenta reproducirlas |
| iPhone físico | **Opción A aprobada e implementada** en `nomi-ios` (§5.1) |
| Catálogo | Sigue `PENDIENTE_DE_CALIBRACION_O1`. C0 solo para desarrollo del ejecutor, pruebas del entorno, familiarización, VAL y verificación técnica. **Nunca para los 30 registros oficiales** sin una decisión expresa |
| Ejecución | El comando de ejecución oficial del ejecutor queda **bloqueado** (§18) |

---

## 1. Propósito y alcance

Obtener los 30 registros del postest (O2) mediante una **evaluación controlada del prototipo Nomi
con escenarios de compra**, ejecutando su flujo de compra real.

- **Humano = ejecuta el proceso de compra** con la app `nomi-ios`.
- **Script = instrumenta, controla y registra**: prepara los datos, verifica el estado, captura
  eventos de PostgreSQL, del backend y de MercadoPago, y genera el CSV/JSON.
- El script **no** realiza las acciones de compra que se miden.
- Las pruebas negativas y funcionales (`VAL-XXX`) van en una batería aparte y **no forman parte
  de la muestra estadística**.
- No se generan, estiman ni emparejan datos de O1.

Variables de la tesis:

| Variable | Dimensión | Indicador | Unidad |
|---|---|---|---|
| Dependiente: gestión de compras | D1. Tiempo del proceso de compra | Tiempo del proceso de compra | minutos |
| | D2. Efectividad del proceso de compra | Tasa de pedidos completados exitosamente | % |
| | D3. Accesibilidad operativa a productos alimenticios | Tasa de productos accesibles para consulta por el usuario | % |
| Independiente: sistema de información inteligente | — | Nomi (app iOS + backend + servicio de IA) | — |

---

## 2. Disponibilidad de O1

**Estado: `PENDIENTE_DE_VERIFICAR`.** La tesis de IX ciclo establece metodológicamente una
muestra de 30 registros y contiene los instrumentos de pretest y postest. Hay que localizar la
**matriz numérica real** de O1 antes de ejecutar la muestra oficial y antes del análisis.
Mientras tanto:

- no se afirma que existan 30 valores observados de O1;
- no se generan ni se estiman datos de O1;
- no se empareja POST-001 con R001, POST-002 con R002, etc. por su número de fila;
- **O1 bloquea la ejecución oficial** (catálogo, franjas y posible emparejamiento por persona).

Los campos que hay que recuperar y lo que depende de ellos están en
[DEPENDENCIAS_O1.md](DEPENDENCIAS_O1.md).

---

## 3. Operacionalización adoptada para el postest

### 3.1 D1. Tiempo del proceso de compra (aprobado)

- **INICIO:** `user_started_at`, el momento en que el operador indica "empieza" y el participante
  comienza a buscar o explorar productos.
- **FIN PRINCIPAL:** `mercadopago_approved_at`, el momento en que MercadoPago aprueba el pago y
  muestra al participante la confirmación de la compra ("¡Listo! Tu pago ya se acreditó").
  Justificación técnica en §4.1.
- **Tiempo del proceso = FIN − INICIO**, en minutos.
- Métricas secundarias: la creación del pedido, el inicio del pago, la confirmación del backend
  (`PREPARANDO`), la confirmación vista en la app y la logística simulada (§11). El retraso de la
  conciliación del sandbox (hasta 60 s) **no** entra en el indicador principal.

### 3.2 D2. Efectividad

- **Tasa = (pedidos completados exitosamente / total de pedidos) × 100.**
- **Completado exitosamente** (`order_success = 1`): el pago fue aprobado en MercadoPago **y** el
  pedido quedó confirmado en el sistema (`PREPARANDO`) dentro de la ventana de observación.
- Las 30 observaciones son **intentos reales de completar una compra**. No incluyen
  cancelaciones planificadas ni fallos diseñados.
- Una observación que falla de forma natural (error del sistema, del pago, del stock, de una
  integración o de una validación) se registra como **no exitosa** y no se elimina ni se repite
  en silencio.
- El **éxito logístico** (hasta `ENTREGADO`) se registra aparte y no se mezcla con el indicador.

### 3.3 D3. Accesibilidad para consulta (definiciones aprobadas; valor pendiente de calibración)

El instrumento aprobado mide la **tasa de productos accesibles para consulta por el usuario**:
productos accesibles para consulta / total de productos ofertados × 100.

| Concepto | Definición en Nomi (canal digital) |
|---|---|
| **Ofertado** | Producto del catálogo del establecimiento, no eliminado y **publicado** (`activo = true`). Un producto no publicado no aparece en ninguna pantalla de la app |
| **Consultable** (`visible`) | Producto ofertado que el estudiante puede encontrar y consultar desde el catálogo de la app. En Nomi, Inicio, la tienda y la búsqueda solo muestran productos con `disponible = true` (§4.2) |
| **Comprable** | Producto consultable que además puede comprarse ahora: stock ≥ 1 y tienda activa |

- **D3 = consultables / ofertados × 100.** Se mide con la misma consulta que usa la pantalla de
  la tienda, no con una regla de stock.
- En Nomi, un producto que llega a stock 0 pasa automáticamente a `disponible = false` y deja de
  mostrarse. Es un comportamiento del sistema (§4.2), que VAL-019 a VAL-021 verificarán en la app
  antes de la muestra.
- **El valor de D3 depende de la composición del catálogo**, así que queda
  `PENDIENTE_DE_CALIBRACION_O1` (§6).
- Las restricciones alimentarias **no** entran en esta fórmula. Se registran como resultados
  complementarios.

---

## 4. Comportamiento verificado de Nomi relevante para la medición

Verificado por inspección del código de `nomi-ios` y `nomi-backend` (27-09-2026). Los puntos de
§4.2 se confirmarán además en la app con VAL-019 a VAL-021.

### 4.1 Qué ocurre después de pagar

| Paso | Comportamiento | Evidencia |
|---|---|---|
| 1 | Al confirmar el checkout, la app crea el pedido (`POST /orders`) y el checkout (`POST /payments`) | `CheckoutViewModel` |
| 2 | Si el checkout se crea, la app vacía el carrito, abre la URL de MercadoPago en el navegador y navega a la pantalla de seguimiento del pedido | `CheckoutViewModel`, `CheckoutView` |
| 3 | El participante paga en MercadoPago. Al aprobarse, **MercadoPago muestra su confirmación** ("¡Listo! Tu pago ya se acreditó") | Observado en las pruebas del sandbox |
| 4 | MercadoPago **no devuelve al participante a la app**: no hay `auto_return` ni enlace de retorno propio (auditoría M8) | `MercadoPagoAdapter.preferenceFor` |
| 5 | En la app, el seguimiento muestra "Pago pendiente" hasta que el backend confirma el pago. Consulta cada 5 s y solo entonces muestra "Pago aprobado · esperando a la tienda" | `OrderTrackingView`, `OrderTrackingViewModel` |
| 6 | En el sandbox, el backend confirma por conciliación, cada 60 s, porque el webhook no se dispara | Pruebas del 27-09-2026 |

La confirmación en la app llega después del retraso artificial del sandbox (0–60 s), la espera
de la consulta (0–5 s) y el regreso manual del participante a la app. El primer momento en que el
participante ve que la compra se completó es la confirmación de MercadoPago, que coincide con
`date_approved`. Por eso es el FIN principal.

### 4.2 Qué productos puede consultar el estudiante

| Pantalla de la app | Consulta al backend | Qué muestra |
|---|---|---|
| Inicio ("Disponible ahora") | `GET /products/search?disponible=true&size=20` | Publicados y disponibles, como mucho 20 |
| Tienda | `GET /products/search?storeId=…&disponible=true&size=50` | Publicados y disponibles de la tienda, como mucho 50 |
| Búsqueda | `GET /products/search?…&disponible=true&size=30` | Publicados y disponibles, como mucho 30 |
| Ficha del producto | `GET /products/{id}` | Cualquier producto no eliminado, con la etiqueta "Agotado" y "Agregar al carrito" desactivado si `disponible = false`. Solo se llega desde las listas anteriores, recomendaciones o favoritos |
| Recomendaciones | `GET /ai/recommendations` | Solo candidatos comprables y compatibles con las restricciones |

El backend pone `disponible = false` automáticamente cuando el stock llega a 0 y lo vuelve a
poner en `true` cuando el stock se repone. La búsqueda **no** filtra por tienda activa, así que
los productos publicados de cualquier tienda aparecen en Inicio y en la búsqueda (§13).

| Estado del producto | ¿Ofertado? | ¿Consultable? | ¿Comprable? |
|---|---|---|---|
| Publicado, disponible, stock ≥ 1 | Sí | Sí | Sí |
| Publicado, stock 0 (agotado) | Sí | **No**: desaparece de Inicio, tienda y búsqueda. Solo se ve desde favoritos, y en el postest los usuarios no tienen favoritos | No |
| Publicado, `disponible = false` con stock (pausado por la tienda) | Sí | **No**, por la misma razón | No |
| No publicado (`activo = false`) | **No** | No | No |

---

## 5. Entorno controlado

| Pieza | Configuración |
|---|---|
| Base de datos | PostgreSQL 16 **exclusiva del postest** (`nomi_postest`, contenedor Docker propio). Se crea con las migraciones V1–V25 de Nomi más el seed sintético. La base de desarrollo no se usa |
| Backend | `nomi-backend` sin cambios de lógica (commit registrado en el manifiesto), apuntando a `nomi_postest`. **Configuración por defecto**: plazo de pago 15 min, máximo 2 pedidos sin pagar, conciliación cada 60 s. Se arranca con la integración de Docker Compose desactivada, porque con ella activa el backend ignora `SPRING_DATASOURCE_URL` y se conecta a la base de desarrollo. El ejecutor comprueba en el log a qué base se conectó y aborta si no es `nomi_postest` |
| Servicio de IA | `nomi-ai-service` con Ollama y el modelo `phi3`, configuración por defecto (caché de 300 s) |
| App | `nomi-ios` en Debug en un **iPhone físico** (§5.1). El **simulador** solo se usa si el dispositivo físico presenta un impedimento técnico documentado |
| Pagos | MercadoPago **Sandbox**: credenciales de prueba de la aplicación y cuenta compradora de prueba con sesión iniciada en el navegador del iPhone. Sin dinero real |
| Webhook | No se usa: en el sandbox no se dispara con pagos reales. La confirmación del backend llega por la conciliación |
| Código productivo | **Sin cambios de lógica.** El único cambio es de configuración en `nomi-ios` (§5.1). Todo lo del postest (seed, escenarios, ejecutor, resultados) va en `nomi-backend/qa/postest/` |

**Datos heredados.** La migración V14 inserta una universidad y un campus de otro contexto
(UCV Lima Norte) que el flujo de compra no usa. En la base del postest se eliminan esas filas
para que ninguna exportación las contenga. No se modifica ninguna migración.

### 5.1 iPhone físico: opción A (aprobada e implementada)

| Cambio en `nomi-ios` | Detalle |
|---|---|
| URL del backend configurable por compilación | Variable `NOMI_API_BASE_URL` en `Config/Debug.xcconfig` (`http://localhost:8080/api` por defecto) y `Config/Release.xcconfig` (el marcador de siempre). El `Info.plist` la expone como `NomiAPIBaseURL`, y `NetworkConfig` la lee. Si falta o no es una URL http/https con host, usa la de siempre |
| Build del postest | `Config/Local.xcconfig` (no versionado): `NOMI_API_BASE_URL = http://<nombre-del-Mac>.local:8080/api`. Aquí también puede ir el equipo de firma (`DEVELOPMENT_TEAM`) |
| Permiso de red local | `NSLocalNetworkUsageDescription` en el `Info.plist` |
| Test | 6 tests nuevos (`NetworkConfig — URL del backend`); la suite de iOS pasa completa (41 tests) |
| Lógica funcional | **Sin cambios** |

Verificado: sin `Local.xcconfig`, la app compilada apunta a `http://localhost:8080/api`; con
`Local.xcconfig`, a `http://<nombre-del-Mac>.local:8080/api`. La app compila para iPhone físico
sin warnings.

Requisitos restantes, en la sesión de preparación:

| Requisito | Cómo |
|---|---|
| Firma | Cuenta personal de Apple, desde Xcode o con `DEVELOPMENT_TEAM` en `Local.xcconfig`. Los perfiles gratuitos duran 7 días: si las sesiones se alargan, hay que reinstalar la app |
| iPhone | iOS 17 o posterior, modo desarrollador activado, misma Wi-Fi que el Mac; aceptar el permiso de red local la primera vez |
| MercadoPago | iPhone **sin la app de MercadoPago** (o con la sesión cerrada), para que el checkout se abra en Safari con la compradora de prueba y nunca con una cuenta real |
| Impedimento técnico | Si el iPhone físico no funciona, se documenta el motivo en el manifiesto y se usa el simulador; `device_type` lo registra en cada fila |

---

## 6. Datos sintéticos y calibración del catálogo

El diseño completo está en [seed/DISENO_SEED.md](seed/DISENO_SEED.md). Resumen:

- **Estructura definitiva:** el quiosco T1 (único de los POST), las tiendas T2 y T3 (solo VAL), el
  dueño de T1, 6 aulas y una cuenta sintética de estudiante por POST (`est01` … `est30`, con el
  perfil de su escenario), sin pedidos, favoritos ni valoraciones de "no me gusta".
- **Catálogo de T1: `PENDIENTE_DE_CALIBRACION_O1`.** Se calibrará con O1 (C0 → C1) antes de la
  ejecución oficial. Los productos no disponibles se eligen **por sorteo con semilla publicada**.
  C1 se congela con hash antes de ejecutar y no se modifica después para producir una mejora
  estadística.
- **Catálogo provisional C0** (35 productos publicados y disponibles, stock uniforme de 25). Solo
  se usa para:
  - desarrollar y probar el ejecutor;
  - probar el entorno, el dispositivo y MercadoPago Sandbox;
  - la compra de familiarización;
  - la batería VAL y la verificación técnica.
- **C0 no se usa para los 30 registros oficiales.** Con C0, D3 vale 100 % por construcción y no
  tiene utilidad inferencial. Solo si resultara imposible recuperar información suficiente de O1,
  se decidirá **expresamente** si ejecutar con C0 y tratar D3 como resultado descriptivo de un
  entorno sintético.

El catálogo también influye en D1: el número de productos cambia el tiempo de exploración.

---

## 7. Participantes

- **30 participantes × 1 observación oficial** (PA01 a PA30). Cada registro procede de una persona
  distinta.

| Jornada | Participantes |
|---|---|
| J1 | PA01 a PA10 |
| J2 | PA11 a PA20 |
| J3 | PA21 a PA30 |

- Participantes **ajenos al desarrollo de Nomi**. Si alguien ya conoce la app, se registra en
  `participant_prior_nomi_experience`.
- Cada participante hace, en este orden:
  1. el consentimiento informado;
  2. una **compra de familiarización**, que no entra en la muestra;
  3. **un solo POST oficial**.
- `participant_id` anónimo, independiente de `test_user_id`. En los resultados no hay nombres ni
  datos personales. Los consentimientos firmados se guardan separados del dataset.
- La asignación está en [scenarios/asignacion_POST.csv](scenarios/asignacion_POST.csv) y se define
  antes de ejecutar. **No se congela definitivamente** mientras O1 pueda demostrar un
  emparejamiento real por persona (§15). En ese caso habría que usar las mismas personas del
  pretest.
- Protocolo: [PROTOCOLO_PARTICIPANTES.md](PROTOCOLO_PARTICIPANTES.md). Consentimiento:
  [CONSENTIMIENTO_INFORMADO.md](CONSENTIMIENTO_INFORMADO.md).

Con 30 × 1 no hay observaciones repetidas de una misma persona: se eliminan la dependencia
intrapersonal y el aprendizaje entre POST. Persiste la variabilidad entre personas, que es propia
del muestreo.

---

## 8. Roles y protocolo de ejecución

| Rol | Responsabilidad |
|---|---|
| Participante | Persona que hace la compra en la app con la cuenta sintética del escenario |
| Operador QA | Prepara el dispositivo, lee la consigna, marca el inicio y supervisa el protocolo |
| Ejecutor | Script que prepara el estado, verifica precondiciones, captura eventos y genera los resultados |

### 8.1 Antes de la sesión (no se registra)

1. Levantar el entorno (§5) y cargar el seed.
2. Iniciar la sesión de la compradora de prueba de MercadoPago en el navegador del iPhone.
3. Ejecutar la batería VAL, incluidas VAL-019 a VAL-021 (visibilidad del catálogo).

### 8.2 Por cada participante

| # | Quién | Acción |
|---|---|---|
| 1 | Operador | Explica el estudio y recoge el consentimiento |
| 2 | Participante | Compra de familiarización con la cuenta `val.postest`. El ejecutor guarda el estado antes y lo restaura después (§13) |
| 3 | Ejecutor | Verifica las precondiciones del POST: estado igual al esperado (§13), cuenta sin pedidos pendientes ni "no me gusta", tienda activa, y la IA y MercadoPago responden. Mide el desfase de reloj con MercadoPago, vacía la caché de la IA y guarda la foto del estado "antes" |
| 4 | Operador | Inicia sesión en la app con la cuenta del escenario y deja la app en Inicio con el carrito vacío. Este paso **no** se cronometra, y el participante no ve las credenciales |
| 5 | Operador | Entrega el iPhone, lee la consigna y pulsa ENTER al decir "empieza" → `user_started_at` |
| 6 | Participante | Explora, arma el carrito, elige aula y punto de encuentro, confirma y paga en MercadoPago Sandbox con la tarjeta de prueba indicada (titular `APRO`) |
| 7 | Ejecutor | Detecta pedido, checkout y pago. **FIN principal** = aprobación en MercadoPago. Sigue observando hasta la confirmación del backend (`PREPARANDO`), métrica secundaria. La observación termina al confirmarse el pedido, al vencer el plazo de pago (15 min) o si el participante declara que no puede completar la compra |
| 8 | Ejecutor | Captura la respuesta de la IA que vio el participante (§11.4) |
| 9 | Ejecutor | Como comercio, avanza el pedido a `LISTO_PARA_RECOGER` → `EN_CAMINO` → `ENTREGADO`. Es **logística simulada**, solo para métricas secundarias |
| 10 | Ejecutor | Guarda la foto "después", comprueba que stock final = stock inicial − cantidades y escribe el registro |

Las consignas describen **objetivos de compra**, no productos exactos, para conservar el tiempo
real de exploración y decisión. Lo que compra el participante se registra tal cual. El
participante no conoce la lista de escenarios de antemano.

---

## 9. Muestra oficial POST-001 a POST-030

Hay **3 jornadas de 10 registros**. Cada jornada empieza con el inventario inicial y se ejecuta en
su ventana horaria, porque la IA usa la hora del día:

| Jornada | Franja | Ventana de la evaluación controlada | Registros | Participantes |
|---|---|---|---|---|
| J1 | Desayuno | 09:00 a 10:30 | POST-001 a POST-010 | PA01 a PA10 |
| J2 | Almuerzo | 12:00 a 14:00 | POST-011 a POST-020 | PA11 a PA20 |
| J3 | Tarde | 16:00 a 17:30 | POST-021 a POST-030 | PA21 a PA30 |

- **Prioridad:** si se recuperan las fechas u horas de O1, se intentará reproducir franjas
  equivalentes, y esta tabla se actualizará antes de ejecutar.
- Estas ventanas son **franjas definidas para la evaluación controlada**. No son los horarios
  oficiales de receso del ISTPC.

Criterios de las consignas:

- Describen **necesidades de compra** (tipo de producto, cantidad, presupuesto o restricción), no
  productos concretos.
- **No** nombran productos de stock bajo ni están pensadas para agotar ninguno. Si el inventario
  cambia como consecuencia natural de las compras, se registra.
- Las cantidades (1 a 3 unidades) buscan variedad de pedidos, no una distribución de stock.

Cada POST tiene su propia cuenta (`estNN` para POST-0NN), creada sin historial (§13). El perfil
de la cuenta es el que ya tenía el escenario: restricción alimentaria y rango de presupuesto.

| ID | Participante | Cuenta | Perfil | Consigna | Elegir con IA | Aula | Punto de encuentro |
|---|---|---|---|---|---|---|---|
| POST-001 | PA01 | est01 | — · medio | Desayuno: 1 bebida caliente + 1 pan o sándwich | No | A-101 | Puerta |
| POST-002 | PA02 | est02 | Vegetariano · medio | 1 producto recomendado por Nomi | **Sí** | B-201 | Pasillo |
| POST-003 | PA03 | est03 | Sin gluten · medio | 2 unidades de una bebida apta para ti | No | LAB-1 | Entrada |
| POST-004 | PA04 | est04 | — · bajo | 1 snack + 1 bebida, total hasta S/ 5.00 | No | A-102 | Escaleras |
| POST-005 | PA05 | est05 | Vegano · medio | Desayuno vegano: 1 bebida + 1 comida | No | TAL-1 | Recepción |
| POST-006 | PA06 | est06 | — · alto | Desayuno para dos: 2 productos recomendados por Nomi, 2 unidades de cada uno | **Sí** | B-202 | Puerta |
| POST-007 | PA07 | est07 | Sin lactosa · medio | 1 comida + 1 bebida sin lactosa | No | A-101 | Pasillo |
| POST-008 | PA08 | est08 | Vegetariano + sin gluten · medio | 1 producto recomendado por Nomi | **Sí** | B-201 | Entrada |
| POST-009 | PA09 | est09 | — · medio | 2 bebidas distintas + 1 postre | No | LAB-1 | Escaleras |
| POST-010 | PA10 | est10 | — · bajo | 1 comida de hasta S/ 5.00 | No | A-102 | Recepción |
| POST-011 | PA11 | est11 | — · medio | 1 plato de fondo + 1 bebida | No | B-202 | Pasillo |
| POST-012 | PA12 | est12 | Vegetariano · medio | Almuerzo vegetariano: 1 plato recomendado por Nomi + 1 bebida | **Sí** | A-101 | Entrada |
| POST-013 | PA13 | est13 | — · alto | 1 plato de fondo + 1 gaseosa u otra bebida | No | B-201 | Escaleras |
| POST-014 | PA14 | est14 | Sin gluten · medio | 1 plato sin gluten recomendado por Nomi | **Sí** | LAB-1 | Recepción |
| POST-015 | PA15 | est15 | — · bajo | 2 unidades de un plato de hasta S/ 6.00 cada uno | No | TAL-1 | Puerta |
| POST-016 | PA16 | est16 | Vegano · medio | 1 plato + 1 postre veganos | No | A-102 | Pasillo |
| POST-017 | PA17 | est17 | Sin lactosa · medio | 1 plato recomendado por Nomi + 1 bebida | **Sí** | B-202 | Entrada |
| POST-018 | PA18 | est18 | — · alto | Almuerzo para dos: 2 platos, iguales o distintos | No | A-101 | Escaleras |
| POST-019 | PA19 | est19 | Vegetariano + sin gluten · medio | 1 plato apto para ti | No | B-201 | Recepción |
| POST-020 | PA20 | est20 | — · medio | Almuerzo completo: 1 producto recomendado por Nomi + 1 bebida + 1 postre | **Sí** | LAB-1 | Puerta |
| POST-021 | PA21 | est21 | — · bajo | 1 snack | No | TAL-1 | Pasillo |
| POST-022 | PA22 | est22 | Vegetariano · medio | 1 snack + 1 bebida vegetarianos | No | A-102 | Entrada |
| POST-023 | PA23 | est23 | — · alto | 3 unidades de una bebida recomendada por Nomi, para compartir | **Sí** | B-202 | Escaleras |
| POST-024 | PA24 | est24 | Sin gluten · medio | 1 snack + 1 bebida sin gluten | No | A-101 | Recepción |
| POST-025 | PA25 | est25 | Vegano · medio | 1 producto recomendado por Nomi | **Sí** | B-201 | Puerta |
| POST-026 | PA26 | est26 | — · medio | 1 bebida + 1 postre | No | LAB-1 | Pasillo |
| POST-027 | PA27 | est27 | Sin lactosa · medio | 1 postre sin lactosa + 1 bebida | No | TAL-1 | Entrada |
| POST-028 | PA28 | est28 | Vegetariano + sin gluten · medio | 2 productos recomendados por Nomi | **Sí** | A-102 | Escaleras |
| POST-029 | PA29 | est29 | — · bajo | 1 comida de hasta S/ 6.00 | No | B-202 | Recepción |
| POST-030 | PA30 | est30 | — · alto | Pedido para el grupo: 3 productos distintos, 2 unidades de uno de ellos | No | A-101 | Puerta |

La tarjeta de prueba de cada POST (Visa crédito, Mastercard crédito o Mastercard débito, siempre
con titular `APRO`) está en `asignacion_POST.csv`. Ningún fallo está planificado.

### 9.1 Distribución de la muestra

| Criterio | Total | J1 | J2 | J3 |
|---|---|---|---|---|
| Registros | 30 | 10 | 10 | 10 |
| Participantes distintos | 30 | 10 | 10 | 10 |
| Elección con IA | 10 | 3 | 4 | 3 |
| Con restricción alimentaria | 15 | 5 | 5 | 5 |
| Cuentas sintéticas | 30, una por POST (`est01` … `est30`) | 10 | 10 | 10 |
| Perfiles de cuenta | Sin restricción: 15 (presupuesto medio 5, bajo 5, alto 5); vegetariano, vegano, sin gluten, sin lactosa y vegetariano + sin gluten: 3 cada uno | | | |
| Aulas | A-101: 6; A-102, B-201, B-202 y LAB-1: 5 cada una; TAL-1: 4 | | | |
| Puntos de encuentro | 6 cada uno | | | |
| Tarjetas | 10 de cada tipo | 4/3/3 | 3/4/3 | 3/3/4 |

---

## 10. Batería de validación VAL (fuera de la muestra)

Se ejecuta antes de las jornadas, con el catálogo C0. Las pruebas de visibilidad (VAL-019 a
VAL-021) usan productos de la tienda T2 para no alterar el estado de T1. Al terminar la batería,
el ejecutor devuelve T2 y T3 al estado de las jornadas (§13).

| ID | Qué valida | Resultado esperado | Ejecución |
|---|---|---|---|
| VAL-001 | Cancelar un pedido pendiente | `CANCELADO`, stock devuelto, checkout cerrado en MercadoPago | Script |
| VAL-002 | Pedir un producto agotado | Rechazo; stock sin cambios | Script |
| VAL-003 | Pedir más unidades que el stock | Rechazo | Script |
| VAL-004 | Caducidad de un pedido sin pagar (espera real de 15 min) | Cancelado por el sistema; stock devuelto | Script |
| VAL-005 | Tercer pedido sin pagar | `409` | Script |
| VAL-006 | Pago rechazado (`OTHE`) y reintento aprobado en el mismo checkout | Pedido pendiente tras el rechazo; confirmado tras el reintento | Persona |
| VAL-007 | Pagar un pedido cancelado | `409` | Script |
| VAL-008 | Cancelar un pedido ya pagado | `409` y el pedido queda confirmado | Persona |
| VAL-009 | Recomendaciones para un usuario vegano | Ningún producto no apto | Script |
| VAL-010 | Servicio de IA caído | Lista vacía con `FALLBACK`; la compra sigue siendo posible | Script |
| VAL-011 | Pedido con productos de T1 y T2 | Rechazo (un pedido, una tienda) | Script |
| VAL-012 | Pedido a una tienda inactiva (T3) | Rechazo | Script |
| VAL-013 | Tienda pasa un pedido `PENDIENTE` a `PREPARANDO` | `409` | Script |
| VAL-014 | Estudiante cambia un estado o consulta un pedido ajeno | `403` | Script |
| VAL-015 | Flujo logístico completo e historial de estados | Llega a `ENTREGADO` con todas las transiciones | Script |
| VAL-016 | Webhook sin firma o con firma inválida | `401` / `403` | Script |
| VAL-017 | Propina mayor al 50 % del subtotal | Rechazo | Script |
| VAL-018 | Pago aprobado de un pedido ya cancelado | Intento de reembolso. **Límite conocido del sandbox:** MercadoPago responde `401 Unauthorized use of live credentials` | Persona |
| VAL-019 | Producto agotado (stock 0) en Inicio, tienda, búsqueda y ficha | No aparece en las listas; la ficha (desde favoritos) muestra "Agotado" sin poder agregarlo | Persona + script |
| VAL-020 | Producto pausado (`disponible = false` con stock) | Igual que VAL-019 | Persona + script |
| VAL-021 | Producto no publicado | No aparece en ninguna pantalla de la app | Persona + script |

---

## 11. Captura de fechas, horas y eventos

### 11.1 Eventos

| Campo | Evento exacto | Fuente | Tipo |
|---|---|---|---|
| `user_started_at` | ENTER del operador en la señal de inicio | Reloj del ejecutor (misma máquina que el backend) | **INICIO** |
| `first_catalog_request_at` | Primera consulta de catálogo (`/api/products/search`) de la app después del inicio | Log del backend | Secundario |
| `ai_requested_at` | Petición `GET /api/ai/recommendations` durante el escenario | Log del backend | Secundario |
| `order_created_at` | `orders.creado_en` | PostgreSQL (escrito por el backend) | Secundario |
| `payment_started_at` | `payments.creado_en`: el backend crea el checkout y la app abre MercadoPago | PostgreSQL | Secundario |
| `mercadopago_payment_created_at` | `date_created` del pago aprobado: el participante envió el formulario de pago | API de MercadoPago | Secundario |
| `mercadopago_approved_at` | `date_approved` del pago aprobado: MercadoPago muestra la confirmación al participante | API de MercadoPago | **FIN principal** |
| `backend_payment_confirmed_at` | Fila `PREPARANDO` de `order_status_history` (= cambio a `PREPARANDO`) | PostgreSQL | Técnico secundario |
| `app_confirmation_seen_at` | Primera consulta de la app a su pedido después de `backend_payment_confirmed_at`: cota superior del momento en que la app mostró "Pago aprobado" | Log del backend | Secundario, aproximado |
| `ready_at`, `on_the_way_at`, `delivered_at` | Filas del historial de estados (logística simulada) | PostgreSQL | Secundario |
| Intentos de pago rechazados | Pagos no aprobados del mismo checkout | API de MercadoPago | Secundario |

### 11.2 Duraciones

| Campo | Fórmula | Qué representa |
|---|---|---|
| `human_interaction_seconds` | `mercadopago_payment_created_at − user_started_at` | Exploración, carrito, checkout y formulario de pago |
| `mercadopago_processing_seconds` | `mercadopago_approved_at − mercadopago_payment_created_at` | Procesamiento de MercadoPago |
| **`purchase_time_seconds`** | `mercadopago_approved_at − user_started_at` (= suma de las dos anteriores) | **Indicador principal D1** |
| `backend_reconciliation_seconds` | `backend_payment_confirmed_at − mercadopago_approved_at` | Conciliación interna del backend (retraso del sandbox, 0–60 s) |
| `technical_total_seconds` | `backend_payment_confirmed_at − user_started_at` | Tiempo total técnico hasta el pedido confirmado en el sistema |

### 11.3 Precisión y relojes

- `user_started_at` y todos los eventos del backend usan el reloj de la misma máquina.
- MercadoPago usa otro reloj y resolución de 1 s. En cada POST se mide el desfase con la cabecera
  HTTP `Date` de MercadoPago (`mp_clock_offset_seconds`). **Regla pre-registrada:** si el desfase
  absoluto es ≤ 1 s, no se corrige; si es mayor, las fechas de MercadoPago se corrigen con el
  desfase medido y se marca `mp_clock_corrected = true`. Se conservan los valores originales y
  los corregidos.
- Incertidumbre esperada del indicador principal: unos ±2 s, despreciable frente a una unidad en
  minutos.
- Todas las fechas se exportan en ISO-8601 con zona horaria. En la base se guardan en UTC, y la
  API las devuelve sin zona (auditoría M7).

### 11.4 Respuesta de la IA

La app pide las recomendaciones al abrir Inicio. La respuesta no se guarda en el sistema, así
que se captura sin cambiar código:

1. El ejecutor detecta en el log del backend la petición del participante.
2. Repite la misma petición con otro token del mismo usuario mientras dura la caché de 300 s del
   servicio de IA.
3. El log del servicio de IA indica si fue un acierto de caché. Si lo fue, la respuesta es
   idéntica a la que vio el participante (`ai_capture_verified = true`); si no, se marca como no
   verificable.
4. La latencia es la de la petición del participante (duración registrada por el backend), no la
   de la repetición, que sale de la caché.

---

## 12. Fórmulas de los indicadores

### 12.1 D1. Tiempo del proceso de compra (aprobado)

- `purchase_time_seconds = mercadopago_approved_at − user_started_at`, con la regla de reloj de
  §11.3.
- `purchase_time_minutes = purchase_time_seconds / 60` (2 decimales).
- Solo se define para compras exitosas (`order_success = 1`). Para las fallidas se registra
  `time_to_failure_seconds` = fin de la observación − `user_started_at`, hasta conocer cómo trató
  el pretest estos casos.

### 12.2 D2. Efectividad

- Por registro: `order_success = 1` si existen `mercadopago_approved_at` y
  `backend_payment_confirmed_at` dentro de la ventana de observación; `0` en otro caso. Es una
  variable **binaria**.
- **Tasa de la muestra = Σ order_success / N × 100**, con N = registros válidos.
- `logistics_success` (llegó a `ENTREGADO`) se registra aparte y no entra en el indicador.

### 12.3 D3. Accesibilidad para consulta (fórmula aprobada; valor pendiente de calibración)

Se calcula en `user_started_at`, antes de que el participante actúe:

- **`total_products_offered`** (SQL):
  ```sql
  SELECT count(*) FROM products p
  WHERE p.store_id = :t1 AND p.deleted_at IS NULL AND p.activo = true;
  ```
- **`accessible_products` = `visible_products`**: número de productos que devuelve
  `GET /api/products/search?storeId=:t1&disponible=true&size=50`, la misma consulta que usa la
  pantalla de la tienda, hecha con la sesión del usuario del escenario. Se contrasta con el
  equivalente en SQL (`activo AND disponible AND deleted_at IS NULL`) y se registra si coinciden.
- **`accessibility_rate = accessible_products / total_products_offered × 100`** (2 decimales).

Campos de control (no cambian la fórmula):

| Campo | Definición |
|---|---|
| `purchasable_products` | Visibles con stock ≥ 1 en una tienda activa |
| `out_of_stock_products` | Ofertados con stock = 0 |
| `hidden_products` | Ofertados no visibles (`total_products_offered − visible_products`) |
| `unpublished_products` | Productos de T1 no publicados (fuera del denominador) |
| `catalog_version` | C0 o C1 (§6) |

### 12.4 Resultados complementarios (no cambian las fórmulas)

| Campo | Definición |
|---|---|
| `compatible_products` | Comprables cuyas etiquetas cumplen **todas** las restricciones del usuario, con la misma regla del sistema (`DietaryRestriction`: un producto vegano cuenta también como vegetariano y sin lactosa) |
| `recommendable_products` | Compatibles sin "no me gusta", como mucho 40: el conjunto de candidatos que el backend envía a la IA |
| `discarded_by_restrictions` | `purchasable_products − compatible_products` |
| `compatible_with_user_restrictions` | Si todos los productos del pedido son compatibles con las restricciones del usuario |
| `ai_result` | `{origen, latencia_ms, recomendaciones}`: origen de la respuesta (`phi3`, `FALLBACK`…), duración de la petición del participante según el log del backend y productos recomendados. Vacío si no se pudo capturar |
| `ai_used_for_selection` | Si el pedido incluye al menos un producto recomendado |

---

## 13. Control del estado inicial

1. **Seed versionado:** el SQL del seed y el catálogo congelado (C0 o C1) se guardan con su hash.
   S0 es la foto del inventario inicial de T1.
2. **Reposición por jornada:** antes de J1, J2 y J3, el stock, la disponibilidad y la
   publicación de T1 vuelven exactamente a S0.
3. **Tiendas de VAL fuera de las jornadas:** durante las jornadas, los productos de T2 y T3 están
   **no publicados**. La búsqueda no filtra por tienda activa, así que si estuvieran publicados
   aparecerían en Inicio, en la búsqueda y en los candidatos de la IA.
4. **Estado esperado por POST:** Sₖ = S0 menos las compras registradas antes de POST-k en la misma
   jornada. Antes de empezar, el ejecutor compara el estado real con Sₖ. Si no coincide, **el POST
   no empieza** y queda registrado como `NO_INICIADA`.
5. **Usuario y sesión:** cada observación usa una cuenta **sin historial**: sin pedidos de ningún
   tipo, favoritos ni "no me gusta". El backend envía a la IA los productos que la cuenta ya
   compró, así que una cuenta compartida haría que un participante viera recomendaciones influidas
   por la compra de otro. Hay una cuenta por POST (`est01` … `est30`); los intentos repetidos y los
   ensayos usan cuentas propias con el mismo perfil (§13.1). La caché de la IA se vacía y el
   operador inicia una sesión nueva en la app (cerrar sesión vacía el carrito).
6. **Compras de familiarización (`FAM`):** se hacen con la cuenta `val.postest`. El ejecutor guarda
   el estado antes y lo restaura después, así que no alteran Sₖ ni entran en la muestra.
7. **Manifiesto:** commits de `nomi-backend`, `nomi-ios` y `nomi-ai-service`; configuración no
   secreta; versiones; dispositivo usado; versión y hash del catálogo; fecha y hora de cada
   jornada; participantes (anónimos) y su asignación.
8. **Reproducción:** cualquier POST puede repetirse restaurando Sₖ con el ejecutor.

### 13.1 Política de repeticiones

| Situación | `execution_result` | Tratamiento |
|---|---|---|
| Compra completada | `EXITOSA` | Registro válido |
| Fallo del sistema Nomi (error, rechazo, integración, caducidad, cierre inesperado de la app) | `FALLIDA_SISTEMA` | Registro **no exitoso**. **Nunca** se reemplaza |
| Desviación del protocolo ajena al sistema (el operador marcó mal el inicio, el participante siguió otra consigna) | `INVALIDA_PROTOCOLO` | Se conserva y el POST se repite como intento 2 desde la misma Sₖ, **con otro participante** que aún no haya hecho su POST oficial |
| Fallo del entorno de prueba ajeno a Nomi (corte de la Wi-Fi o de la luz, el Mac se apaga) | `INVALIDA_ENTORNO` | Se conserva, con la evidencia, y el POST se repite como intento 2 desde la misma Sₖ, **con otro participante** que aún no haya hecho su POST oficial |
| Precondición no cumplida antes del inicio | `NO_INICIADA` | Se corrige el entorno y se ejecuta con el mismo participante (aún no empezó) |

La repetición usa otra persona para mantener una sola observación por participante; el
reemplazo se anota en el manifiesto. En caso de duda entre `FALLIDA_SISTEMA` e
`INVALIDA_ENTORNO`, se clasifica como `FALLIDA_SISTEMA`.

**Cuenta de cada intento.** El intento 2 no puede usar la cuenta del intento 1, porque ya tiene
un pedido. El ejecutor crea al preparar una cuenta nueva con el mismo perfil del escenario
(`est07i2` para el intento 2 de POST-007). Los ensayos técnicos usan también su propia cuenta
(`ens004` para ENS-004), así que nunca dejan pedidos en las cuentas de los POST.

**Reclasificación.** La clasificación se decide al cerrar la observación. Si después se comprueba
que un `FALLIDA_SISTEMA` tuvo una causa ajena a Nomi, se reclasifica con el ejecutor
(`reclasificar`), que solo permite pasar a `INVALIDA_PROTOCOLO` o `INVALIDA_ENTORNO`, nunca a
`EXITOSA`, y exige un motivo. El registro conserva la clasificación original, el motivo y la
fecha, y el cambio aparece en el resumen y el manifiesto. Que un participante tarde más de 15
minutos en pagar **no** es causa ajena: es una caducidad y se queda como `FALLIDA_SISTEMA`.

---

## 14. Estructura final del CSV/JSON

La cabecera exacta, en orden, está en
[plantillas/postest_O2_plantilla.csv](plantillas/postest_O2_plantilla.csv). El JSON tiene los
mismos campos, con `products`, `initial_stock` y `ai_result` como estructuras en vez de texto.

| Campo | Descripción | Fuente | Unidad |
|---|---|---|---|
| `scenario_id` | POST-001 … POST-030 | Diseño | — |
| `attempt` | Número de intento (1 por defecto) | Ejecutor | — |
| `jornada`, `time_slot` | Jornada y franja horaria | Ejecutor | — |
| `participant_id` | Identificador anónimo de la persona (PA01…) | Asignación | — |
| `participant_prior_nomi_experience` | Si la persona conocía Nomi antes de la sesión | Operador | booleano |
| `device_type` | `iphone_fisico` o `simulador` | Manifiesto | — |
| `test_user_id`, `test_user_email` | Cuenta sintética | PostgreSQL | — |
| `restrictions`, `budget_range` | Perfil de la cuenta sintética | PostgreSQL | — |
| `store_id` | Tienda (T1) | PostgreSQL | — |
| `catalog_version` | C0 o C1 | Manifiesto | — |
| `aula_id`, `aula_name`, `meeting_point` | Entrega elegida | PostgreSQL (`orders`) | — |
| `consigna`, `ai_instructed` | Objetivo de compra y si pedía elegir con la IA | Asignación | — / booleano |
| `order_id` | Pedido creado | PostgreSQL | — |
| `products` | Productos comprados con cantidad y precio | PostgreSQL (`order_items`) | — |
| `quantities_total` | Unidades totales | PostgreSQL | unidades |
| `initial_stock` | Stock de cada producto comprado al inicio | Foto "antes" | unidades |
| `stock_check_ok` | stock final = inicial − cantidad | Fotos "antes" y "después" | booleano |
| `total_products_offered` | §12.3 | PostgreSQL (foto) | productos |
| `accessible_products`, `visible_products` | §12.3 (mismo valor). Si la API de catálogo no responde quedan vacíos, igual que `accessibility_rate` y `hidden_products`: nunca se sustituyen por el conteo SQL. El JSON guarda el estado HTTP de la consulta (`catalog_api_status`) | API de catálogo (foto) | productos |
| `accessible_sql_check_ok` | El conteo de la API coincide con el de SQL | Ejecutor | booleano |
| `accessibility_rate` | §12.3 | Calculado | % |
| `purchasable_products`, `out_of_stock_products`, `hidden_products`, `unpublished_products` | §12.3 | PostgreSQL (foto) | productos |
| `compatible_products`, `recommendable_products`, `discarded_by_restrictions` | §12.4 | PostgreSQL (foto) | productos |
| `compatible_with_user_restrictions` | §12.4 | Calculado | booleano |
| `ai_requested`, `ai_requested_at` | Petición de recomendaciones | Log del backend | — / fecha |
| `ai_result`, `ai_generated_by`, `ai_capture_verified` | §11.4 | Backend + log de la IA | — |
| `ai_used_for_selection` | §12.4 | Calculado | booleano |
| `user_started_at` | INICIO | Reloj del ejecutor | fecha |
| `first_catalog_request_at` | §11.1 | Log del backend | fecha |
| `order_created_at` | §11.1 | PostgreSQL | fecha |
| `payment_started_at` | §11.1 | PostgreSQL | fecha |
| `mercadopago_payment_created_at` | §11.1 | MercadoPago | fecha |
| `mercadopago_approved_at` | FIN principal | MercadoPago | fecha |
| `purchase_completed_at` | Igual a `mercadopago_approved_at` | MercadoPago | fecha |
| `mp_clock_offset_seconds`, `mp_clock_corrected` | §11.3 | Ejecutor | s / booleano |
| `mp_payment_id`, `payment_amount` | Pago aprobado | MercadoPago / PostgreSQL | — / S/ |
| `payment_card_type` | Tarjeta de prueba asignada. El medio real lo guarda el JSON (`payment_method_mp`, de MercadoPago) y el resumen señala los pagos hechos con otro medio, por ejemplo el saldo de la cuenta de prueba (`account_money`) | Asignación | — |
| `rejected_payment_attempts` | Intentos rechazados del checkout | MercadoPago | intentos |
| `backend_payment_confirmed_at` | Técnico secundario | PostgreSQL | fecha |
| `app_confirmation_seen_at` | Secundario, aproximado | Log del backend | fecha |
| `ready_at`, `on_the_way_at`, `delivered_at` | Logística simulada | PostgreSQL | fecha |
| `human_interaction_seconds` | §11.2 | Calculado | s |
| `mercadopago_processing_seconds` | §11.2 | Calculado | s |
| `purchase_time_seconds`, `purchase_time_minutes` | D1, §12.1 | Calculado | s / min |
| `backend_reconciliation_seconds` | §11.2 | Calculado | s |
| `technical_total_seconds` | §11.2 | Calculado | s |
| `time_to_failure_seconds` | Solo registros fallidos | Calculado | s |
| `order_success` | D2, §12.2 | Calculado | 0/1 |
| `logistics_success` | Llegó a `ENTREGADO` | PostgreSQL | booleano |
| `final_status` | Estado final del pedido | PostgreSQL | — |
| `execution_result` | `EXITOSA`, `FALLIDA_SISTEMA`, `INVALIDA_PROTOCOLO`, `INVALIDA_ENTORNO` o `NO_INICIADA` | Ejecutor | — |
| `error_code`, `error_description` | Detalle del fallo, si lo hubo | Backend / MercadoPago / ejecutor | — |
| `notes` | Observaciones del operador | Operador | — |

**Matriz de análisis.** `postest_O2.csv` conserva todos los registros como evidencia. La matriz
que se analiza es `postest_O2_analisis.csv` / `.json`: solo `EXITOSA` y `FALLIDA_SISTEMA`, un
registro por POST (N = 30). El ejecutor solo la escribe si pasa la validación descrita en
`runner/README.md` (30 filas, POST y participantes únicos, sin registros no válidos ni de ensayo,
D1 reproducible en las exitosas, D2 en todas, D3 en todas con la fórmula de §12.3).

No se incluye una tasa de efectividad por registro en 0/100: el dato por registro es
`order_success` (0/1) y la tasa solo existe para el conjunto (§12.2).

---

## 15. Compatibilidad estadística O1/O2

La metodología original contempla **t de Student para muestras relacionadas** si hay normalidad
y **Wilcoxon para muestras relacionadas** si no la hay. Esta sección **no cambia** esa
metodología: describe las dos situaciones posibles, que se decidirán al revisar los datos reales
de O1.

### Caso A: existe emparejamiento real

- Solo se pueden usar pruebas para muestras relacionadas si existe una relación
  **metodológicamente justificable** entre cada observación de O1 y su correspondiente de O2.
- Criterios que podrían justificarlo, si O1 los registró:
  - la **misma persona compradora** en O1 y en O2 (el más sólido);
  - un emparejamiento por condiciones equivalentes (misma franja, mismo tipo de compra),
    definido antes de ver los resultados.
- **No está permitido emparejar por número de fila** (POST-001 con R001).
- Si se quisiera emparejar por persona, los participantes de O2 tendrían que ser las mismas
  personas del pretest, y la asignación se rehace **antes** de ejecutar.

### Caso B: no existe emparejamiento

- Si O1 contiene 30 procesos de compra presenciales distintos y O2 otros 30 procesos digitales
  distintos, las observaciones son **independientes**.
- En ese caso, la metodología estadística debe actualizarse explícitamente **antes** del
  capítulo de resultados, justificando el cambio.

**Estado: sin decidir** hasta revisar la matriz numérica de O1.

---

## 16. Tratamiento estadístico por dimensión

**No se ejecutará ninguna prueba inferencial en esta fase** (ni Shapiro-Wilk, ni t de Student, ni
Wilcoxon, ni Mann-Whitney, ni McNemar, ni Fisher, ni chi-cuadrado). Se registra solo qué tipo de
prueba correspondería según el caso, para que el diseño de datos lo permita.

| Dimensión | Naturaleza del dato por registro | Caso A (relacionadas) | Caso B (independientes) |
|---|---|---|---|
| D1. Tiempo | Cuantitativa continua (minutos) | Normalidad de las diferencias (Shapiro-Wilk sobre dᵢ) → t para muestras relacionadas o Wilcoxon de rangos con signo | Normalidad por grupo → t de Welch o U de Mann-Whitney |
| D2. Efectividad | **Binaria 0/1** (`order_success`) | Prueba para datos binarios pareados (McNemar) | Comparación de proporciones: exacta de Fisher (preferible con n = 30 y proporciones extremas) o chi-cuadrado |
| D3. Accesibilidad | Proporción por registro (k de n productos) | Pruebas para diferencias pareadas de proporciones, valorando su distribución discreta y acotada | U de Mann-Whitney sobre las tasas, o comparación de proporciones agregadas si O1 solo tiene un valor global |

Reglas:

- **No** se aplica Shapiro-Wilk a `order_success`.
- **No** se aplican t de Student ni Wilcoxon a valores 0/100 de efectividad.
- Por registro se conservan `order_success` (0/1) y, para el conjunto, la tasa = éxitos / total × 100.
- Si O1 solo contiene tasas agregadas (un valor por dimensión), la comparación inferencial por
  registro no será posible para esa dimensión y habrá que tratarla de forma descriptiva o
  replantearla.
- **Dentro de O2** no hay dependencia intrapersonal: cada registro es de una persona distinta.
  Sigue habiendo dependencia serial de D3 dentro de cada jornada (§17), algo a considerar al
  elegir la prueba de D3.

---

## 17. Limitaciones e inconsistencias metodológicas

1. **En Nomi, un producto agotado deja de ser consultable.** El sistema oculta de sus listas los
   productos sin stock y los pausados (§4.2). Si en el pretest un agotado seguía siendo
   consultable (por ejemplo, porque figuraba en el menú o la pizarra), D3 no mide lo mismo en O1 y
   en O2. Es una característica del sistema, no del diseño de la prueba, y debe discutirse en la
   tesis.
2. **D3 depende del catálogo.** Con C0, D3 es 100 % por construcción. Solo con un catálogo
   calibrado con O1 (C1) tiene sentido compararlo. Si no fuera posible calibrar y se decidiera
   expresamente ejecutar con C0, D3 se reportaría solo como resultado descriptivo de un entorno
   sintético.
3. **El catálogo influye en D1.** El número de productos y categorías cambia el tiempo de
   exploración. Un catálogo muy distinto al del establecimiento del pretest afecta también a la
   comparación de D1.
4. **Límite de las pantallas.** Inicio muestra como mucho 20 productos, la búsqueda 30 y la tienda
   50, sin paginación adicional. Si el catálogo calibrado superara 50 productos, algunos dejarían
   de ser consultables desde la tienda. Habría que documentarlo como comportamiento del sistema.
5. **Dependencia serial de D3.** Los registros de una misma jornada comparten el estado del
   catálogo: una compra que agota un producto baja D3 en los registros siguientes. Las
   observaciones de D3 no son independientes dentro de una jornada.
6. **FIN distinto al del pretest.** En la compra presencial el estudiante probablemente recibía el
   producto al pagar. En el postest el FIN es la aprobación del pago; la entrega es posterior.
7. **Factores humanos.** Con 30 personas distintas no hay aprendizaje entre POST ni observaciones
   repetidas. Queda la variabilidad entre personas y su familiaridad previa con apps de pedidos,
   que se mitiga con la compra de familiarización y se documenta con
   `participant_prior_nomi_experience`.
8. **Dispositivo.** Se usa un iPhone físico para que la interacción sea la de un móvil real. Si
   hubiera que usar el simulador, la interacción con ratón o trackpad podría alterar D1, y
   `device_type` lo registraría en cada fila.
9. **Efectividad con techo.** En condiciones normales y sin fallos planificados, lo esperable es
   una tasa cercana al 100 %.
10. **Uso de la IA.** La app pide recomendaciones al abrir Inicio, así que `ai_requested` será casi
    siempre verdadero; la variable relevante es `ai_used_for_selection`. El límite de 5 peticiones
    de recomendaciones por minuto puede ocultar la lista si el participante vuelve muchas veces a
    Inicio; si ocurre, se registra.
11. **Pago en el sandbox.** La compradora de prueba inicia sesión antes de la sesión para no sumar
    al tiempo pasos del sandbox que no existen en producción. Los datos de la tarjeta de prueba se
    escriben en cada compra. El participante vuelve a la app a mano (M8); no afecta al FIN
    principal.
12. **Logística simulada.** Ninguna app de Nomi tiene pantallas de comercio ni de repartidor, y el
    código de confirmación no se verifica al entregar (auditoría M4). Los tiempos de preparación
    y entrega no son representativos y no entran en ningún indicador.
13. **Defectos del catálogo detectados (no afectan a la app).** `GET /products/store/{id}` devuelve
    también productos eliminados y no publicados, y `GET /products` y `/products/categoria/{c}`
    devuelven no publicados. La app no usa esas rutas. No se corrigen durante el postest para no
    cambiar el sistema.

---

## 18. Bloqueo de la ejecución oficial

Hasta que se decida qué hacer con O1, el ejecutor **no permite ejecutar los POST oficiales**:

- Los comandos oficiales (`runner post preparar|iniciar|cerrar` y `runner exportar --modo oficial`)
  se niegan a correr si no existe `qa/postest/AUTORIZACION_OFICIAL.json`. Ese archivo lo crea
  **solo el investigador**, e indica la decisión sobre O1, la versión y el hash del catálogo, las
  franjas y la fecha. El ejecutor comprueba que el catálogo cargado coincide con ese hash.
- Mientras tanto se puede usar el modo **ensayo** (`runner ensayo`) con C0: ejecuta la misma
  instrumentación, pero cada registro se identifica como ensayo (`ENS-0XX`, `modo = ensayo`), se
  guarda aparte, se exporta a `results/ensayos/` y nunca entra en `postest_O2`.

Mientras se recupera O1 se puede avanzar con: la implementación de la opción A del iPhone (hecha),
el ejecutor, el SQL del seed, el entorno `nomi_postest`, la batería VAL, las pruebas del
dispositivo y de MercadoPago Sandbox, el exportador CSV/JSON, las validaciones del catálogo y C0
para pruebas técnicas.

---

## 19. Entregables

| Archivo | Estado |
|---|---|
| `DISENO_POSTEST.md`, `seed/DISENO_SEED.md`, `PROTOCOLO_PARTICIPANTES.md`, `CONSENTIMIENTO_INFORMADO.md`, `scenarios/asignacion_POST.csv`, `INSTRUCCIONES_SESION.md`, `plantillas/postest_O2_plantilla.csv`, `DEPENDENCIAS_O1.md` | Versión 0.4 |
| `nomi-ios`: `Config/*.xcconfig`, `NetworkConfig`, `Info.plist`, `NetworkConfigTests` | Implementado (opción A) |
| `seed/*.sql`, `runner/` | En desarrollo, con C0 |
| `results/<fecha>/postest_O2.csv`, `postest_O2.json`, `val_results.json`, `snapshots/`, `logs/`, `manifest.json`, `RESUMEN.md` | Al ejecutar, tras la autorización |

---

## 20. Pendiente antes de ejecutar

| # | Pendiente | Depende de |
|---|---|---|
| 1 | Calibrar el catálogo (C1), o decidir expresamente ejecutar con C0 | O1 |
| 2 | Reproducir las franjas de O1 si existen; si no, usar las ventanas de §9 | O1 |
| 3 | Confirmar la asignación, o rehacerla con las personas del pretest si hay emparejamiento por persona | O1 |
| 4 | Tratamiento del tiempo de los registros fallidos | O1 |
| 5 | Completar el correo de contacto del consentimiento (título, investigador y programa ya completados) | Investigador |
| 6 | Crear `AUTORIZACION_OFICIAL.json` | Investigador |
| 7 | Recrear `nomi_postest` desde cero (`entorno recrear --confirmar`, que guarda antes un `pg_dump`) y cargar el seed definitivo, para que la ejecución oficial empiece sin pedidos de ensayos ni de la VAL | Ejecución oficial |

**Decidido el 27-09-2026: una cuenta por POST.** El backend envía a la IA los productos más
pedidos por el usuario (sus últimos 10 pedidos `ENTREGADO`). Con 8 cuentas compartidas, las
recomendaciones de un participante habrían dependido de lo que compraron los anteriores con la
misma cuenta; se observó en los ensayos, donde tras dos compras de `est01` la IA pasó a
recomendar esos dos productos. Ahora cada POST, cada intento repetido y cada ensayo usa una cuenta
sin historial con el perfil de su escenario, y `preparar` rechaza una cuenta que ya tenga pedidos
(§13).
