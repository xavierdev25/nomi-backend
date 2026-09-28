# Protocolo para participantes del postest

**Evaluación controlada del prototipo Nomi mediante escenarios de compra.** Este protocolo
describe lo que el operador explica y pide a cada participante. Se aplica igual a todas las
personas para que las observaciones sean comparables.

> Todo lo que el participante ve en la app (quiosco, productos, precios, stock, aulas, cuentas)
> son **datos de prueba**. Los pagos se hacen en el entorno de pruebas de MercadoPago: **no se
> cobra dinero real**.

---

## 1. Perfil de los participantes

- 30 personas (PA01 a PA30), cada una con **una sola observación oficial**: PA01–PA10 en la
  jornada de desayuno, PA11–PA20 en la de almuerzo y PA21–PA30 en la de tarde.
- **Ajenas al desarrollo de Nomi.** Si alguien ya conoce la app, se anota; no se excluye.
- Cualquier persona que sepa usar un teléfono inteligente. No hace falta experiencia técnica.
- Cada persona se identifica solo con su código (PA01…). En los resultados no aparece su nombre.

## 2. Antes de la observación

1. **Consentimiento.** El operador explica el estudio y entrega el
   [consentimiento informado](CONSENTIMIENTO_INFORMADO.md). Sin consentimiento no se participa.
2. **Explicación breve** (texto para leer tal cual):

   > "Vamos a evaluar una app para pedir comida dentro de un instituto. Te pediré hacer una compra
   > de práctica y luego una compra siguiendo una consigna. No estamos evaluándote a ti, sino a la app: no hay respuestas
   > correctas ni incorrectas. Compra como lo harías normalmente, sin apurarte ni ir más lento de
   > lo habitual. Los pagos son de prueba y no se cobra nada."

3. **Compra de familiarización** (no se registra): el participante hace una compra libre con una
   cuenta de prueba para conocer la app. El operador puede resolver dudas durante esta compra,
   pero **no** durante la observación.

## 3. Reglas durante la observación

1. El operador entrega el teléfono con la app abierta en Inicio y la sesión ya iniciada. El
   participante no necesita usuario ni contraseña.
2. El operador lee la consigna y la deja por escrito a la vista. Al decir **"empieza"**, comienza
   la observación.
3. El participante busca los productos, arma el carrito, elige el aula y el punto de encuentro de
   la consigna, confirma el pedido y paga.
4. Si la consigna pide **elegir entre las recomendaciones de Nomi**, debe elegir al menos un
   producto de la sección de recomendaciones.
5. Para pagar usa la **tarjeta de prueba** de la tarjeta de consigna (§5). La sesión de pago de
   prueba ya está iniciada.
6. Cuando MercadoPago muestre que el pago se acreditó, dice **"listo"**. Puede volver a la app de
   Nomi, pero no es obligatorio.
7. Durante la observación el operador **no ayuda ni sugiere**. Si el participante no puede
   continuar, lo dice en voz alta y la observación termina: se registra tal como ocurrió.
8. No se usan otras apps, salvo la página de pago que abre Nomi.
9. Si algo falla (un error, una pantalla que no carga), el participante lo dice en voz alta y
   decide si reintentar o no, como lo haría en la vida real. No se le indica qué hacer.

## 4. Tarjeta de consigna (modelo)

El operador prepara una tarjeta por observación a partir de
[scenarios/asignacion_POST.csv](scenarios/asignacion_POST.csv):

```
Observación: POST-0XX            Participante: PAXX
Consigna:    <texto de la consigna>
Entrega:     <aula> – punto de encuentro: <punto>
Pago:        <tipo de tarjeta de prueba> (datos en el reverso)
```

La tarjeta **no** menciona productos concretos ni el objetivo de medición.

## 5. Tarjetas de prueba de MercadoPago (Perú)

Son tarjetas públicas del entorno de pruebas de MercadoPago. No permiten pagos reales.

| Tipo | Número | CVV | Vencimiento |
|---|---|---|---|
| Visa crédito | 4009 1753 3280 6176 | 123 | 11/30 |
| Mastercard crédito | 5031 7557 3453 0604 | 123 | 11/30 |
| Mastercard débito | 5178 7816 2220 2455 | 123 | 11/30 |

- **Titular:** `APRO` (hace que el pago de prueba se apruebe).
- **Documento:** 123456789.
- **Se paga con la tarjeta del reverso**, no con el saldo de la cuenta de MercadoPago («Dinero en
  cuenta»), aunque la página lo ofrezca. El ejecutor registra el medio real (`payment_method_mp`) y
  el resumen señala cualquier pago hecho con un medio distinto del asignado. Se observó en el
  ensayo ENS-005, pagado con el saldo de la cuenta de prueba.

## 6. Después de la observación

- El operador recoge el teléfono y agradece la participación.
- No se comentan tiempos ni resultados con el participante, ni se le pide que cuente la consigna a
  otras personas que aún vayan a participar.

## 7. Lo que se registra de cada participante

| Dato | ¿Se registra? |
|---|---|
| Código (PA01…) | Sí |
| Si conocía Nomi antes | Sí |
| Tiempos, acciones en la app y resultado de cada compra | Sí, vinculados solo al código |
| Nombre, correo, teléfono, documento o cualquier dato personal | **No** en los resultados. El consentimiento firmado se guarda aparte (ver el consentimiento) |
| Grabaciones de audio, vídeo o pantalla | **No** |
