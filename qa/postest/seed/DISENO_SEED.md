# Diseño del seed del postest (O2)

| | |
|---|---|
| **Versión** | 0.4 (27-09-2026) |
| **Base de datos** | `nomi_postest` (exclusiva del postest; nunca la base de desarrollo ni una de producción) |
| **Estructura** | Definitiva |
| **Catálogo de T1** | `PENDIENTE_DE_CALIBRACION_O1`. Existe un catálogo provisional C0 |

> Todo el contenido de este seed son **datos sintéticos de prueba**. No es información real del
> ISTPC, no representa transacciones reales y no reproduce el catálogo del pretest. Los nombres
> de tiendas y aulas llevan "(prueba)" o "(entorno de prueba)" para que nunca se confundan con
> datos reales.

---

## 1. Principios

1. **Neutralidad.** Ningún valor del seed se elige para producir un resultado estadístico. El
   stock, la disponibilidad y la publicación no se relacionan con las consignas.
2. **Calibración antes que invención.** Las características del catálogo que influyen en los
   indicadores (número de productos, categorías y disponibilidad) se toman de O1 cuando se
   recupere. Mientras tanto se usa un catálogo provisional neutral (C0).
3. **Congelado antes de ejecutar.** El catálogo usado (C0 o C1) se congela con un hash antes del
   primer POST y no se modifica después.
4. **Reproducibilidad.** El seed es un SQL idempotente que se carga siempre sobre una base recién
   migrada.

---

## 2. Estructura definitiva

### 2.1 Establecimientos

| Id lógico | Nombre | Estado | Uso |
|---|---|---|---|
| T1 | Quiosco ISTPC (entorno de prueba) | Activo | Único establecimiento de los POST |
| T2 | Cafetería Taller (prueba) | Activo | Solo VAL: pedido con varias tiendas (VAL-011) y visibilidad (VAL-019 a VAL-021) |
| T3 | Quiosco Cerrado (prueba) | Inactivo | Solo VAL: tienda inactiva (VAL-012) |

Durante las jornadas, los productos de T2 y T3 están **no publicados** (§5).

### 2.2 Cuentas

| Cuenta | Rol | Uso |
|---|---|---|
| `comercio.postest@nomi.test` | `COMERCIO`, dueño de T1 | El ejecutor avanza la logística simulada |
| `comercio2.postest@nomi.test` | `COMERCIO`, dueño de T2 y T3 | Solo VAL |
| `est01.postest@nomi.test` … `est30.postest@nomi.test` | `ESTUDIANTE` | Una por POST: la opera el participante de ese POST |
| `val.postest@nomi.test` | `ESTUDIANTE` | VAL y compras de familiarización (para no dejar pedidos en las cuentas de los POST) |
| `val2.postest@nomi.test` | `ESTUDIANTE`, vegana | Solo VAL: recomendaciones con restricción (VAL-009) y «otro estudiante» (VAL-014) |

Las contraseñas se generan al cargar el seed y se guardan en un archivo local fuera del control
de versiones. No se publican.

**Una cuenta por POST.** El backend envía a la IA los productos que la cuenta ya compró (sus
últimos 10 pedidos entregados). Si varios participantes compartieran una cuenta, las
recomendaciones de uno dependerían de lo que compraron los anteriores. Por eso cada POST tiene su
cuenta `estNN` (POST-0NN), creada sin historial, con el perfil de su escenario: la restricción y
el presupuesto de las columnas `restrictions` y `budget_range` de
[../scenarios/asignacion_POST.csv](../scenarios/asignacion_POST.csv), de donde el seed las crea.

| Perfil | Cuentas |
|---|---|
| Sin restricción, presupuesto medio | est01, est09, est11, est20, est26 |
| Sin restricción, presupuesto bajo | est04, est10, est15, est21, est29 |
| Sin restricción, presupuesto alto | est06, est13, est18, est23, est30 |
| Vegetariano, medio | est02, est12, est22 |
| Sin gluten, medio | est03, est14, est24 |
| Vegano, medio | est05, est16, est25 |
| Sin lactosa, medio | est07, est17, est27 |
| Vegetariano + sin gluten, medio | est08, est19, est28 |

Las cuentas de los intentos repetidos (`est07i2`, …) y de los ensayos técnicos (`ens004`, …) no
se crean en el seed: el ejecutor las crea al preparar la observación, con el mismo perfil del
escenario. Ninguna cuenta tiene favoritos, valoraciones de "no me gusta" ni pedidos al empezar, y
`preparar` rechaza la que los tenga.

### 2.3 Aulas

