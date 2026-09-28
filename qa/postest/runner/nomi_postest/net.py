"""Cliente HTTP mínimo (sin dependencias) para el backend, el servicio de IA y MercadoPago."""

from __future__ import annotations

import json
import ssl
import urllib.error
import urllib.request
from dataclasses import dataclass
from email.utils import parsedate_to_datetime
from datetime import datetime
from typing import Any

from . import config


@dataclass
class Response:
    status: int
    body: Any
    headers: dict[str, str]

    @property
    def date(self) -> datetime | None:
        """Cabecera `Date` del servidor, para medir el desfase de reloj."""
        value = self.headers.get("date")
        return parsedate_to_datetime(value) if value else None


def _ssl_context() -> ssl.SSLContext:
    try:
        return ssl.create_default_context(cafile=config.SYSTEM_CA_BUNDLE)
    except (FileNotFoundError, ssl.SSLError):
        return ssl.create_default_context()


_CONTEXT = _ssl_context()


def request(method: str, url: str, *, body: Any = None, token: str | None = None,
            headers: dict[str, str] | None = None, timeout: int = 30) -> Response:
    """Petición JSON. Los errores HTTP se devuelven como respuesta, no como excepción."""
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    req.add_header("Accept", "application/json")
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    for key, value in (headers or {}).items():
        req.add_header(key, value)
    try:
        with urllib.request.urlopen(req, timeout=timeout, context=_CONTEXT) as resp:
            return Response(resp.status, _parse(resp.read()), {k.lower(): v for k, v in resp.headers.items()})
    except urllib.error.HTTPError as err:
        return Response(err.code, _parse(err.read()), {k.lower(): v for k, v in err.headers.items()})
    except (urllib.error.URLError, TimeoutError, ConnectionError) as err:
        # Sin respuesta (servicio caído o arrancando): status 0, para que quien llama decida.
        return Response(0, str(err), {})


def _parse(raw: bytes) -> Any:
    if not raw:
        return None
    try:
        return json.loads(raw)
    except ValueError:
        return raw.decode("utf-8", errors="replace")


def backend(method: str, path: str, **kwargs: Any) -> Response:
    return request(method, f"{config.BACKEND_URL}{path}", **kwargs)
