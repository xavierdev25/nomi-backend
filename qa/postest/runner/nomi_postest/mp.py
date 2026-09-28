"""Consultas a MercadoPago Sandbox (solo lectura): pagos de un checkout y desfase de reloj."""

from __future__ import annotations

from datetime import datetime, timedelta
from typing import Any

from . import config, net, tiempo
from .shell import RunnerError, read_env_file


def _token() -> str:
    value = read_env_file(config.BACKEND_ENV_FILE).get("MERCADOPAGO_ACCESS_TOKEN")
    if not value:
        raise RunnerError("Falta MERCADOPAGO_ACCESS_TOKEN en nomi-backend/.env")
    return value


def clock_offset_seconds() -> float | None:
    """Reloj de MercadoPago menos reloj local, con la resolución de 1 s de la cabecera `Date`."""
    before = tiempo.now()
    response = net.request("GET", f"{config.MERCADOPAGO_API}/users/me", token=_token(), timeout=15)
    after = tiempo.now()
    if response.date is None:
        return None
    local = before + (after - before) / 2
    return round((response.date - local).total_seconds(), 1)


def payments_by_reference(reference: str) -> list[dict[str, Any]]:
    """Todos los intentos de pago del checkout, del más antiguo al más reciente."""
    response = net.request(
        "GET", f"{config.MERCADOPAGO_API}/v1/payments/search?external_reference={reference}"
               f"&sort=date_created&criteria=asc&limit=50&offset=0",
        token=_token(), timeout=20)
    if response.status != 200:
        raise RunnerError(f"MercadoPago no respondió a la búsqueda de pagos: HTTP {response.status}")
    return response.body.get("results", [])


def parse(payment: dict[str, Any]) -> dict[str, Any]:
    return {
        "id": str(payment.get("id")),
        "status": payment.get("status"),
        "status_detail": payment.get("status_detail"),
        "amount": payment.get("transaction_amount"),
        "created_at": tiempo.from_iso(payment.get("date_created")),
        "approved_at": tiempo.from_iso(payment.get("date_approved")),
        "payment_method": payment.get("payment_method_id"),
        "payment_type": payment.get("payment_type_id"),
    }


def corrected(value: datetime | None, offset: float | None) -> tuple[datetime | None, bool]:
    """Regla pre-registrada (§11.3): solo se corrige si el desfase supera 1 s."""
    if value is None or offset is None or abs(offset) <= 1:
        return value, False
    return value - timedelta(seconds=offset), True
