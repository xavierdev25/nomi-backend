"""Fechas y horas con zona. Cada fuente escribe en un formato distinto:

- PostgreSQL: `TIMESTAMP` sin zona, en UTC (el backend configura Hibernate en UTC).
- Logs del backend y de la IA: hora local del Mac, sin zona.
- MercadoPago: ISO-8601 con desfase (por ejemplo, `-04:00`).
- Ejecutor: reloj del Mac, en UTC.

Todo se normaliza a `datetime` con zona y se exporta en ISO-8601.
"""

from __future__ import annotations

from datetime import datetime, timezone


def now() -> datetime:
    return datetime.now(timezone.utc)


def from_db(value: str | None) -> datetime | None:
    """`TIMESTAMP` de PostgreSQL (UTC sin zona) → UTC con zona."""
    if not value:
        return None
    return datetime.fromisoformat(value).replace(tzinfo=timezone.utc)


def from_local(value: str) -> datetime:
    """Hora local del Mac sin zona (logs) → con la zona local del sistema."""
    return datetime.fromisoformat(value.replace(",", ".")).astimezone()


def from_iso(value: str | None) -> datetime | None:
    """ISO-8601 con desfase (MercadoPago, fotos del ejecutor)."""
    if not value:
        return None
    return datetime.fromisoformat(value)


def iso(value: datetime | None) -> str | None:
    return value.astimezone(timezone.utc).isoformat(timespec="milliseconds") if value else None


def seconds(start: datetime | None, end: datetime | None) -> float | None:
    if start is None or end is None:
        return None
    return round((end - start).total_seconds(), 3)
