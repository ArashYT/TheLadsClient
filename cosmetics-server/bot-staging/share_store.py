"""
share_store.py - Short share code storage and API endpoints for The Lads Bot.
Stores Lads Client HUD share codes in SQLite with WAL mode, abuse limits,
deduplication, and automatic LRU expiration at capacity.
"""

import os
import sys
import time
import re
import json
import secrets
import hashlib
import sqlite3
import asyncio
import logging
from pathlib import Path
from datetime import datetime, timezone, timedelta
from typing import Optional, Tuple, Dict, List

from aiohttp import web

logger = logging.getLogger("share_store")

# Allowed characters for short share codes (32 chars: excludes 0, 1, I, L, O)
SHARE_ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"
CODE_LENGTH = 6

# Limits
MAX_PAYLOAD_CHARS = 16384
MAX_BODY_BYTES = 20 * 1024  # 20 KB (20,480 bytes)
DEFAULT_MAX_CODES = 200_000
DEFAULT_RETENTION_DAYS = 180

# Rate limits
DEFAULT_CREATE_RATE_LIMIT = 20  # per hour
DEFAULT_CREATE_RATE_WINDOW = 3600.0  # 1 hour in seconds
DEFAULT_LOOKUP_RATE_LIMIT = 120  # per minute
DEFAULT_LOOKUP_RATE_WINDOW = 60.0  # 1 minute in seconds

# IP privacy salt
def _load_or_create_ip_salt() -> str:
    env_salt = os.getenv("LADS_SHARE_IP_SALT") or os.getenv("SHARE_IP_SALT")
    if env_salt:
        return env_salt.strip()
    salt_file = Path(__file__).resolve().parent / ".share_ip_salt"
    try:
        if salt_file.exists():
            existing = salt_file.read_text(encoding="utf-8").strip()
            if len(existing) >= 32:
                return existing
        generated = secrets.token_hex(32)
        salt_file.write_text(generated, encoding="utf-8")
        return generated
    except Exception as e:
        logger.warning("Could not persist .share_ip_salt: %s", e)
        return secrets.token_hex(32)


DEFAULT_IP_SALT = ""

# Base64url characters: alphanumeric, hyphen, underscore, optional padding '='
BASE64URL_PATTERN = re.compile(r"^[A-Za-z0-9\-_]+={0,2}$")


try:
    from client_ip import get_client_ip
except ImportError:
    import sys
    from pathlib import Path
    sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
    from client_ip import get_client_ip


def hash_creator(ip: str, salt: str = DEFAULT_IP_SALT) -> str:
    """IP hashes are no longer stored to protect player privacy (A/16)."""
    return ""


def normalize_code(raw_code: str) -> str:
    """Normalize short code: strip whitespace, remove optional 'LADS-' prefix, uppercase."""
    cleaned = raw_code.strip()
    if cleaned.upper().startswith("LADS-"):
        cleaned = cleaned[5:]
    return cleaned.upper()


def validate_payload(payload: str) -> Tuple[bool, str]:
    """Validate Lads Client share code payload."""
    if not isinstance(payload, str):
        return False, "payload must be a string"
    if not payload.startswith("LADS1:"):
        return False, "payload must start with 'LADS1:'"
    if len(payload) > MAX_PAYLOAD_CHARS:
        return False, f"payload exceeds maximum length of {MAX_PAYLOAD_CHARS} characters"
    data_part = payload[len("LADS1:"):]
    if not data_part:
        return False, "payload data is empty"
    if not BASE64URL_PATTERN.fullmatch(data_part):
        return False, "payload contains invalid base64url characters"
    return True, ""


def json_response(data: dict, status: int = 200, headers: Optional[Dict[str, str]] = None) -> web.Response:
    """Return JSON response with Content-Type: application/json and Cache-Control: no-store."""
    resp_headers = {
        "Content-Type": "application/json",
        "Cache-Control": "no-store",
    }
    if headers:
        resp_headers.update(headers)
    return web.Response(
        body=json.dumps(data).encode("utf-8"),
        status=status,
        headers=resp_headers
    )


