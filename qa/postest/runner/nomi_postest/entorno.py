"""Entorno controlado: PostgreSQL del postest, Redis, servicio de IA y backend.

El backend y el servicio de IA se arrancan con su configuración por defecto; lo único que cambia
es la base de datos del backend (`SPRING_DATASOURCE_URL` → `nomi_postest`). Así se evalúa el
sistema tal como es, sin ajustes para el experimento.
"""

from __future__ import annotations

import json
import os
import signal
import socket
import subprocess
import time
from datetime import datetime, timezone
from typing import Any

from . import config, db, net
from .shell import RunnerError, read_env_file, run


# --- utilidades ---------------------------------------------------------------------------------

def _port_open(port: int) -> bool:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.settimeout(0.5)
        return sock.connect_ex(("127.0.0.1", port)) == 0


def _wait(condition, seconds: int, what: str) -> None:
    deadline = time.time() + seconds
    while time.time() < deadline:
        if condition():
            return
        time.sleep(2)
    raise RunnerError(f"Tiempo agotado esperando: {what}")


def _docker_ok() -> bool:
    return run(["docker", "info"], check=False, timeout=20).returncode == 0


def _container_state(name: str) -> str | None:
    result = run(["docker", "inspect", "-f", "{{.State.Status}}", name], check=False)
    return result.stdout.strip() if result.returncode == 0 else None


def _load_state() -> dict[str, Any]:
    if config.ENVIRONMENT_FILE.exists():
        return json.loads(config.ENVIRONMENT_FILE.read_text(encoding="utf-8"))
    return {}


def _save_state(state: dict[str, Any]) -> None:
    config.RUNTIME_DIR.mkdir(parents=True, exist_ok=True)
    config.ENVIRONMENT_FILE.write_text(json.dumps(state, indent=2, ensure_ascii=False), encoding="utf-8")


def _pid_alive(pid: int | None) -> bool:
    if not pid:
        return False
    try:
        os.kill(pid, 0)
        return True
    except OSError:
        return False


def _git_commit(path) -> str:
    result = run(["git", "-C", str(path), "rev-parse", "--short", "HEAD"], check=False)
    dirty = run(["git", "-C", str(path), "status", "--porcelain"], check=False).stdout.strip()
    return (result.stdout.strip() or "desconocido") + (" (con cambios sin commit)" if dirty else "")


# --- levantar -----------------------------------------------------------------------------------

def levantar() -> None:
    state = _load_state()

    if not _docker_ok():
        print("· Docker no responde: arrancando OrbStack…")
        run(["orb", "start"], check=False, timeout=120)
        _wait(_docker_ok, 90, "Docker")

    _start_postgres()
    _start_redis()
    # El estado se guarda tras cada arranque: si algo falla después, `detener` sabe qué parar.
    state["ai_service"] = _start_ai_service(state.get("ai_service", {}))
    _save_state(state)
    state["backend"] = _start_backend(state.get("backend", {}))
    _save_state(state)
    state["commits"] = {
        "nomi-backend": _git_commit(config.BACKEND_DIR),
        "nomi-ios": _git_commit(config.IOS_DIR),
        "nomi-ai-service": _git_commit(config.AI_SERVICE_DIR),
    }
    state["levantado_en"] = datetime.now(timezone.utc).isoformat()
    _save_state(state)
    print("✓ Entorno levantado.")
    print(_seed_status())


def _seed_status() -> str:
    """Si la base del postest ya tiene el seed (el volumen se conserva entre arranques)."""
    try:
        stores = int(db.scalar(f"SELECT count(*) FROM stores WHERE nombre = {db.literal(config.T1_NAME)}") or 0)
    except RunnerError:
        stores = 0
    if stores and config.FROZEN_CATALOG_FILE.exists():
        frozen = json.loads(config.FROZEN_CATALOG_FILE.read_text(encoding="utf-8"))
        return (f"  Seed ya cargado: catálogo {frozen['version']} ({frozen['hash_catalogo'][:12]}…). "
                "Comprueba el estado con `estado comparar S0`; recárgalo solo si quieres volver a S0.")
    return "  Siguiente paso: `seed cargar --catalogo C0` (pruebas técnicas)."


