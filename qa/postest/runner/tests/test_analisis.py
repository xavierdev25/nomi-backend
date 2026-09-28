"""Pruebas de la matriz de análisis (`nomi_postest/analisis.py`).

Los registros de estas pruebas son ficticios y solo existen en memoria o en una carpeta temporal:
sirven para comprobar las reglas de selección y validación, nunca se escriben en `results/`.
Se ejecutan desde `runner/` con `python3 -m unittest discover -s tests`.
"""

from __future__ import annotations

import copy
import json
import tempfile
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path

from nomi_postest import analisis, exportar

START = datetime(2030, 1, 1, 9, 0, tzinfo=timezone.utc)


def record(n: int, *, result: str = "EXITOSA", attempt: int = 1, participant: str | None = None, **changes):
    """Registro oficial ficticio de POST-0nn con los campos que usa la validación."""
    started = START + timedelta(minutes=10 * n)
    approved = started + timedelta(seconds=120 + n)
    success = 1 if result == "EXITOSA" else 0
    seconds = round((approved - started).total_seconds(), 3) if success else None
    base = {
        "modo": "oficial", "scenario_id": f"POST-{n:03d}", "attempt": attempt,
        "participant_id": participant or f"PA{n:02d}",
        "test_user_email": f"est{n:02d}{'' if attempt == 1 else f'i{attempt}'}.postest@nomi.test",
        "catalog_version": "C1", "execution_result": result, "order_success": success,
        "order_id": 1000 + 10 * n + attempt, "mp_payment_id": f"mp-{n}-{attempt}" if success else None,
        "user_started_at": started.isoformat(timespec="milliseconds"),
        "mercadopago_approved_at": approved.isoformat(timespec="milliseconds") if success else None,
        "purchase_time_seconds": seconds, "purchase_time_minutes": round(seconds / 60, 2) if success else None,
        "time_to_failure_seconds": None if success else 1020.0,
        "total_products_offered": 40, "accessible_products": 36, "visible_products": 36,
        "accessibility_rate": 90.0, "catalog_api_status": 200,
    }
    base.update(changes)
    return base


def sample() -> list[dict]:
    return [record(n) for n in range(1, 31)]


def build(records):
    return analisis.build(records, catalog_version="C1")


class ValidSampleTest(unittest.TestCase):
    def test_thirty_valid_records_make_a_valid_matrix(self):
        rows, report = build(sample())
        self.assertTrue(report["valida"], report["errores"])
        self.assertEqual(len(rows), 30)
        self.assertEqual([r["scenario_id"] for r in rows], list(analisis.EXPECTED_SCENARIOS))

    def test_system_failures_stay_in_the_matrix(self):
        records = sample()
        records[4] = record(5, result="FALLIDA_SISTEMA")
        rows, report = build(records)
        self.assertTrue(report["valida"], report["errores"])
        self.assertEqual((report["exitosas"], report["fallidas_sistema"]), (29, 1))

    def test_invalid_attempt_is_excluded_and_its_repetition_kept(self):
        records = sample()
        records[6] = record(7, result="INVALIDA_PROTOCOLO", mp_payment_id=None)
        records.append(record(7, attempt=2, participant="PA07"))
        rows, report = build(records)
        self.assertTrue(report["valida"], report["errores"])
        self.assertEqual(rows[6]["attempt"], 2)
        self.assertEqual(report["excluidos_por_resultado"], {"INVALIDA_PROTOCOLO": 1})

    def test_not_started_and_environment_failures_never_enter(self):
        records = sample()
        records.append(record(3, result="NO_INICIADA", order_id=None, mp_payment_id=None))
        records.append(record(9, result="INVALIDA_ENTORNO", attempt=2, order_id=None, mp_payment_id=None))
        rows, report = build(records)
        self.assertTrue(report["valida"], report["errores"])
        self.assertNotIn("NO_INICIADA", {r["execution_result"] for r in rows})
        self.assertNotIn("INVALIDA_ENTORNO", {r["execution_result"] for r in rows})


