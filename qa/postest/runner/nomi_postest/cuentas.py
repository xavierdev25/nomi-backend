"""Sesiones del ejecutor en el backend con las cuentas sintéticas.

El ejecutor inicia su propia sesión con cada cuenta. No interfiere con la sesión del participante
en la app: el backend admite varias sesiones por usuario y el límite de peticiones va por token.
"""

from __future__ import annotations

import time

from . import config, db, net, seed
from .shell import RunnerError

_tokens: dict[str, str] = {}


def token(email: str) -> str:
    if email not in _tokens:
        password = seed.passwords().get(email)
        if not password:
            raise RunnerError(f"No hay contraseña para {email} en .secrets/cuentas.env")
        response = net.backend("POST", "/auth/login", body={"email": email, "password": password})
        if response.status == 409:
            # El backend genera el mismo refresh token para dos inicios de sesión de la misma cuenta
            # en el mismo segundo (restricción única → 409). Pasa si el operador acaba de iniciar
            # sesión en la app: se reintenta en el segundo siguiente.
            time.sleep(1.1)
            response = net.backend("POST", "/auth/login", body={"email": email, "password": password})
        if response.status != 200:
            raise RunnerError(f"No se pudo iniciar sesión con {email}: HTTP {response.status}")
        _tokens[email] = response.body["accessToken"]
    return _tokens[email]


def user(email: str) -> dict:
    rows = db.query(f"""SELECT id, email, restrictions, budget_range FROM users
                        WHERE lower(email) = lower({db.literal(email)}) AND deleted_at IS NULL""")
    if not rows:
        raise RunnerError(f"No existe la cuenta {email}")
    return rows[0]


def store_owner_token() -> str:
    return token(config.STORE_OWNER_EMAIL)
