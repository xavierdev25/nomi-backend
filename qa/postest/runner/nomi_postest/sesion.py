"""Una observación de compra: preparar → iniciar → cerrar (DISENO_POSTEST.md §8 y §11–§13).

El mismo motor sirve para la muestra oficial (`POST-0XX`, bloqueada sin autorización) y para los
ensayos técnicos (`ENS-0XX`, que reutilizan la consigna de un POST y nunca se exportan a O2).
El ejecutor solo observa: la compra la hace una persona en la app.
"""

from __future__ import annotations

import csv
import json
import re
import time
from datetime import timedelta, timezone
from typing import Any

from . import bloqueo, config, cuentas, db, entorno, estado, ia, logs, medicion, mp, net, seed, tiempo
from .shell import RunnerError

SESSIONS_DIR = config.RUNTIME_DIR / "sesiones"
RECORDS_DIR = config.RUNTIME_DIR / "registros"
AULA_CODES = {"PRB-A101": "A-101", "PRB-A102": "A-102", "PRB-B201": "B-201", "PRB-B202": "B-202",
              "PRB-LAB1": "LAB-1", "PRB-TAL1": "TAL-1"}
MEETING_POINTS = {"la puerta del salón": "puerta", "el pasillo": "pasillo", "la entrada del edificio": "entrada",
                  "las escaleras": "escaleras", "recepción": "recepcion"}
CONFIRMED_STATUSES = {"PREPARANDO", "LISTO_PARA_RECOGER", "EN_CAMINO", "ENTREGADO"}
AI_CACHE_SECONDS = 300
# `payment_method_id` de MercadoPago para cada tarjeta de la asignación. Si el participante paga con
# otro medio (por ejemplo, el saldo de la cuenta de prueba, `account_money`), el registro lo señala.
EXPECTED_PAYMENT_METHOD = {"Visa crédito": "visa", "Mastercard crédito": "master", "Mastercard débito": "debmaster"}
APP_FETCH_GRACE_SECONDS = 60


# --- utilidades ---------------------------------------------------------------------------------

def assignment(scenario_id: str) -> dict[str, str]:
    with open(config.SCENARIOS_DIR / "asignacion_POST.csv", encoding="utf-8") as f:
        for row in csv.DictReader(f):
            if row["scenario_id"] == scenario_id:
                return row
    raise RunnerError(f"{scenario_id} no está en scenarios/asignacion_POST.csv")


def _session_path(obs_id: str):
    return SESSIONS_DIR / f"{obs_id}.json"


def _load(obs_id: str) -> dict[str, Any]:
    path = _session_path(obs_id)
    if not path.exists():
        raise RunnerError(f"{obs_id} no está preparado")
    return json.loads(path.read_text(encoding="utf-8"))


def _save(session: dict[str, Any]) -> None:
    SESSIONS_DIR.mkdir(parents=True, exist_ok=True)
    _session_path(session["id"]).write_text(json.dumps(session, indent=2, ensure_ascii=False, default=str),
                                            encoding="utf-8")


def _check_mode(mode: str, obs_id: str) -> None:
    if mode == "oficial":
        if not re.fullmatch(r"POST-\d{3}", obs_id):
            raise RunnerError("Las observaciones oficiales se llaman POST-001 … POST-030")
        bloqueo.official_authorization()
    elif not re.fullmatch(r"ENS-\d{3}", obs_id):
        raise RunnerError("Los ensayos técnicos se llaman ENS-001, ENS-002…")


def account_for(mode: str, obs_id: str, row: dict[str, str], attempt: int) -> str:
    """Cuenta de la observación: nueva para cada POST, cada intento repetido y cada ensayo.

    El backend envía a la IA los productos que la cuenta ya compró, así que una cuenta reutilizada
    haría que un participante viera recomendaciones influidas por la compra de otro. Los intentos
    repetidos y los ensayos usan cuentas propias con el mismo perfil del escenario.
    """
    base = row["test_user"].split(".")[0] if mode == "oficial" else "ens" + obs_id.split("-")[1]  # est07 / ens004
    local = base if attempt == 1 else f"{base}i{attempt}"
    return f"{local}{config.SYNTHETIC_EMAIL_SUFFIX}"