class RateLimiter:
    """In-memory sliding-window rate limiter per client IP."""

    def __init__(self):
        self._create_history: Dict[str, List[float]] = {}
        self._lookup_history: Dict[str, List[float]] = {}
        self._lock = asyncio.Lock()

    async def check_create(
        self, ip: str, limit: int = DEFAULT_CREATE_RATE_LIMIT, window: float = DEFAULT_CREATE_RATE_WINDOW
    ) -> Tuple[bool, int]:
        async with self._lock:
            return self._check(self._create_history, ip, limit, window)

    async def check_lookup(
        self, ip: str, limit: int = DEFAULT_LOOKUP_RATE_LIMIT, window: float = DEFAULT_LOOKUP_RATE_WINDOW
    ) -> Tuple[bool, int]:
        async with self._lock:
            return self._check(self._lookup_history, ip, limit, window)

    def _check(
        self, history: Dict[str, List[float]], ip: str, limit: int, window: float
    ) -> Tuple[bool, int]:
        now = time.time()
        cutoff = now - window

        # Periodic dictionary pruning if tracked IP count is high
        if len(history) > 10000:
            stale_keys = [k for k, timestamps in history.items() if not timestamps or timestamps[-1] < cutoff]
            for k in stale_keys:
                del history[k]

        records = history.setdefault(ip, [])
        history[ip] = [t for t in records if t > cutoff]
        records = history[ip]

        if len(records) >= limit:
            oldest = records[0]
            retry_after = max(1, int(window - (now - oldest) + 0.999))
            return False, retry_after

        records.append(now)
        return True, 0

    def reset(self):
        self._create_history.clear()
        self._lookup_history.clear()


def _get_connection(db_path: Path) -> sqlite3.Connection:
    conn = sqlite3.connect(str(db_path), timeout=10.0)
    conn.execute("PRAGMA journal_mode=WAL;")
    conn.execute("PRAGMA busy_timeout=5000;")
    return conn


def init_db(db_path: Path):
    """Initialize SQLite database schema and WAL mode."""
    db_path.parent.mkdir(parents=True, exist_ok=True)
    with _get_connection(db_path) as conn:
        conn.execute("""
            CREATE TABLE IF NOT EXISTS codes (
                code TEXT PRIMARY KEY,
                payload TEXT NOT NULL,
                sha256 TEXT UNIQUE NOT NULL,
                created TEXT NOT NULL,
                last_used TEXT NOT NULL,
                creator_hash TEXT NOT NULL
            );
        """)
        conn.execute("CREATE INDEX IF NOT EXISTS idx_codes_sha256 ON codes(sha256);")
        conn.execute("CREATE INDEX IF NOT EXISTS idx_codes_last_used ON codes(last_used);")


class StoreResult:
    def __init__(self, code: Optional[str] = None, is_new: bool = False, error: Optional[str] = None):
        self.code = code
        self.is_new = is_new
        self.error = error