| Id lógico | Código | Nombre |
|---|---|---|
| A-101 | PRB-A101 | Aula 101 – Pabellón A (prueba) |
| A-102 | PRB-A102 | Aula 102 – Pabellón A (prueba) |
| B-201 | PRB-B201 | Aula 201 – Pabellón B (prueba) |
| B-202 | PRB-B202 | Aula 202 – Pabellón B (prueba) |
| LAB-1 | PRB-LAB1 | Laboratorio de Cómputo 1 (prueba) |
| TAL-1 | PRB-TAL1 | Taller de Electrónica (prueba) |

Los puntos de encuentro no se siembran: son las cinco opciones fijas de la app (la puerta del
salón, el pasillo, la entrada del edificio, las escaleras y recepción).

### 2.4 Datos heredados

La migración V14 inserta una universidad y un campus de otro contexto (UCV Lima Norte). El flujo
de compra no los usa. El seed los elimina de `nomi_postest`, junto con sus dependientes, para que
ninguna exportación los contenga. No se modifica ninguna migración.

---

## 3. Catálogo provisional C0 de T1

**Estado: provisional.** Se usa solo para:

- desarrollar y probar el ejecutor;
- probar el entorno, el dispositivo y MercadoPago Sandbox;
- la compra de familiarización;
- la batería VAL y la verificación técnica.

**No se usa para los 30 registros oficiales.** Solo si resultara imposible recuperar información
suficiente de O1, se decidiría expresamente si ejecutar con C0 y tratar D3 como resultado
descriptivo de un entorno sintético.

Reglas de C0:

- 35 productos alimenticios habituales en un quiosco estudiantil, en 4 categorías.
- **Todos publicados y disponibles.** Ninguno agotado, pausado ni con stock bajo elegido a mano.
- **Stock inicial uniforme: 25 unidades por producto.** Es una cantidad de jornada, sin relación
  con las consignas.
- Precios sintéticos en soles, en rangos plausibles para un quiosco estudiantil.
- Etiquetas dietéticas según la composición habitual de cada producto: V = vegetariano,
  VG = vegano, SG = sin gluten, SL = sin lactosa. "Todas" = V, VG, SG y SL.

| # | Producto | Categoría | Precio (S/) | Etiquetas |
|---|---|---|---:|---|
| C01 | Arroz con pollo | Comida | 8.00 | SG, SL |
| C02 | Tallarines verdes con papa | Comida | 8.00 | V |
| C03 | Ají de gallina | Comida | 9.00 | — |
| C04 | Lomo saltado | Comida | 12.00 | SL |
| C05 | Arroz chaufa de pollo | Comida | 9.00 | SL |
| C06 | Chaufa de verduras | Comida | 8.00 | V, SL |
| C07 | Causa de pollo | Comida | 6.00 | SG |
| C08 | Papa a la huancaína | Comida | 5.00 | V, SG |
| C09 | Ensalada de quinua | Comida | 7.00 | Todas |
| C10 | Tamal de cerdo | Comida | 4.00 | SG |
| C11 | Salchipapa | Comida | 6.00 | — |
| C12 | Pan con pollo | Comida | 5.00 | SL |
| C13 | Pan con chicharrón | Comida | 6.00 | SL |
| C14 | Pan con palta | Comida | 3.50 | V, VG, SL |
| C15 | Triple (palta, tomate y huevo) | Comida | 4.50 | V |
| C16 | Empanada de carne | Comida | 4.00 | — |
| C17 | Chicha morada 500 ml | Bebida | 2.50 | Todas |
| C18 | Emoliente | Bebida | 1.50 | Todas |
| C19 | Agua mineral 625 ml | Bebida | 1.50 | Todas |
| C20 | Gaseosa 500 ml | Bebida | 3.00 | Todas |
| C21 | Café pasado | Bebida | 2.00 | Todas |
| C22 | Yogurt bebible | Bebida | 3.50 | V, SG |
| C23 | Jugo de papaya | Bebida | 4.00 | Todas |
| C24 | Avena con leche | Bebida | 2.00 | V |
| C25 | Quinua con manzana | Bebida | 2.00 | Todas |
| C26 | Papas fritas en bolsa | Snack | 2.50 | Todas |
| C27 | Galletas de soda | Snack | 1.00 | V, SL |
| C28 | Maní salado | Snack | 2.00 | Todas |
| C29 | Chifles | Snack | 2.50 | Todas |
| C30 | Barra de cereal | Snack | 2.50 | V |
| C31 | Alfajor | Postre | 2.00 | V |
| C32 | Arroz con leche | Postre | 3.00 | V, SG |
| C33 | Mazamorra morada | Postre | 3.00 | Todas |
| C34 | Gelatina | Postre | 2.00 | SG, SL |
| C35 | Fruta picada | Postre | 3.50 | Todas |

Reparto: 16 comidas, 9 bebidas, 5 snacks y 5 postres. Todas las restricciones de las cuentas
sintéticas tienen al menos un plato, una bebida, un snack y un postre compatibles, para que
ninguna consigna sea imposible por diseño.