def _start_postgres() -> None:
    status = _container_state(config.POSTEST_DB_CONTAINER)
    env = read_env_file(config.BACKEND_ENV_FILE)
    if status is None:
        print(f"· Creando {config.POSTEST_DB_CONTAINER} (base {config.POSTEST_DB_NAME}, puerto {config.POSTEST_DB_PORT})…")
        run(["docker", "run", "-d", "--name", config.POSTEST_DB_CONTAINER,
             "-p", f"{config.POSTEST_DB_PORT}:5432",
             "-e", f"POSTGRES_DB={config.POSTEST_DB_NAME}",
             "-e", f"POSTGRES_USER={env.get('DB_USERNAME', 'nomi_user')}",
             "-e", "POSTGRES_PASSWORD",  # sin valor: docker lo toma del entorno, no de la línea de comandos
             "-v", f"{config.POSTEST_DB_VOLUME}:/var/lib/postgresql/data",
             config.POSTEST_DB_IMAGE], env={"POSTGRES_PASSWORD": env["DB_PASSWORD"]})
    elif status != "running":
        print(f"· Arrancando {config.POSTEST_DB_CONTAINER}…")
        run(["docker", "start", config.POSTEST_DB_CONTAINER])
    user = env.get("DB_USERNAME", "nomi_user")
    _wait(lambda: run(["docker", "exec", config.POSTEST_DB_CONTAINER, "pg_isready", "-U", user,
                       "-d", config.POSTEST_DB_NAME], check=False).returncode == 0, 60, "PostgreSQL del postest")
    print(f"✓ PostgreSQL del postest listo ({config.POSTEST_DB_NAME})")


def _start_redis() -> None:
    status = _container_state(config.REDIS_CONTAINER)
    if status is None:
        raise RunnerError(f"No existe el contenedor {config.REDIS_CONTAINER}: arranca el docker compose del backend una vez")
    if status != "running":
        run(["docker", "start", config.REDIS_CONTAINER])
    print("✓ Redis listo")


def _start_ai_service(previous: dict[str, Any]) -> dict[str, Any]:
    if net.request("GET", f"{config.OLLAMA_URL}/api/tags", timeout=5).status != 200:
        raise RunnerError("Ollama no responde en :11434. Arráncalo con `ollama serve`.")
    if _port_open(config.AI_SERVICE_PORT):
        if _pid_alive(previous.get("pid")):
            print("✓ Servicio de IA ya en marcha")
            return previous
        raise RunnerError(f"El puerto {config.AI_SERVICE_PORT} está ocupado por un proceso que no arrancó el ejecutor. Detenlo primero.")
    uvicorn = config.AI_SERVICE_DIR / "venv" / "bin" / "uvicorn"
    if not uvicorn.exists():
        raise RunnerError("No existe nomi-ai-service/venv: crea el entorno virtual del servicio de IA.")
    config.RUNTIME_DIR.mkdir(parents=True, exist_ok=True)
    log = open(config.AI_LOG, "a", encoding="utf-8")
    process = subprocess.Popen([str(uvicorn), "main:app", "--port", str(config.AI_SERVICE_PORT)],
                               cwd=config.AI_SERVICE_DIR, stdout=log, stderr=subprocess.STDOUT,
                               start_new_session=True)
    _wait(lambda: _port_open(config.AI_SERVICE_PORT), 60, "servicio de IA")
    print(f"✓ Servicio de IA arrancado (pid {process.pid}, log runtime/ai.log)")
    return {"pid": process.pid, "log": str(config.AI_LOG)}


