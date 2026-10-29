"""Lads cosmetics relay & backend (1.9.11/Phase 2): which Lads Client players are emoting and wearing cosmetics,
per Minecraft server ("room"), so every Lads player on the same server sees it.

Phase 2 additions:
- SQLite store for entitlements(uuid, item_id, source, granted_at, revoked_at) and equipped(uuid, slot, item_id).
- Profile GET /cosmetics/profile/{uuid} (everyone owns "lads:groove").
- WebSocket equip message {"t":"equip","slot","item"} and authenticated HTTP POST /cosmetics/equip.
- Tebex webhook endpoint POST /cosmetics/tebex (fail-closed, idempotent, signature-verified, payment.completed / refund).
- Link-code flow over authenticated WebSocket {"t":"link"} (and authenticated GET /cosmetics/link/start).
- Single-use link token redemption to prevent replay attacks.
- Admin CLI: grant / revoke / list for staff.
"""
from __future__ import annotations

import argparse
import asyncio
import hashlib
import hmac
import json
import logging
import os
from pathlib import Path
import re
import secrets
import sqlite3
import sys
import time
from typing import Any

import aiohttp
from aiohttp import web

FREE = ["lads:groove", "lads:wave", "lads:clap", "lads:floss", "lads:cheer", "lads:bow", "lads:facepalm", "lads:salute", "lads:shrug", "lads:dab", "lads:taunt"]  # everyone owns these
EMOTES = frozenset(FREE)  # allow-list (the client's EmoteLibrary): any other free emote id is rejected unless owned
HAS_JOINED = "https://sessionserver.mojang.com/session/minecraft/hasJoined"
AUTH_TIMEOUT = 15
IDLE_TIMEOUT = 90
HEARTBEAT = 30
MAX_PER_IP = 10
MAX_RATE = 8  # messages per second per socket
MAX_FRAME = 1024
UUID_RE = re.compile(r"[0-9a-f]{32}")
NAME_RE = re.compile(r"[A-Za-z0-9_]{1,16}")
ROOM_RE = re.compile(r"[0-9a-f]{64}")
ALLOWED_SLOTS: frozenset[str] = frozenset({"emote", "cape", "hat", "glasses"})
ITEM_ID_RE = re.compile(r"^[a-z0-9_]{1,32}:[a-z0-9_]{1,32}$")
LINK_ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"
LINK_CODE_TTL = 600  # 10 minutes

def load_catalog_from_file(path: str | Path | None) -> dict[int | str, str] | None:
    """Load Tebex package -> cosmetic item catalog from a JSON file.
    Keys can be integer or string package IDs.
    """
    if not path:
        return None
    p = Path(path)
    if not p.is_file():
        log.warning("COSMETICS_CATALOG file not found: %s", path)
        return None
    try:
        with open(p, "r", encoding="utf-8") as f:
            data = json.load(f)
            if isinstance(data, dict):
                catalog: dict[int | str, str] = {}
                for k, v in data.items():
                    if isinstance(v, str):
                        catalog[k] = v
                        try:
                            catalog[int(k)] = v
                        except (ValueError, TypeError):
                            pass
                return catalog
            log.warning("COSMETICS_CATALOG JSON root must be a dictionary, got %s", type(data))
            return None
    except Exception as e:
        log.warning("Failed to parse COSMETICS_CATALOG JSON file %s: %s", path, e)
        return None


EMOTE_NAMES: dict[str, str] = {
    "lads:groove": "Groove",
    "lads:wave": "Wave",
    "lads:clap": "Clap",
    "lads:floss": "Floss",
    "lads:cheer": "Cheer",
    "lads:bow": "Bow",
    "lads:facepalm": "Facepalm",
    "lads:salute": "Salute",
    "lads:shrug": "Shrug",
    "lads:dab": "Dab",
    "lads:taunt": "Taunt",
}


def categorize_item(item_id: str) -> str:
    """Categorize a cosmetic item into Capes, Emotes, or Others."""
    lower = item_id.lower()
    if ":cape" in lower or "cape_" in lower:
        return "Capes"
    if item_id in EMOTES or ":emote" in lower or "emote_" in lower:
        return "Emotes"
    return "Others"


def format_item_name(item_id: str) -> str:
    """Derive a friendly display name from an item ID."""
    if item_id in EMOTE_NAMES:
        return EMOTE_NAMES[item_id]
    part = item_id.split(":", 1)[-1]
    words = part.replace("_", " ").split()
    return " ".join(w.capitalize() for w in words)


_DEFAULT_CATALOG: dict[int | str, str] | None = None


def get_all_cosmetics(catalog_dict: dict[int | str, str] | None = None) -> list[dict[str, str]]:
    """Return all known cosmetic items from the real sources: the FREE emotes allow-list and COSMETICS_CATALOG."""
    seen: set[str] = set()
    result: list[dict[str, str]] = []

    # 1. Built-in emotes from FREE allow-list (in order)
    for emote_id in FREE:
        if emote_id not in seen:
            seen.add(emote_id)
            result.append({
                "id": emote_id,
                "name": format_item_name(emote_id),
                "category": "Emotes",
                "free": True,
            })

    # 2. Dynamic items from catalog (parameter > module default > env COSMETICS_CATALOG)
    active_cat = catalog_dict
    if active_cat is None:
        active_cat = _DEFAULT_CATALOG
    if active_cat is None:
        env_cat = os.getenv("COSMETICS_CATALOG")
        if env_cat:
            active_cat = load_catalog_from_file(env_cat)

    if active_cat:
        for val in active_cat.values():
            if isinstance(val, str) and val not in seen and ITEM_ID_RE.fullmatch(val):
                seen.add(val)
                result.append({
                    "id": val,
                    "name": format_item_name(val),
                    "category": categorize_item(val),
                    "free": val in FREE,
                })
    return result


def get_all_cosmetic_ids(catalog_dict: dict[int | str, str] | None = None) -> list[str]:
    """Return list of all known cosmetic item IDs."""
    return [c["id"] for c in get_all_cosmetics(catalog_dict)]


log = logging.getLogger("cosmetics")


def server_id(nonce: str) -> str:
    """Minecraft-style signed hex SHA-1 of the nonce: the serverId the client joins with on Mojang's session server."""
    digest = hashlib.sha1(("lads-cosmetics:" + nonce).encode()).digest()
    return format(int.from_bytes(digest, "big", signed=True), "x")


def plain_uuid(value) -> str | None:
    text = str(value or "").replace("-", "").lower()
    return text if UUID_RE.fullmatch(text) else None


async def has_joined(name: str, sid: str) -> str | None:
    """Mojang's answer: the profile id that joined with this serverId, or None. The tests replace this."""
    try:
        session = await get_mojang_session()
        async with session.get(HAS_JOINED, params={"username": name, "serverId": sid}) as response:
            if response.status == 429 or response.status >= 500:
                raise MojangUnavailableError(f"Mojang status {response.status}")
            if response.status != 200:
                return None
            body = await response.json(content_type=None)
            return plain_uuid(body.get("id")) if isinstance(body, dict) else None
    except aiohttp.ClientError as e:
        raise MojangUnavailableError(f"Mojang connection error: {e}") from e


from client_ip import client_ip, get_client_ip, normalize_ip as get_auth_ip_key

class MojangUnavailableError(Exception):
    """Raised when Mojang sessionserver returns 429, 5xx, or network failure."""
    pass

_MOJANG_SEMAPHORE = asyncio.Semaphore(8)
_mojang_session: aiohttp.ClientSession | None = None

async def get_mojang_session() -> aiohttp.ClientSession:
    global _mojang_session
    if _mojang_session is None or _mojang_session.closed:
        connector = aiohttp.TCPConnector(limit=16, ttl_dns_roundrobin=True)
        _mojang_session = aiohttp.ClientSession(
            connector=connector,
            timeout=aiohttp.ClientTimeout(total=5)
        )
    return _mojang_session


