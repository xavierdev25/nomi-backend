"""Matriz para el análisis estadístico: un registro válido por POST (DISENO_POSTEST.md §12–§14).

`postest_O2.csv` guarda todo (intentos, `NO_INICIADA`, `INVALIDA_*`) como evidencia. La matriz de
análisis se queda solo con los registros que cuentan para N: `EXITOSA` y `FALLIDA_SISTEMA`, uno por
POST. Antes de escribirla se valida; si algo no cuadra, no se genera la matriz y solo queda el
informe con los errores, para que nunca circule una matriz incompleta como si fuera válida.
"""

from __future__ import annotations

import csv
import json
import re
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any

from . import tiempo

VALID_RESULTS = ("EXITOSA", "FALLIDA_SISTEMA")
EXPECTED_SCENARIOS = tuple(f"POST-{n:03d}" for n in range(1, 31))
D1_FIELDS = ("user_started_at", "mercadopago_approved_at", "purchase_time_seconds", "purchase_time_minutes")
D3_FIELDS = ("total_products_offered", "accessible_products", "visible_products", "accessibility_rate")
_OFFICIAL_ACCOUNT = re.compile(r"^est\d{2}(i\d+)?\.postest@nomi\.test$")
_PARTICIPANT = re.compile(r"^PA\d{2}$")
# Tolerancias para comparar con los valores recalculados: los segundos se guardan con 3 decimales
# y los porcentajes con 2.
_SECONDS_TOLERANCE = 0.002
_RATE_TOLERANCE = 0.005


def build(records: list[dict[str, Any]], *, catalog_version: str,
          expected_scenarios: tuple[str, ...] = EXPECTED_SCENARIOS) -> tuple[list[dict[str, Any]], dict[str, Any]]:
    """Selecciona y valida la matriz. Devuelve (filas, informe); las filas solo sirven si el informe es válido.

    D3 se exige en todas las filas: con C0 y con C1 el ejecutor lo mide con la API que usa la app,
    así que un D3 vacío significa que la medición falló, no que no correspondiera medirlo.
    """
    errors: list[str] = []

    foreign = [_label(r) for r in records
               if r.get("modo") != "oficial" or not re.fullmatch(r"POST-\d{3}", str(r.get("scenario_id")))
               or not _OFFICIAL_ACCOUNT.match(str(r.get("test_user_email") or ""))]
    if foreign:
        errors.append(f"registros ajenos a la muestra oficial (ensayo, familiarización o VAL): {foreign}")

    valid = [r for r in records if r.get("execution_result") in VALID_RESULTS]
    excluded = Counter(str(r.get("execution_result")) for r in records if r.get("execution_result") not in VALID_RESULTS)

    by_scenario: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for record in valid:
        by_scenario[record["scenario_id"]].append(record)
    repeated = {s: [r.get("attempt") for r in rs] for s, rs in by_scenario.items() if len(rs) > 1}
    if repeated:
        errors.append(f"más de un registro válido para el mismo POST (intentos): {repeated}")
    missing = [s for s in expected_scenarios if s not in by_scenario]
    if missing:
        errors.append(f"POST sin registro válido: {missing}")
    unexpected = sorted(set(by_scenario) - set(expected_scenarios))
    if unexpected:
        errors.append(f"POST fuera de POST-001…POST-{len(expected_scenarios):03d}: {unexpected}")

    rows = [by_scenario[s][0] for s in expected_scenarios if len(by_scenario.get(s, [])) == 1]
    if len(rows) != len(expected_scenarios):
        errors.append(f"la matriz tendría {len(rows)} filas; se esperan {len(expected_scenarios)}")

    participants = Counter(r.get("participant_id") for r in rows)
    repeated_participants = sorted(str(p) for p, n in participants.items() if n > 1)
    if repeated_participants:
        errors.append(f"participantes con más de un registro válido: {repeated_participants}")
    bad_participants = sorted(str(p) for p in participants if not _PARTICIPANT.match(str(p or "")))
    if bad_participants:
        errors.append(f"participant_id fuera de PA01…PA30: {bad_participants}")
    if len(participants) != len(expected_scenarios):
        errors.append(f"{len(participants)} participant_id distintos; se esperan {len(expected_scenarios)}")

    for field in ("order_id", "mp_payment_id"):
        values = Counter(r.get(field) for r in rows if r.get(field) is not None)
        repeated_values = sorted(str(v) for v, n in values.items() if n > 1)
        if repeated_values:
            errors.append(f"{field} repetido en varias filas: {repeated_values}")

    catalogs = sorted({str(r.get("catalog_version")) for r in rows})
    if catalogs and catalogs != [catalog_version]:
        errors.append(f"catálogos en la matriz {catalogs}; el cargado es {catalog_version}")

    for record in rows:
        errors.extend(f"{_label(record)}: {problem}" for problem in _row_problems(record))

    report = {
        "valida": not errors,
        "errores": errors,
        "filas": len(rows) if not errors else 0,
        "registros_leidos": len(records),
        "excluidos_por_resultado": dict(excluded),
        "exitosas": sum(1 for r in rows if r.get("execution_result") == "EXITOSA") if not errors else None,
        "fallidas_sistema": sum(1 for r in rows if r.get("execution_result") == "FALLIDA_SISTEMA") if not errors else None,
        "catalogo": catalog_version,
        "regla": "EXITOSA o FALLIDA_SISTEMA, un registro por POST (DISENO_POSTEST.md §13.1)",
    }
    return (rows if not errors else []), report


