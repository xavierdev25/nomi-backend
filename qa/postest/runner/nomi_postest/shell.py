"""Ejecución de comandos del sistema y lectura de archivos `.env`."""

from __future__ import annotations

import os
import subprocess
from pathlib import Path


class RunnerError(Exception):
    """Error que detiene el comando con un mensaje para el operador."""


def run(args: list[str], *, input_text: str | None = None, check: bool = True,
        cwd: Path | None = None, timeout: int = 120,
        env: dict[str, str] | None = None) -> subprocess.CompletedProcess[str]:
    """Ejecuta un comando y devuelve su salida. Con `check`, un código distinto de 0 es un error.

    Los errores solo citan el inicio del comando: los argumentos pueden incluir datos que no deben
    acabar en la terminal. `env` se añade al entorno del proceso (para pasar secretos sin ponerlos
    en la línea de comandos).
    """
    try:
        result = subprocess.run(args, input=input_text, capture_output=True, text=True, cwd=cwd,
                                timeout=timeout, env={**os.environ, **env} if env else None)
    except subprocess.TimeoutExpired:
        raise RunnerError(f"`{' '.join(args[:3])}…` no respondió en {timeout} s") from None
    if check and result.returncode != 0:
        detail = (result.stderr or result.stdout).strip().splitlines()[-5:]
        raise RunnerError(f"Falló `{' '.join(args[:3])}…`: " + " | ".join(detail))
    return result


def read_env_file(path: Path) -> dict[str, str]:
    """Lee un archivo KEY=valor, sin comillas ni exportaciones."""
    values: dict[str, str] = {}
    if not path.exists():
        return values
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip()
    return values
