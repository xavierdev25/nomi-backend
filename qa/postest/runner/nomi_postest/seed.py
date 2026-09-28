"""Carga del seed: cuentas sintéticas, estructura, catálogo congelado y foto S0.

Las cuentas se registran por la API, igual que lo haría un usuario, para que sus contraseñas
tengan el hash de la aplicación. Sus contraseñas se generan aquí y se guardan en
`.secrets/cuentas.env` (no versionado); nunca se imprimen.
"""

from __future__ import annotations

import csv
import hashlib
import json
import os
import secrets
import string
import time
from datetime import datetime, timezone
from typing import Any

from . import config, db, estado, net
from .shell import RunnerError, read_env_file

# (correo, nombres, apellidos, rol, restricciones, presupuesto)
Account = tuple[str, str, str, str, list[str], str]

FIXED_ACCOUNTS: list[Account] = [
    (config.STORE_OWNER_EMAIL, "Comercio", "Quiosco Prueba", "COMERCIO", [], "MEDIO"),
    (config.VAL_OWNER_EMAIL, "Comercio", "Taller Prueba", "COMERCIO", [], "MEDIO"),
    (config.VAL_STUDENT_EMAIL, "Validación", "Prueba", "ESTUDIANTE", [], "MEDIO"),
    (config.VAL_STUDENT2_EMAIL, "Validación", "Prueba Vegana", "ESTUDIANTE", ["VEGANO"], "MEDIO"),
]


def account_from_row(row: dict[str, str], email: str) -> Account:
    """Cuenta de estudiante con el perfil (restricciones y presupuesto) de un escenario."""
    value = row["restrictions"].strip()
    restrictions = [] if value in ("", "—") else [r.strip() for r in value.split(";")]
    # El backend solo acepta letras en nombres y apellidos; la cuenta se distingue por el correo.
    return (email, "Estudiante", "Prueba", "ESTUDIANTE", restrictions, row["budget_range"])


def scenario_accounts() -> list[Account]:
    """Una cuenta por POST: el backend envía a la IA el historial de compras de la cuenta, así que
    compartirla entre participantes haría que unos influyeran en las recomendaciones de otros."""
    with open(config.SCENARIOS_DIR / "asignacion_POST.csv", encoding="utf-8") as f:
        return [account_from_row(row, row["test_user"]) for row in csv.DictReader(f)]


def _new_password() -> str:
    """Cumple la política del backend: 8–72 caracteres con mayúscula, minúscula y número."""
    alphabet = string.ascii_letters + string.digits
    while True:
        value = "Pt" + "".join(secrets.choice(alphabet) for _ in range(18)) + "9"
        if any(c.islower() for c in value) and any(c.isupper() for c in value) and any(c.isdigit() for c in value):
            return value


def passwords() -> dict[str, str]:
    return read_env_file(config.ACCOUNTS_FILE)


def _save_passwords(values: dict[str, str]) -> None:
    config.SECRETS_DIR.mkdir(parents=True, exist_ok=True)
    lines = ["# Contraseñas de las cuentas sintéticas del postest. NO versionar ni compartir."]
    lines += [f"{email}={pwd}" for email, pwd in sorted(values.items())]
    config.ACCOUNTS_FILE.write_text("\n".join(lines) + "\n", encoding="utf-8")
    os.chmod(config.ACCOUNTS_FILE, 0o600)


def ensure_account(account: Account) -> bool:
    """Registra la cuenta si no existe. Devuelve True si la creó.

    Si ya existe, comprueba que su perfil es el esperado: una cuenta con otro perfil daría otras
    recomendaciones y otro D3.
    """
    email, nombres, apellidos, role, restrictions, budget = account
    rows = db.query(f"""SELECT restrictions, budget_range FROM users
                        WHERE lower(email) = lower({db.literal(email)}) AND deleted_at IS NULL""")
    if rows:
        if email not in passwords():
            raise RunnerError(f"{email} ya existe pero no está en .secrets/cuentas.env; "
                              "recrea la base del postest (`entorno recrear`) para regenerar las cuentas")
        current = sorted(r for r in (rows[0]["restrictions"] or []) if r != "NINGUNA")
        if current != sorted(restrictions) or rows[0]["budget_range"] != budget:
            raise RunnerError(f"{email} tiene otro perfil ({current}, {rows[0]['budget_range']}) que el esperado "
                              f"({sorted(restrictions)}, {budget}); recrea la base del postest (`entorno recrear`)")
        return False
    password = _new_password()
    body = {"nombres": nombres, "apellidos": apellidos, "email": email, "password": password,
            "role": role, "restrictions": restrictions, "budgetRange": budget,
            "preferences": [], "cuisineTypes": []}
    response = net.backend("POST", "/auth/register", body=body)
    # El backend limita los intentos de autenticación por minuto; con 30+ cuentas se alcanza el límite.
    for _ in range(3):
        if response.status != 429:
            break
        print(f"  · Límite de registros del backend alcanzado; esperando 60 s para registrar {email}…", flush=True)
        time.sleep(60)
        response = net.backend("POST", "/auth/register", body=body)
    if response.status not in (200, 201):
        raise RunnerError(f"No se pudo registrar {email}: HTTP {response.status} {response.body}")
    known = passwords()
    known[email] = password
    _save_passwords(known)
    return True


