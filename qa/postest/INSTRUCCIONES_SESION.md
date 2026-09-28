# Instrucciones para ejecutar las sesiones del postest

Guía del **operador QA**. Se usa junto con [DISENO_POSTEST.md](DISENO_POSTEST.md),
[PROTOCOLO_PARTICIPANTES.md](PROTOCOLO_PARTICIPANTES.md) y
[scenarios/asignacion_POST.csv](scenarios/asignacion_POST.csv).

> **La ejecución oficial está bloqueada** hasta que se decida qué hacer con O1
> ([DISENO_POSTEST.md §18](DISENO_POSTEST.md)). Mientras tanto solo se usan el modo **ensayo** y la
> batería VAL, con el catálogo provisional C0. `runner …` significa `python3 postest.py …` desde
> la carpeta `runner/`; el detalle de cada comando está en [runner/README.md](runner/README.md).

---

## 0. Qué hace cada uno

| Quién | Hace | No hace |
|---|---|---|
| Participante | La compra completa en la app | Iniciar sesión, preparar datos, ver credenciales |
| Operador | Prepara el entorno y el iPhone, inicia la sesión de la cuenta, lee la consigna, marca el inicio y anota incidencias | Ayudar o sugerir durante la observación |
| Ejecutor (script) | Verifica el estado, captura eventos, avanza la logística simulada y exporta | Ninguna acción de compra que se mida |

## 1. Preparación del entorno (una vez)

| # | Paso | Comprobación |
|---|---|---|
| 1 | `runner entorno levantar`: arranca PostgreSQL del postest (`nomi_postest`, contenedor propio), el servicio de IA y el backend apuntando a `nomi_postest`. **Antes de la ejecución oficial**, `runner entorno recrear --confirmar` en su lugar: guarda un `pg_dump` y deja la base vacía, sin los pedidos de los ensayos ni de la VAL | Todo responde |
| 2 | `runner seed cargar --catalogo C0` para pruebas técnicas, o `--catalogo C1` para la ejecución oficial. Crea las cuentas fijas y una por POST (`est01` … `est30`) | El ejecutor muestra el hash del catálogo y guarda S0 |
| 3 | Instalar la app en el **iPhone físico** con `Config/Local.xcconfig` (URL del Mac y equipo de firma; ver el README de `nomi-ios`). iPhone y Mac en la misma Wi-Fi; aceptar el permiso de red local | La app muestra el catálogo del quiosco de prueba |
| 4 | En Safari del iPhone, iniciar sesión con la **compradora de prueba** de MercadoPago. Comprobar que la app de MercadoPago **no** está instalada o no tiene sesión | La página de MercadoPago muestra la cuenta de prueba |
| 5 | `runner entorno verificar` | Backend, IA, MercadoPago, catálogo S0, desfase de reloj y cuentas sin pedidos: todo en verde |

Si el iPhone físico tiene un impedimento técnico, se documenta en el manifiesto y se usa el
simulador.

## 2. Batería VAL (antes de la jornada J1)

1. `runner val ejecutar`: corre las VAL automáticas y deja para el final las que requieren persona.
2. VAL con persona: `runner val persona VAL-006` y `runner val persona VAL-008` (pago en el
   sandbox) y `runner val persona VAL-018` (se registra como no verificable en el sandbox). Para
   la parte visual de VAL-019 a VAL-021: `runner val visual preparar`, mirar Inicio, Buscar, la
   tienda T2 y Favoritos en la app, `runner val registrar VAL-0XX --resultado … --evidencia …` y
   `runner val visual cerrar`. Al final, `runner val final` consolida todo en
   `results/val/VAL_FINAL.md`.
3. `runner val cerrar`: cancela los pedidos pendientes de la VAL, despublica T2 y T3 y restaura T1
   al estado previo. Los resultados ya quedaron en `results/val/val_results_<fecha>.json`.
4. **Si alguna VAL de visibilidad (019–021) no da el resultado esperado, se detiene todo** y se
   revisa la definición de D3 antes de ejecutar ningún POST.

## 3. Cada jornada

| Jornada | Ventana de la evaluación controlada | Participantes |
|---|---|---|
| J1 | 09:00 a 10:30 (desayuno) | PA01 a PA10 |
| J2 | 12:00 a 14:00 (almuerzo) | PA11 a PA20 |
| J3 | 16:00 a 17:30 (tarde) | PA21 a PA30 |

Si se recuperan las franjas de O1 y difieren, se usan las de O1 (se actualizará esta tabla antes
de ejecutar).

### 3.1 Al empezar

1. Comprobar que la hora está dentro de la ventana de la jornada.
2. `runner jornada iniciar J1`: restaura T1 a S0, comprueba que T2 y T3 están despublicados y
   que las cuentas sintéticas no tienen pedidos pendientes.

### 3.2 Cada participante (en el orden de `asignacion_POST.csv`)