def verify_tebex_signature(raw_body: bytes, signature: str | None, secret: str | None) -> bool:
    """Validate Tebex webhook signature. Fail closed: secret and signature are strictly required.
    Per Tebex docs:
    1. SHA-256 hash the raw body to hex string.
    2. HMAC-SHA256 the hex string using the webhook secret key.
    Also accepts standard single-step HMAC-SHA256 for cross-version compatibility.
    """
    if not secret or not signature:
        return False
    sig = signature.strip()
    # Tebex two-step HMAC
    sig_bytes = sig.encode("ascii", errors="replace")
    body_hash = hashlib.sha256(raw_body).hexdigest().encode("ascii")
    expected_two_step = hmac.new(secret.encode("utf-8"), body_hash, hashlib.sha256).hexdigest().encode("ascii")
    if hmac.compare_digest(expected_two_step, sig_bytes):
        return True
    # Standard single-step HMAC
    expected_single_step = hmac.new(secret.encode("utf-8"), raw_body, hashlib.sha256).hexdigest().encode("ascii")
    if hmac.compare_digest(expected_single_step, sig_bytes):
        return True
    return False


def extract_buyer_raw_uuid(subject: dict) -> str | None:
    """Extract raw buyer Minecraft UUID from Tebex payload subject.
    Checks customer.username.id (official Tebex Minecraft checkout payload format),
    player.uuid / player.id, and customer.uuid.
    """
    # 1. Real Tebex Minecraft payload: customer.username.id
    cust = subject.get("customer")
    if isinstance(cust, dict):
        uname = cust.get("username")
        if isinstance(uname, dict):
            u = plain_uuid(uname.get("id"))
            if u:
                return u
        u = plain_uuid(cust.get("uuid"))
        if u:
            return u

    # 2. Player object (player.uuid or player.id)
    player = subject.get("player")
    if isinstance(player, dict):
        for key in ("uuid", "id"):
            u = plain_uuid(player.get(key))
            if u:
                return u

    # 3. Direct subject keys
    for key in ("uuid", "player_uuid"):
        u = plain_uuid(subject.get(key))
        if u:
            return u

    return None


def extract_link_code(subject: dict) -> str | None:
    """Extract in-game 6-character link code from Tebex payload subject."""
    for container in (subject.get("customer"), subject.get("custom"), subject):
        if isinstance(container, dict):
            code = container.get("link_code") or container.get("code")
            if code and isinstance(code, str) and len(code.strip()) == 6:
                return code.strip().upper()
    return None


def resolve_package_item_id(pkg: dict, catalog: dict[int | str, str] | None) -> str | None:
    """Resolve Tebex package to an explicit, safe cosmetic item ID (<= 64 chars, [a-z0-9_:]).
    Items come ONLY from explicit catalog lookup by package ID.
    No bypass for product names or unmapped IDs.
    """
    if not catalog or not isinstance(pkg, dict):
        return None

    # Look up by package ID in catalog
    raw_id = pkg.get("id")
    if raw_id is not None:
        if raw_id in catalog:
            item_id = catalog[raw_id]
            if isinstance(item_id, str) and len(item_id) <= 64 and ITEM_ID_RE.fullmatch(item_id):
                return item_id
        try:
            pkg_int = int(raw_id)
            if pkg_int in catalog:
                item_id = catalog[pkg_int]
                if isinstance(item_id, str) and len(item_id) <= 64 and ITEM_ID_RE.fullmatch(item_id):
                    return item_id
        except (ValueError, TypeError):
            pass
        pkg_str = str(raw_id)
        if pkg_str in catalog:
            item_id = catalog[pkg_str]
            if isinstance(item_id, str) and len(item_id) <= 64 and ITEM_ID_RE.fullmatch(item_id):
                return item_id

    return None


# ---------------------------------------------------------------------------
# SQLite Store: entitlements, equipped, transactions, link_codes
# ---------------------------------------------------------------------------

