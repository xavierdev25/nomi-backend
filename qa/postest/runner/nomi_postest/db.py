"""Acceso a la base del postest mediante `psql` dentro de su contenedor.

Así el ejecutor no necesita un driver de PostgreSQL. Las consultas devuelven JSON (`json_agg`)
para no depender del formato de texto de `psql`. Solo se conecta a `nomi_postest`: la base de
desarrollo nunca se toca.
"""

from __future__ import annotations

import json
from typing import Any

from . import config
from .shell import RunnerError, read_env_file, run


def _psql(args: list[str], *, input_text: str | None = None) -> str:
    # Dentro del contenedor, psql entra por el socket local, que la imagen de PostgreSQL deja sin
    # contraseña (`trust`): así la contraseña no aparece en la línea de comandos ni en los errores.
    user = read_env_file(config.BACKEND_ENV_FILE).get("DB_USERNAME", "nomi_user")
    command = ["docker", "exec", "-i", config.POSTEST_DB_CONTAINER,
               "psql", "-U", user, "-d", config.POSTEST_DB_NAME, "-v", "ON_ERROR_STOP=1",
               "-X", "-q", "-A", "-t", *args]
    return run(command, input_text=input_text).stdout


def literal(value: Any) -> str:
    """Valor SQL seguro para los datos controlados del ejecutor (texto, número, booleano, lista)."""
    if value is None:
        return "NULL"
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, (int, float)):
        return str(value)
    if isinstance(value, (list, tuple)):
        return "ARRAY[" + ",".join(literal(v) for v in value) + "]::text[]" if value else "'{}'::text[]"
    return "'" + str(value).replace("'", "''") + "'"


def query(sql: str) -> list[dict[str, Any]]:
    """Filas de una consulta como lista de diccionarios."""
    out = _psql(["-c", f"SELECT coalesce(json_agg(t), '[]'::json) FROM ({sql}) t"]).strip()
    return json.loads(out) if out else []


def scalar(sql: str) -> Any:
    rows = query(sql)
    if not rows:
        return None
    return next(iter(rows[0].values()))


def execute_script(sql: str) -> None:
    """Ejecuta un script SQL completo (se recomienda que incluya BEGIN/COMMIT)."""
    _psql(["-f", "-"], input_text=sql)