| # | Operador | Ejecutor |
|---|---|---|
| 1 | Explica el estudio y recoge el consentimiento firmado. La hoja se archiva aparte | — |
| 2 | Anota si el participante conocía Nomi (se pasa en el paso 4) | — |
| 3 | `runner fam preparar PAxx`; el participante hace la compra de familiarización con la cuenta `val.postest`; después, `runner fam cerrar PAxx` | Guarda el estado antes, cancela lo pendiente y lo restaura después. La compra no entra en la muestra |
| 4 | `runner post preparar POST-0XX --conocia-nomi si\|no` | Verifica que el estado es Sₖ, que la cuenta no tiene pedidos, favoritos ni «no me gusta», y que T2 y T3 están despublicados; vacía la caché de la IA, mide el desfase de reloj y guarda la foto "antes". Muestra la cuenta que corresponde. Si algo no cuadra, escribe un registro `NO_INICIADA` y no deja continuar |
| 5 | Cierra la sesión anterior en la app e inicia sesión con la cuenta que mostró el ejecutor (`estNN` para POST-0NN; su contraseña está en `.secrets/cuentas.env`). Deja la app en Inicio con el carrito vacío | — |
| 6 | Entrega el iPhone y la tarjeta de consigna. Lee la consigna en voz alta | — |
| 7 | `runner post iniciar POST-0XX`, dice **"empieza"** y pulsa ENTER a la vez | Registra `user_started_at`, mide D3 y captura las recomendaciones de la IA que vio el participante |
| 8 | `runner post cerrar POST-0XX` en la misma terminal. Observa sin intervenir y anota incidencias | Espera a que el pedido se confirme, se cancele o venza el plazo de pago (15 min + 2 min de margen) |
| 9 | Cuando el participante dice "listo", no hace nada: el FIN lo toma el ejecutor de MercadoPago | Lee `mercadopago_approved_at` y la confirmación del backend, y espera hasta 60 s la consulta de la app al pedido confirmado |
| 10 | Recoge el iPhone | Avanza la logística simulada, guarda la foto "después", comprueba el stock y escribe el registro |

Si el participante declara que no puede completar la compra, el operador interrumpe la espera
(Ctrl+C, no cambia nada) y ejecuta `runner post cerrar POST-0XX --no-completada`. Las desviaciones
ajenas a Nomi se cierran con `--resultado INVALIDA_PROTOCOLO` o `--resultado INVALIDA_ENTORNO`, y
las observaciones con `--notas "…"`.

En modo ensayo, los pasos 4 a 10 son iguales con `runner ensayo … ENS-0XX --escenario POST-0XX`.
Cada ensayo usa su propia cuenta (`ens0XX`), que el ejecutor crea con el perfil del escenario, así
que nunca deja pedidos en las cuentas de los POST. Los registros de ensayo se guardan aparte
(`runtime/registros/ensayo/`) y se exportan a `results/ensayos/`, nunca a `postest_O2`. Los
comandos oficiales `runner post …` fallan si no existe una `AUTORIZACION_OFICIAL.json` válida.

### 3.3 Clasificación de incidencias

| Situación | `execution_result` | ¿Se repite? |
|---|---|---|
| Compra completada | `EXITOSA` | No |
| Error de la app o del backend, pago rechazado, caducidad, cierre inesperado de la app, el participante no pudo completar la compra | `FALLIDA_SISTEMA` | **No. Nunca se reemplaza** |
| El operador marcó mal el inicio o el participante siguió otra consigna | `INVALIDA_PROTOCOLO` | Sí, como intento 2 desde la misma Sₖ, **con otro participante** que aún no haya hecho su POST, conservando el intento 1 |
| Corte de la Wi-Fi o de la luz, el Mac se apagó (ajeno a Nomi) | `INVALIDA_ENTORNO` | Sí, como intento 2, con otro participante, conservando el intento 1 y la evidencia |
| El ejecutor no pudo verificar el estado antes de empezar | `NO_INICIADA` | Se corrige y se ejecuta con el mismo participante |

En caso de duda entre `FALLIDA_SISTEMA` e `INVALIDA_ENTORNO`, se clasifica como
`FALLIDA_SISTEMA`. Toda repetición queda en el archivo con su motivo y el reemplazo de
participante se anota en el manifiesto.

- **Repetición:** `runner post preparar POST-0XX --intento 2 …`. El ejecutor crea una cuenta nueva
  con el mismo perfil (`estNNi2`), porque la del intento 1 ya tiene un pedido.
- **Reclasificar después de cerrar:** si se comprueba que un `FALLIDA_SISTEMA` tuvo una causa
  ajena a Nomi, `runner post reclasificar POST-0XX --resultado INVALIDA_… --motivo "…"`. Solo pasa
  de `FALLIDA_SISTEMA` a `INVALIDA_PROTOCOLO` o `INVALIDA_ENTORNO`, conserva la clasificación
  original y queda en el resumen y el manifiesto. Que el participante tarde más de 15 minutos en
  pagar **no** es causa ajena: es una caducidad (`FALLIDA_SISTEMA`).

### 3.4 Al terminar la jornada

1. `runner jornada cerrar J1`: guarda una copia de seguridad de la base (`pg_dump`) en
   `results/respaldos/`. No se versiona: se archiva aparte como evidencia.
2. Revisar que haya 10 registros, uno por POST de la jornada, más los intentos repetidos si los
   hubo.

## 4. Cierre del postest

1. `runner exportar --modo oficial`: genera `postest_O2.csv`, `postest_O2.json`, `manifest.json` y
   `RESUMEN.md` en `results/<fecha>/`, y la matriz de análisis `postest_O2_analisis.csv` / `.json`
   solo si supera la validación (resultado en `validacion_analisis.json`).
2. Revisar el `RESUMEN.md`: escenarios ejecutados, exitosos, fallidos y por qué, intentos
   repetidos y validez de la muestra.
3. `runner entorno detener`. La base `nomi_postest` se conserva como evidencia.
4. No compartir el archivo de contraseñas de las cuentas sintéticas ni los consentimientos
   firmados.

## 5. Lista rápida por participante

- [ ] Consentimiento firmado (archivado aparte)
- [ ] Compra de familiarización (`FAM`) hecha y estado restaurado
- [ ] Estado verificado (Sₖ) y foto "antes"
- [ ] Sesión de la cuenta correcta, en Inicio, con el carrito vacío
- [ ] Tarjeta de consigna correcta
- [ ] "Empieza" y ENTER a la vez
- [ ] Sin intervenir durante la observación
- [ ] Registro cerrado con su clasificación y notas