def _prior_orders(user_id: int) -> int:
    return int(db.scalar(f"SELECT count(*) FROM orders WHERE user_id = {user_id}") or 0)


def _record_path(session: dict[str, Any], suffix: str = ""):
    return RECORDS_DIR / session["modo"] / f"{session['id']}__intento{session['attempt']}{suffix}.json"


def _user_extras(user_id: int) -> tuple[int, int]:
    favorites = db.scalar(f"""SELECT (SELECT count(*) FROM favorite_products WHERE user_id = {user_id})
                               + (SELECT count(*) FROM favorite_stores WHERE user_id = {user_id}) AS n""")
    dislikes = db.scalar(f"SELECT count(*) FROM ai_recommendation_feedback WHERE user_id = {user_id} AND liked = false")
    return int(favorites or 0), int(dislikes or 0)


# --- preparar -----------------------------------------------------------------------------------

def preparar(mode: str, obs_id: str, *, base: str | None, participant: str | None, device: str,
             prior_experience: bool | None, attempt: int) -> dict[str, Any]:
    _check_mode(mode, obs_id)
    base = obs_id if mode == "oficial" else (base or "POST-001")
    row = assignment(base)
    if _session_path(obs_id).exists() and _load(obs_id).get("estado") == "INICIADO":
        raise RunnerError(f"{obs_id} está en curso; ciérralo antes de volver a prepararlo")
    if _record_path({"modo": mode, "id": obs_id, "attempt": attempt}).exists():
        raise RunnerError(f"{obs_id} intento {attempt} ya tiene registro; un intento nuevo lleva --intento {attempt + 1}"
                          + (" y un ensayo nuevo, otro identificador" if mode == "ensayo" else ""))

    email = account_for(mode, obs_id, row, attempt)
    created = seed.ensure_account(seed.account_from_row(row, email))
    user = cuentas.user(email)
    frozen = seed.frozen_catalog()
    if mode == "oficial" and frozen["version"] != bloqueo.official_authorization()["catalogo"]:
        raise RunnerError("El catálogo cargado no es el autorizado")

    problems: list[str] = []
    expected = estado.load("esperado")["productos"]
    diffs = estado.differences(expected, estado.snapshot_t1())
    if diffs:
        problems.append("estado distinto del esperado: " + "; ".join(diffs[:5]))
    prior = _prior_orders(user["id"])
    if prior:
        problems.append(f"la cuenta ya tiene {prior} pedidos (cada observación usa una cuenta sin historial)")
    favorites, dislikes = _user_extras(user["id"])
    if favorites or dislikes:
        problems.append(f"la cuenta tiene {favorites} favoritos y {dislikes} «no me gusta»")
    if not estado.val_stores_unpublished():
        problems.append("hay productos de T2/T3 publicados")
    if not entorno.backend_database_ok():
        problems.append("el backend no está conectado a la base del postest")

    session: dict[str, Any] = {
        "id": obs_id, "modo": mode, "escenario_base": base, "attempt": attempt,
        "participant_id": participant or ("PT-TEC" if mode == "ensayo" else row["participant_id"]),
        "participant_prior_nomi_experience": prior_experience, "device_type": device,
        "asignacion": row, "email": email, "user_id": user["id"],
        "catalog_version": frozen["version"], "catalog_hash": frozen["hash_catalogo"],
        "cuenta_creada_al_preparar": created,
        "preparado_en": tiempo.iso(tiempo.now()), "ventanas_ejecutor": [],
    }
    if problems:
        session["estado"] = "CERRADO"
        _save(session)
        # Un NO_INICIADA no ocupa el intento: se corrige y se prepara de nuevo, y queda su propio archivo.
        stamp = tiempo.now().strftime("%Y%m%dT%H%M%S")
        _write_record(session, _not_started_record(session, "; ".join(problems)), suffix=f"__no_iniciada_{stamp}")
        raise RunnerError(f"{obs_id} NO_INICIADA: " + "; ".join(problems))

    session["ia_cache_vaciada"] = ia.flush_cache()
    session["mp_clock_offset_seconds"] = mp.clock_offset_seconds()
    estado.save(f"{obs_id}-antes", expected)
    session["estado"] = "PREPARADO"
    _save(session)
    return session