class CosmeticsStore:
    """SQLite store using WAL mode and asyncio.to_thread pattern (matching share_store.py)."""

    def __init__(self, db_path: Path | str | None = None):
        if db_path is None:
            env_db = os.getenv("COSMETICS_DB")
            if env_db:
                self.db_path = Path(env_db)
            else:
                self.db_path = Path(__file__).resolve().parent / "cosmetics.db"
        else:
            self.db_path = Path(db_path)
        self.init_db()

    def _get_connection(self) -> sqlite3.Connection:
        conn = sqlite3.connect(str(self.db_path), timeout=10.0)
        conn.execute("PRAGMA journal_mode=WAL;")
        conn.execute("PRAGMA busy_timeout=5000;")
        conn.row_factory = sqlite3.Row
        return conn

    def init_db(self) -> None:
        self.db_path.parent.mkdir(parents=True, exist_ok=True)
        with self._get_connection() as conn:
            conn.execute("""
                CREATE TABLE IF NOT EXISTS entitlements (
                    uuid TEXT NOT NULL,
                    item_id TEXT NOT NULL,
                    source TEXT NOT NULL,
                    granted_at INTEGER NOT NULL,
                    revoked_at INTEGER,
                    PRIMARY KEY(uuid, item_id)
                );
            """)
            conn.execute("CREATE INDEX IF NOT EXISTS idx_entitlements_uuid ON entitlements(uuid);")
            conn.execute("CREATE INDEX IF NOT EXISTS idx_entitlements_item ON entitlements(item_id);")

            conn.execute("""
                CREATE TABLE IF NOT EXISTS equipped (
                    uuid TEXT NOT NULL,
                    slot TEXT NOT NULL,
                    item_id TEXT NOT NULL,
                    PRIMARY KEY(uuid, slot)
                );
            """)
            conn.execute("CREATE INDEX IF NOT EXISTS idx_equipped_uuid ON equipped(uuid);")

            conn.execute("""
                CREATE TABLE IF NOT EXISTS transactions (
                    transaction_id TEXT PRIMARY KEY,
                    event_type TEXT NOT NULL,
                    uuid TEXT NOT NULL,
                    items_json TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                );
            """)
            conn.execute("CREATE INDEX IF NOT EXISTS idx_transactions_uuid ON transactions(uuid);")

            conn.execute("""
                CREATE TABLE IF NOT EXISTS link_codes (
                    code TEXT PRIMARY KEY,
                    uuid TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    expires_at INTEGER NOT NULL
                );
            """)
            conn.execute("CREATE INDEX IF NOT EXISTS idx_link_codes_uuid ON link_codes(uuid);")

            conn.execute("""
                CREATE TABLE IF NOT EXISTS all_access (
                    uuid TEXT PRIMARY KEY,
                    granted_by TEXT NOT NULL,
                    granted_at INTEGER NOT NULL
                );
            """)
            conn.execute("CREATE INDEX IF NOT EXISTS idx_all_access_uuid ON all_access(uuid);")

    # --- Synchronous Worker Methods ---

    def has_all_access_sync(self, uuid: str) -> bool:
        clean = plain_uuid(uuid)
        if not clean:
            return False
        with self._get_connection() as conn:
            cur = conn.cursor()
            cur.execute("SELECT 1 FROM all_access WHERE uuid = ?;", (clean,))
            return cur.fetchone() is not None

    def get_all_access_details_sync(self, uuid: str) -> dict | None:
        clean = plain_uuid(uuid)
        if not clean:
            return None
        with self._get_connection() as conn:
            cur = conn.cursor()
            cur.execute("SELECT granted_by, granted_at FROM all_access WHERE uuid = ?;", (clean,))
            row = cur.fetchone()
            if row:
                return {"granted_by": row[0], "granted_at": row[1]}
            return None

    def set_all_access_sync(
        self,
        uuid_raw: str,
        enabled: bool,
        granted_by: str = "admin",
        timestamp: int | None = None
    ) -> bool:
        uuid = plain_uuid(uuid_raw)
        if not uuid:
            raise ValueError(f"Invalid UUID: {uuid_raw}")
        if timestamp is None:
            timestamp = int(time.time())
        with self._get_connection() as conn:
            conn.isolation_level = None
            conn.execute("BEGIN IMMEDIATE;")
            try:
                cur = conn.cursor()
                cur.execute("SELECT 1 FROM all_access WHERE uuid = ?;", (uuid,))
                had_it = cur.fetchone() is not None
                if enabled:
                    conn.execute("""
                        INSERT INTO all_access (uuid, granted_by, granted_at)
                        VALUES (?, ?, ?)
                        ON CONFLICT(uuid) DO UPDATE SET
                            granted_by = excluded.granted_by,
                            granted_at = excluded.granted_at;
                    """, (uuid, granted_by, timestamp))
                    event = "admin.all_access.grant"
                else:
                    conn.execute("DELETE FROM all_access WHERE uuid = ?;", (uuid,))
                    event = "admin.all_access.revoke"

                # Audit log in transactions
                tx_id = f"allaccess_{uuid}_{timestamp}_{secrets.token_hex(4)}"
                details = json.dumps({"enabled": enabled, "granted_by": granted_by, "action": "all_access"})
                conn.execute("""
                    INSERT INTO transactions (transaction_id, event_type, uuid, items_json, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?);
                """, (tx_id, event, uuid, details, timestamp, timestamp))
                conn.execute("COMMIT;")
                return had_it != enabled
            except Exception:
                conn.execute("ROLLBACK;")
                raise

    def grant_sync(self, uuid: str, item_id: str, source: str = "admin", granted_at: int | None = None) -> None:
        clean = plain_uuid(uuid) or uuid
        if granted_at is None:
            granted_at = int(time.time())
        with self._get_connection() as conn:
            conn.isolation_level = None
            conn.execute("BEGIN IMMEDIATE;")
            try:
                conn.execute("""
                    INSERT INTO entitlements (uuid, item_id, source, granted_at, revoked_at)
                    VALUES (?, ?, ?, ?, NULL)
                    ON CONFLICT(uuid, item_id) DO UPDATE SET
                        source = excluded.source,
                        granted_at = excluded.granted_at,
                        revoked_at = NULL;
                """, (clean, item_id, source, granted_at))
                # Audit log in transactions
                tx_id = f"grant_{clean}_{item_id}_{granted_at}_{secrets.token_hex(4)}"
                details = json.dumps({"item_id": item_id, "source": source, "action": "grant"})
                conn.execute("""
                    INSERT INTO transactions (transaction_id, event_type, uuid, items_json, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?);
                """, (tx_id, "admin.grant", clean, details, granted_at, granted_at))
                conn.execute("COMMIT;")
            except Exception:
                conn.execute("ROLLBACK;")
                raise

    def revoke_sync(self, uuid: str, item_id: str, revoked_at: int | None = None, actor: str = "admin") -> None:
        clean = plain_uuid(uuid) or uuid
        if revoked_at is None:
            revoked_at = int(time.time())
        with self._get_connection() as conn:
            conn.isolation_level = None
            conn.execute("BEGIN IMMEDIATE;")
            try:
                conn.execute("""
                    UPDATE entitlements
                    SET revoked_at = ?
                    WHERE uuid = ? AND item_id = ? AND revoked_at IS NULL;
                """, (revoked_at, clean, item_id))
                conn.execute("DELETE FROM equipped WHERE uuid = ? AND item_id = ?;", (clean, item_id))
                # Audit log in transactions
                tx_id = f"revoke_{clean}_{item_id}_{revoked_at}_{secrets.token_hex(4)}"
                details = json.dumps({"item_id": item_id, "actor": actor, "action": "revoke"})
                conn.execute("""
                    INSERT INTO transactions (transaction_id, event_type, uuid, items_json, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?);
                """, (tx_id, "admin.revoke", clean, details, revoked_at, revoked_at))
                conn.execute("COMMIT;")
            except Exception:
                conn.execute("ROLLBACK;")
                raise

    def has_entitlement_sync(self, uuid: str, item_id: str) -> bool:
        clean = plain_uuid(uuid)
        if not clean:
            return False
        # Free items are free for everyone
        if item_id in FREE:
            return True
        # Single ownership check: all-access users own EVERY current and future item!
        if self.has_all_access_sync(clean):
            return True
        with self._get_connection() as conn:
            cur = conn.cursor()
            cur.execute("""
                SELECT 1 FROM entitlements
                WHERE uuid = ? AND item_id = ? AND revoked_at IS NULL;
            """, (clean, item_id))
            return cur.fetchone() is not None

    def get_profile_sync(self, uuid: str) -> dict:
        clean = plain_uuid(uuid) or uuid
        is_all_access = self.has_all_access_sync(clean)
        with self._get_connection() as conn:
            cur = conn.cursor()
            cur.execute("""
                SELECT item_id FROM entitlements
                WHERE uuid = ? AND revoked_at IS NULL;
            """, (clean,))
            owned = [row[0] for row in cur.fetchall()]
            for f in FREE:
                if f not in owned:
                    owned.insert(0, f)
            if is_all_access:
                for item in get_all_cosmetic_ids():
                    if item not in owned:
                        owned.append(item)
            cur.execute("SELECT slot, item_id FROM equipped WHERE uuid = ?;", (clean,))
            equipped = {row[0]: row[1] for row in cur.fetchall()}
            profile = {"owned": owned, "equipped": equipped}
            if is_all_access:
                profile["all_access"] = True
            return profile

    def set_equipped_sync(self, uuid: str, slot: str, item_id: str) -> None:
        if slot not in ALLOWED_SLOTS:
            raise ValueError(f"Invalid slot '{slot}', must be one of {sorted(ALLOWED_SLOTS)}")
        with self._get_connection() as conn:
            conn.execute("""
                INSERT INTO equipped (uuid, slot, item_id)
                VALUES (?, ?, ?)
                ON CONFLICT(uuid, slot) DO UPDATE SET item_id = excluded.item_id;
            """, (uuid, slot, item_id))
            conn.commit()

    def remove_equipped_sync(self, uuid: str, slot: str) -> None:
        if slot not in ALLOWED_SLOTS:
            raise ValueError(f"Invalid slot '{slot}', must be one of {sorted(ALLOWED_SLOTS)}")
        with self._get_connection() as conn:
            conn.execute("DELETE FROM equipped WHERE uuid = ? AND slot = ?;", (uuid, slot))
            conn.commit()

    def process_tebex_payment_sync(
        self,
        transaction_id: str,
        payload_str: str,
        raw_uuid: str | None,
        link_code: str | None,
        items: list[str]
    ) -> dict:
        now = int(time.time())
        with self._get_connection() as conn:
            conn.isolation_level = None
            conn.execute("BEGIN IMMEDIATE;")
            try:
                cur = conn.cursor()

                # 1. Deduplication check: check if transaction already exists
                cur.execute("SELECT uuid, items_json FROM transactions WHERE transaction_id = ?;", (transaction_id,))
                row = cur.fetchone()
                if row:
                    conn.execute("COMMIT;")
                    return {
                        "status": "already_processed",
                        "transaction_id": transaction_id,
                        "uuid": row[0],
                        "items": json.loads(row[1]) if row[1] else []
                    }

                # 2. Resolve buyer UUID
                buyer_uuid = None
                if raw_uuid:
                    buyer_uuid = plain_uuid(raw_uuid)
                elif link_code:
                    normalized = link_code.strip().upper()
                    cur.execute(
                        "DELETE FROM link_codes WHERE code = ? AND expires_at > ? RETURNING uuid;",
                        (normalized, now)
                    )
                    code_row = cur.fetchone()
                    if code_row:
                        buyer_uuid = code_row[0]

                if not buyer_uuid:
                    conn.execute("ROLLBACK;")
                    return {"status": "error", "error": "missing or unresolvable buyer uuid"}

                # 3. Record transaction
                cur.execute("""
                    INSERT INTO transactions (transaction_id, event_type, uuid, items_json, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?);
                """, (transaction_id, "payment.completed", buyer_uuid, json.dumps(items), now, now))

                # 4. Grant entitlements
                granted = []
                for item_id in items:
                    cur.execute("""
                        INSERT INTO entitlements (uuid, item_id, source, granted_at, revoked_at)
                        VALUES (?, ?, ?, ?, NULL)
                        ON CONFLICT(uuid, item_id) DO UPDATE SET
                            revoked_at = NULL,
                            granted_at = excluded.granted_at,
                            source = excluded.source;
                    """, (buyer_uuid, item_id, f"tebex:{transaction_id}", now))
                    granted.append(item_id)

                conn.execute("COMMIT;")
                return {
                    "status": "granted",
                    "transaction_id": transaction_id,
                    "uuid": buyer_uuid,
                    "items": granted
                }
            except Exception:
                conn.execute("ROLLBACK;")
                raise

    def process_tebex_refund_sync(
        self,
        transaction_id: str,
        event_type: str,
        payload_str: str,
        raw_uuid: str | None = None,
        items: list[str] | None = None
    ) -> dict | None:
        now = int(time.time())
        with self._get_connection() as conn:
            conn.isolation_level = None
            conn.execute("BEGIN IMMEDIATE;")
            try:
                cur = conn.cursor()
                cur.execute("SELECT uuid, items_json, event_type FROM transactions WHERE transaction_id = ?;", (transaction_id,))
                row = cur.fetchone()
                if not row:
                    # Check if entitlements exist with source = tebex:<transaction_id>
                    cur.execute("SELECT uuid, item_id FROM entitlements WHERE source = ?;", (f"tebex:{transaction_id}",))
                    ent_rows = cur.fetchall()
                    if not ent_rows:
                        if not raw_uuid and not items:
                            conn.execute("COMMIT;")
                            return None
                        uuid = plain_uuid(raw_uuid)
                        items_to_revoke = items or []
                    else:
                        uuid = ent_rows[0][0]
                        items_to_revoke = [r[1] for r in ent_rows]
                    prev_event = "payment.completed"
                else:
                    uuid = row[0]
                    items_to_revoke = items if items else json.loads(row[1])
                    prev_event = row[2]

                if prev_event in ("payment.refunded", "payment.dispute.opened", "payment.disputed", "payment.chargeback", "payment.dispute.lost"):
                    conn.execute("COMMIT;")
                    return {"already_refunded": True, "uuid": uuid, "items": items_to_revoke}

                revoked = []
                if uuid:
                    for item_id in items_to_revoke:
                        cur.execute("""
                            UPDATE entitlements
                            SET revoked_at = ?
                            WHERE uuid = ? AND item_id = ? AND revoked_at IS NULL;
                        """, (now, uuid, item_id))
                        cur.execute("DELETE FROM equipped WHERE uuid = ? AND item_id = ?;", (uuid, item_id))
                        revoked.append(item_id)

                if row:
                    cur.execute("""
                        UPDATE transactions
                        SET event_type = ?, updated_at = ?
                        WHERE transaction_id = ?;
                    """, (event_type, now, transaction_id))
                else:
                    cur.execute("""
                        INSERT INTO transactions (transaction_id, event_type, uuid, items_json, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?);
                    """, (transaction_id, event_type, uuid or "", json.dumps(revoked), now, now))

                conn.execute("COMMIT;")
                return {"already_refunded": False, "uuid": uuid, "items": revoked}
            except Exception:
                conn.execute("ROLLBACK;")
                raise

    def record_transaction_sync(self, transaction_id: str, event_type: str, uuid: str, items: list[str]) -> dict:
        now = int(time.time())
        with self._get_connection() as conn:
            cur = conn.cursor()
            cur.execute("SELECT event_type FROM transactions WHERE transaction_id = ?;", (transaction_id,))
            row = cur.fetchone()
            if row:
                return {"already_processed": True, "event_type": row[0]}
            conn.execute("""
                INSERT INTO transactions (transaction_id, event_type, uuid, items_json, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?);
            """, (transaction_id, event_type, uuid, json.dumps(items), now, now))
            conn.commit()
            return {"already_processed": False, "event_type": event_type}

    def refund_transaction_sync(self, transaction_id: str, event_type: str) -> dict | None:
        return self.process_tebex_refund_sync(transaction_id, event_type, "")

    def create_link_code_sync(self, uuid: str, ttl: int = LINK_CODE_TTL) -> str:
        now = int(time.time())
        with self._get_connection() as conn:
            # Clean expired codes and enforce max one live link code per uuid (new replaces old)
            conn.execute("DELETE FROM link_codes WHERE expires_at < ? OR uuid = ?;", (now, uuid))
            conn.commit()
            for _ in range(20):
                code = "".join(secrets.choice(LINK_ALPHABET) for _ in range(6))
                try:
                    conn.execute("""
                        INSERT INTO link_codes (code, uuid, created_at, expires_at)
                        VALUES (?, ?, ?, ?);
                    """, (code, uuid, now, now + ttl))
                    conn.commit()
                    return code
                except sqlite3.IntegrityError:
                    continue
            raise RuntimeError("Failed to generate unique link code")

    def get_uuid_for_link_code_sync(self, code: str) -> str | None:
        now = int(time.time())
        normalized = code.strip().upper()
        with self._get_connection() as conn:
            cur = conn.cursor()
            cur.execute("SELECT uuid, expires_at FROM link_codes WHERE code = ?;", (normalized,))
            row = cur.fetchone()
            if row and row[1] > now:
                return row[0]
            return None

    def consume_link_code_sync(self, code: str) -> str | None:
        """Atomically resolve and delete a single-use link code using DELETE ... RETURNING."""
        now = int(time.time())
        normalized = code.strip().upper()
        with self._get_connection() as conn:
            cur = conn.cursor()
            cur.execute(
                "DELETE FROM link_codes WHERE code = ? AND expires_at > ? RETURNING uuid;",
                (normalized, now)
            )
            row = cur.fetchone()
            conn.commit()
            return row[0] if row else None

    def list_sync(self, uuid: str) -> dict:
        clean = plain_uuid(uuid) or uuid
        all_access_details = self.get_all_access_details_sync(clean)
        with self._get_connection() as conn:
            cur = conn.cursor()
            cur.execute("""
                SELECT item_id, source, granted_at, revoked_at
                FROM entitlements
                WHERE uuid = ?
                ORDER BY granted_at ASC;
            """, (clean,))
            entitlements = [
                {
                    "item_id": r[0],
                    "source": r[1],
                    "granted_at": r[2],
                    "revoked_at": r[3],
                    "active": r[3] is None,
                }
                for r in cur.fetchall()
            ]
            cur.execute("SELECT slot, item_id FROM equipped WHERE uuid = ?;", (clean,))
            equipped = {r[0]: r[1] for r in cur.fetchall()}
            return {
                "uuid": clean,
                "entitlements": entitlements,
                "equipped": equipped,
                "all_access": all_access_details is not None,
                "all_access_details": all_access_details,
            }

    # --- Asynchronous Wrappers (asyncio.to_thread) ---

    async def has_all_access(self, uuid: str) -> bool:
        return await asyncio.to_thread(self.has_all_access_sync, uuid)

    async def get_all_access_details(self, uuid: str) -> dict | None:
        return await asyncio.to_thread(self.get_all_access_details_sync, uuid)

    async def set_all_access(self, uuid: str, enabled: bool, granted_by: str = "admin") -> bool:
        return await asyncio.to_thread(self.set_all_access_sync, uuid, enabled, granted_by)

    async def grant(self, uuid: str, item_id: str, source: str = "admin") -> None:
        await asyncio.to_thread(self.grant_sync, uuid, item_id, source)

    async def revoke(self, uuid: str, item_id: str, actor: str = "admin") -> None:
        await asyncio.to_thread(self.revoke_sync, uuid, item_id, actor=actor)

    async def has_entitlement(self, uuid: str, item_id: str) -> bool:
        return await asyncio.to_thread(self.has_entitlement_sync, uuid, item_id)

    async def get_profile(self, uuid: str) -> dict:
        return await asyncio.to_thread(self.get_profile_sync, uuid)

    async def set_equipped(self, uuid: str, slot: str, item_id: str) -> None:
        await asyncio.to_thread(self.set_equipped_sync, uuid, slot, item_id)

    async def remove_equipped(self, uuid: str, slot: str) -> None:
        await asyncio.to_thread(self.remove_equipped_sync, uuid, slot)

    async def process_tebex_payment(
        self,
        transaction_id: str,
        payload_str: str,
        raw_uuid: str | None,
        link_code: str | None,
        items: list[str]
    ) -> dict:
        return await asyncio.to_thread(
            self.process_tebex_payment_sync,
            transaction_id,
            payload_str,
            raw_uuid,
            link_code,
            items
        )

    async def process_tebex_refund(
        self,
        transaction_id: str,
        event_type: str,
        payload_str: str,
        raw_uuid: str | None = None,
        items: list[str] | None = None
    ) -> dict | None:
        return await asyncio.to_thread(
            self.process_tebex_refund_sync,
            transaction_id,
            event_type,
            payload_str,
            raw_uuid,
            items
        )

    async def record_transaction(self, transaction_id: str, event_type: str, uuid: str, items: list[str]) -> dict:
        return await asyncio.to_thread(self.record_transaction_sync, transaction_id, event_type, uuid, items)

    async def refund_transaction(self, transaction_id: str, event_type: str) -> dict | None:
        return await asyncio.to_thread(self.refund_transaction_sync, transaction_id, event_type)

    async def create_link_code(self, uuid: str, ttl: int = LINK_CODE_TTL) -> str:
        return await asyncio.to_thread(self.create_link_code_sync, uuid, ttl)

    async def get_uuid_for_link_code(self, code: str) -> str | None:
        return await asyncio.to_thread(self.get_uuid_for_link_code_sync, code)

    async def consume_link_code(self, code: str) -> str | None:
        return await asyncio.to_thread(self.consume_link_code_sync, code)

    async def list_cosmetics(self, uuid: str) -> dict:
        return await asyncio.to_thread(self.list_sync, uuid)


