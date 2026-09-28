"""Bloqueo de la ejecución oficial de POST-001 a POST-030.

La muestra oficial no se puede ejecutar hasta que el investigador decida qué hacer con O1. Esa
decisión queda en `AUTORIZACION_OFICIAL.json`, que solo crea el investigador. El ejecutor exige
además que el catálogo cargado sea exactamente el autorizado (mismo hash).
"""

from __future__ import annotations

import json
from typing import Any

from . import config, seed
from .shell import RunnerError

REQUIRED_FIELDS = ("aprobado_por", "fecha", "decision_o1", "catalogo", "hash_catalogo", "franjas")

BLOCKED_MESSAGE = (
    "BLOQUEADO: la ejecución oficial de POST-001 a POST-030 está pendiente de la decisión sobre O1 "
    "(DISENO_POSTEST.md §18). Para pruebas técnicas usa `ensayo`. La ejecución oficial requiere que el "
    "investigador cree qa/postest/AUTORIZACION_OFICIAL.json con: " + ", ".join(REQUIRED_FIELDS) + "."
)


def official_authorization() -> dict[str, Any]:
    """Devuelve la autorización si es válida; si no, detiene con el motivo."""
    if not config.AUTHORIZATION_FILE.exists():
        raise RunnerError(BLOCKED_MESSAGE)
    auth = json.loads(config.AUTHORIZATION_FILE.read_text(encoding="utf-8"))
    missing = [f for f in REQUIRED_FIELDS if not auth.get(f)]
    if missing:
        raise RunnerError(f"AUTORIZACION_OFICIAL.json incompleta: faltan {', '.join(missing)}")
    frozen = seed.frozen_catalog()
    if auth["catalogo"] != frozen["version"] or auth["hash_catalogo"] != frozen["hash_catalogo"]:
        raise RunnerError("El catálogo cargado no es el autorizado (versión o hash distintos). "
                          f"Cargado: {frozen['version']} {frozen['hash_catalogo'][:12]}…")
    if auth["catalogo"] == "C0" and "C0" not in str(auth["decision_o1"]).upper():
        raise RunnerError("Usar C0 en la ejecución oficial exige una decisión expresa sobre O1 que lo mencione.")
    return auth
