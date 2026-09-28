"""Fotos del estado del catálogo de T1 y restauración a un estado conocido (S0, Sₖ)."""

from __future__ import annotations

import json
from datetime import datetime, timezone
from typing import Any

from . import config, db
from .shell import RunnerError


def store_id(name: str) -> int:
    value = db.scalar(f"SELECT id FROM stores WHERE nombre = {db.literal(name)} AND deleted_at IS NULL")
    if value is None:
        raise RunnerError(f"No existe la tienda «{name}»: carga el seed primero")
    return int(value)


def snapshot_t1() -> list[dict[str, Any]]:
    """Estado de cada producto de T1 que afecta a los indicadores."""
    t1 = store_id(config.T1_NAME)
    return db.query(f"""
        SELECT id, nombre, stock, disponible, activo
        FROM products WHERE store_id = {t1} AND deleted_at IS NULL ORDER BY id""")


def save(name: str, rows: list[dict[str, Any]] | None = None) -> dict[str, Any]:
    config.STATE_DIR.mkdir(parents=True, exist_ok=True)
    data = {"nombre": name, "tomada_en": datetime.now(timezone.utc).isoformat(),
            "productos": rows if rows is not None else snapshot_t1()}
    (config.STATE_DIR / f"{name}.json").write_text(json.dumps(data, indent=2, ensure_ascii=False), encoding="utf-8")
    return data


def load(name: str) -> dict[str, Any]:
    path = config.STATE_DIR / f"{name}.json"
    if not path.exists():
        raise RunnerError(f"No existe la foto de estado «{name}»")
    return json.loads(path.read_text(encoding="utf-8"))


def differences(expected: list[dict[str, Any]], actual: list[dict[str, Any]]) -> list[str]:
    """Diferencias entre dos fotos, en texto legible. Vacía si coinciden."""
    by_id = {row["id"]: row for row in actual}
    diffs = []
    for row in expected:
        current = by_id.pop(row["id"], None)
        if current is None:
            diffs.append(f"falta el producto {row['id']} ({row['nombre']})")
            continue
        for field in ("stock", "disponible", "activo"):
            if row[field] != current[field]:
                diffs.append(f"{row['nombre']}: {field} {current[field]} (se esperaba {row[field]})")
    for extra in by_id.values():
        diffs.append(f"producto no esperado {extra['id']} ({extra['nombre']})")
    return diffs


def restore(name: str) -> None:
    """Devuelve T1 a una foto y deja T2 y T3 sin publicar (estado de las jornadas)."""
    rows = load(name)["productos"]
    updates = "\n".join(
        f"UPDATE products SET stock = {r['stock']}, disponible = {db.literal(r['disponible'])}, "
        f"activo = {db.literal(r['activo'])}, actualizado_en = now() WHERE id = {r['id']};"
        for r in rows)
    db.execute_script(f"""BEGIN;
{updates}
UPDATE products SET activo = false
WHERE store_id IN (SELECT id FROM stores WHERE nombre IN ({db.literal(config.T2_NAME)}, {db.literal(config.T3_NAME)}));
COMMIT;""")
    remaining = differences(rows, snapshot_t1())
    if remaining:
        raise RunnerError("La restauración no dejó el estado esperado: " + "; ".join(remaining))


def val_stores_unpublished() -> bool:
    count = db.scalar(f"""SELECT count(*) FROM products WHERE activo AND store_id IN
        (SELECT id FROM stores WHERE nombre IN ({db.literal(config.T2_NAME)}, {db.literal(config.T3_NAME)}))""")
    return int(count or 0) == 0