# ---------------------------------------------------------------------------
# Peer, Hub & Real-time Relay
# ---------------------------------------------------------------------------

class Peer:
    __slots__ = ("ws", "uuid", "room", "emote", "since", "window", "sent", "gone", "room_history")

    def __init__(self, ws, uuid):
        self.ws, self.uuid, self.room, self.emote, self.since = ws, uuid, None, None, 0.0
        self.window, self.sent, self.gone = 0.0, 0, False
        self.room_history = []

    def view(self, now: float) -> dict:
        return {"uuid": self.uuid, "emote": self.emote, "age": int((now - self.since) * 1000) if self.emote else 0}


async def send(peer: Peer, msg) -> None:
    try:
        await peer.ws.send_str(msg if isinstance(msg, str) else json.dumps(msg, separators=(",", ":")))
    except Exception:
        pass  # dead socket: handled by its own cleanup


async def broadcast(peers, msg: dict) -> None:
    text = json.dumps(msg, separators=(",", ":"))
    await asyncio.gather(*(send(p, text) for p in peers))


class Hub:
    def __init__(self, insecure: bool = False, store: CosmeticsStore | None = None):
        self.insecure = insecure
        self.store = store
        self.peers: dict[str, Peer] = {}  # uuid -> its one socket
        self.rooms: dict[str, set[Peer]] = {}
        self.ips: dict[str, int] = {}
        self.auth_failures: dict[str, list[float]] = {}
        self.auth_blocked_until: dict[str, float] = {}

    def is_ip_auth_blocked(self, ip: str) -> bool:
        ip_key = get_auth_ip_key(ip)
        now = time.monotonic()
        blocked_until = self.auth_blocked_until.get(ip_key, 0.0)
        if now < blocked_until:
            return True
        if ip_key in self.auth_blocked_until:
            del self.auth_blocked_until[ip_key]
        return False

    def record_auth_failure(self, ip: str) -> None:
        ip_key = get_auth_ip_key(ip)
        now = time.monotonic()
        fails = self.auth_failures.setdefault(ip_key, [])
        fails.append(now)
        valid = [t for t in fails if now - t <= 60.0]
        self.auth_failures[ip_key] = valid
        if len(valid) >= 5:
            self.auth_blocked_until[ip_key] = now + 60.0
            self.auth_failures.pop(ip_key, None)

    async def leave(self, peer: Peer) -> None:
        room, peer.room, peer.emote = peer.room, None, None
        members = self.rooms.get(room)
        if members is None:
            return
        members.discard(peer)
        if not members:
            del self.rooms[room]
        await broadcast(members, {"t": "leave", "uuid": peer.uuid})

    async def join(self, peer: Peer, room: str | None) -> None:
        await self.leave(peer)
        if peer.gone:
            return
        members = self.rooms.setdefault(room, set()) if room else set()
        now = time.monotonic()
        snapshot, others = [p.view(now) for p in members], list(members)
        if room:  # in before any await
            members.add(peer)
            peer.room = room
        await send(peer, {"t": "snapshot", "players": snapshot})
        await broadcast(others, {"t": "join", "uuid": peer.uuid})
        log.debug("room %s %s (%d others)", peer.uuid, (room or "-")[:8], len(others))

    async def emote(self, peer: Peer, emote: str | None) -> None:
        peer.emote, peer.since = emote, time.monotonic()
        if peer.room and not peer.gone:
            others = [p for p in self.rooms[peer.room] if p is not peer]
            await broadcast(others, {"t": "state", "uuid": peer.uuid, "emote": emote, "age": 0})
            log.debug("emote %s %s -> %d", peer.uuid, emote, len(others))

    async def equip(self, peer: Peer, slot: str, item: str | None) -> bool:
        """Validate ownership, update store, and broadcast to the room (WebSocket-authenticated)."""
        if not (isinstance(slot, str) and slot in ALLOWED_SLOTS):
            await send(peer, {"t": "error", "error": f"invalid slot '{slot}', must be one of {sorted(ALLOWED_SLOTS)}"})
            return False

        if item is not None:
            if not (isinstance(item, str) and len(item) <= 64 and ITEM_ID_RE.fullmatch(item)):
                await send(peer, {"t": "error", "error": "invalid item"})
                return False
            # Check ownership: FREE or in entitlements
            owned = (item in FREE)
            if not owned and self.store:
                owned = await self.store.has_entitlement(peer.uuid, item)
            if not owned:
                await send(peer, {"t": "error", "error": "item not owned"})
                return False
            if self.store:
                await self.store.set_equipped(peer.uuid, slot, item)
        else:
            if self.store:
                await self.store.remove_equipped(peer.uuid, slot)

        # Broadcast to others in the room
        if peer.room and not peer.gone:
            others = [p for p in self.rooms[peer.room] if p is not peer]
            await broadcast(others, {"t": "equip", "uuid": peer.uuid, "slot": slot, "item": item})
        # Confirmation to sender
        await send(peer, {"t": "equip", "uuid": peer.uuid, "slot": slot, "item": item, "status": "ok"})
        log.debug("equip %s %s=%s", peer.uuid, slot, item)
        return True

    async def link_start(self, peer: Peer) -> str:
        """Issue a 6-character link code valid for 10 minutes bound to the authenticated peer's UUID."""
        if not self.store:
            raise RuntimeError("Store not configured")
        code = await self.store.create_link_code(peer.uuid, ttl=LINK_CODE_TTL)
        await send(peer, {"t": "link_code", "code": code, "uuid": peer.uuid, "expires_in": LINK_CODE_TTL})
        return code


