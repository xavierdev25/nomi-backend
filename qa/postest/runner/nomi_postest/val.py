"""Batería de validación VAL (DISENO_POSTEST.md §10). Fuera de la muestra estadística.

Las pruebas automáticas usan la cuenta `val.postest` y las tiendas T2 y T3, y dejan cada estado
como lo encontraron. `val cerrar` devuelve T1 al estado esperado, despublica T2 y T3 y cancela lo
que haya quedado pendiente.
"""

from __future__ import annotations

import json
import time
from datetime import datetime
from typing import Any, Callable

from . import config, cuentas, db, entorno, estado, mp, net, sesion, tiempo
from .shell import RunnerError, read_env_file

VAL_DIR = config.RESULTS_DIR / "val"
# Resultados de las VAL que hace una persona (pago en el sandbox, comprobación visual). Solo se añaden.
PERSONA_LOG = VAL_DIR / "val_persona.jsonl"
CONFIRMED = {"PREPARANDO", "LISTO_PARA_RECOGER", "EN_CAMINO", "ENTREGADO"}
# Productos de T2 para la comprobación visual de VAL-019 a VAL-021 y un control que sí debe verse.
VISUAL_PRODUCTS = {"VAL-019": "Keke de naranja (T2)", "VAL-020": "Queque de chocolate (T2)",
                   "VAL-021": "Combo taller (T2)", "control": "Sándwich mixto (T2)"}
_results: list[dict[str, Any]] = []


# --- utilidades ---------------------------------------------------------------------------------

def _val_token() -> str:
    return cuentas.token(config.VAL_STUDENT_EMAIL)


def _aula() -> int:
    return int(db.scalar("SELECT id FROM aulas WHERE codigo = 'PRB-A101'"))


def _product(store_name: str, name: str | None = None) -> dict[str, Any]:
    where = f"p.nombre = {db.literal(name)}" if name else "p.activo AND p.disponible AND p.stock >= 1"
    rows = db.query(f"""SELECT p.id, p.nombre, p.stock, p.precio, p.store_id FROM products p JOIN stores s ON s.id = p.store_id
                        WHERE s.nombre = {db.literal(store_name)} AND p.deleted_at IS NULL AND {where} ORDER BY p.id LIMIT 1""")
    if not rows:
        raise RunnerError(f"No hay producto para VAL en {store_name} ({name or 'comprable'})")
    return rows[0]


def _stock(product_id: int) -> int:
    return int(db.scalar(f"SELECT stock FROM products WHERE id = {product_id}"))


def _order(items: list[tuple[int, int]], store_id: int, *, propina: float = 0, token: str | None = None):
    return net.backend("POST", "/orders", token=token or _val_token(), body={
        "storeId": store_id, "aulaId": _aula(), "notas": "Punto de encuentro: la puerta del salón | VAL",
        "propina": propina, "items": [{"productId": pid, "cantidad": qty} for pid, qty in items]})


def _cancel(order_id: int, token: str | None = None):
    return net.backend("PATCH", f"/orders/{order_id}/cancel", body={"motivo": "VAL"}, token=token or _val_token())


def _publish(names: list[str], published: bool) -> None:
    db.execute_script(f"""BEGIN; UPDATE products SET activo = {db.literal(published)}
        WHERE nombre IN ({", ".join(db.literal(n) for n in names)}); COMMIT;""")


def _cleanup_pending() -> None:
    user = cuentas.user(config.VAL_STUDENT_EMAIL)
    for row in db.query(f"SELECT id FROM orders WHERE user_id = {user['id']} AND status = 'PENDIENTE'"):
        _cancel(row["id"])