def _store_code_sync(
    db_path: Path,
    payload: str,
    creator_hash: str,
    max_codes: int,
    retention_days: int
) -> StoreResult:
    payload_hash = hashlib.sha256(payload.encode("utf-8")).hexdigest()
    now_iso = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

    with _get_connection(db_path) as conn:
        cur = conn.cursor()

        # 1. Dedupe check: same payload returns existing code
        cur.execute("SELECT code FROM codes WHERE sha256 = ?", (payload_hash,))
        row = cur.fetchone()
        if row:
            return StoreResult(code=row[0], is_new=False)

        # 2. Check global cap
        cur.execute("SELECT COUNT(*) FROM codes")
        total_count = cur.fetchone()[0]

        if total_count >= max_codes:
            cutoff_iso = (datetime.now(timezone.utc) - timedelta(days=retention_days)).strftime("%Y-%m-%dT%H:%M:%SZ")
            # Delete least-recently-used codes older than retention_days
            cur.execute("DELETE FROM codes WHERE last_used < ?", (cutoff_iso,))
            deleted = cur.rowcount
            if deleted <= 0:
                # Evict oldest least-recently-used row(s) so table never permanently wedges at capacity
                evict_count = max(1, (total_count - max_codes) + 1)
                cur.execute(
                    "DELETE FROM codes WHERE code IN ("
                    "SELECT code FROM codes ORDER BY last_used ASC, created ASC LIMIT ?"
                    ")",
                    (evict_count,)
                )
                if cur.rowcount <= 0:
                    return StoreResult(error="capacity_reached")

        # 3. Generate random code with collision retry
        for attempt in range(20):
            candidate = "".join(secrets.choice(SHARE_ALPHABET) for _ in range(CODE_LENGTH))
            try:
                cur.execute(
                    "INSERT INTO codes (code, payload, sha256, created, last_used, creator_hash) "
                    "VALUES (?, ?, ?, ?, ?, ?)",
                    (candidate, payload, payload_hash, now_iso, now_iso, creator_hash)
                )
                conn.commit()
                return StoreResult(code=candidate, is_new=True)
            except sqlite3.IntegrityError:
                # Collision check: if sha256 collided from concurrent insert, return existing
                cur.execute("SELECT code FROM codes WHERE sha256 = ?", (payload_hash,))
                existing = cur.fetchone()
                if existing:
                    return StoreResult(code=existing[0], is_new=False)
                # Collision was on primary key code; loop and retry with fresh code
                continue

        raise RuntimeError("Failed to generate unique share code after 20 attempts")


def _get_code_sync(db_path: Path, code: str) -> Optional[str]:
    now_iso = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    with _get_connection(db_path) as conn:
        cur = conn.cursor()
        cur.execute("SELECT payload FROM codes WHERE code = ?", (code,))
        row = cur.fetchone()
        if not row:
            return None
        cur.execute("UPDATE codes SET last_used = ? WHERE code = ?", (now_iso, code))
        conn.commit()
        return row[0]