async def read(ws) -> dict | None:
    msg = await ws.receive()
    if msg.type != aiohttp.WSMsgType.TEXT:
        return None  # close, error or binary
    try:
        data = json.loads(msg.data)
    except ValueError:
        return None
    return data if isinstance(data, dict) else None


async def authenticate(hub: Hub, ws, ip: str = "127.0.0.1") -> Peer | None:
    if hub.is_ip_auth_blocked(ip):
        await ws.close(code=4003, message=b"auth throttled")
        return None
    nonce = secrets.token_hex(16)
    await ws.send_str(json.dumps({"t": "challenge", "nonce": nonce}))
    msg = await read(ws)
    if not msg or msg.get("t") != "auth":
        hub.record_auth_failure(ip)
        return None
    uuid, name, sid = plain_uuid(msg.get("uuid")), msg.get("name"), server_id(nonce)
    if not uuid or not isinstance(name, str) or not NAME_RE.fullmatch(name) or msg.get("serverId") != sid:
        hub.record_auth_failure(ip)
        return None
    if not hub.insecure:
        try:
            async with _MOJANG_SEMAPHORE:
                joined = await has_joined(name, sid)
        except MojangUnavailableError as e:
            log.warning("mojang unavailable: %s", e)
            await ws.close(code=1013, message=b"try again later")
            return None
        except Exception as e:
            log.warning("mojang hasJoined failed: %s", type(e).__name__)
            joined = None
        if joined != uuid:
            hub.record_auth_failure(ip)
            log.info("auth refused %s", uuid)
            await ws.close(code=4003, message=b"auth refused")
            return None
    peer = Peer(ws, uuid)
    old = hub.peers.get(uuid)
    hub.peers[uuid] = peer
    if old:  # one socket per player: new replaces old
        old.gone = True
        await hub.leave(old)
        await old.ws.close(code=4001, message=b"replaced")
    log.info("auth %s", uuid)
    return peer


