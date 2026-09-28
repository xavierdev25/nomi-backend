# Batería VAL — resultado final

Catálogo: C0 · automática: `val_results_20260927-214240.json` · con persona: `val_persona.jsonl`

- FALLA: 1
- NO_VERIFICABLE_EN_SANDBOX: 1
- PASA: 22

| VAL | Parte | Resultado | Evidencia |
|---|---|---|---|
| VAL-001 | Cancelar un pedido pendiente | PASA | pedido 11: CANCELADO; stock 25→25; checkout vence 2026-09-27T21:26:49.855-05:00 |
| VAL-002 | Pedir un producto agotado | PASA | HTTP 400; stock sigue en 0 |
| VAL-003 | Pedir más unidades que el stock | PASA | 26 unidades → HTTP 400 |
| VAL-004 | Caducidad de un pedido sin pagar | PASA | pedido 12 cancelado por el sistema: «El pago no se completó a tiempo»; stock 25→25 |
| VAL-005 | Tercer pedido sin pagar | PASA | tres pedidos sin pagar → [201, 201, 409] |
| VAL-006 | Pago rechazado y reintento aprobado | PASA | pedido 23: rechazo 180234818175 (cc_rejected_other_reason) → pedido PENDIENTE; aprobado 181232752616 → pedido PREPARANDO |
| VAL-007 | Pagar un pedido cancelado | PASA | pagar el pedido cancelado 15 → HTTP 409 |
| VAL-008 | Cancelar un pedido ya pagado | PASA | pedido 22: pago 181229589988 aprobado; estado al cancelar PENDIENTE; cancelación → HTTP 409; estado después PREPARANDO |
| VAL-009 | Recomendaciones para un usuario vegano | PASA | 5 recomendados (phi3); no aptos: ninguno |
| VAL-010 | Servicio de IA caído | PASA | recomendaciones → HTTP 200 FALLBACK; pedido → HTTP 201 |
| VAL-011 | Pedido con productos de T1 y T2 | PASA | pedido con productos de T1 y T2 → HTTP 400 |
| VAL-012 | Pedido a una tienda inactiva | PASA | pedido a la tienda inactiva T3 → HTTP 400 |
| VAL-013 | La tienda pasa un pedido PENDIENTE a PREPARANDO | PASA | tienda PENDIENTE→PREPARANDO sin pago → HTTP 409 |
| VAL-014 | Estudiante cambia un estado o consulta un pedido ajeno | PASA | estudiante cambia estado → HTTP 403; otro estudiante consulta el pedido → HTTP 403 |
| VAL-015 | Flujo logístico completo e historial | PASA | pedido 23: historial ['PREPARANDO', 'LISTO_PARA_RECOGER', 'EN_CAMINO', 'ENTREGADO'] |
| VAL-016 | Webhook sin firma o con firma inválida | PASA | sin firma → 401; firma falsa → 403 |
| VAL-017 | Propina mayor al 50 % del subtotal | PASA | propina del 100 % del subtotal → HTTP 400 |
| VAL-018 | Pago aprobado de un pedido ya cancelado | NO_VERIFICABLE_EN_SANDBOX | límite conocido del sandbox: MercadoPago rechaza el reembolso de pagos de prueba con 401 «Unauthorized use of live credentials» (observado el 27-09-2026). No se fuerza: además, al cancelar se cierra el checkout, así que el caso solo aparece si el pago llega mientras se cancela. |
| VAL-019 | Producto agotado (parte API) | PASA | en listas de la app: no; ficha HTTP 200, disponible=False |
| VAL-020 | Producto pausado (parte API) | PASA | en listas de la app: no; ficha HTTP 200, disponible=False |
| VAL-021 | Producto no publicado (parte API) | PASA | en listas de la app: no; ficha HTTP 200, disponible=True |
| VAL-019 | visual | PASA | Simulador, cuenta val.postest. Keke de naranja (T2), publicado con stock 0: no aparece en Inicio, en Buscar («T2») ni en el menú de la tienda T2, donde sí aparece el control disponible. Su ficha, desde Favoritos, muestra «Agotado» y «Agregar al carrito» deshabilitado. Capturas: results/val/evidencia/20260927-visibilidad/01–04 y 06 |
| VAL-020 | visual | PASA | Simulador, cuenta val.postest. Queque de chocolate (T2), publicado y pausado (disponible = false, stock 15): no aparece en Inicio, Buscar ni en el menú de la tienda T2. Su ficha, desde Favoritos, muestra «Agotado» (la app usa la misma etiqueta para pausado) y «Agregar al carrito» deshabilitado. Capturas: results/val/evidencia/20260927-visibilidad/01–04 y 07 |
| VAL-021 | visual | FALLA | Simulador, cuenta val.postest. Combo taller (T2), sin publicar: no aparece en Inicio, Buscar ni en el menú de la tienda T2, PERO sí aparece en Perfil › Productos favoritos y su ficha tiene «Agregar al carrito» activo; el backend rechaza el pedido (HTTP 400 «Producto no disponible»). Solo es alcanzable si la cuenta ya lo tenía en favoritos. Capturas: results/val/evidencia/20260927-visibilidad/04 y 05 |
