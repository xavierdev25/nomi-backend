"""Eventos que solo quedan en los logs: peticiones de la app al backend y aciertos de caché de la IA.

El postest usa un backend y un servicio de IA dedicados, así que las peticiones de sus logs son
las de la app del participante y las del propio ejecutor. Las del ejecutor se distinguen porque
se hacen en momentos conocidos y se excluyen por su hora.
"""

from __future__ import annotations

import json
import re
from dataclasses import dataclass
from datetime import datetime

from . import config, tiempo

_REQUEST = re.compile(
    r"^(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3}) \[[^\]]+\] INFO\s+\S*RequestLoggingFilter \[\w*\] - "
    r"(GET|POST|PUT|PATCH|DELETE) (\S+) (\d{3}) (\d+)ms")


@dataclass
class LoggedRequest:
    at: datetime
    method: str
    path: str
    status: int
    millis: int


def backend_requests(since: datetime, until: datetime | None = None) -> list[LoggedRequest]:
    if not config.BACKEND_LOG.exists():
        return []
    found = []
    with open(config.BACKEND_LOG, encoding="utf-8", errors="replace") as log:
        for line in log:
            match = _REQUEST.match(line)
            if not match:
                continue
            at = tiempo.from_local(match.group(1))
            if at < since or (until and at > until):
                continue
            found.append(LoggedRequest(at, match.group(2), match.group(3), int(match.group(4)), int(match.group(5))))
    return found


def first(requests: list[LoggedRequest], pattern: str, exclude: list[tuple[datetime, datetime]] = ()) -> LoggedRequest | None:
    """Primera petición cuya ruta cumple `pattern`, fuera de los intervalos del propio ejecutor."""
    regex = re.compile(pattern)
    for request in requests:
        if regex.search(request.path) and not any(a <= request.at <= b for a, b in exclude):
            return request
    return None


def last(requests: list[LoggedRequest], pattern: str, exclude: list[tuple[datetime, datetime]] = ()) -> LoggedRequest | None:
    regex = re.compile(pattern)
    matches = [r for r in requests if regex.search(r.path) and not any(a <= r.at <= b for a, b in exclude)]
    return matches[-1] if matches else None


def ai_messages(since: datetime) -> list[tuple[datetime, str]]:
    """Mensajes del log JSON del servicio de IA desde `since`."""
    if not config.AI_LOG.exists():
        return []
    found = []
    with open(config.AI_LOG, encoding="utf-8", errors="replace") as log:
        for line in log:
            if not line.startswith("{"):
                continue
            try:
                entry = json.loads(line)
                at = tiempo.from_local(entry["asctime"])
            except (ValueError, KeyError):
                continue
            if at >= since:
                found.append((at, entry.get("message", "")))
    return found