async def serve(hub: Hub, peer: Peer) -> None:
    while (msg := await read(peer.ws)) is not None:
        now = time.monotonic()
        if now - peer.window >= 1:
            peer.window, peer.sent = now, 0
        peer.sent += 1
        if peer.sent > MAX_RATE:
            await peer.ws.close(code=1008, message=b"rate")
            return
        kind = msg.get("t")
        if kind == "room":
            room = msg.get("room")
            if room is not None and not (isinstance(room, str) and ROOM_RE.fullmatch(room)):
                return
            now_mono = time.monotonic()
            peer.room_history = [t for t in peer.room_history if t > now_mono - 60.0]
            if len(peer.room_history) >= 6:
                await send(peer, {"t": "error", "error": "room rate limit exceeded"})
                continue
            peer.room_history.append(now_mono)
            await hub.join(peer, room)
        elif kind == "emote":
            emote = msg.get("id")
            # Owned or free emote check
            allowed = False
            if emote is None:
                allowed = True
            elif isinstance(emote, str):
                if emote in EMOTES:
                    allowed = True
                elif hub.store and await hub.store.has_entitlement(peer.uuid, emote):
                    allowed = True
            if not allowed:
                await send(peer, {"t": "error", "error": "unknown emote"})
                continue
            await hub.emote(peer, emote)
        elif kind == "equip":
            slot = msg.get("slot")
            item = msg.get("item") if "item" in msg else msg.get("item_id")
            await hub.equip(peer, slot, item)
        elif kind in ("link", "link_start", "link/start"):
            if hub.store:
                await hub.link_start(peer)
            else:
                await send(peer, {"t": "error", "error": "store unavailable"})
        else:
            return  # protocol error: close


# ---------------------------------------------------------------------------
# HTTP Route Handlers
# ---------------------------------------------------------------------------

async def handle_ws(request: web.Request) -> web.StreamResponse:
    hub = request.app[HUB]
    ip = client_ip(request)
    if hub.ips.get(ip, 0) >= MAX_PER_IP:
        return web.json_response({"error": "too many connections"}, status=429)
    if hub.is_ip_auth_blocked(ip):
        return web.json_response({"error": "auth throttled"}, status=429)
    hub.ips[ip] = hub.ips.get(ip, 0) + 1
    ws = web.WebSocketResponse(heartbeat=HEARTBEAT, receive_timeout=IDLE_TIMEOUT, max_msg_size=MAX_FRAME)
    peer = None
    try:
        await ws.prepare(request)
        peer = await asyncio.wait_for(authenticate(hub, ws, ip), AUTH_TIMEOUT)
        if peer:
            await serve(hub, peer)
    except (asyncio.TimeoutError, ConnectionError):
        pass
    finally:
        hub.ips[ip] -= 1
        if not hub.ips[ip]:
            del hub.ips[ip]
        if peer:
            if hub.peers.get(peer.uuid) is peer:
                del hub.peers[peer.uuid]
                log.info("closed %s", peer.uuid)
            await hub.leave(peer)
        await ws.close()
    return ws


async def handle_health(request: web.Request) -> web.Response:
    hub = request.app[HUB]
    return web.json_response({"status": "ok", "players": len(hub.peers), "rooms": len(hub.rooms)})


async def handle_profile(request: web.Request) -> web.Response:
    uuid = plain_uuid(request.match_info["uuid"])
    if not uuid:
        return web.json_response({"error": "bad uuid"}, status=400)
    store: CosmeticsStore | None = request.app.get(STORE)
    if store:
        data = await store.get_profile(uuid)
    else:
        data = {"owned": FREE, "equipped": {}}
    return web.json_response(data)


def _check_http_auth(request: web.Request) -> bool:
    """Verify Authorization: Bearer <secret> for sensitive HTTP management endpoints."""
    auth_header = request.headers.get("Authorization", "")
    secret = request.app.get(AUTH_SECRET) or os.getenv("COSMETICS_AUTH_TOKEN")
    if not secret or not auth_header.startswith("Bearer "):
        return False
    token = auth_header[len("Bearer "):].strip()
    return hmac.compare_digest(token.encode("utf-8", errors="replace"), secret.encode("utf-8", errors="replace"))


async def handle_equip_http(request: web.Request) -> web.Response:
    """HTTP POST /cosmetics/equip: requires authorization header to prevent unauthorized IDOR equip.
    Normal player equip is performed over the authenticated WebSocket session.
    """
    if not _check_http_auth(request):
        return web.json_response({"error": "unauthorized"}, status=401)

    try:
        body = await request.json()
    except Exception:
        return web.json_response({"error": "invalid json"}, status=400)

    uuid = plain_uuid(body.get("uuid"))
    if not uuid:
        return web.json_response({"error": "bad uuid"}, status=400)

    slot = body.get("slot")
    if not (isinstance(slot, str) and slot in ALLOWED_SLOTS):
        return web.json_response({"error": "invalid slot", "allowed": sorted(ALLOWED_SLOTS)}, status=400)

    item = body.get("item") if "item" in body else body.get("item_id")
    store: CosmeticsStore = request.app[STORE]

    if item is not None:
        if not (isinstance(item, str) and len(item) <= 64 and ITEM_ID_RE.fullmatch(item)):
            return web.json_response({"error": "invalid item"}, status=400)
        owned = (item in FREE) or (await store.has_entitlement(uuid, item))
        if not owned:
            return web.json_response({"error": "item not owned"}, status=403)
        await store.set_equipped(uuid, slot, item)
    else:
        await store.remove_equipped(uuid, slot)

    # Broadcast to room if the player is connected online
    hub: Hub = request.app[HUB]
    peer = hub.peers.get(uuid)
    if peer and peer.room and not peer.gone:
        others = [p for p in hub.rooms[peer.room] if p is not peer]
        await broadcast(others, {"t": "equip", "uuid": uuid, "slot": slot, "item": item})

    return web.json_response({"status": "ok", "uuid": uuid, "slot": slot, "item": item})