def _start_backend(previous: dict[str, Any]) -> dict[str, Any]:
    if _port_open(config.BACKEND_PORT):
        if _pid_alive(previous.get("pid")):
            print("✓ Backend ya en marcha")
            return previous
        raise RunnerError(f"El puerto {config.BACKEND_PORT} está ocupado por otro backend (probablemente el de "
                          "desarrollo, contra la base de desarrollo). Detenlo antes de levantar el postest.")
    env = dict(os.environ)
    env["SPRING_DATASOURCE_URL"] = f"jdbc:postgresql://localhost:{config.POSTEST_DB_PORT}/{config.POSTEST_DB_NAME}"
    # Con la integración de Docker Compose activa (perfil dev), Spring Boot conecta con la base del
    # compose y esa conexión tiene prioridad sobre SPRING_DATASOURCE_URL: hay que desactivarla.
    env["SPRING_DOCKER_COMPOSE_ENABLED"] = "false"
    config.RUNTIME_DIR.mkdir(parents=True, exist_ok=True)
    log_offset = config.BACKEND_LOG.stat().st_size if config.BACKEND_LOG.exists() else 0
    log = open(config.BACKEND_LOG, "a", encoding="utf-8")
    process = subprocess.Popen(["./mvnw", "-o", "-q", "spring-boot:run"], cwd=config.BACKEND_DIR, env=env,
                               stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
    print(f"· Arrancando el backend contra {config.POSTEST_DB_NAME} (pid {process.pid})…")
    _wait(lambda: net.backend("GET", "/actuator/health", timeout=5).status == 200, 240, "backend")
    _ensure_postest_database(process.pid, log_offset)
    print(f"✓ Backend listo y conectado a {config.POSTEST_DB_NAME} (log runtime/backend.log)")
    return {"pid": process.pid, "log": str(config.BACKEND_LOG), "datasource": env["SPRING_DATASOURCE_URL"]}


def _ensure_postest_database(pid: int, log_offset: int) -> None:
    """Salvaguarda: si el backend no se conectó a la base del postest, se detiene y se aborta."""
    with open(config.BACKEND_LOG, encoding="utf-8", errors="replace") as log:
        log.seek(log_offset)
        lines = [line for line in log if "Database: jdbc:postgresql" in line]
    expected = f":{config.POSTEST_DB_PORT}/{config.POSTEST_DB_NAME}"
    if not lines or expected not in lines[-1]:
        os.killpg(os.getpgid(pid), signal.SIGTERM)
        found = lines[-1].split("Database: ", 1)[-1].strip() if lines else "desconocida"
        raise RunnerError(f"El backend no se conectó a {config.POSTEST_DB_NAME} (conectó a {found}); "
                          "se detuvo para no tocar otra base.")


def backend_database_ok() -> bool:
    """Si el último arranque del backend registrado en su log fue contra la base del postest."""
    if not config.BACKEND_LOG.exists():
        return False
    with open(config.BACKEND_LOG, encoding="utf-8", errors="replace") as log:
        lines = [line for line in log if "Database: jdbc:postgresql" in line]
    return bool(lines) and f":{config.POSTEST_DB_PORT}/{config.POSTEST_DB_NAME}" in lines[-1]


# --- verificar ----------------------------------------------------------------------------------

def verificar(*, quiet: bool = False) -> bool:
    """Comprueba el entorno. Devuelve True si todo está en verde."""
    checks: list[tuple[str, bool, str]] = []

    def check(name: str, ok: bool, detail: str = "") -> None:
        checks.append((name, ok, detail))

    check("Docker", _docker_ok())
    check("PostgreSQL del postest", _container_state(config.POSTEST_DB_CONTAINER) == "running", config.POSTEST_DB_NAME)
    check("Redis", _container_state(config.REDIS_CONTAINER) == "running")
    health = net.backend("GET", "/actuator/health", timeout=5)
    check("Backend", health.status == 200)
    check("Backend conectado a la base del postest", backend_database_ok(), config.POSTEST_DB_NAME)
    try:
        version = db.scalar("SELECT max(version::int) AS v FROM flyway_schema_history WHERE success")
        check("Migraciones", str(version) == config.EXPECTED_FLYWAY_VERSION, f"V{version}")
    except RunnerError as err:
        check("Migraciones", False, str(err))
    check("Servicio de IA", _port_open(config.AI_SERVICE_PORT))
    check("Ollama", net.request("GET", f"{config.OLLAMA_URL}/api/tags", timeout=5).status == 200)

    token = read_env_file(config.BACKEND_ENV_FILE).get("MERCADOPAGO_ACCESS_TOKEN", "")
    me = net.request("GET", f"{config.MERCADOPAGO_API}/users/me", token=token, timeout=15)
    is_test = me.status == 200 and "test_user" in ((me.body or {}).get("tags") or [])
    check("MercadoPago Sandbox (cuenta de prueba)", is_test, "credenciales de prueba" if is_test else f"HTTP {me.status}")
    if me.date:
        offset = (me.date - datetime.now(timezone.utc)).total_seconds()
        check("Reloj de MercadoPago", abs(offset) <= 5, f"desfase {offset:+.1f} s")

    if not quiet:
        for name, ok, detail in checks:
            print(f"{'✓' if ok else '✗'} {name}{' — ' + detail if detail else ''}")
    return all(ok for _, ok, _ in checks)


def stop_ai_service() -> None:
    """Solo para VAL-010 (IA caída)."""
    state = _load_state()
    pid = state.get("ai_service", {}).get("pid")
    if _pid_alive(pid):
        os.killpg(os.getpgid(pid), signal.SIGTERM)
    _wait(lambda: not _port_open(config.AI_SERVICE_PORT), 30, "parada del servicio de IA")
    state.pop("ai_service", None)
    _save_state(state)


def start_ai_service() -> None:
    state = _load_state()
    state["ai_service"] = _start_ai_service(state.get("ai_service", {}))
    _save_state(state)


# --- detener ------------------------------------------------------------------------------------

def detener() -> None:
    state = _load_state()
    for key in ("backend", "ai_service"):
        pid = state.get(key, {}).get("pid")
        if _pid_alive(pid):
            os.killpg(os.getpgid(pid), signal.SIGTERM)
            print(f"✓ {key} detenido (pid {pid})")
        state.pop(key, None)
    if _container_state(config.POSTEST_DB_CONTAINER) == "running":
        run(["docker", "stop", config.POSTEST_DB_CONTAINER])
        print(f"✓ {config.POSTEST_DB_CONTAINER} detenido (el volumen {config.POSTEST_DB_VOLUME} se conserva)")
    _save_state(state)


# --- respaldo y recreación ----------------------------------------------------------------------

def dump(label: str) -> str:
    """`pg_dump` de la base del postest en `results/respaldos/` (no versionado)."""
    folder = config.RESULTS_DIR / "respaldos"
    folder.mkdir(parents=True, exist_ok=True)
    user = read_env_file(config.BACKEND_ENV_FILE).get("DB_USERNAME", "nomi_user")
    content = run(["docker", "exec", config.POSTEST_DB_CONTAINER, "pg_dump", "-U", user, config.POSTEST_DB_NAME],
                  timeout=300).stdout
    path = folder / f"{label}-{datetime.now().strftime('%Y%m%d-%H%M%S')}.sql"
    path.write_text(content, encoding="utf-8")
    return str(path.relative_to(config.POSTEST_DIR))


def recrear(*, confirmed: bool) -> None:
    """Borra la base del postest y la crea de nuevo: migraciones desde cero, sin pedidos ni cuentas.

    Se usa antes de la ejecución oficial (los pedidos de los ensayos no deben quedar en la base de
    los POST) o cuando cambian las cuentas del seed. Antes guarda un `pg_dump` como evidencia. Solo
    toca el contenedor y el volumen del postest; la base de desarrollo no se usa.
    """
    if not confirmed:
        raise RunnerError("`entorno recrear` borra la base del postest; repítelo con --confirmar")
    open_sessions = [p.stem for p in (config.RUNTIME_DIR / "sesiones").glob("*.json")
                     if json.loads(p.read_text(encoding="utf-8")).get("estado") == "INICIADO"]
    if open_sessions:
        raise RunnerError(f"Hay observaciones en curso ({', '.join(open_sessions)}): ciérralas antes de recrear la base")

    if _container_state(config.POSTEST_DB_CONTAINER) is not None:
        _start_postgres()
        print(f"✓ Copia de seguridad previa en {dump('recrear')}")

    state = _load_state()
    pid = state.get("backend", {}).get("pid")
    if _pid_alive(pid):
        os.killpg(os.getpgid(pid), signal.SIGTERM)
        _wait(lambda: not _port_open(config.BACKEND_PORT), 60, "parada del backend")
        print(f"✓ Backend detenido (pid {pid})")
    state.pop("backend", None)
    _save_state(state)

    run(["docker", "rm", "-f", config.POSTEST_DB_CONTAINER], check=False)
    run(["docker", "volume", "rm", config.POSTEST_DB_VOLUME], check=False)
    if run(["docker", "volume", "inspect", config.POSTEST_DB_VOLUME], check=False).returncode == 0:
        raise RunnerError(f"No se pudo borrar el volumen {config.POSTEST_DB_VOLUME}")
    # Lo que describía la base anterior deja de valer: catálogo cargado, fotos y contraseñas.
    stale = [config.FROZEN_CATALOG_FILE, config.ACCOUNTS_FILE, *config.STATE_DIR.glob("*.json")]
    for path in stale:
        path.unlink(missing_ok=True)
    print(f"✓ Base {config.POSTEST_DB_NAME} borrada (contenedor, volumen, fotos, catálogo cargado y contraseñas)")
    levantar()