# --- iniciar ------------------------------------------------------------------------------------

def iniciar(mode: str, obs_id: str, *, now: bool) -> dict[str, Any]:
    _check_mode(mode, obs_id)
    session = _load(obs_id)
    if session["estado"] != "PREPARADO":
        raise RunnerError(f"{obs_id} está en estado {session['estado']}")
    if cuentas.user(session["email"])["id"] != session["user_id"]:
        raise RunnerError(f"La cuenta de {obs_id} no es la que se preparó (¿se recreó la base?): vuelve a prepararlo")
    row = session["asignacion"]
    print(f"\n  {obs_id}  ·  participante {session['participant_id']}  ·  cuenta {session['email']}")
    print(f"  Consigna: {row['consigna']}")
    print(f"  Entrega:  {row['aula_name']} – {row['meeting_point_label']}")
    print(f"  Pago:     {row['payment_card_type']} (titular {row['payment_card_holder']})\n")
    if not now:
        input("  Pulsa ENTER al decir «empieza»… ")
    started = tiempo.now()
    session["user_started_at"] = tiempo.iso(started)

    # D3 en el instante del inicio. La consulta del ejecutor queda en el log: se excluye después.
    before = tiempo.now()
    session["d3"] = medicion.accessibility(session["email"])
    session["ventanas_ejecutor"].append([tiempo.iso(before), tiempo.iso(tiempo.now())])

    # Recomendaciones que vio el participante: la petición de la app al abrir Inicio, tras el login.
    app_request = logs.last(logs.backend_requests(tiempo.from_iso(session["preparado_en"]), started),
                            r"^/api/ai/recommendations$")
    if app_request and (started - app_request.at).total_seconds() < AI_CACHE_SECONDS - 10:
        captured = ia.capture(session["email"])
        window = captured.pop("window")
        session["ventanas_ejecutor"].append([tiempo.iso(window[0]), tiempo.iso(window[1])])
        # La latencia es la que vivió el participante (su petición, según el backend), no la de la
        # repetición, que sale de la caché.
        session["ia"] = {"requested_at": tiempo.iso(app_request.at), "latency_ms": app_request.millis, **captured}
    else:
        session["ia"] = None
    session["estado"] = "INICIADO"
    _save(session)
    print(f"  ✓ Inicio registrado: {session['user_started_at']}")
    return session


# --- cerrar -------------------------------------------------------------------------------------

def cerrar(mode: str, obs_id: str, *, declared_failure: bool, result_override: str | None,
           notes: str | None, max_wait: int) -> dict[str, Any]:
    _check_mode(mode, obs_id)
    session = _load(obs_id)
    if session["estado"] != "INICIADO":
        raise RunnerError(f"{obs_id} está en estado {session['estado']}")
    started = tiempo.from_iso(session["user_started_at"])
    user_id = session["user_id"]

    order, observation_end = _wait_for_outcome(user_id, started, declared_failure, max_wait)
    payment = mp_payments = approved = None
    if order:
        rows = db.query(f"SELECT * FROM payments WHERE order_id = {order['id']}")
        payment = rows[0] if rows else None
        if payment:
            reference = payment.get("external_reference") or str(order["id"])
            mp_payments = [mp.parse(p) for p in mp.payments_by_reference(reference)]
            approved = next((p for p in mp_payments if p["status"] == "approved"), None)

    confirmed_at = None
    if order:
        confirmed_at = tiempo.from_db(db.scalar(f"""SELECT min(creado_en) FROM order_status_history
            WHERE order_id = {order['id']} AND status = 'PREPARANDO'"""))

    app_seen = _wait_for_app_fetch(session, order, confirmed_at) if order and confirmed_at else None
    logistics = _simulated_logistics(order) if order and confirmed_at else {}
    after = estado.snapshot_t1()
    estado.save(f"{obs_id}-despues", after)
    estado.save("esperado", after)

    record = _build_record(session, order, payment, mp_payments, approved, confirmed_at, app_seen, logistics,
                           after, observation_end, declared_failure, result_override, notes)
    session["estado"] = "CERRADO"
    session["cerrado_en"] = tiempo.iso(tiempo.now())
    _save(session)
    _write_record(session, record)
    return record