class ShareStore:
    """Manages short share code lifecycle, database operations, and HTTP route handlers."""

    def __init__(
        self,
        db_path: Optional[Path] = None,
        max_codes: int = DEFAULT_MAX_CODES,
        retention_days: int = DEFAULT_RETENTION_DAYS,
        create_rate_limit: int = DEFAULT_CREATE_RATE_LIMIT,
        create_rate_window: float = DEFAULT_CREATE_RATE_WINDOW,
        lookup_rate_limit: int = DEFAULT_LOOKUP_RATE_LIMIT,
        lookup_rate_window: float = DEFAULT_LOOKUP_RATE_WINDOW,
        salt: str = DEFAULT_IP_SALT,
    ):
        if db_path is None:
            self.db_path = Path(__file__).resolve().parent / "share.db"
        else:
            self.db_path = Path(db_path)
        self.max_codes = max_codes
        self.retention_days = retention_days
        self.create_rate_limit = create_rate_limit
        self.create_rate_window = create_rate_window
        self.lookup_rate_limit = lookup_rate_limit
        self.lookup_rate_window = lookup_rate_window
        self.salt = salt
        self.rate_limiter = RateLimiter()
        init_db(self.db_path)

    async def handle_post_share(self, request: web.Request) -> web.Response:
        try:
            client_ip = get_client_ip(request)
            allowed, retry_after = await self.rate_limiter.check_create(
                client_ip, self.create_rate_limit, self.create_rate_window
            )
            if not allowed:
                return json_response(
                    {"error": "rate limit exceeded"},
                    status=429,
                    headers={"Retry-After": str(retry_after)}
                )

            # Check request body size limit
            if request.content_length is not None and request.content_length > MAX_BODY_BYTES:
                return json_response({"error": "request body too large"}, status=413)

            body_bytes = await request.read()
            if len(body_bytes) > MAX_BODY_BYTES:
                return json_response({"error": "request body too large"}, status=413)

            try:
                data = json.loads(body_bytes.decode("utf-8"))
            except (json.JSONDecodeError, UnicodeDecodeError):
                return json_response({"error": "invalid json"}, status=400)

            if not isinstance(data, dict):
                return json_response({"error": "request body must be a json object"}, status=400)

            payload = data.get("payload")
            if not isinstance(payload, str):
                return json_response({"error": "missing or invalid payload"}, status=400)

            valid, err_msg = validate_payload(payload)
            if not valid:
                return json_response({"error": err_msg}, status=400)

            creator_hash = hash_creator(client_ip, self.salt)

            result: StoreResult = await asyncio.to_thread(
                _store_code_sync,
                self.db_path,
                payload,
                creator_hash,
                self.max_codes,
                self.retention_days
            )

            if result.error == "capacity_reached":
                return json_response({"error": "storage capacity reached"}, status=503)

            if result.error or not result.code:
                logger.error("Failed to store code: %s", result.error)
                return json_response({"error": "internal server error"}, status=500)

            return json_response({"code": result.code}, status=200)

        except web.HTTPException:
            raise
        except Exception as e:
            logger.exception("Unexpected error in handle_post_share: %s", e)
            print(f"[share_store] Unexpected error in POST /share: {e}", file=sys.stderr, flush=True)
            return json_response({"error": "internal server error"}, status=500)

    async def handle_get_share(self, request: web.Request) -> web.Response:
        try:
            client_ip = get_client_ip(request)
            allowed, retry_after = await self.rate_limiter.check_lookup(
                client_ip, self.lookup_rate_limit, self.lookup_rate_window
            )
            if not allowed:
                return json_response(
                    {"error": "rate limit exceeded"},
                    status=429,
                    headers={"Retry-After": str(retry_after)}
                )

            raw_code = request.match_info.get("code", "")
            code = normalize_code(raw_code)

            if not code or len(code) != CODE_LENGTH or not all(c in SHARE_ALPHABET for c in code):
                return json_response({"error": "not found"}, status=404)

            payload = await asyncio.to_thread(_get_code_sync, self.db_path, code)
            if payload is None:
                return json_response({"error": "not found"}, status=404)

            return json_response({"payload": payload}, status=200)

        except web.HTTPException:
            raise
        except Exception as e:
            logger.exception("Unexpected error in handle_get_share: %s", e)
            print(f"[share_store] Unexpected error in GET /share: {e}", file=sys.stderr, flush=True)
            return json_response({"error": "internal server error"}, status=500)

    def attach_routes(self, app: web.Application):
        app.router.add_post("/share", self.handle_post_share)
        app.router.add_get("/share/{code}", self.handle_get_share)


_default_store: Optional[ShareStore] = None


def get_default_store() -> ShareStore:
    global _default_store
    if _default_store is None:
        _default_store = ShareStore()
    return _default_store


async def handle_post_share(request: web.Request) -> web.Response:
    return await get_default_store().handle_post_share(request)


async def handle_get_share(request: web.Request) -> web.Response:
    return await get_default_store().handle_get_share(request)


def setup_share_routes(app: web.Application, store: Optional[ShareStore] = None):
    """Add /share and /share/{code} routes to an aiohttp application."""
    if store is None:
        store = get_default_store()
    store.attach_routes(app)


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description="Standalone Share Store Server")
    parser.add_argument("--port", type=int, default=18085, help="Port to listen on (default 18085)")
    parser.add_argument("--host", type=str, default="127.0.0.1", help="Host to listen on (default 127.0.0.1)")
    args = parser.parse_args()

    app = web.Application()
    setup_share_routes(app)
    print(f"Starting standalone share store on http://{args.host}:{args.port}", flush=True)
    web.run_app(app, host=args.host, port=args.port)
