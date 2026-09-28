"""Compra de familiarización (`FAM`): no entra en la muestra y no altera el estado esperado.

El participante compra con la cuenta `val.postest`. El ejecutor guarda el estado antes y lo
restaura después, así la siguiente observación empieza exactamente en Sₖ.
"""

from __future__ import annotations

import json

from . import config, cuentas, db, estado, net, tiempo
from .shell import RunnerError

FAM_LOG = config.RUNTIME_DIR / "fam.jsonl"


def preparar(participant: str) -> None:
    user = cuentas.user(config.VAL_STUDENT_EMAIL)
    pending = int(db.scalar(f"SELECT count(*) FROM orders WHERE user_id = {user['id']} AND status = 'PENDIENTE'") or 0)
    if pending:
        raise RunnerError("La cuenta de familiarización tiene pedidos pendientes: cierra la FAM anterior")
    estado.save(f"FAM-{participant}-antes", estado.load("esperado")["productos"])
    _log({"participante": participant, "evento": "preparada", "en": tiempo.iso(tiempo.now())})
    print(f"✓ FAM de {participant} preparada. El operador inicia sesión en la app con {config.VAL_STUDENT_EMAIL} "
          "(contraseña en .secrets/cuentas.env) y el participante hace una compra libre.")


def cerrar(participant: str) -> None:
    user = cuentas.user(config.VAL_STUDENT_EMAIL)
    token = cuentas.token(config.VAL_STUDENT_EMAIL)
    orders = db.query(f"SELECT id, status FROM orders WHERE user_id = {user['id']} AND status = 'PENDIENTE'")
    for order in orders:
        response = net.backend("PATCH", f"/orders/{order['id']}/cancel", body={"motivo": "Compra de familiarización"},
                               token=token)
        if response.status not in (200, 409):
            raise RunnerError(f"No se pudo cancelar el pedido de familiarización {order['id']}: HTTP {response.status}")
    estado.restore(f"FAM-{participant}-antes")
    _log({"participante": participant, "evento": "cerrada", "en": tiempo.iso(tiempo.now()),
          "pedidos_cancelados": [o["id"] for o in orders]})
    print(f"✓ FAM de {participant} cerrada: estado restaurado; no entra en la muestra.")


def _log(entry: dict) -> None:
    config.RUNTIME_DIR.mkdir(parents=True, exist_ok=True)
    with open(FAM_LOG, "a", encoding="utf-8") as f:
        f.write(json.dumps(entry, ensure_ascii=False) + "\n")