def _wait_for_outcome(user_id: int, started, declared_failure: bool, max_wait: int):
    """Espera a que el pedido se confirme, se cancele o venza. Devuelve (pedido, fin de la observación)."""
    # La base guarda UTC sin zona.
    since = started.astimezone(timezone.utc).strftime("%Y-%m-%d %H:%M:%S.%f")
    deadline = tiempo.now() + timedelta(seconds=max_wait)
    announced = 0.0
    while True:
        try:
            rows = db.query(f"""SELECT * FROM orders WHERE user_id = {user_id}
                                AND creado_en >= {db.literal(since)}::timestamp ORDER BY creado_en LIMIT 1""")
        except RunnerError as err:
            # Un corte pasajero de Docker no debe perder la observación: se reintenta hasta el límite.
            if tiempo.now() > deadline:
                raise
            print(f"  ! la base no respondió ({err}); reintento en 10 s", flush=True)
            time.sleep(10)
            continue
        order = rows[0] if rows else None
        now = tiempo.now()
        if declared_failure:
            return order, now
        if order and order["status"] in CONFIRMED_STATUSES | {"CANCELADO"}:
            return order, now
        expires = tiempo.from_db(order["pago_expira_en"]) if order else None
        if (expires and now > expires + timedelta(seconds=120)) or now > deadline:
            return order, now
        if time.time() - announced > 30:
            print(f"  … esperando la confirmación ({'pedido ' + str(order['id']) + ' ' + order['status'] if order else 'sin pedido todavía'})")
            announced = time.time()
        time.sleep(3)


def _wait_for_app_fetch(session: dict[str, Any], order: dict[str, Any], confirmed_at):
    """Primera consulta de la app a su pedido después de la confirmación (`app_confirmation_seen_at`).

    La app consulta el pedido cada 5 s y el ejecutor detecta la confirmación antes, así que hay que
    esperar esa consulta. Se espera antes de la logística simulada para que la app vea el pedido
    confirmado y no ya entregado. Si la app está en segundo plano (el participante sigue en el
    navegador), la consulta no llega y el campo queda vacío.
    """
    windows = [(tiempo.from_iso(a), tiempo.from_iso(b)) for a, b in session["ventanas_ejecutor"]]
    pattern = rf"^/api/(orders|payments/order)/{order['id']}$"
    deadline = time.time() + APP_FETCH_GRACE_SECONDS
    while True:
        seen = logs.first(logs.backend_requests(confirmed_at), pattern, windows)
        if seen or time.time() > deadline:
            return seen.at if seen else None
        time.sleep(2)


def _simulated_logistics(order: dict[str, Any]) -> dict[str, Any]:
    """Avanza el pedido como comercio hasta ENTREGADO (métrica secundaria, logística simulada)."""
    token = cuentas.store_owner_token()
    result: dict[str, Any] = {}
    status = db.scalar(f"SELECT status FROM orders WHERE id = {order['id']}")
    for target in ("LISTO_PARA_RECOGER", "EN_CAMINO", "ENTREGADO"):
        if status == target:
            continue
        response = net.backend("PATCH", f"/orders/{order['id']}/status", body={"status": target}, token=token)
        if response.status != 200:
            result["error"] = f"{target}: HTTP {response.status}"
            break
        status = target
    history = db.query(f"SELECT status, min(creado_en) AS at FROM order_status_history WHERE order_id = {order['id']} GROUP BY status")
    times = {row["status"]: tiempo.from_db(row["at"]) for row in history}
    result.update({"ready_at": times.get("LISTO_PARA_RECOGER"), "on_the_way_at": times.get("EN_CAMINO"),
                   "delivered_at": times.get("ENTREGADO")})
    return result


