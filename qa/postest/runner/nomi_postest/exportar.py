"""Exportación de los registros: CSV (cabecera de la plantilla), JSON, manifiesto y resumen.

Los ensayos técnicos se exportan en `results/ensayos/` con otro nombre de archivo, para que nunca
se confundan con la muestra O2. La exportación oficial añade la matriz de análisis
(`postest_O2_analisis.*`), que solo se escribe si supera la validación (ver `analisis.py`).
"""

from __future__ import annotations

import csv
import hashlib
import json
from collections import Counter
from datetime import datetime
from pathlib import Path
from typing import Any

from . import analisis, bloqueo, config, seed, sesion, tiempo
from .shell import RunnerError

# Campos que pueden quedar vacíos en una compra exitosa, por diseño (DISENO_POSTEST.md §14).
EMPTY_BY_DESIGN_ON_SUCCESS = {
    "time_to_failure_seconds": "solo en registros fallidos",
    "error_code": "solo en registros fallidos",
    "error_description": "solo en registros fallidos",
    "notes": "observaciones opcionales del operador",
    "participant_prior_nomi_experience": "lo informa el operador",
}


def template_columns() -> list[str]:
    with open(config.TEMPLATES_DIR / "postest_O2_plantilla.csv", encoding="utf-8") as f:
        return next(csv.reader(f))


def _records(mode: str) -> list[dict[str, Any]]:
    folder = config.RUNTIME_DIR / "registros" / mode
    records = [json.loads(p.read_text(encoding="utf-8")) for p in sorted(folder.glob("*.json"))]
    return sorted(records, key=lambda r: (r["scenario_id"], r["attempt"]))


def _cell(value: Any) -> str:
    if value is None:
        return ""
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, (list, dict)):
        return json.dumps(value, ensure_ascii=False)
    return str(value)