async def handle_tebex_webhook(request: web.Request) -> web.Response:
    """Tebex Webhook endpoint: POST /cosmetics/tebex.
    Validates Tebex HMAC signature fail-closed, handles endpoint validation, payment.completed (idempotent),
    and refund/chargeback/dispute revoking in atomic SQLite transactions.
    """
    secret = request.app.get(TEBEX_SECRET) or os.getenv("TEBEX_WEBHOOK_SECRET")
    if not secret:
        log.warning("Tebex webhook received but TEBEX_WEBHOOK_SECRET is not configured")
        return web.json_response({"error": "webhook secret not configured"}, status=401)

    raw_body = await request.read()
    sig = request.headers.get("X-Tebex-Signature") or request.headers.get("X-Signature") or request.headers.get("X-BC-Sig")

    if not verify_tebex_signature(raw_body, sig, secret):
        return web.json_response({"error": "invalid signature"}, status=401)

    try:
        payload = json.loads(raw_body.decode("utf-8"))
    except Exception:
        return web.json_response({"error": "invalid json"}, status=400)

    event_type = payload.get("type")

    # 1. Tebex Endpoint Handshake
    if event_type == "validation.webhook":
        return web.json_response({"id": payload.get("id")})

    store: CosmeticsStore = request.app[STORE]
    subject = payload.get("subject", {})
    tx_id = str(subject.get("transaction_id") or subject.get("id") or payload.get("id") or "")

    if not tx_id:
        return web.json_response({"error": "missing transaction id"}, status=400)

    catalog = request.app.get(CATALOG)
    if catalog is None:
        env_cat = os.getenv("COSMETICS_CATALOG")
        if env_cat:
            catalog = load_catalog_from_file(env_cat)

    # 2. Payment Completed: Grants items idempotently in an atomic transaction
    if event_type == "payment.completed":
        if not catalog:
            log.error("Rejecting Tebex payment %s: no cosmetics catalog configured (COSMETICS_CATALOG unset or empty)", tx_id)
            return web.json_response({"error": "catalog not configured"}, status=503)

        raw_uuid = extract_buyer_raw_uuid(subject)
        link_code = extract_link_code(subject)

        packages = subject.get("products") or subject.get("packages") or []
        if not packages:
            log.warning("Tebex payment %s contains no products or packages", tx_id)
            return web.json_response({"error": "no packages in payment"}, status=422)

        items = []
        for pkg in packages:
            if not isinstance(pkg, dict):
                log.warning("Invalid package object in payment %s: %r", tx_id, pkg)
                return web.json_response({"error": f"invalid package: {pkg!r}"}, status=422)
            resolved = resolve_package_item_id(pkg, catalog)
            if not resolved:
                log.warning(
                    "Unknown or unmapped Tebex package in payment %s: %r. Rejecting transaction so Tebex retries.",
                    tx_id, pkg
                )
                return web.json_response(
                    {"error": f"unresolved package: {pkg.get('id') or pkg.get('name')}"},
                    status=422
                )
            items.append(resolved)

        # Process payment atomically (handles duplicate tx_id, single-use link code consume, and grant)
        res = await store.process_tebex_payment(
            transaction_id=tx_id,
            payload_str=raw_body.decode("utf-8", errors="replace"),
            raw_uuid=raw_uuid,
            link_code=link_code,
            items=items
        )
        if res.get("status") == "error":
            return web.json_response({"error": res["error"]}, status=400)
        return web.json_response(res, status=200)

    # 3. Refund / Chargeback / Dispute: Revokes items idempotently
    elif event_type in ("payment.refunded", "payment.dispute.opened", "payment.disputed", "payment.chargeback", "payment.dispute.lost"):
        raw_uuid = extract_buyer_raw_uuid(subject)
        packages = subject.get("products") or subject.get("packages") or []
        items = [resolve_package_item_id(p, catalog) for p in packages if isinstance(p, dict)] if catalog else []
        items = [i for i in items if i]

        res = await store.process_tebex_refund(
            transaction_id=tx_id,
            event_type=event_type,
            payload_str=raw_body.decode("utf-8", errors="replace"),
            raw_uuid=raw_uuid,
            items=items if items else None
        )
        if res and res.get("already_refunded"):
            return web.json_response({"status": "already_refunded", "transaction_id": tx_id})
        if res:
            buyer_uuid = res.get("uuid")
            if buyer_uuid:
                hub: Hub = request.app[HUB]
                peer = hub.peers.get(buyer_uuid)
                if peer and peer.room:
                    for item_id in res.get("items", []):
                        others = [p for p in hub.rooms[peer.room] if p is not peer]
                        await broadcast(others, {"t": "revoke", "uuid": buyer_uuid, "item": item_id})
            return web.json_response({
                "status": "revoked",
                "transaction_id": tx_id,
                "uuid": buyer_uuid,
                "items": res.get("items", [])
            })
        return web.json_response({"status": "not_found", "transaction_id": tx_id}, status=404)

    return web.json_response({"status": "ignored", "type": event_type})


async def handle_link_start_http(request: web.Request) -> web.Response:
    """GET /cosmetics/link/start: requires authorization header to prevent unauthenticated link code hijacking.
    Normal in-game link code flow is issued over the authenticated WebSocket session.
    """
    if not _check_http_auth(request):
        return web.json_response({"error": "unauthorized"}, status=401)

    uuid = plain_uuid(request.query.get("uuid"))
    if not uuid:
        return web.json_response({"error": "missing or invalid uuid parameter"}, status=400)
    store: CosmeticsStore = request.app[STORE]
    code = await store.create_link_code(uuid, ttl=LINK_CODE_TTL)
    return web.json_response({"code": code, "uuid": uuid, "expires_in": LINK_CODE_TTL})


async def handle_link_verify_http(request: web.Request) -> web.Response:
    """GET /cosmetics/link/verify?code=...: look up UUID by link code (requires Bearer auth)."""
    if not _check_http_auth(request):
        return web.json_response({"error": "unauthorized"}, status=401)
    code = request.query.get("code") or request.match_info.get("code")
    if not code:
        return web.json_response({"error": "missing code"}, status=400)
    store: CosmeticsStore = request.app[STORE]
    uuid = await store.get_uuid_for_link_code(code)
    if not uuid:
        return web.json_response({"error": "invalid or expired link code"}, status=404)
    return web.json_response({"code": code.strip().upper(), "uuid": uuid, "valid": True})


async def close_all(app: web.Application) -> None:
    await asyncio.gather(*(p.ws.close(code=1001, message=b"restart") for p in list(app[HUB].peers.values())))
    global _mojang_session
    if _mojang_session and not _mojang_session.closed:
        await _mojang_session.close()
        _mojang_session = None


HUB = web.AppKey("cosmetics_hub", Hub)
STORE = web.AppKey("cosmetics_store", CosmeticsStore)
TEBEX_SECRET = web.AppKey("tebex_secret", str)
AUTH_SECRET = web.AppKey("auth_secret", str)
CATALOG = web.AppKey("cosmetics_catalog", Any)

_DEFAULT_STORE: CosmeticsStore | None = None


def get_store(db_path: Path | str | None = None) -> CosmeticsStore:
    global _DEFAULT_STORE
    if db_path is not None:
        return CosmeticsStore(db_path)
    if _DEFAULT_STORE is None:
        _DEFAULT_STORE = CosmeticsStore()
    return _DEFAULT_STORE


def setup(
    app: web.Application,
    insecure: bool = False,
    db_path: Path | str | None = None,
    tebex_secret: str | None = None,
    auth_secret: str | None = None,
    catalog: dict[int | str, str] | None = None
) -> None:
    store = CosmeticsStore(db_path)
    global _DEFAULT_STORE
    _DEFAULT_STORE = store
    app[STORE] = store
    app[TEBEX_SECRET] = tebex_secret or os.getenv("TEBEX_WEBHOOK_SECRET", "")
    # M1: admin bearer requires explicit auth token; no fallback to tebex_secret
    app[AUTH_SECRET] = auth_secret or os.getenv("COSMETICS_AUTH_TOKEN", "")

    # Catalog: explicit parameter > COSMETICS_CATALOG file > None (fail-closed)
    if catalog is not None:
        app[CATALOG] = catalog
    else:
        env_cat = os.getenv("COSMETICS_CATALOG")
        app[CATALOG] = load_catalog_from_file(env_cat) if env_cat else None

    global _DEFAULT_CATALOG
    _DEFAULT_CATALOG = app[CATALOG]

    hub = Hub(insecure, store=store)
    app[HUB] = hub

    app.router.add_get("/cosmetics/health", handle_health)
    app.router.add_get("/cosmetics/ws", handle_ws)
    app.router.add_get("/cosmetics/profile/{uuid}", handle_profile)
    app.router.add_post("/cosmetics/equip", handle_equip_http)
    # Exactly ONE webhook route
    app.router.add_post("/cosmetics/tebex", handle_tebex_webhook)
    app.router.add_get("/cosmetics/link/start", handle_link_start_http)
    app.router.add_get("/cosmetics/link/verify", handle_link_verify_http)
    app.on_shutdown.append(close_all)

    import social
    social.mount(app, insecure=insecure, db_path=db_path)