# --- registro -----------------------------------------------------------------------------------

def _base_record(session: dict[str, Any]) -> dict[str, Any]:
    row = session["asignacion"]
    user = cuentas.user(session["email"])
    return {
        "scenario_id": session["id"], "attempt": session["attempt"], "jornada": row["jornada"],
        "time_slot": row["time_slot"], "participant_id": session["participant_id"],
        "participant_prior_nomi_experience": session["participant_prior_nomi_experience"],
        "device_type": session["device_type"], "test_user_id": user["id"], "test_user_email": session["email"],
        "restrictions": medicion.restrictions_of(user), "budget_range": user["budget_range"],
        "catalog_version": session["catalog_version"], "consigna": row["consigna"],
        "ai_instructed": row["ai_instructed"] == "si", "payment_card_type": row["payment_card_type"],
        "modo": session["modo"], "escenario_base": session["escenario_base"],
    }


def _not_started_record(session: dict[str, Any], reason: str) -> dict[str, Any]:
    record = _base_record(session)
    record.update({"order_success": 0, "execution_result": "NO_INICIADA", "error_code": "PRECONDICION",
                   "error_description": reason})
    return record


def _build_record(session, order, payment, mp_payments, approved, confirmed_at, app_seen, logistics, after,
                  observation_end, declared_failure, result_override, notes) -> dict[str, Any]:
    record = _base_record(session)
    started = tiempo.from_iso(session["user_started_at"])
    offset = session.get("mp_clock_offset_seconds")
    d3 = session["d3"]
    record.update({k: d3[k] for k in (
        "store_id", "total_products_offered", "accessible_products", "visible_products", "accessible_sql_check_ok",
        "accessibility_rate", "purchasable_products", "out_of_stock_products", "hidden_products",
        "unpublished_products", "compatible_products", "recommendable_products", "discarded_by_restrictions")})
    record["catalog_api_status"] = d3.get("catalog_api_status")  # 200 si D3 se midió con la API de la app
    record["user_started_at"] = tiempo.iso(started)
    record["mp_clock_offset_seconds"] = offset

    # Productos del pedido y stock
    items = []
    if order:
        items = db.query(f"""SELECT product_id, product_nombre AS nombre, cantidad, product_precio AS precio, subtotal
                             FROM order_items WHERE order_id = {order['id']} ORDER BY id""")
    before = {row["id"]: row for row in estado.load(f"{session['id']}-antes")["productos"]}
    after_by_id = {row["id"]: row for row in after}
    confirmed = confirmed_at is not None
    ordered = {item["product_id"]: item["cantidad"] for item in items}
    stock_ok = all(after_by_id.get(pid, {}).get("stock") ==
                   row["stock"] - (ordered.get(pid, 0) if confirmed else 0)
                   for pid, row in before.items())
    tags = medicion.tags_by_product(list(ordered))
    record.update({
        "order_id": order["id"] if order else None,
        "products": items,
        "quantities_total": sum(ordered.values()) if items else None,
        "initial_stock": {str(pid): before[pid]["stock"] for pid in ordered if pid in before},
        "stock_check_ok": stock_ok,
        "compatible_with_user_restrictions": (all(medicion.compatible(tags.get(pid), record["restrictions"]) for pid in ordered)
                                              if items else None),
    })

    # Entrega elegida
    if order:
        aula = db.query(f"SELECT codigo, nombre FROM aulas WHERE id = {order['aula_id']}")
        notes_text = (order.get("notas") or "").lower()
        label = notes_text.split("punto de encuentro:", 1)[-1].split("|", 1)[0].strip() if "punto de encuentro:" in notes_text else ""
        record.update({"aula_id": AULA_CODES.get(aula[0]["codigo"], aula[0]["codigo"]) if aula else None,
                       "aula_name": aula[0]["nombre"] if aula else None,
                       "meeting_point": MEETING_POINTS.get(label, label or None)})
    else:
        record.update({"aula_id": None, "aula_name": None, "meeting_point": None})

    # IA
    ai = session.get("ia")
    window_list = [(tiempo.from_iso(a), tiempo.from_iso(b)) for a, b in session["ventanas_ejecutor"]]
    requests = logs.backend_requests(tiempo.from_iso(session["preparado_en"]), observation_end)
    ai_request = ai["requested_at"] if ai else None
    if not ai_request:
        seen = logs.first(requests, r"^/api/ai/recommendations$", window_list)
        ai_request = tiempo.iso(seen.at) if seen else None
    recommended_ids = {r["product_id"] for r in (ai or {}).get("recommendations", [])}
    record.update({
        "ai_requested": ai_request is not None, "ai_requested_at": ai_request,
        "ai_result": ({"origen": ai.get("generated_by"), "latencia_ms": ai.get("latency_ms"),
                       "recomendaciones": ai.get("recommendations")} if ai else None),
        "ai_generated_by": (ai or {}).get("generated_by"),
        "ai_capture_verified": bool(ai and ai.get("cache_hit")),
        "ai_used_for_selection": bool(recommended_ids & set(ordered)) if ai else None,
    })

    # Tiempos
    after_start = [r for r in requests if r.at >= started]
    catalog = logs.first(after_start, r"^/api/products/search", window_list)
    order_created = tiempo.from_db(order["creado_en"]) if order else None
    payment_started = tiempo.from_db(payment["creado_en"]) if payment else None
    mp_created, corrected_a = mp.corrected(approved["created_at"] if approved else None, offset)
    mp_approved, corrected_b = mp.corrected(approved["approved_at"] if approved else None, offset)
    success = 1 if (mp_approved and confirmed_at) else 0
    record.update({
        "first_catalog_request_at": tiempo.iso(catalog.at) if catalog else None,
        "order_created_at": tiempo.iso(order_created), "payment_started_at": tiempo.iso(payment_started),
        "mercadopago_payment_created_at": tiempo.iso(mp_created), "mercadopago_approved_at": tiempo.iso(mp_approved),
        "purchase_completed_at": tiempo.iso(mp_approved) if success else None,
        "mp_clock_corrected": corrected_a or corrected_b,
        "mp_payment_id": approved["id"] if approved else None,
        "payment_amount": float(payment["amount"]) if payment else None,
        "rejected_payment_attempts": sum(1 for p in (mp_payments or []) if p["status"] == "rejected") if payment else None,
        "backend_payment_confirmed_at": tiempo.iso(confirmed_at), "app_confirmation_seen_at": tiempo.iso(app_seen),
        "ready_at": tiempo.iso(logistics.get("ready_at")), "on_the_way_at": tiempo.iso(logistics.get("on_the_way_at")),
        "delivered_at": tiempo.iso(logistics.get("delivered_at")),
        "human_interaction_seconds": tiempo.seconds(started, mp_created),
        "mercadopago_processing_seconds": tiempo.seconds(mp_created, mp_approved),
        "purchase_time_seconds": tiempo.seconds(started, mp_approved) if success else None,
        "backend_reconciliation_seconds": tiempo.seconds(mp_approved, confirmed_at),
        "technical_total_seconds": tiempo.seconds(started, confirmed_at),
        "time_to_failure_seconds": None if success else tiempo.seconds(started, observation_end),
        "order_success": success,
        "logistics_success": bool(logistics.get("delivered_at")),
        "final_status": db.scalar(f"SELECT status FROM orders WHERE id = {order['id']}") if order else None,
    })
    record["purchase_time_minutes"] = round(record["purchase_time_seconds"] / 60, 2) if success else None
    record["payment_method_mp"] = approved["payment_method"] if approved else None
    record["payment_method_expected"] = EXPECTED_PAYMENT_METHOD.get(record["payment_card_type"])
    record["payment_method_ok"] = (record["payment_method_mp"] == record["payment_method_expected"]) if approved else None

    code, description = _failure(order, payment, mp_payments, approved, confirmed_at, declared_failure)
    record["execution_result"] = result_override or ("EXITOSA" if success else "FALLIDA_SISTEMA")
    record["error_code"], record["error_description"] = (None, None) if success else (code, description)
    if logistics.get("error"):
        record["logistics_error"] = logistics["error"]
    record["notes"] = notes
    return record