def exportar(mode: str) -> Path:
    if mode == "oficial":
        bloqueo.official_authorization()
    records = _records(mode)
    if not records:
        raise RunnerError(f"No hay registros en modo {mode}")
    stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    folder = (config.TRIALS_DIR if mode == "ensayo" else config.RESULTS_DIR) / stamp
    folder.mkdir(parents=True, exist_ok=True)
    name = "ensayo_tecnico" if mode == "ensayo" else "postest_O2"
    columns = template_columns()

    with open(folder / f"{name}.csv", "w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(columns)
        for record in records:
            writer.writerow([_cell(record.get(c)) for c in columns])
    (folder / f"{name}.json").write_text(json.dumps(records, indent=2, ensure_ascii=False, default=str), encoding="utf-8")

    analysis = None
    if mode == "oficial":
        analysis = analisis.write(folder, records, catalog_version=seed.frozen_catalog()["version"],
                                  columns=columns, cell=_cell)
    manifest = _manifest(mode, records)
    manifest["analisis"] = analysis
    (folder / "manifest.json").write_text(json.dumps(manifest, indent=2, ensure_ascii=False), encoding="utf-8")
    report = completeness(records, columns)
    (folder / "RESUMEN.md").write_text(_summary(mode, records, report, analysis), encoding="utf-8")
    return folder


def completeness(records: list[dict[str, Any]], columns: list[str]) -> dict[str, Any]:
    """Qué columnas quedan vacías en las compras exitosas y si es por diseño.

    Vacío es lo que sale como celda vacía en el CSV. Una lista vacía es un dato: `restrictions = []`
    significa que la cuenta no tiene restricciones.
    """
    successes = [r for r in records if r.get("order_success") == 1]
    missing: dict[str, int] = Counter()
    for record in successes:
        for column in columns:
            if _cell(record.get(column)) == "":
                missing[column] += 1
    unexpected = {c: n for c, n in missing.items() if c not in EMPTY_BY_DESIGN_ON_SUCCESS}
    return {"exitosas": len(successes), "vacios": dict(missing), "vacios_inesperados": unexpected,
            "columnas_ausentes_en_registros": [c for c in columns if all(c not in r for r in records)]}


def _manifest(mode: str, records: list[dict[str, Any]]) -> dict[str, Any]:
    environment = json.loads(config.ENVIRONMENT_FILE.read_text(encoding="utf-8")) if config.ENVIRONMENT_FILE.exists() else {}
    assignment = (config.SCENARIOS_DIR / "asignacion_POST.csv").read_bytes()
    return {
        "modo": mode,
        "naturaleza": "Evaluación controlada del prototipo Nomi mediante escenarios de compra. Datos sintéticos de prueba.",
        "generado_en": tiempo.iso(tiempo.now()),
        "catalogo": seed.frozen_catalog(),
        "asignacion_sha256": hashlib.sha256(assignment).hexdigest(),
        "commits": environment.get("commits"),
        "backend_datasource": environment.get("backend", {}).get("datasource"),
        "configuracion_backend": "por defecto (plazo de pago 15 min, conciliación 60 s)",
        "dispositivos": sorted({r.get("device_type") for r in records if r.get("device_type")}),
        "participantes": sorted({r.get("participant_id") for r in records if r.get("participant_id")}),
        "registros": len(records),
        "reclasificaciones": [{"registro": f"{r['scenario_id']}__intento{r['attempt']}", **r["reclasificacion"]}
                              for r in records if r.get("reclasificacion")],
        "autorizacion": (json.loads(config.AUTHORIZATION_FILE.read_text(encoding="utf-8"))
                         if mode == "oficial" and config.AUTHORIZATION_FILE.exists() else None),
    }


def _reclassification_note(record: dict[str, Any]) -> str:
    change = record.get("reclasificacion")
    return f" (reclasificado desde {change['desde']}: {change['motivo']})" if change else ""


def _summary(mode: str, records: list[dict[str, Any]], report: dict[str, Any], analysis: dict[str, Any] | None = None) -> str:
    results = Counter(r.get("execution_result") for r in records)
    lines = []
    if mode == "ensayo":
        lines += ["# Ensayo técnico del ejecutor (NO forma parte de O2)", "",
                  "> Registros de prueba técnica con el catálogo provisional C0. No son observaciones oficiales "
                  "del postest y no deben usarse en el análisis.", ""]
    else:
        lines += ["# Resumen del postest (O2)", "",
                  "> Evaluación controlada del prototipo Nomi mediante escenarios de compra. Datos sintéticos de prueba.", ""]
    lines += [f"- Registros: **{len(records)}**",
              *[f"- {result}: {count}" for result, count in sorted(results.items(), key=lambda x: str(x[0]))], ""]
    failed = [r for r in records if r.get("execution_result") != "EXITOSA"]
    if failed:
        lines += ["## Registros no exitosos o no válidos", "", "| Registro | Intento | Resultado | Código | Descripción |",
                  "|---|---|---|---|---|"]
        lines += [f"| {r['scenario_id']} | {r['attempt']} | {r.get('execution_result')} | {r.get('error_code') or ''} | "
                  f"{r.get('error_description') or ''}{_reclassification_note(r)} |" for r in failed]
        lines.append("")
    # Se calcula aquí, y no solo al cerrar, para cubrir también los registros anteriores a la comprobación.
    mismatched = [r for r in records if r.get("payment_method_mp")
                  and r["payment_method_mp"] != sesion.EXPECTED_PAYMENT_METHOD.get(r.get("payment_card_type"))]
    if mismatched:
        lines += ["## Pagos con un medio distinto del asignado", "",
                  "`payment_card_type` es la tarjeta de la asignación; el medio real es el que registró MercadoPago.", "",
                  "| Registro | Intento | Asignado | Usado en MercadoPago |", "|---|---|---|---|"]
        lines += [f"| {r['scenario_id']} | {r['attempt']} | {r.get('payment_card_type')} "
                  f"(`{sesion.EXPECTED_PAYMENT_METHOD.get(r.get('payment_card_type'))}`) | `{r['payment_method_mp']}` |"
                  for r in mismatched]
        lines.append("")
    lines += ["## Completitud de los campos en las compras exitosas", "",
              f"- Compras exitosas revisadas: {report['exitosas']}",
              f"- Columnas de la plantilla ausentes en los registros: {report['columnas_ausentes_en_registros'] or 'ninguna'}",
              f"- Campos vacíos inesperados: {report['vacios_inesperados'] or 'ninguno'}", ""]
    if report["vacios"]:
        lines += ["| Campo vacío | Registros | ¿Por diseño? |", "|---|---|---|"]
        lines += [f"| `{c}` | {n} | {EMPTY_BY_DESIGN_ON_SUCCESS.get(c, '**no**')} |" for c, n in sorted(report["vacios"].items())]
        lines.append("")
    if analysis is not None:
        lines += ["## Matriz de análisis", ""]
        if analysis["valida"]:
            lines += [f"- **Válida.** `postest_O2_analisis.csv` / `.json`: {analysis['filas']} filas "
                      f"({analysis['exitosas']} EXITOSA, {analysis['fallidas_sistema']} FALLIDA_SISTEMA).",
                      f"- Excluidos por resultado: {analysis['excluidos_por_resultado'] or 'ninguno'}", ""]
        else:
            lines += ["- **No válida: no se generó la matriz de análisis.** Errores (detalle en `validacion_analisis.json`):", ""]
            lines += [f"  - {e}" for e in analysis["errores"]]
            lines.append("")
    lines += ["No se ejecuta ninguna prueba inferencial en esta fase (DISENO_POSTEST.md §16).", ""]
    return "\n".join(lines)