def register_accounts() -> None:
    accounts = FIXED_ACCOUNTS + scenario_accounts()
    created = sum(ensure_account(account) for account in accounts)
    print(f"✓ Cuentas sintéticas: {len(accounts)} ({created} nuevas; contraseñas en .secrets/cuentas.env)")


def _catalog(version: str) -> tuple[dict[str, Any], bytes]:
    path = config.CATALOG_FILES.get(version)
    if path is None:
        raise RunnerError("Catálogo desconocido: usa C0 (pruebas técnicas) o C1 (calibrado con O1)")
    if not path.exists():
        raise RunnerError(f"No existe {path.name}. C1 se genera al calibrar con O1 (seed/DISENO_SEED.md §4).")
    raw = path.read_bytes()
    return json.loads(raw), raw


def _catalog_sql(catalog: dict[str, Any]) -> str:
    t1 = db.literal(config.T1_NAME)
    names = [p["nombre"] for p in catalog["productos"]]
    statements = ["BEGIN;"]
    for p in catalog["productos"]:
        name, categoria = db.literal(p["nombre"]), db.literal(p["categoria"])
        precio, stock, tags = p["precio"], int(p["stock"]), db.literal(p["etiquetas"])
        activo, disponible = db.literal(p["publicado"]), db.literal(p["disponible"])
        statements.append(f"""
INSERT INTO products (nombre, descripcion, precio, stock, categoria, store_id, activo, disponible, etiquetas_dieteticas)
SELECT {name}, 'Producto sintético de prueba.', {precio}, {stock}, {categoria}, s.id, {activo}, {disponible}, {tags}
FROM stores s WHERE s.nombre = {t1}
  AND NOT EXISTS (SELECT 1 FROM products p WHERE p.store_id = s.id AND p.nombre = {name} AND p.deleted_at IS NULL);
UPDATE products SET precio = {precio}, stock = {stock}, categoria = {categoria}, activo = {activo},
       disponible = {disponible}, etiquetas_dieteticas = {tags}, actualizado_en = now()
WHERE store_id = (SELECT id FROM stores WHERE nombre = {t1}) AND nombre = {name} AND deleted_at IS NULL;""")
    # Un producto de T1 que no está en el catálogo cargado se elimina (borrado lógico).
    statements.append(f"""
UPDATE products SET deleted_at = now()
WHERE store_id = (SELECT id FROM stores WHERE nombre = {t1}) AND deleted_at IS NULL
  AND nombre NOT IN ({", ".join(db.literal(n) for n in names)});""")
    statements.append("COMMIT;")
    return "\n".join(statements)


def cargar(version: str) -> None:
    if net.backend("GET", "/actuator/health", timeout=5).status != 200:
        raise RunnerError("El backend del postest no responde: `entorno levantar` primero")
    catalog, raw = _catalog(version)
    if catalog.get("version") != version:
        raise RunnerError(f"{config.CATALOG_FILES[version].name} declara la versión {catalog.get('version')}")

    register_accounts()
    structure = (config.SEED_DIR / "estructura.sql").read_text(encoding="utf-8")
    db.execute_script(structure)
    print("✓ Estructura: tiendas T1–T3, aulas sintéticas y datos heredados eliminados")

    db.execute_script(_catalog_sql(catalog))
    loaded = estado.snapshot_t1()
    expected = {p["nombre"]: p for p in catalog["productos"]}
    mismatches = [row["nombre"] for row in loaded
                  if row["nombre"] not in expected
                  or row["stock"] != int(expected[row["nombre"]]["stock"])
                  or row["activo"] != expected[row["nombre"]]["publicado"]
                  or row["disponible"] != expected[row["nombre"]]["disponible"]]
    if mismatches or len(loaded) != len(expected):
        raise RunnerError(f"El catálogo cargado no coincide con {version}: {mismatches}")

    frozen = {
        "version": version,
        "hash_catalogo": hashlib.sha256(raw).hexdigest(),
        "hash_estructura": hashlib.sha256(structure.encode("utf-8")).hexdigest(),
        "productos": len(loaded),
        "cargado_en": datetime.now(timezone.utc).isoformat(),
        "uso": "prueba técnica" if version == "C0" else "ejecución oficial (requiere autorización)",
    }
    config.RUNTIME_DIR.mkdir(parents=True, exist_ok=True)
    config.FROZEN_CATALOG_FILE.write_text(json.dumps(frozen, indent=2, ensure_ascii=False), encoding="utf-8")
    estado.save("S0", loaded)
    estado.save("esperado", loaded)  # recién cargado, el estado esperado es S0
    print(f"✓ Catálogo {version}: {len(loaded)} productos en T1, hash {frozen['hash_catalogo'][:12]}…")
    print("✓ Foto S0 guardada (runtime/estado/S0.json)")
    if version == "C0":
        print("  C0 es provisional: sirve para pruebas técnicas, familiarización y VAL, no para la muestra oficial.")


def frozen_catalog() -> dict[str, Any]:
    if not config.FROZEN_CATALOG_FILE.exists():
        raise RunnerError("No hay catálogo cargado: `seed cargar` primero")
    return json.loads(config.FROZEN_CATALOG_FILE.read_text(encoding="utf-8"))