def _failure(order, payment, mp_payments, approved, confirmed_at, declared_failure) -> tuple[str | None, str | None]:
    if declared_failure:
        return "NO_COMPLETADA", "El participante declaró que no pudo completar la compra"
    if not order:
        return "SIN_PEDIDO", "No se creó ningún pedido durante la observación"
    if order["status"] == "CANCELADO":
        return "PEDIDO_CANCELADO", order.get("motivo_cancelacion") or "El pedido se canceló"
    if not payment:
        return "SIN_CHECKOUT", "El pedido no llegó a tener checkout de MercadoPago"
    if not approved:
        rejected = [p for p in (mp_payments or []) if p["status"] == "rejected"]
        if rejected:
            return "PAGO_RECHAZADO", f"{len(rejected)} intento(s) rechazado(s): {rejected[-1]['status_detail']}"
        return "SIN_PAGO_APROBADO", "No hubo un pago aprobado dentro de la ventana de observación"
    if not confirmed_at:
        return "SIN_CONFIRMACION_BACKEND", "MercadoPago aprobó el pago, pero el backend no confirmó el pedido a tiempo"
    return None, None


def _write_record(session: dict[str, Any], record: dict[str, Any], suffix: str = "") -> None:
    path = _record_path(session, suffix)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(record, indent=2, ensure_ascii=False, default=str), encoding="utf-8")