def _run(val_id: str, description: str, expected: str, test: Callable[[], tuple[bool, str]]) -> None:
    started = tiempo.now()
    try:
        ok, evidence = test()
        result = "PASA" if ok else "FALLA"
    except Exception as err:  # una VAL rota no detiene la batería
        result, evidence = "ERROR", f"{type(err).__name__}: {err}"
    finally:
        _cleanup_pending()
    _results.append({"id": val_id, "descripcion": description, "esperado": expected, "resultado": result,
                     "evidencia": evidence, "ejecutada_en": tiempo.iso(started)})
    print(f"{'✓' if result == 'PASA' else '✗'} {val_id} {result} — {description}: {evidence}")


def _manual(val_id: str, description: str, expected: str, how: str) -> None:
    _results.append({"id": val_id, "descripcion": description, "esperado": expected,
                     "resultado": "PENDIENTE_PERSONA", "evidencia": how, "ejecutada_en": None})
    print(f"· {val_id} PENDIENTE_PERSONA — {description}: {how}")


# --- pruebas ------------------------------------------------------------------------------------

def val_001() -> tuple[bool, str]:
    product = _product(config.T1_NAME)
    before = _stock(product["id"])
    order = _order([(product["id"], 1)], product["store_id"])
    if order.status != 201:
        return False, f"no se creó el pedido: HTTP {order.status}"
    oid = order.body["id"]
    checkout = net.backend("POST", "/payments", body={"orderId": oid}, token=_val_token())
    if checkout.status != 201:
        return False, f"no se creó el checkout: HTTP {checkout.status}"
    cancelled_at = tiempo.now()
    cancel = _cancel(oid)
    time.sleep(2)
    token = read_env_file(config.BACKEND_ENV_FILE)["MERCADOPAGO_ACCESS_TOKEN"]
    pref = net.request("GET", f"{config.MERCADOPAGO_API}/checkout/preferences/{checkout.body['externalId']}", token=token)
    closes = tiempo.from_iso((pref.body or {}).get("expiration_date_to"))
    closed = closes is not None and abs((closes - cancelled_at).total_seconds()) < 60
    ok = cancel.status == 200 and cancel.body["status"] == "CANCELADO" and _stock(product["id"]) == before and closed
    return ok, (f"pedido {oid}: {cancel.body.get('status')}; stock {before}→{_stock(product['id'])}; "
                f"checkout vence {pref.body.get('expiration_date_to') if isinstance(pref.body, dict) else '?'}")


def val_002() -> tuple[bool, str]:
    product = _product(config.T2_NAME, "Keke de naranja (T2)")
    _publish([product["nombre"]], True)
    try:
        response = _order([(product["id"], 1)], product["store_id"])
        ok = response.status in (400, 409) and _stock(product["id"]) == 0
        return ok, f"HTTP {response.status}; stock sigue en {_stock(product['id'])}"
    finally:
        _publish([product["nombre"]], False)


def val_003() -> tuple[bool, str]:
    product = _product(config.T1_NAME)
    before = _stock(product["id"])
    response = _order([(product["id"], before + 1)], product["store_id"])
    return response.status in (400, 409) and _stock(product["id"]) == before, f"{before + 1} unidades → HTTP {response.status}"


def val_004() -> tuple[bool, str]:
    product = _product(config.T1_NAME)
    before = _stock(product["id"])
    order = _order([(product["id"], 1)], product["store_id"])
    if order.status != 201:
        return False, f"no se creó el pedido: HTTP {order.status}"
    oid = order.body["id"]
    deadline = time.time() + 20 * 60
    while time.time() < deadline:
        status = db.scalar(f"SELECT status FROM orders WHERE id = {oid}")
        if status == "CANCELADO":
            reason = db.scalar(f"SELECT motivo_cancelacion FROM orders WHERE id = {oid}")
            return _stock(product["id"]) == before, f"pedido {oid} cancelado por el sistema: «{reason}»; stock {before}→{_stock(product['id'])}"
        time.sleep(15)
    return False, f"el pedido {oid} no caducó en 20 minutos"


def val_005() -> tuple[bool, str]:
    product = _product(config.T1_NAME)
    codes = [_order([(product["id"], 1)], product["store_id"]).status for _ in range(3)]
    return codes == [201, 201, 409], f"tres pedidos sin pagar → {codes}"