**Consecuencia para D3.** Con C0, al inicio de cada jornada hay 35 ofertados y 35 consultables:
D3 = 100 % **por construcción**. Con 25 unidades por producto y unas 20 unidades vendidas por
jornada repartidas entre varios productos, es poco probable que alguno se agote. **Ese valor no
refleja el comportamiento del sistema ni del establecimiento y no debe usarse para inferencia.**

---

## 4. Calibración con O1 (C0 → C1)

Si se recupera la matriz de O1 con información del catálogo del establecimiento, se construye el
catálogo **C1** con este procedimiento, **antes** de ejecutar ningún POST:

| Característica de C1 | Dato de O1 que la determina | Regla |
|---|---|---|
| Número de productos ofertados (N) | Productos ofertados por observación | N = mediana de O1. Si N < 35, se eliminan de C0 productos por sorteo estratificado por categoría. Si N > 35, se añaden productos de las mismas categorías y rangos de precio. Límite del sistema: la tienda muestra como mucho 50 |
| Reparto por categorías | Categorías del establecimiento observadas | Proporciones de O1 redondeadas; si no se registraron, se mantienen las de C0 |
| Productos no consultables al inicio (k) | Proporción media de productos no accesibles al empezar las observaciones de O1 | k = redondeo de (1 − tasa media de accesibilidad de O1) × N. Los k productos se eligen **por sorteo con una semilla publicada en el manifiesto**, nunca a mano |
| Forma en que un producto no es consultable | Motivo observado en O1 (agotado, no ofrecido ese día…) | Agotado → stock 0. No ofrecido temporalmente → `disponible = false`. En Nomi ambos dejan de ser consultables (comportamiento del sistema) |
| Stock inicial de los disponibles | Cantidades del establecimiento, si O1 las registró | Si las hay, se usan; si no, se mantiene la regla uniforme de C0 (25) |
| Precios | Precios de O1, si se registraron | Si los hay, se usan; si no, los de C0 |
| Comportamiento de los agotados | Si en O1 un agotado seguía siendo consultable (menú, pizarra) | No se puede reproducir sin cambiar el sistema. Se documenta como diferencia en la comparación de D3 |

Después de calibrar:

1. Se genera el SQL de C1 y se calcula su hash (SHA-256).
2. El hash, la semilla del sorteo y la tabla de calibración (dato de O1 → valor de C1) se guardan
   en el manifiesto.
3. **C1 se congela.** No se modifica después de ver ningún resultado. Si se detectara un error
   material de calibración, se documentaría y se repetiría la muestra completa con la corrección,
   conservando la ejecución anterior.

Si O1 no permite calibrar, **no se ejecuta automáticamente con C0**: se decide expresamente. Si se
ejecutara con C0, se documentaría que D3 se obtuvo en un entorno sintético controlado y que solo
tiene valor descriptivo.

---

## 5. Estado de T2 y T3

| Tienda | Productos | Durante VAL | Durante las jornadas |
|---|---|---|---|
| T2 | 2 productos normales, 1 agotado (stock 0), 1 pausado (`disponible = false` con stock) y 1 no publicado | Publicados según cada VAL | **Todos no publicados** |
| T3 | 2 productos normales | Publicados solo para VAL-012 | **Todos no publicados** |

La búsqueda del sistema no filtra por tienda activa. Si los productos de T2 o T3 estuvieran
publicados durante las jornadas, aparecerían en Inicio, en la búsqueda y entre los candidatos de
la IA de los POST.

---

## 6. Estados que gestiona el ejecutor

| Momento | Operación sobre `nomi_postest` |
|---|---|
| Recreación (`entorno recrear --confirmar`) | `pg_dump` de la base anterior en `results/respaldos/` → borra el contenedor y el volumen del postest → levanta una base vacía. Se hace antes de la ejecución oficial, para que no queden pedidos de ensayos ni de la VAL |
| Carga inicial | Migraciones V1–V25 → cuentas (fijas + una por POST) → seed de estructura → catálogo congelado (C0 o C1) → foto S0 |
| Antes de la batería VAL | Publica los productos de T2 y T3 que cada VAL necesita |
| Después de la batería VAL | Despublica T2 y T3; restaura T1 a S0; cancela o elimina los datos de la cuenta `val.postest` si afectaran a los POST |
| Antes de cada jornada | Restaura el stock, la disponibilidad y la publicación de T1 a S0 |
| Antes de cada POST | Verifica que el estado es Sₖ (S0 menos las compras previas de la jornada) y que la cuenta no tiene pedidos, favoritos ni «no me gusta»; vacía la caché de la IA |

Los pedidos, pagos e historiales de los POST **no se borran nunca**: son la evidencia de la
ejecución.
