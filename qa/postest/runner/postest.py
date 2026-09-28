#!/usr/bin/env python3
"""Ejecutor del postest de Nomi (evaluación controlada del prototipo mediante escenarios de compra).

Instrumenta, controla y registra; nunca ejecuta las acciones de compra que se miden. Ver
qa/postest/INSTRUCCIONES_SESION.md y runner/README.md.
"""

from __future__ import annotations

import argparse
import csv
import sys
from datetime import datetime

from nomi_postest import config, db, entorno, estado, exportar, fam, seed, sesion, val
from nomi_postest.shell import RunnerError

JORNADA_WINDOWS = {"J1": ("09:00", "10:30"), "J2": ("12:00", "14:00"), "J3": ("16:00", "17:30")}


def cmd_entorno(args: argparse.Namespace) -> int:
    if args.accion == "levantar":
        entorno.levantar()
    elif args.accion == "verificar":
        return 0 if entorno.verificar() else 1
    elif args.accion == "detener":
        entorno.detener()
    elif args.accion == "recrear":
        entorno.recrear(confirmed=args.confirmar)
    return 0


def cmd_seed(args: argparse.Namespace) -> int:
    seed.cargar(args.catalogo)
    return 0


def cmd_estado(args: argparse.Namespace) -> int:
    if args.accion == "foto":
        estado.save(args.nombre)
        print(f"✓ Foto «{args.nombre}» guardada")
    elif args.accion == "restaurar":
        estado.restore(args.nombre)
        print(f"✓ T1 restaurada a «{args.nombre}»; T2 y T3 sin publicar")
    elif args.accion == "comparar":
        diffs = estado.differences(estado.load(args.nombre)["productos"], estado.snapshot_t1())
        print("✓ El estado coincide" if not diffs else "✗ Diferencias:\n  " + "\n  ".join(diffs))
        return 0 if not diffs else 1
    return 0


def _pending_orders() -> int:
    suffix = db.literal("%" + config.SYNTHETIC_EMAIL_SUFFIX)
    return int(db.scalar(f"""SELECT count(*) FROM orders o JOIN users u ON u.id = o.user_id
        WHERE o.status = 'PENDIENTE' AND u.email LIKE {suffix}""") or 0)


def cmd_jornada(args: argparse.Namespace) -> int:
    if args.accion == "cerrar":
        print(f"✓ Copia de seguridad de la base en {entorno.dump(args.jornada)}")
        return 0
    start, end = JORNADA_WINDOWS[args.jornada]
    now = datetime.now().strftime("%H:%M")
    frozen = seed.frozen_catalog()
    estado.restore("S0")
    estado.save("esperado", estado.snapshot_t1())
    pending = _pending_orders()
    print(f"✓ {args.jornada}: T1 restaurada a S0 (catálogo {frozen['version']}); T2 y T3 sin publicar")
    print(f"{'✓' if pending == 0 else '✗'} Pedidos pendientes en las cuentas del postest: {pending}")
    inside = start <= now <= end
    print(f"{'✓' if inside else '!'} Hora {now}; ventana de {args.jornada}: {start}–{end}"
          + ("" if inside else " (fuera de la ventana: válido solo para ensayos)"))
    return 0 if pending == 0 else 1


def _observation(mode: str, args: argparse.Namespace) -> int:
    if args.accion == "preparar":
        session = sesion.preparar(mode, args.id, base=getattr(args, "escenario", None), participant=args.participante,
                                  device=args.dispositivo, prior_experience=args.conocia_nomi, attempt=args.intento)
        print(f"✓ {args.id} preparado (cuenta {session['email']}"
              + (", creada ahora" if session["cuenta_creada_al_preparar"] else "")
              + f", catálogo {session['catalog_version']}, desfase MercadoPago {session['mp_clock_offset_seconds']} s).")
        print("  El operador inicia sesión en la app con esa cuenta (contraseña en .secrets/cuentas.env)"
              " y la deja en Inicio con el carrito vacío.")
    elif args.accion == "iniciar":
        sesion.iniciar(mode, args.id, now=args.ahora)
    elif args.accion == "cerrar":
        record = sesion.cerrar(mode, args.id, declared_failure=args.no_completada, result_override=args.resultado,
                               notes=args.notas, max_wait=args.espera)
        print(f"✓ {args.id} cerrado: {record['execution_result']}"
              + (f", tiempo {record['purchase_time_minutes']} min" if record.get("purchase_time_minutes") is not None else "")
              + (f", error {record['error_code']}" if record.get("error_code") else ""))
    elif args.accion == "reclasificar":
        record = sesion.reclasificar(mode, args.id, attempt=args.intento, result=args.resultado, reason=args.motivo)
        change = record["reclasificacion"]
        print(f"✓ {args.id} intento {args.intento}: {change['desde']} → {change['a']} (motivo registrado)")
    return 0


def cmd_ensayo(args: argparse.Namespace) -> int:
    return _observation("ensayo", args)


def cmd_post(args: argparse.Namespace) -> int:
    return _observation("oficial", args)


def cmd_fam(args: argparse.Namespace) -> int:
    (fam.preparar if args.accion == "preparar" else fam.cerrar)(args.participante)
    return 0


def cmd_val(args: argparse.Namespace) -> int:
    if args.accion == "ejecutar":
        val.ejecutar(args.incluir_caducidad)
    elif args.accion == "cerrar":
        val.cerrar()
    elif args.accion == "persona":
        val.persona(args.objetivo, args.espera)
    elif args.accion == "visual":
        val.visual(args.objetivo)
    elif args.accion == "registrar":
        val.registrar(args.objetivo, args.resultado, args.evidencia)
    elif args.accion == "final":
        val.final()
    return 0