def _row_problems(record: dict[str, Any]) -> list[str]:
    problems: list[str] = []
    success = record.get("order_success")
    result = record.get("execution_result")

    # D2 en todos los registros válidos, coherente con la clasificación.
    if success not in (0, 1):
        problems.append(f"order_success no es 0/1 ({success!r})")
    elif (result == "EXITOSA") != (success == 1):
        problems.append(f"order_success={success} no coincide con {result}")

    # D1 solo en las compras exitosas, y reproducible desde las marcas de tiempo.
    if success == 1:
        empty = [f for f in D1_FIELDS if record.get(f) is None]
        if empty:
            problems.append(f"falta D1: {empty}")
        else:
            seconds = tiempo.seconds(tiempo.from_iso(record["user_started_at"]), tiempo.from_iso(record["mercadopago_approved_at"]))
            if abs(seconds - record["purchase_time_seconds"]) > _SECONDS_TOLERANCE:
                problems.append(f"purchase_time_seconds={record['purchase_time_seconds']} no coincide con FIN − INICIO ({seconds})")
            if record["purchase_time_minutes"] != round(record["purchase_time_seconds"] / 60, 2):
                problems.append("purchase_time_minutes no es purchase_time_seconds / 60")
    elif record.get("purchase_time_seconds") is not None or record.get("purchase_time_minutes") is not None:
        problems.append("una compra no exitosa tiene tiempo de compra (D1 solo se define para las exitosas)")

    # D3 en todas las filas, con la fórmula aprobada.
    empty = [f for f in D3_FIELDS if record.get(f) is None]
    if empty:
        problems.append(f"falta D3: {empty}")
    else:
        offered, accessible = record["total_products_offered"], record["accessible_products"]
        if accessible != record["visible_products"]:
            problems.append("accessible_products y visible_products difieren")
        if not offered:
            problems.append("total_products_offered es 0")
        elif abs(record["accessibility_rate"] - round(accessible / offered * 100, 2)) > _RATE_TOLERANCE:
            problems.append(f"accessibility_rate={record['accessibility_rate']} no es {accessible}/{offered}×100")
    if record.get("catalog_api_status") not in (None, 200):
        problems.append(f"D3 no se midió con la API de la app (HTTP {record.get('catalog_api_status')})")
    return problems


def _label(record: dict[str, Any]) -> str:
    return f"{record.get('scenario_id')}#intento{record.get('attempt')}"


def write(folder: Path, records: list[dict[str, Any]], *, catalog_version: str, columns: list[str], cell) -> dict[str, Any]:
    """Escribe el informe siempre y la matriz solo si es válida."""
    rows, report = build(records, catalog_version=catalog_version)
    (folder / "validacion_analisis.json").write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")
    if report["valida"]:
        with open(folder / "postest_O2_analisis.csv", "w", newline="", encoding="utf-8") as f:
            writer = csv.writer(f)
            writer.writerow(columns)
            for record in rows:
                writer.writerow([cell(record.get(c)) for c in columns])
        (folder / "postest_O2_analisis.json").write_text(json.dumps(rows, indent=2, ensure_ascii=False, default=str),
                                                         encoding="utf-8")
    return report
