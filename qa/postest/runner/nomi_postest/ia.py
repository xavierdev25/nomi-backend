"""Caché y captura de las recomendaciones de la IA (DISENO_POSTEST.md §11.4).

La respuesta que vio el participante no se guarda en el sistema. Se recupera repitiendo la misma
petición con el mismo usuario mientras dura la caché de 300 s del servicio de IA: si el log de la
IA registra un acierto de caché justo después, la respuesta es idéntica a la que vio.
"""

from __future__ import annotations

import time
from typing import Any

from . import config, cuentas, logs, net, tiempo
from .shell import run


def flush_cache() -> int:
    """Vacía la caché de recomendaciones para que cada escenario empiece sin respuestas previas."""
    keys = run(["docker", "exec", config.REDIS_CONTAINER, "redis-cli", "--scan", "--pattern",
                config.AI_CACHE_KEY_PATTERN]).stdout.split()
    for index in range(0, len(keys), 100):
        run(["docker", "exec", config.REDIS_CONTAINER, "redis-cli", "DEL", *keys[index:index + 100]])
    return len(keys)


def capture(email: str) -> dict[str, Any]:
    """Repite la petición de la app y comprueba en el log de la IA si vino de la caché."""
    started = tiempo.now()
    response = net.backend("GET", "/ai/recommendations", token=cuentas.token(email), timeout=30)
    finished = tiempo.now()
    time.sleep(0.5)  # el log de la IA se escribe justo después de responder
    messages = logs.ai_messages(started)
    cache_hit = any("Cache HIT" in message for at, message in messages if at <= finished + (finished - started))
    body = response.body if isinstance(response.body, dict) else {}
    return {
        "status": response.status,
        "generated_by": body.get("generatedBy"),
        "recommendations": [
            {"product_id": r.get("productId"), "nombre": r.get("nombre"), "precio": r.get("precio"),
             "categoria": r.get("categoria"), "reason": r.get("reason")}
            for r in body.get("recommendations", [])
        ],
        "cache_hit": cache_hit,
        "replayed_at": tiempo.iso(started),
        "window": (started, finished),
    }