def cmd_exportar(args: argparse.Namespace) -> int:
    folder = exportar.exportar(args.modo)
    print(f"✓ Exportado en {folder.relative_to(config.POSTEST_DIR)}")
    return 0


def cmd_asignacion(args: argparse.Namespace) -> int:
    with open(config.SCENARIOS_DIR / "asignacion_POST.csv", encoding="utf-8") as f:
        for row in csv.DictReader(f):
            if args.id and row["scenario_id"] != args.id:
                continue
            print(f"{row['scenario_id']} {row['participant_id']} {row['jornada']} {row['time_window']} "
                  f"{row['test_user'].split('.')[0]} IA={row['ai_instructed']} · {row['consigna']}")
    return 0


def _yes_no(value: str) -> bool:
    return value.lower() in ("si", "sí", "true", "1")


def _add_observation_parser(sub, name: str, help_text: str, official: bool) -> None:
    p = sub.add_parser(name, help=help_text)
    p.add_argument("accion", choices=["preparar", "iniciar", "cerrar", "reclasificar"])
    p.add_argument("id", help="POST-0XX" if official else "ENS-0XX")
    if not official:
        p.add_argument("--escenario", default="POST-001", help="POST cuya consigna, cuenta y entrega se reutilizan")
    p.add_argument("--participante", help="PA01…PA30 (oficial) o identificador del técnico (ensayo)")
    p.add_argument("--dispositivo", default="iphone_fisico", choices=["iphone_fisico", "simulador"])
    p.add_argument("--conocia-nomi", type=_yes_no, default=None, help="si/no")
    p.add_argument("--intento", type=int, default=1)
    p.add_argument("--ahora", action="store_true", help="iniciar sin esperar ENTER")
    p.add_argument("--no-completada", action="store_true", help="el participante declaró que no pudo completar la compra")
    p.add_argument("--resultado", choices=["INVALIDA_PROTOCOLO", "INVALIDA_ENTORNO"], help="solo para desviaciones ajenas a Nomi")
    p.add_argument("--notas")
    p.add_argument("--motivo", help="obligatorio al reclasificar: por qué la causa fue ajena a Nomi")
    p.add_argument("--espera", type=int, default=20 * 60, help="segundos máximos esperando la confirmación")
    p.set_defaults(func=cmd_post if official else cmd_ensayo)


def main() -> int:
    parser = argparse.ArgumentParser(prog="postest", description=__doc__)
    sub = parser.add_subparsers(dest="comando", required=True)

    p = sub.add_parser("entorno", help="levantar, verificar, detener o recrear el entorno controlado")
    p.add_argument("accion", choices=["levantar", "verificar", "detener", "recrear"])
    p.add_argument("--confirmar", action="store_true", help="necesario para `recrear` (borra la base del postest)")
    p.set_defaults(func=cmd_entorno)

    p = sub.add_parser("seed", help="cargar cuentas, estructura y catálogo")
    p.add_argument("accion", choices=["cargar"])
    p.add_argument("--catalogo", required=True, choices=sorted(config.CATALOG_FILES))
    p.set_defaults(func=cmd_seed)

    p = sub.add_parser("estado", help="fotos del catálogo de T1")
    p.add_argument("accion", choices=["foto", "restaurar", "comparar"])
    p.add_argument("nombre")
    p.set_defaults(func=cmd_estado)

    p = sub.add_parser("jornada", help="iniciar (restaura S0) o cerrar (copia de seguridad) una jornada")
    p.add_argument("accion", choices=["iniciar", "cerrar"])
    p.add_argument("jornada", choices=sorted(JORNADA_WINDOWS))
    p.set_defaults(func=cmd_jornada)

    _add_observation_parser(sub, "ensayo", "ensayo técnico (nunca forma parte de O2)", official=False)
    _add_observation_parser(sub, "post", "observación OFICIAL (bloqueada sin AUTORIZACION_OFICIAL.json)", official=True)

    p = sub.add_parser("fam", help="compra de familiarización (no entra en la muestra)")
    p.add_argument("accion", choices=["preparar", "cerrar"])
    p.add_argument("participante")
    p.set_defaults(func=cmd_fam)

    p = sub.add_parser("val", help="batería de validación")
    p.add_argument("accion", choices=["ejecutar", "cerrar", "persona", "visual", "registrar", "final"])
    p.add_argument("objetivo", nargs="?", help="persona: VAL-006|VAL-008|VAL-018 · visual: preparar|cerrar · registrar: VAL-019|020|021")
    p.add_argument("--incluir-caducidad", action="store_true", help="incluye VAL-004 (espera real de 15 min)")
    p.add_argument("--espera", type=int, default=20 * 60, help="persona: segundos máximos esperando el pago")
    p.add_argument("--resultado", choices=["PASA", "FALLA"], help="registrar: resultado de la comprobación visual")
    p.add_argument("--evidencia", help="registrar: qué se vio y dónde está la captura")
    p.set_defaults(func=cmd_val)

    p = sub.add_parser("exportar", help="CSV/JSON, manifiesto y resumen")
    p.add_argument("--modo", choices=["ensayo", "oficial"], default="ensayo")
    p.set_defaults(func=cmd_exportar)

    p = sub.add_parser("asignacion", help="mostrar la asignación de escenarios")
    p.add_argument("id", nargs="?")
    p.set_defaults(func=cmd_asignacion)

    args = parser.parse_args()
    try:
        return args.func(args)
    except RunnerError as err:
        print(f"✗ {err}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