def val_007() -> tuple[bool, str]:
    product = _product(config.T1_NAME)
    oid = _order([(product["id"], 1)], product["store_id"]).body["id"]
    _cancel(oid)
    response = net.backend("POST", "/payments", body={"orderId": oid}, token=_val_token())
    return response.status == 409, f"pagar el pedido cancelado {oid} → HTTP {response.status}"


def val_009() -> tuple[bool, str]:
    response = net.backend("GET", "/ai/recommendations", token=cuentas.token(config.VAL_STUDENT2_EMAIL), timeout=40)
    items = (response.body or {}).get("recommendations", []) if isinstance(response.body, dict) else []
    ids = [r["productId"] for r in items]
    if not ids:
        return response.status == 200, f"sin recomendaciones ({(response.body or {}).get('generatedBy')}): nada no apto"
    rows = db.query(f"SELECT nombre, etiquetas_dieteticas AS tags FROM products WHERE id IN ({', '.join(map(str, ids))})")
    bad = [r["nombre"] for r in rows if "VEGANO" not in (r["tags"] or [])]
    return not bad, f"{len(ids)} recomendados ({response.body.get('generatedBy')}); no aptos: {bad or 'ninguno'}"


def val_010() -> tuple[bool, str]:
    entorno.stop_ai_service()
    try:
        time.sleep(2)
        response = net.backend("GET", "/ai/recommendations", token=_val_token(), timeout=40)
        body = response.body if isinstance(response.body, dict) else {}
        product = _product(config.T1_NAME)
        order = _order([(product["id"], 1)], product["store_id"])
        ok = response.status == 200 and body.get("generatedBy") == "FALLBACK" and not body.get("recommendations") and order.status == 201
        return ok, f"recomendaciones → HTTP {response.status} {body.get('generatedBy')}; pedido → HTTP {order.status}"
    finally:
        entorno.start_ai_service()


def val_011() -> tuple[bool, str]:
    t1 = _product(config.T1_NAME)
    t2 = _product(config.T2_NAME, "Sándwich mixto (T2)")
    _publish([t2["nombre"]], True)
    try:
        response = _order([(t1["id"], 1), (t2["id"], 1)], t1["store_id"])
        return response.status in (400, 409), f"pedido con productos de T1 y T2 → HTTP {response.status}"
    finally:
        _publish([t2["nombre"]], False)


def val_012() -> tuple[bool, str]:
    t3 = _product(config.T3_NAME, "Galletas de avena (T3)")
    _publish([t3["nombre"]], True)
    try:
        response = _order([(t3["id"], 1)], t3["store_id"])
        return response.status in (400, 409), f"pedido a la tienda inactiva T3 → HTTP {response.status}"
    finally:
        _publish([t3["nombre"]], False)


def val_013() -> tuple[bool, str]:
    product = _product(config.T1_NAME)
    oid = _order([(product["id"], 1)], product["store_id"]).body["id"]
    response = net.backend("PATCH", f"/orders/{oid}/status", body={"status": "PREPARANDO"}, token=cuentas.store_owner_token())
    return response.status == 409, f"tienda PENDIENTE→PREPARANDO sin pago → HTTP {response.status}"


def val_014() -> tuple[bool, str]:
    product = _product(config.T1_NAME)
    oid = _order([(product["id"], 1)], product["store_id"]).body["id"]
    own = net.backend("PATCH", f"/orders/{oid}/status", body={"status": "CANCELADO"}, token=_val_token())
    other = net.backend("GET", f"/orders/{oid}", token=cuentas.token(config.VAL_STUDENT2_EMAIL))
    return own.status == 403 and other.status == 403, (f"estudiante cambia estado → HTTP {own.status}; "
                                                       f"otro estudiante consulta el pedido → HTTP {other.status}")


