# Lo que depende de recuperar O1 (pretest)

**Estado de O1: `PENDIENTE_DE_VERIFICAR`.** No se ha visto todavía la matriz numérica real del
pretest. Nada de este documento supone que exista, y no se genera ni se estima ningún dato de O1.

---

## 1. Datos que hay que recuperar

### Por cada registro del pretest

| Dato | Para qué sirve |
|---|---|
| Identificador del registro (R001…) | Trazabilidad |
| Fecha y franja horaria | Condiciones de la observación; posible emparejamiento por franja |
| Establecimiento observado | Confirmar que es el que emula el postest |
| Identificador anonimizado de la persona compradora, si existe | Único criterio fuerte de emparejamiento real (caso A) |
| Productos y cantidades, si se registraron | Posible emparejamiento por tipo de compra y calibración del catálogo |
| **D1:** hora de inicio, hora de término y tiempo (min) | Indicador D1 y verificación de término − inicio |
| **D2:** completado sí/no y motivo si no | Indicador D2 por registro |
| **D3:** productos ofertados y productos accesibles para consulta en ese momento | Indicador D3 por registro y calibración del catálogo |

### Del instrumento (ficha de registro)

| Dato | Para qué sirve |
|---|---|
| Definición del evento de inicio y del de término | Comparabilidad de D1 |
| Cómo se registró el tiempo de las compras no completadas | Tratamiento de `time_to_failure_seconds` en O2 |
| Criterio de "completado exitosamente" | Comparabilidad de D2 |
| Criterio de "accesible para consulta" (menú, pizarra, vitrina, preguntar…) | Comparabilidad de D3 |
| **Si un producto agotado contaba como consultable** | En Nomi un agotado no es consultable: decide si D3 es comparable |
| Si D3 se midió por registro o por jornada | Tipo de análisis posible |

### Del establecimiento (para calibrar el catálogo)

| Dato | Uso en la calibración C0 → C1 |
|---|---|
| Número de productos ofertados | Tamaño del catálogo de T1 |
| Categorías de productos | Reparto por categorías |
| Disponibilidad observada (proporción no disponible al empezar) | Número de productos no consultables al inicio, elegidos por sorteo |
| Motivo de no disponibilidad (agotado, no ofrecido ese día) | Stock 0 o `disponible = false` |
| Precios y cantidades, si existen | Precios y stock inicial |

El procedimiento completo está en [seed/DISENO_SEED.md §4](seed/DISENO_SEED.md).

---

## 2. Decisiones que dependen de O1

### Antes de ejecutar los POST

| # | Decisión | Qué necesita de O1 | Si O1 no lo tiene |
|---|---|---|---|
| 1 | **Catálogo C1** (calibrado) o ejecutar con C0 | Datos del establecimiento (§1) | Se ejecuta con C0 y se declara que D3 viene de un entorno sintético y que su comparación inferencial con O1 podría no ser válida |
| 2 | **Emparejar por persona** (caso A) | Identificador de la persona compradora y que esas personas estén disponibles | No hay emparejamiento por persona |
| 3 | Si se empareja por persona: **rehacer la asignación** de participantes (`asignacion_POST.csv`) con esas mismas personas | Lo mismo | Se mantiene la asignación actual (PA01–PA30, una observación cada una, ajenos al desarrollo) |
| 4 | **Franjas horarias** de las jornadas | Fechas u horas de las observaciones de O1 | Se usan las ventanas de la evaluación controlada: J1 09:00–10:30, J2 12:00–14:00, J3 16:00–17:30 |

### Antes del análisis estadístico

| # | Decisión | Qué necesita de O1 |
|---|---|---|
| 5 | **Caso A o caso B** ([DISENO_POSTEST.md §15](DISENO_POSTEST.md)) | Si existe una relación metodológicamente justificable entre cada registro de O1 y uno de O2. Nunca por número de fila |
| 6 | Si es caso B: **actualizar la metodología estadística** antes del capítulo de resultados | La estructura real de O1 |
| 7 | **Pruebas de D1** | Tiempos por registro de O1 |
| 8 | **Pruebas de D2** (datos binarios) | Si O1 tiene resultado por registro o solo la tasa agregada |
| 9 | **Pruebas de D3** | Si O1 tiene tasa por registro o solo un valor global |
| 10 | **Comparabilidad de D1** | Eventos de inicio y fin de la ficha; tratamiento de las compras fallidas |
| 11 | **Comparabilidad de D3** | Si un agotado contaba como consultable en O1 |

Mientras no se conozca la estructura de O1 **no se ejecuta ninguna prueba inferencial**: ni
Shapiro-Wilk, ni t de Student, ni Wilcoxon, ni Mann-Whitney, ni McNemar, ni Fisher, ni
chi-cuadrado.

---

## 3. Lo que no depende de O1

Se puede preparar y, una vez aprobado, ejecutar sin O1:

- el entorno controlado, el ejecutor y la batería VAL;
- la medición de D1 (INICIO → aprobación en MercadoPago) y de D2 (`order_success`);
- la medición de D3 con sus definiciones (ofertado, consultable, comprable), aunque su **valor**
  dependa del catálogo usado;
- el protocolo y el consentimiento;
- la implementación del iPhone físico (opción A, ya hecha), el ejecutor, el SQL del seed, el
  entorno `nomi_postest`, la batería VAL, las pruebas del dispositivo y de MercadoPago Sandbox y
  el exportador, usando C0 solo para pruebas técnicas.

**La ejecución oficial de POST-001 a POST-030 sí depende de O1** y está bloqueada en el ejecutor
([DISENO_POSTEST.md §18](DISENO_POSTEST.md)).

---

## 4. Si O1 no se puede recuperar

1. Se documenta que no existe la matriz numérica de O1.
2. Se decide **expresamente** si ejecutar con C0. En ese caso, D3 se reporta solo como resultado
   descriptivo de una evaluación controlada con catálogo sintético, sin afirmar comparabilidad con
   el pretest.
3. La comparación O1/O2 se resuelve metodológicamente antes del análisis. Por ejemplo, un análisis
   descriptivo de O2 frente a los valores de referencia documentados del pretest, si los hubiera,
   declarando la limitación. Esta decisión corresponde a la metodología de la tesis, no a este
   diseño.
4. En ningún caso se generan, estiman ni imputan datos de O1.
