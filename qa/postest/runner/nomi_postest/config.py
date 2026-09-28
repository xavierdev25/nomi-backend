"""Rutas, puertos y constantes del entorno del postest.

Todo lo que identifica el entorno está aquí para que el resto del ejecutor no dependa de rutas
sueltas. Los secretos (contraseñas, token de MercadoPago) se leen de archivos locales que no se
versionan; nunca se imprimen.
"""

from __future__ import annotations

from pathlib import Path

# Rutas
POSTEST_DIR = Path(__file__).resolve().parents[2]  # nomi-backend/qa/postest
BACKEND_DIR = POSTEST_DIR.parents[1]  # nomi-backend
NOMI_DIR = BACKEND_DIR.parent  # carpeta con los repos
AI_SERVICE_DIR = NOMI_DIR / "nomi-ai-service"
IOS_DIR = NOMI_DIR / "nomi-ios"

SEED_DIR = POSTEST_DIR / "seed"
SCENARIOS_DIR = POSTEST_DIR / "scenarios"
TEMPLATES_DIR = POSTEST_DIR / "plantillas"
RESULTS_DIR = POSTEST_DIR / "results"
TRIALS_DIR = RESULTS_DIR / "ensayos"
SECRETS_DIR = POSTEST_DIR / ".secrets"
RUNTIME_DIR = POSTEST_DIR / "runtime"
STATE_DIR = RUNTIME_DIR / "estado"

ACCOUNTS_FILE = SECRETS_DIR / "cuentas.env"
BACKEND_ENV_FILE = BACKEND_DIR / ".env"
AUTHORIZATION_FILE = POSTEST_DIR / "AUTORIZACION_OFICIAL.json"
ENVIRONMENT_FILE = RUNTIME_DIR / "entorno.json"
FROZEN_CATALOG_FILE = RUNTIME_DIR / "catalogo_congelado.json"
BACKEND_LOG = RUNTIME_DIR / "backend.log"
AI_LOG = RUNTIME_DIR / "ai.log"

# Base de datos exclusiva del postest (nunca la de desarrollo)
POSTEST_DB_CONTAINER = "nomi-postest-pg"
POSTEST_DB_VOLUME = "nomi-postest-data"
POSTEST_DB_NAME = "nomi_postest"
POSTEST_DB_PORT = 55433
POSTEST_DB_IMAGE = "postgres:16-alpine"
REDIS_CONTAINER = "nomi-redis"

# Servicios
BACKEND_PORT = 8080
BACKEND_URL = f"http://localhost:{BACKEND_PORT}/api"
AI_SERVICE_PORT = 8001
AI_SERVICE_URL = f"http://localhost:{AI_SERVICE_PORT}"
OLLAMA_URL = "http://localhost:11434"
MERCADOPAGO_API = "https://api.mercadopago.com"
EXPECTED_FLYWAY_VERSION = "25"

# Caché del servicio de IA (clave de `nomi-ai-service/services/cache_service.py`)
AI_CACHE_KEY_PATTERN = "nomi:ai:recs:*"

# Certificados raíz del sistema: el Python de python.org no trae los suyos
SYSTEM_CA_BUNDLE = "/etc/ssl/cert.pem"

# Datos sintéticos (ver seed/DISENO_SEED.md)
T1_NAME = "Quiosco ISTPC (entorno de prueba)"
T2_NAME = "Cafetería Taller (prueba)"
T3_NAME = "Quiosco Cerrado (prueba)"
STORE_OWNER_EMAIL = "comercio.postest@nomi.test"
VAL_OWNER_EMAIL = "comercio2.postest@nomi.test"
VAL_STUDENT_EMAIL = "val.postest@nomi.test"
VAL_STUDENT2_EMAIL = "val2.postest@nomi.test"  # vegana: VAL-009 (IA con restricción) y VAL-014 (otro estudiante)
# Todas las cuentas sintéticas comparten este dominio; las de los POST y los ensayos se derivan
# de la asignación (ver sesion.account_for).
SYNTHETIC_EMAIL_SUFFIX = ".postest@nomi.test"

# Catálogos permitidos. C1 no existe hasta calibrar con O1.
CATALOG_FILES = {"C0": SEED_DIR / "catalogo_C0.json", "C1": SEED_DIR / "catalogo_C1.json"}