# --- reclasificar -------------------------------------------------------------------------------

RECLASSIFIABLE = {"FALLIDA_SISTEMA": {"INVALIDA_PROTOCOLO", "INVALIDA_ENTORNO"}}
RECLASSIFICATIONS_LOG = config.RUNTIME_DIR / "reclasificaciones.jsonl"


def reclasificar(mode: str, obs_id: str, *, attempt: int, result: str | None, reason: str | None) -> dict[str, Any]:
    """Corrige la clasificación de un registro cerrado cuando la causa fue ajena a Nomi (§13).

    Solo de FALLIDA_SISTEMA a INVALIDA_PROTOCOLO o INVALIDA_ENTORNO, con motivo, y nunca hacia
    EXITOSA: así no se puede convertir un fallo en éxito. El registro conserva la clasificación
    original y el cambio queda también en `runtime/reclasificaciones.jsonl`.
    """
    _check_mode(mode, obs_id)
    if not result or not reason:
        raise RunnerError("La reclasificación exige --resultado y --motivo")
    path = _record_path({"modo": mode, "id": obs_id, "attempt": attempt})
    if not path.exists():
        raise RunnerError(f"No hay registro de {obs_id} intento {attempt}")
    record = json.loads(path.read_text(encoding="utf-8"))
    current = record["execution_result"]
    if result not in RECLASSIFIABLE.get(current, set()):
        raise RunnerError(f"No se reclasifica de {current} a {result}: solo FALLIDA_SISTEMA → INVALIDA_PROTOCOLO o INVALIDA_ENTORNO")
    change = {"desde": current, "a": result, "motivo": reason, "en": tiempo.iso(tiempo.now())}
    record["execution_result"] = result
    record["reclasificacion"] = change
    note = f"Reclasificado de {current} a {result}: {reason}"
    record["notes"] = f"{record['notes']} | {note}" if record.get("notes") else note
    path.write_text(json.dumps(record, indent=2, ensure_ascii=False, default=str), encoding="utf-8")
    RECLASSIFICATIONS_LOG.parent.mkdir(parents=True, exist_ok=True)
    with open(RECLASSIFICATIONS_LOG, "a", encoding="utf-8") as log:
        log.write(json.dumps({"registro": f"{obs_id}__intento{attempt}", "modo": mode, **change}, ensure_ascii=False) + "\n")
    return record