class InvalidSampleTest(unittest.TestCase):
    def assertInvalid(self, records, fragment):
        rows, report = build(records)
        self.assertFalse(report["valida"])
        self.assertEqual(rows, [])
        self.assertTrue(any(fragment in e for e in report["errores"]), report["errores"])

    def test_missing_post(self):
        self.assertInvalid([r for r in sample() if r["scenario_id"] != "POST-017"], "POST-017")

    def test_two_valid_records_for_the_same_post(self):
        records = sample() + [record(12, attempt=2)]
        self.assertInvalid(records, "más de un registro válido")

    def test_repeated_participant(self):
        records = sample()
        records[1]["participant_id"] = "PA01"
        self.assertInvalid(records, "participantes con más de un registro válido")

    def test_trial_record_in_the_official_folder(self):
        records = sample() + [record(31, scenario_id="ENS-001", modo="ensayo", test_user_email="ens001.postest@nomi.test")]
        self.assertInvalid(records, "ajenos a la muestra oficial")

    def test_familiarization_account(self):
        records = sample()
        records[0]["test_user_email"] = "val.postest@nomi.test"
        self.assertInvalid(records, "ajenos a la muestra oficial")

    def test_duplicated_payment(self):
        records = sample()
        records[3]["mp_payment_id"] = records[2]["mp_payment_id"]
        self.assertInvalid(records, "mp_payment_id repetido")

    def test_success_without_d1(self):
        records = sample()
        records[10]["mercadopago_approved_at"] = None
        self.assertInvalid(records, "falta D1")

    def test_d1_not_reproducible_from_timestamps(self):
        records = sample()
        records[10]["purchase_time_seconds"] += 5
        self.assertInvalid(records, "no coincide con FIN − INICIO")

    def test_failure_with_a_purchase_time(self):
        records = sample()
        records[4] = record(5, result="FALLIDA_SISTEMA", purchase_time_seconds=300.0, purchase_time_minutes=5.0)
        self.assertInvalid(records, "D1 solo se define para las exitosas")

    def test_d2_inconsistent_with_result(self):
        records = sample()
        records[8]["order_success"] = 0
        self.assertInvalid(records, "no coincide con EXITOSA")

    def test_missing_d3(self):
        records = sample()
        records[20]["accessible_products"] = None
        self.assertInvalid(records, "falta D3")

    def test_d3_rate_not_reproducible(self):
        records = sample()
        records[20]["accessibility_rate"] = 100.0
        self.assertInvalid(records, "accessibility_rate")

    def test_d3_not_measured_with_the_app_api(self):
        records = sample()
        records[20]["catalog_api_status"] = 500
        self.assertInvalid(records, "no se midió con la API")

    def test_other_catalog(self):
        records = sample()
        records[0]["catalog_version"] = "C0"
        self.assertInvalid(records, "catálogos en la matriz")


class WriteTest(unittest.TestCase):
    def write(self, records):
        folder = Path(tempfile.mkdtemp())
        report = analisis.write(folder, records, catalog_version="C1", columns=["scenario_id", "order_success"],
                                cell=exportar._cell)
        return folder, report

    def test_valid_matrix_is_written(self):
        folder, report = self.write(sample())
        self.assertTrue(report["valida"])
        self.assertEqual(len((folder / "postest_O2_analisis.csv").read_text(encoding="utf-8").splitlines()), 31)
        self.assertEqual(len(json.loads((folder / "postest_O2_analisis.json").read_text(encoding="utf-8"))), 30)

    def test_invalid_matrix_is_never_written(self):
        folder, report = self.write(sample()[:29])
        self.assertFalse(report["valida"])
        self.assertFalse((folder / "postest_O2_analisis.csv").exists())
        self.assertFalse((folder / "postest_O2_analisis.json").exists())
        self.assertFalse(json.loads((folder / "validacion_analisis.json").read_text(encoding="utf-8"))["valida"])

    def test_input_records_are_not_modified(self):
        records = sample()
        before = copy.deepcopy(records)
        self.write(records)
        self.assertEqual(records, before)


if __name__ == "__main__":
    unittest.main()