# ---------------------------------------------------------------------------
# Staff Admin CLI
# ---------------------------------------------------------------------------

def cli_grant(uuid_raw: str, item_id: str, source: str = "admin", db_path: Path | str | None = None) -> int:
    uuid = plain_uuid(uuid_raw)
    if not uuid:
        print(f"Error: invalid UUID '{uuid_raw}'", file=sys.stderr)
        return 1
    store = CosmeticsStore(db_path)
    store.grant_sync(uuid, item_id, source=source)
    print(f"Granted '{item_id}' to {uuid} (source: {source})")
    return 0


def cli_revoke(uuid_raw: str, item_id: str, db_path: Path | str | None = None) -> int:
    uuid = plain_uuid(uuid_raw)
    if not uuid:
        print(f"Error: invalid UUID '{uuid_raw}'", file=sys.stderr)
        return 1
    store = CosmeticsStore(db_path)
    store.revoke_sync(uuid, item_id)
    print(f"Revoked '{item_id}' from {uuid}")
    return 0


def cli_allaccess(uuid_raw: str, state_str: str, granted_by: str = "admin", db_path: Path | str | None = None) -> int:
    uuid = plain_uuid(uuid_raw)
    if not uuid:
        print(f"Error: invalid UUID '{uuid_raw}'", file=sys.stderr)
        return 1
    enabled = state_str.lower() in ("on", "true", "1", "enable", "enabled")
    store = CosmeticsStore(db_path)
    store.set_all_access_sync(uuid, enabled, granted_by=granted_by)
    print(f"All-Access {'ENABLED' if enabled else 'DISABLED'} for {uuid} (granted_by: {granted_by})")
    return 0


def cli_list(uuid_raw: str, db_path: Path | str | None = None) -> int:
    uuid = plain_uuid(uuid_raw)
    if not uuid:
        print(f"Error: invalid UUID '{uuid_raw}'", file=sys.stderr)
        return 1
    store = CosmeticsStore(db_path)
    res = store.list_sync(uuid)
    print(f"Cosmetics profile for {uuid}:")
    print(f"  All-Access: {'YES' if res.get('all_access') else 'NO'}")
    if res.get("all_access_details"):
        d = res["all_access_details"]
        print(f"    Granted by: {d['granted_by']}, at: {d['granted_at']}")
    print("  Active Entitlements:")
    active = [e for e in res["entitlements"] if e["active"]]
    if active:
        for e in active:
            print(f"    - {e['item_id']} (source: {e['source']}, granted_at: {e['granted_at']})")
    else:
        print("    (none)")
    print("  Revoked Entitlements:")
    revoked = [e for e in res["entitlements"] if not e["active"]]
    if revoked:
        for e in revoked:
            print(f"    - {e['item_id']} (source: {e['source']}, revoked_at: {e['revoked_at']})")
    else:
        print("    (none)")
    print("  Equipped:")
    if res["equipped"]:
        for slot, item in res["equipped"].items():
            print(f"    {slot}: {item}")
    else:
        print("    (none)")
    return 0


def main(argv=None) -> None:
    raw_args = list(argv if argv is not None else sys.argv[1:])

    # Check for direct Admin subcommands or flags
    if raw_args:
        first = raw_args[0].lower()
        if first in ("grant", "revoke", "list", "allaccess", "admin") or first.startswith("--grant") or first.startswith("--revoke") or first.startswith("--list") or first.startswith("--allaccess"):
            db_parent = argparse.ArgumentParser(add_help=False)
            db_parent.add_argument("--db", default=None, help="Path to cosmetics.db")

            admin_parser = argparse.ArgumentParser(description="Lads Cosmetics Staff Admin CLI", parents=[db_parent])

            # Handle 'admin grant ...' or 'grant ...'
            sub = admin_parser.add_subparsers(dest="action")
            grant_p = sub.add_parser("grant", help="Grant cosmetic entitlement", parents=[db_parent])
            grant_p.add_argument("uuid", help="Minecraft player UUID (plain hex or hyphenated)")
            grant_p.add_argument("item_id", help="Cosmetic item ID (e.g. lads:cloak_aurora)")
            grant_p.add_argument("--source", default="admin", help="Grant source (default: admin)")

            revoke_p = sub.add_parser("revoke", help="Revoke cosmetic entitlement", parents=[db_parent])
            revoke_p.add_argument("uuid", help="Minecraft player UUID")
            revoke_p.add_argument("item_id", help="Cosmetic item ID")

            list_p = sub.add_parser("list", help="List cosmetic entitlements and equipped items", parents=[db_parent])
            list_p.add_argument("uuid", help="Minecraft player UUID")

            allaccess_p = sub.add_parser("allaccess", help="Toggle all-access for player", parents=[db_parent])
            allaccess_p.add_argument("uuid", help="Minecraft player UUID")
            allaccess_p.add_argument("state", choices=["on", "off"], help="on or off")
            allaccess_p.add_argument("--granted-by", default="admin", help="Admin username")

            admin_sub = sub.add_parser("admin", help="Admin subcommands", parents=[db_parent])
            admin_sub_action = admin_sub.add_subparsers(dest="admin_action")
            agrant_p = admin_sub_action.add_parser("grant", parents=[db_parent])
            agrant_p.add_argument("uuid")
            agrant_p.add_argument("item_id")
            agrant_p.add_argument("--source", default="admin")
            arevoke_p = admin_sub_action.add_parser("revoke", parents=[db_parent])
            arevoke_p.add_argument("uuid")
            arevoke_p.add_argument("item_id")
            alist_p = admin_sub_action.add_parser("list", parents=[db_parent])
            alist_p.add_argument("uuid")
            aallaccess_p = admin_sub_action.add_parser("allaccess", parents=[db_parent])
            aallaccess_p.add_argument("uuid")
            aallaccess_p.add_argument("state", choices=["on", "off"])
            aallaccess_p.add_argument("--granted-by", default="admin")

            parsed, _ = admin_parser.parse_known_args(raw_args)
            action = parsed.action
            if action == "admin":
                action = getattr(parsed, "admin_action", None)

            if action == "grant":
                sys.exit(cli_grant(parsed.uuid, parsed.item_id, getattr(parsed, "source", "admin"), parsed.db))
            elif action == "revoke":
                sys.exit(cli_revoke(parsed.uuid, parsed.item_id, parsed.db))
            elif action == "list":
                sys.exit(cli_list(parsed.uuid, parsed.db))
            elif action == "allaccess":
                sys.exit(cli_allaccess(parsed.uuid, parsed.state, getattr(parsed, "granted_by", "admin"), parsed.db))

    # Standard Standalone Server Parser
    parser = argparse.ArgumentParser(description="Lads cosmetics relay & backend, standalone (the bot mounts it with setup(app)).")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8095)
    parser.add_argument("--db", default=None, help="Path to cosmetics.db SQLite file")
    parser.add_argument("--tebex-secret", default=None, help="Tebex webhook secret")
    parser.add_argument("--auth-secret", default=None, help="Cosmetics HTTP management auth token")
    parser.add_argument("--dev-insecure-auth", action="store_true",
                        help="QA only: trust the client's name and uuid without asking Mojang (offline QA accounts); 127.0.0.1 only")
    args = parser.parse_args(raw_args)
    if args.dev_insecure_auth and args.host != "127.0.0.1":
        parser.error("--dev-insecure-auth refuses to start unless bound to 127.0.0.1")
    if args.dev_insecure_auth:
        os.environ.setdefault("LADS_SOCIAL_DEV", "1")
    logging.basicConfig(level=logging.DEBUG, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
    app = web.Application()
    setup(app, insecure=args.dev_insecure_auth, db_path=args.db, tebex_secret=args.tebex_secret, auth_secret=args.auth_secret)
    if args.dev_insecure_auth:
        log.warning("dev mode: Mojang verification is OFF (127.0.0.1 only)")
    web.run_app(app, host=args.host, port=args.port)


if __name__ == "__main__":
    main()