def val_016() -> tuple[bool, str]:
    unsigned = net.backend("POST", "/payments/webhook?data.id=1&type=payment", body={"type": "payment", "data": {"id": "1"}})
    forged = net.backend("POST", "/payments/webhook?data.id=1&type=payment", body={"type": "payment", "data": {"id": "1"}},
                         headers={"x-signature": f"ts={int(time.time())},v1={'0' * 64}", "x-request-id": "val-016"})
    return unsigned.status == 401 and forged.status == 403, f"sin firma → {unsigned.status}; firma falsa → {forged.status}"


def val_017() -> tuple[bool, str]:
    product = _product(config.T1_NAME)
    response = _order([(product["id"], 1)], product["store_id"], propina=float(product["precio"]))
    return response.status == 400, f"propina del 100 % del subtotal → HTTP {response.status}"


def val_visibility(val_id: str, name: str, publish: bool, description: str, expected_disponible: bool | None) -> None:
    def test() -> tuple[bool, str]:
        product = _product(config.T2_NAME, name)
        if publish:
            _publish([name], True)
        try:
            token = _val_token()
            everywhere = net.backend("GET", "/products/search?disponible=true&size=100", token=token).body["content"]
            in_store = net.backend("GET", f"/products/search?storeId={product['store_id']}&disponible=true&size=50", token=token).body["content"]
            listed = any(p["id"] == product["id"] for p in everywhere + in_store)
            detail = net.backend("GET", f"/products/{product['id']}", token=token)
            detail_info = f"ficha HTTP {detail.status}, disponible={detail.body.get('disponible') if isinstance(detail.body, dict) else '?'}"
            ok = not listed and (expected_disponible is None or (detail.status == 200 and detail.body.get("disponible") == expected_disponible))
            return ok, f"en listas de la app: {'sí' if listed else 'no'}; {detail_info}"
        finally:
            if publish:
                _publish([name], False)
    _run(val_id, description + " (parte API)", "no aparece en las listas", test)


# --- comandos -----------------------------------------------------------------------------------

def ejecutar(include_expiry: bool) -> None:
    if not estado.val_stores_unpublished():
        raise RunnerError("Hay productos de T2/T3 publicados: ejecuta `val cerrar` antes")
    estado.save("VAL-antes", estado.load("esperado")["productos"])
    _results.clear()
    _run("VAL-001", "Cancelar un pedido pendiente", "CANCELADO, stock devuelto, checkout cerrado", val_001)
    _run("VAL-002", "Pedir un producto agotado", "rechazo, stock sin cambios", val_002)
    _run("VAL-003", "Pedir más unidades que el stock", "rechazo", val_003)
    if include_expiry:
        _run("VAL-004", "Caducidad de un pedido sin pagar", "cancelado por el sistema, stock devuelto", val_004)
    else:
        _results.append({"id": "VAL-004", "descripcion": "Caducidad de un pedido sin pagar", "resultado": "NO_EJECUTADA",
                         "evidencia": "espera real de 15 min: usar `val ejecutar --incluir-caducidad`", "ejecutada_en": None})
        print("· VAL-004 NO_EJECUTADA — usar --incluir-caducidad (espera real de 15 min)")
    _run("VAL-005", "Tercer pedido sin pagar", "409", val_005)
    _manual("VAL-006", "Pago rechazado y reintento aprobado", "pendiente tras el rechazo; confirmado tras el reintento",
            "`val persona VAL-006`: pagar un checkout con titular OTHE y después con APRO")
    _run("VAL-007", "Pagar un pedido cancelado", "409", val_007)
    _manual("VAL-008", "Cancelar un pedido ya pagado", "409 y el pedido queda confirmado",
            "`val persona VAL-008`: pagar con APRO; el ejecutor cancela en cuanto MercadoPago aprueba, antes de la conciliación")
    _run("VAL-009", "Recomendaciones para un usuario vegano", "ningún producto no apto", val_009)
    _run("VAL-010", "Servicio de IA caído", "FALLBACK vacío; la compra sigue siendo posible", val_010)
    _run("VAL-011", "Pedido con productos de T1 y T2", "rechazo", val_011)
    _run("VAL-012", "Pedido a una tienda inactiva", "rechazo", val_012)
    _run("VAL-013", "La tienda pasa un pedido PENDIENTE a PREPARANDO", "409", val_013)
    _run("VAL-014", "Estudiante cambia un estado o consulta un pedido ajeno", "403", val_014)
    _manual("VAL-015", "Flujo logístico completo e historial", "llega a ENTREGADO",
            "lo ejerce cada ensayo u observación con compra aprobada (logística simulada)")
    _run("VAL-016", "Webhook sin firma o con firma inválida", "401 / 403", val_016)
    _run("VAL-017", "Propina mayor al 50 % del subtotal", "rechazo", val_017)
    _manual("VAL-018", "Pago aprobado de un pedido ya cancelado", "intento de reembolso",
            "límite del sandbox: MercadoPago rechaza el reembolso con 401")
    val_visibility("VAL-019", "Keke de naranja (T2)", True, "Producto agotado", False)
    val_visibility("VAL-020", "Queque de chocolate (T2)", True, "Producto pausado", False)
    val_visibility("VAL-021", "Combo taller (T2)", False, "Producto no publicado", None)
    for val_id in ("VAL-019", "VAL-020", "VAL-021"):
        _manual(val_id, "Parte visual en la app", "no aparece en Inicio, tienda ni búsqueda", "comprobar en el iPhone")
    _save()


def _save() -> None:
    VAL_DIR.mkdir(parents=True, exist_ok=True)
    stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    path = VAL_DIR / f"val_results_{stamp}.json"
    catalog = json.loads(config.FROZEN_CATALOG_FILE.read_text(encoding="utf-8"))
    path.write_text(json.dumps({"catalogo": catalog, "resultados": _results}, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"✓ Resultados en {path.relative_to(config.POSTEST_DIR)}")


def cerrar() -> None:
    _cleanup_pending()
    estado.restore("VAL-antes")
    estado.save("esperado", estado.snapshot_t1())
    print("✓ VAL cerrada: pedidos pendientes cancelados, T1 restaurada, T2 y T3 sin publicar")


# --- VAL con persona ----------------------------------------------------------------------------

def _record_persona(val_id: str, description: str, expected: str, result: str, evidence: str, **extra: Any) -> None:
    VAL_DIR.mkdir(parents=True, exist_ok=True)
    entry = {"id": val_id, "descripcion": description, "esperado": expected, "resultado": result,
             "evidencia": evidence, "ejecutada_en": tiempo.iso(tiempo.now()), **extra}
    with open(PERSONA_LOG, "a", encoding="utf-8") as log:
        log.write(json.dumps(entry, ensure_ascii=False, default=str) + "\n")
    print(f"{'✓' if result == 'PASA' else '·' if result.startswith('NO_') else '✗'} {val_id} {result} — {evidence}")


def _require_expected_state() -> None:
    if estado.differences(estado.load("esperado")["productos"], estado.snapshot_t1()) or not estado.val_stores_unpublished():
        raise RunnerError("T1 no está en el estado esperado o hay productos de T2/T3 publicados: `val cerrar` o `jornada iniciar` antes")
    if int(db.scalar(f"""SELECT count(*) FROM orders o JOIN users u ON u.id = o.user_id
                        WHERE u.email = {db.literal(config.VAL_STUDENT_EMAIL)} AND o.status = 'PENDIENTE'""") or 0):
        raise RunnerError("La cuenta de VAL tiene pedidos pendientes: `val cerrar` antes")


def _checkout_for_person() -> tuple[int, str, str]:
    """Pedido y checkout de la cuenta de VAL. Devuelve (pedido, referencia externa, URL de pago)."""
    product = _product(config.T1_NAME)
    order = _order([(product["id"], 1)], product["store_id"])
    if order.status != 201:
        raise RunnerError(f"No se pudo crear el pedido de VAL: HTTP {order.status}")
    oid = order.body["id"]
    checkout = net.backend("POST", "/payments", body={"orderId": oid}, token=_val_token())
    if checkout.status != 201:
        raise RunnerError(f"No se pudo crear el checkout: HTTP {checkout.status}")
    reference = db.scalar(f"SELECT external_reference FROM payments WHERE order_id = {oid}")
    return oid, reference, checkout.body["paymentUrl"]


def _status(oid: int) -> str:
    return db.scalar(f"SELECT status FROM orders WHERE id = {oid}")


def _wait(condition, max_wait: int, what: str, every: float = 2.0):
    deadline = time.time() + max_wait
    announced = 0.0
    while time.time() < deadline:
        value = condition()
        if value:
            return value
        if time.time() - announced > 30:
            print(f"  … esperando {what}", flush=True)
            announced = time.time()
        time.sleep(every)
    return None


def _deliver_and_check_history(oid: int) -> None:
    """Avanza el pedido confirmado hasta ENTREGADO y registra VAL-015 con su historial."""
    sesion._simulated_logistics({"id": oid})
    history = [row["status"] for row in db.query(
        f"SELECT status FROM order_status_history WHERE order_id = {oid} ORDER BY creado_en, id")]
    expected = ["PREPARANDO", "LISTO_PARA_RECOGER", "EN_CAMINO", "ENTREGADO"]
    ok = history[-4:] == expected and _status(oid) == "ENTREGADO"
    _record_persona("VAL-015", "Flujo logístico completo e historial", "llega a ENTREGADO con todas las transiciones",
                    "PASA" if ok else "FALLA", f"pedido {oid}: historial {history}")


def persona(val_id: str, max_wait: int) -> None:
    if val_id == "VAL-018":
        _record_persona("VAL-018", "Pago aprobado de un pedido ya cancelado", "intento de reembolso",
                        "NO_VERIFICABLE_EN_SANDBOX",
                        "límite conocido del sandbox: MercadoPago rechaza el reembolso de pagos de prueba con 401 "
                        "«Unauthorized use of live credentials» (observado el 27-09-2026). No se fuerza: además, al "
                        "cancelar se cierra el checkout, así que el caso solo aparece si el pago llega mientras se cancela.")
        return
    if val_id not in ("VAL-006", "VAL-008"):
        raise RunnerError("`val persona` admite VAL-006, VAL-008 y VAL-018")
    _require_expected_state()
    oid, reference, url = _checkout_for_person()
    print(f"\n  {val_id} · pedido {oid} de la cuenta de VAL · plazo de pago 15 min")
    print(f"  Checkout: {url}")
    if val_id == "VAL-006":
        print("  1) Paga con la tarjeta de prueba y titular OTHE (será rechazado).")
        print("  2) En el MISMO checkout, reintenta con titular APRO.\n", flush=True)
        try:
            _persona_006(oid, reference, max_wait)
        finally:
            _cleanup_pending()
            estado.restore("esperado")
    else:
        print("  Paga con la tarjeta de prueba y titular APRO. El ejecutor cancelará el pedido como el estudiante")
        print("  en cuanto MercadoPago apruebe el pago, antes de la conciliación del backend.\n", flush=True)
        try:
            _persona_008(oid, reference, max_wait)
        finally:
            _cleanup_pending()
            estado.restore("esperado")


def _payments(reference: str) -> list[dict[str, Any]]:
    return [mp.parse(p) for p in mp.payments_by_reference(reference)]


def _persona_006(oid: int, reference: str, max_wait: int) -> None:
    description, expected = "Pago rechazado y reintento aprobado", "pendiente tras el rechazo; confirmado tras el reintento"
    seen: dict[str, Any] = {}

    def approved():
        payments = _payments(reference)
        rejected = next((p for p in payments if p["status"] == "rejected"), None)
        if rejected and "rejected" not in seen:
            seen["rejected"] = rejected
            seen["status_after_rejection"] = _status(oid)
            print(f"  · rechazo detectado ({rejected['status_detail']}); pedido {seen['status_after_rejection']}", flush=True)
        return next((p for p in payments if p["status"] == "approved"), None)

    payment = _wait(approved, max_wait, "el rechazo y el reintento aprobado", every=3)
    if not payment:
        _record_persona("VAL-006", description, expected, "NO_CONCLUYENTE", f"pedido {oid}: no hubo pago aprobado a tiempo; "
                        f"rechazo: {'sí' if 'rejected' in seen else 'no'}")
        return
    confirmed = _wait(lambda: _status(oid) in CONFIRMED, 180, "la confirmación del backend")
    rejected = seen.get("rejected")
    if rejected is None:
        # Sin un intento rechazado no se ejercitó lo que mide la VAL: no es un fallo de Nomi.
        _record_persona("VAL-006", description, expected, "NO_CONCLUYENTE",
                        f"pedido {oid}: MercadoPago no registró ningún intento rechazado; solo el pago aprobado "
                        f"{payment['id']} → pedido {_status(oid)}")
        if confirmed:
            _deliver_and_check_history(oid)
        return
    ok = (seen.get("status_after_rejection") == "PENDIENTE" and bool(confirmed)
          and rejected["created_at"] <= payment["created_at"])
    _record_persona("VAL-006", description, expected, "PASA" if ok else "FALLA",
                    f"pedido {oid}: rechazo {rejected['id'] if rejected else '—'} ({rejected['status_detail'] if rejected else '—'}) "
                    f"→ pedido {seen.get('status_after_rejection')}; aprobado {payment['id']} → pedido {_status(oid)}")
    if confirmed:
        _deliver_and_check_history(oid)


def _persona_008(oid: int, reference: str, max_wait: int) -> None:
    description, expected = "Cancelar un pedido ya pagado", "409 y el pedido queda confirmado"
    payment = _wait(lambda: next((p for p in _payments(reference) if p["status"] == "approved"), None),
                    max_wait, "el pago aprobado", every=2)
    if not payment:
        _record_persona("VAL-008", description, expected, "NO_CONCLUYENTE", f"pedido {oid}: no hubo pago aprobado a tiempo")
        return
    before = _status(oid)
    response = _cancel(oid)
    time.sleep(1)
    after = _status(oid)
    evidence = (f"pedido {oid}: pago {payment['id']} aprobado; estado al cancelar {before}; cancelación → HTTP {response.status}; "
                f"estado después {after}")
    if before != "PENDIENTE":
        # La conciliación confirmó el pedido antes de cancelar: no se probó el caso que pide la VAL.
        _record_persona("VAL-008", description, expected, "NO_CONCLUYENTE", evidence + " (la conciliación llegó antes; repetir)")
    else:
        _record_persona("VAL-008", description, expected, "PASA" if response.status == 409 and after in CONFIRMED else "FALLA", evidence)
    if after in CONFIRMED:
        _deliver_and_check_history(oid)


def visual(action: str) -> None:
    """Prepara (o deshace) el estado de T2 para mirar VAL-019 a VAL-021 en la app con la cuenta de VAL."""
    names = list(VISUAL_PRODUCTS.values())
    token = _val_token()
    ids = {row["nombre"]: row["id"] for row in db.query(
        f"SELECT id, nombre FROM products WHERE nombre IN ({', '.join(db.literal(n) for n in names)})")}
    favorites = [VISUAL_PRODUCTS[v] for v in ("VAL-019", "VAL-020", "VAL-021")]
    if action == "preparar":
        _require_expected_state()
        _publish([VISUAL_PRODUCTS["VAL-019"], VISUAL_PRODUCTS["VAL-020"], VISUAL_PRODUCTS["control"]], True)
        codes = [net.backend("POST", f"/favorites/products/{ids[n]}", token=token).status for n in favorites]
        print("✓ Estado visual preparado (cuenta val.postest):")
        print(f"  VAL-019 {VISUAL_PRODUCTS['VAL-019']}: publicado, stock 0")
        print(f"  VAL-020 {VISUAL_PRODUCTS['VAL-020']}: publicado, pausado (disponible = false)")
        print(f"  VAL-021 {VISUAL_PRODUCTS['VAL-021']}: sin publicar")
        print(f"  Control {VISUAL_PRODUCTS['control']}: publicado y disponible (debe verse)")
        print(f"  Favoritos añadidos para llegar a la ficha: HTTP {codes}")
        print("  Mirar en la app: Inicio, Buscar, la tienda T2 (desde el control) y Perfil › Productos favoritos.")
    else:
        for n in favorites:
            net.backend("DELETE", f"/favorites/products/{ids[n]}", token=token)
        estado.restore("esperado")
        print("✓ Estado visual deshecho: favoritos quitados, T2 y T3 sin publicar, T1 en el estado esperado")


def registrar(val_id: str, result: str, evidence: str) -> None:
    if val_id not in ("VAL-019", "VAL-020", "VAL-021") or result not in ("PASA", "FALLA") or not evidence:
        raise RunnerError("`val registrar` es para la parte visual de VAL-019 a VAL-021, con --resultado PASA|FALLA y --evidencia")
    _record_persona(val_id, "Parte visual en la app", "no aparece en Inicio, tienda ni búsqueda; la ficha no permite comprar",
                    result, evidence, parte="visual")


def final() -> None:
    """Consolida la última batería automática y los resultados con persona en VAL_FINAL.json / .md."""
    automatic = sorted(VAL_DIR.glob("val_results_*.json"))
    if not automatic:
        raise RunnerError("No hay resultados de `val ejecutar`")
    base = json.loads(automatic[-1].read_text(encoding="utf-8"))
    persona_entries = [json.loads(line) for line in PERSONA_LOG.read_text(encoding="utf-8").splitlines()] if PERSONA_LOG.exists() else []
    latest: dict[str, dict[str, Any]] = {}
    for entry in persona_entries:
        latest[entry["id"] + (":visual" if entry.get("parte") == "visual" else "")] = entry
    rows = []
    for item in base["resultados"]:
        key = item["id"] + (":visual" if item["descripcion"] == "Parte visual en la app" else "")
        if item["resultado"] in ("PENDIENTE_PERSONA", "NO_EJECUTADA") and key in latest:
            rows.append({**latest[key], "fuente": "persona"})
        else:
            rows.append({**item, "fuente": automatic[-1].name})
    counts: dict[str, int] = {}
    for row in rows:
        counts[row["resultado"]] = counts.get(row["resultado"], 0) + 1
    VAL_DIR.mkdir(parents=True, exist_ok=True)
    (VAL_DIR / "VAL_FINAL.json").write_text(json.dumps({"catalogo": base["catalogo"], "resumen": counts, "resultados": rows},
                                                       indent=2, ensure_ascii=False, default=str), encoding="utf-8")
    lines = ["# Batería VAL — resultado final", "", f"Catálogo: {base['catalogo']['version']} · automática: `{automatic[-1].name}`"
             f" · con persona: `{PERSONA_LOG.name}`", "", *[f"- {k}: {v}" for k, v in sorted(counts.items())], "",
             "| VAL | Parte | Resultado | Evidencia |", "|---|---|---|---|"]
    lines += [f"| {r['id']} | {'visual' if r.get('parte') == 'visual' or r['descripcion'] == 'Parte visual en la app' else r['descripcion']} "
              f"| {r['resultado']} | {str(r['evidencia']).replace('|', '/')} |" for r in rows]
    (VAL_DIR / "VAL_FINAL.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"✓ VAL final: {counts} → results/val/VAL_FINAL.md")
