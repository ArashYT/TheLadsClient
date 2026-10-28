"""Lads social relay & backend (Task S1, Hardened Round 2): friends graph, presence, DMs,
invites, join requests, ICE signaling, and Cloudflare TURN minting.

Mounted on the cosmetics aiohttp server at /social/ws.
"""
from __future__ import annotations

import asyncio
from collections import OrderedDict
import ipaddress
import json
import logging
import os
import re
import secrets
import sqlite3
import time
from pathlib import Path
from typing import Optional, Dict, Any, List, Set, Tuple
from urllib.parse import quote

import aiohttp
from aiohttp import web

from cosmetics import plain_uuid, server_id, has_joined, NAME_RE

log = logging.getLogger("social")

MAX_FRIENDS = 500
MAX_BLOCKS_PER_USER = 1000
FRIEND_REQUEST_RATE_LIMIT = 20  # per hour
DM_RATE_LIMIT = 30              # per minute
DM_MAX_CHARS = 256
INVITE_RATE_LIMIT = 30          # per hour
JOIN_REQUEST_RATE_LIMIT = 30    # per hour
OFFLINE_DM_TTL_MS = 7 * 86400 * 1000  # 7 days
MAX_OFFLINE_DMS_PER_RECIPIENT = 100
MAX_OFFLINE_DMS_PER_PAIR = 50
INVITE_TTL_SECONDS = 300        # 5 minutes
JOIN_REQUEST_TTL_SECONDS = 300  # 5 minutes
MAX_PENDING_INVITES_PER_HOST = 20
MAX_PENDING_JOIN_REQUESTS_PER_HOST = 20
MAX_SIGNALS_PER_SEC = 40
MAX_SIGNAL_DATA_BYTES = 8192
MAX_CONCURRENT_SOCKETS_PER_IP = 8
AUTH_TIMEOUT = 10.0
WS_MAX_MSG_SIZE = 65536
GLOBAL_RELAY_BUDGET_BYTES = 900 * 10**9  # 900 GB (decimal)
RELAY_BUDGET_BYTES = GLOBAL_RELAY_BUDGET_BYTES
USER_RELAY_BUDGET_BYTES = 30 * 10**9     # 30 GB per user per UTC month
MAX_AUTH_DICT_ENTRIES = 10_000
RELAY_MAX_BYTES_PER_SEC = 12_500_000  # 100 Mbit/s
MAX_RELAY_SESSION_BYTES = 50 * 10**9   # 50 GB per session ceiling
MAX_RELAY_SESSION_DURATION = 6.0 * 3600.0  # 6 hours
TURN_MINTS_PER_DAY = 60  # TURN credential sets per account (host or guest) per day
STUN_SERVER = "stun:stun.cloudflare.com:3478"
TURN_USER_AGENT = "LadsClient-social/1.0"  # explicit: Cloudflare refuses some default library agents
ALLOWED_STATES = frozenset({"menu", "singleplayer", "hosting", "server", "offline"})
ALLOWED_SCOPES = frozenset({"off", "lan", "friends", "invite"})
KNOWN_GAME_KEYS = frozenset({"mc", "loader", "profileKind", "packId", "packVersion", "modsHash", "lads"})

_MOJANG_SEMAPHORE = asyncio.Semaphore(8)

_PROFILE_CACHE: OrderedDict[str, tuple[float, dict | None]] = OrderedDict()
MAX_PROFILE_CACHE = 10000


def get_default_db_path(db_path: Path | str | None = None) -> Path:
    env_db = os.getenv("SOCIAL_DB")
    if env_db:
        return Path(env_db).resolve()
    if db_path is not None:
        p = Path(db_path).resolve()
        if p.is_dir():
            return (p / "social.db").resolve()
        if p.name == "social.db":
            return p
        return p.with_name("social.db").resolve()
    cosmetics_env = os.getenv("COSMETICS_DB")
    if cosmetics_env:
        return Path(cosmetics_env).resolve().with_name("social.db")
    return (Path(__file__).resolve().parent / "social.db").resolve()


from client_ip import get_client_ip, normalize_ip as get_auth_ip_key


async def lookup_mojang_profile(name: str) -> dict | None | False:
    """Resolve Minecraft username to {id: plain_uuid, name: canonical_name} with bounded LRU cache."""
    now = time.monotonic()
    if name in _PROFILE_CACHE:
        ts, cached = _PROFILE_CACHE[name]
        if now - ts < 3600:
            _PROFILE_CACHE.move_to_end(name)
            return cached
        else:
            del _PROFILE_CACHE[name]

    url = f"https://api.mojang.com/users/profiles/minecraft/{name}"
    try:
        async with aiohttp.ClientSession(timeout=aiohttp.ClientTimeout(total=5)) as session:
            async with session.get(url) as resp:
                if resp.status == 200:
                    data = await resp.json(content_type=None)
                    if isinstance(data, dict) and "id" in data and "name" in data:
                        res = {"id": plain_uuid(data["id"]), "name": data["name"]}
                        _PROFILE_CACHE[name] = (now, res)
                        _PROFILE_CACHE.move_to_end(name)
                        if len(_PROFILE_CACHE) > MAX_PROFILE_CACHE:
                            _PROFILE_CACHE.popitem(last=False)
                        return res
                elif resp.status in (204, 404):
                    _PROFILE_CACHE[name] = (now, None)
                    _PROFILE_CACHE.move_to_end(name)
                    if len(_PROFILE_CACHE) > MAX_PROFILE_CACHE:
                        _PROFILE_CACHE.popitem(last=False)
                    return None
                else:
                    log.warning("Mojang profile lookup returned HTTP %d", resp.status)
                    return False
    except Exception as e:
        log.warning("Mojang profile lookup error: %s", type(e).__name__)
        return False


async def mint_turn_credentials() -> list[dict]:
    """Mint short-lived Cloudflare Realtime TURN credentials if key and token are configured."""
    key_id = os.getenv("CF_TURN_KEY_ID")
    token = os.getenv("CF_TURN_API_TOKEN")
    if not key_id or not token:
        return []

    url = f"https://rtc.live.cloudflare.com/v1/turn/keys/{key_id}/credentials/generate-ice-servers"
    headers = {
        "Authorization": f"Bearer {token}",
        "Content-Type": "application/json",
        "User-Agent": TURN_USER_AGENT,  # Cloudflare answers 403 to default library agents (seen with urllib)
    }
    body = {"ttl": 21600}
    try:
        async with aiohttp.ClientSession(timeout=aiohttp.ClientTimeout(total=5)) as session:
            async with session.post(url, headers=headers, json=body) as resp:
                if resp.status in (200, 201):
                    data = await resp.json(content_type=None)
                    turn_servers = []
                    for s in data.get("iceServers", []) if isinstance(data, dict) else []:
                        if not isinstance(s, dict) or not s.get("username"):
                            continue
                        username = str(s["username"])
                        credential = str(s.get("credential", ""))
                        urls = s.get("urls")
                        if isinstance(urls, str):
                            urls = [urls]
                        elif not isinstance(urls, list):
                            urls = [s.get("url")] if s.get("url") else []
                        for u in urls:
                            # UDP only (the client speaks TURN over UDP); a turn: URL without a transport is UDP (RFC 7065)
                            if isinstance(u, str) and u.startswith("turn:") and ("transport=" not in u or "transport=udp" in u):
                                turn_servers.append({"url": u, "user": username, "credential": credential})
                    return turn_servers
                else:
                    log.warning("CF TURN mint failed with status %d", resp.status)
                    return []
    except Exception as e:
        log.warning("CF TURN request failed: %s", type(e).__name__)
        return []


async def revoke_turn_credentials(usernames: list[str]) -> None:
    """Best effort: revoke a finished session's TURN credentials so they cannot be used for the rest of their TTL."""
    key_id = os.getenv("CF_TURN_KEY_ID")
    token = os.getenv("CF_TURN_API_TOKEN")
    if not key_id or not token:
        return
    headers = {"Authorization": f"Bearer {token}", "User-Agent": TURN_USER_AGENT}
    try:
        async with aiohttp.ClientSession(timeout=aiohttp.ClientTimeout(total=5)) as session:
            for user in usernames:
                url = f"https://rtc.live.cloudflare.com/v1/turn/keys/{key_id}/credentials/{quote(user, safe='')}/revoke"
                async with session.post(url, headers=headers) as resp:
                    if resp.status not in (200, 204):
                        log.warning("CF TURN revoke failed with status %d", resp.status)
    except Exception as e:
        log.warning("CF TURN revoke request failed: %s", type(e).__name__)


_BACKGROUND: set[asyncio.Task] = set()  # strong refs so fire-and-forget revokes are not garbage collected mid-request


def validate_game(game: Any) -> tuple[bool, dict | None]:
    if game is None:
        return True, {}
    if not isinstance(game, dict):
        return False, None
    sanitized = {}
    for k, v in game.items():
        if not isinstance(k, str) or len(k) > 32 or not isinstance(v, str) or len(v) > 64:
            return False, None
        if k in KNOWN_GAME_KEYS:
            sanitized[k] = v
    return True, sanitized


def validate_world(world: Any) -> tuple[bool, dict | None]:
    if world is None:
        return True, None
    if not isinstance(world, dict):
        return False, None
    wid = world.get("id")
    wname = world.get("name")
    scope = world.get("scope")
    slots = world.get("slots")
    used = world.get("used")
    relay_only = world.get("relayOnly", False)

    if not isinstance(wid, str) or len(wid) > 64:
        return False, None
    if not isinstance(wname, str) or len(wname) > 64:
        return False, None
    if not isinstance(scope, str) or scope.lower() not in ALLOWED_SCOPES:
        return False, None
    if not isinstance(slots, int) or type(slots) is bool or not (0 <= slots <= 64):
        return False, None
    if not isinstance(used, int) or type(used) is bool or not (0 <= used <= 64):
        return False, None

    return True, {
        "id": wid,
        "name": wname,
        "scope": scope.lower(),
        "slots": slots,
        "used": used,
        "relayOnly": relay_only is True
    }


def validate_presence(state: Any, game: Any, server: Any, world: Any) -> tuple[bool, dict | None]:
    if not isinstance(state, str) or state not in ALLOWED_STATES:
        return False, None

    ok_g, g = validate_game(game)
    if not ok_g:
        return False, None

    if server is not None and (not isinstance(server, str) or len(server) > 255):
        return False, None

    ok_w, w = validate_world(world)
    if not ok_w:
        return False, None

    return True, {
        "state": state,
        "game": g,
        "server": server,
        "world": w
    }


def are_games_compatible(host_game: dict | None, guest_game: dict | None) -> bool:
    """Verify Minecraft version, loader, and modpack match."""
    if not host_game or not guest_game:
        return True
    if host_game.get("mc") and guest_game.get("mc") and host_game.get("mc") != guest_game.get("mc"):
        return False
    if host_game.get("loader") and guest_game.get("loader") and host_game.get("loader") != guest_game.get("loader"):
        return False
    if host_game.get("packId", "") != guest_game.get("packId", ""):
        return False
    if host_game.get("packVersion", "") != guest_game.get("packVersion", ""):
        return False
    return True


# ---------------------------------------------------------------------------
# Storage
# ---------------------------------------------------------------------------

class SocialStore:
    def __init__(self, db_path: Path | str | None = None):
        self.db_path = get_default_db_path(db_path)
        self.db_path.parent.mkdir(parents=True, exist_ok=True)
        self._init_db()

    def _connect(self) -> sqlite3.Connection:
        conn = sqlite3.connect(self.db_path, timeout=5.0)
        conn.execute("PRAGMA journal_mode=WAL;")
        conn.execute("PRAGMA busy_timeout=5000;")
        return conn

    def _init_db(self) -> None:
        with self._connect() as conn:
            conn.executescript("""
                CREATE TABLE IF NOT EXISTS friends (
                    user_uuid TEXT NOT NULL,
                    friend_uuid TEXT NOT NULL,
                    nick TEXT,
                    since INTEGER NOT NULL,
                    PRIMARY KEY (user_uuid, friend_uuid)
                );
                CREATE INDEX IF NOT EXISTS idx_friends_user ON friends(user_uuid);

                CREATE TABLE IF NOT EXISTS requests (
                    from_uuid TEXT NOT NULL,
                    to_uuid TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    PRIMARY KEY (from_uuid, to_uuid)
                );
                CREATE INDEX IF NOT EXISTS idx_requests_to ON requests(to_uuid);
                CREATE INDEX IF NOT EXISTS idx_requests_from ON requests(from_uuid);

                CREATE TABLE IF NOT EXISTS request_log (
                    from_uuid TEXT NOT NULL,
                    sent_at INTEGER NOT NULL
                );
                CREATE INDEX IF NOT EXISTS idx_req_log ON request_log(from_uuid, sent_at);

                CREATE TABLE IF NOT EXISTS blocks (
                    user_uuid TEXT NOT NULL,
                    blocked_uuid TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    PRIMARY KEY (user_uuid, blocked_uuid)
                );
                CREATE INDEX IF NOT EXISTS idx_blocks_user ON blocks(user_uuid);

                CREATE TABLE IF NOT EXISTS dm_offline (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    from_uuid TEXT NOT NULL,
                    to_uuid TEXT NOT NULL,
                    text TEXT NOT NULL,
                    ts INTEGER NOT NULL
                );
                CREATE INDEX IF NOT EXISTS idx_dm_to ON dm_offline(to_uuid);

                CREATE TABLE IF NOT EXISTS settings (
                    uuid TEXT PRIMARY KEY,
                    show_server INTEGER NOT NULL DEFAULT 1,
                    appear_offline INTEGER NOT NULL DEFAULT 0
                );

                CREATE TABLE IF NOT EXISTS relay_usage (
                    month TEXT PRIMARY KEY,
                    bytes INTEGER NOT NULL DEFAULT 0
                );

                CREATE TABLE IF NOT EXISTS user_relay_usage (
                    month TEXT NOT NULL,
                    uuid TEXT NOT NULL,
                    bytes INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY (month, uuid)
                );

                CREATE TABLE IF NOT EXISTS player_names (
                    uuid TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    updated_at INTEGER NOT NULL
                );
                CREATE INDEX IF NOT EXISTS idx_player_names_name ON player_names(name COLLATE NOCASE);
            """)
            # One-time migration for legacy epoch seconds: values < 10^11 are seconds
            conn.execute("UPDATE friends SET since = since * 1000 WHERE since < 100000000000;")

    # --- Names ---
    async def save_name(self, uuid: str, name: str) -> None:
        def _op():
            with self._connect() as conn:
                now = int(time.time())
                conn.execute("DELETE FROM player_names WHERE name = ? COLLATE NOCASE AND uuid != ?", (name, uuid))
                conn.execute(
                    """INSERT INTO player_names (uuid, name, updated_at) VALUES (?, ?, ?)
                       ON CONFLICT(uuid) DO UPDATE SET name = ?, updated_at = ?""",
                    (uuid, name, now, name, now)
                )
        await asyncio.to_thread(_op)

    async def get_name(self, uuid: str) -> str | None:
        def _op():
            with self._connect() as conn:
                row = conn.execute("SELECT name FROM player_names WHERE uuid = ?", (uuid,)).fetchone()
                return row[0] if row else None
        return await asyncio.to_thread(_op)

    async def get_uuid_by_name(self, name: str) -> str | None:
        def _op():
            with self._connect() as conn:
                row = conn.execute("SELECT uuid FROM player_names WHERE name = ? COLLATE NOCASE ORDER BY updated_at DESC", (name,)).fetchone()
                return row[0] if row else None
        return await asyncio.to_thread(_op)

    async def get_name_row(self, name: str) -> tuple[str, str, int] | None:
        def _op():
            with self._connect() as conn:
                row = conn.execute(
                    "SELECT uuid, name, updated_at FROM player_names WHERE name = ? COLLATE NOCASE ORDER BY updated_at DESC",
                    (name,)
                ).fetchone()
                return (row[0], row[1], row[2]) if row else None
        return await asyncio.to_thread(_op)

    # --- Friends ---
    async def get_friends(self, user_uuid: str) -> list[dict]:
        def _op():
            with self._connect() as conn:
                cursor = conn.execute(
                    """SELECT f.friend_uuid, p.name, f.nick, f.since
                       FROM friends f
                       LEFT JOIN player_names p ON f.friend_uuid = p.uuid
                       WHERE f.user_uuid = ?""",
                    (user_uuid,)
                )
                res = []
                for row in cursor.fetchall():
                    res.append({
                        "uuid": row[0],
                        "name": row[1] or "Player",
                        "nick": row[2],
                        "since": row[3]
                    })
                return res
        return await asyncio.to_thread(_op)

    async def are_friends(self, u1: str, u2: str) -> bool:
        def _op():
            with self._connect() as conn:
                row = conn.execute(
                    "SELECT 1 FROM friends WHERE user_uuid = ? AND friend_uuid = ?",
                    (u1, u2)
                ).fetchone()
                return row is not None
        return await asyncio.to_thread(_op)

    async def add_friend(self, u1: str, u2: str) -> None:
        now = int(time.time() * 1000)
        def _op():
            with self._connect() as conn:
                conn.execute(
                    "INSERT OR IGNORE INTO friends (user_uuid, friend_uuid, since) VALUES (?, ?, ?)",
                    (u1, u2, now)
                )
                conn.execute(
                    "INSERT OR IGNORE INTO friends (user_uuid, friend_uuid, since) VALUES (?, ?, ?)",
                    (u2, u1, now)
                )
                conn.execute(
                    "DELETE FROM requests WHERE (from_uuid = ? AND to_uuid = ?) OR (from_uuid = ? AND to_uuid = ?)",
                    (u1, u2, u2, u1)
                )
        await asyncio.to_thread(_op)

    async def remove_friend(self, u1: str, u2: str) -> None:
        def _op():
            with self._connect() as conn:
                conn.execute(
                    "DELETE FROM friends WHERE (user_uuid = ? AND friend_uuid = ?) OR (user_uuid = ? AND friend_uuid = ?)",
                    (u1, u2, u2, u1)
                )
        await asyncio.to_thread(_op)

    async def set_nick(self, user_uuid: str, friend_uuid: str, nick: str | None) -> None:
        def _op():
            with self._connect() as conn:
                conn.execute(
                    "UPDATE friends SET nick = ? WHERE user_uuid = ? AND friend_uuid = ?",
                    (nick, user_uuid, friend_uuid)
                )
        await asyncio.to_thread(_op)

    async def friend_count(self, user_uuid: str) -> int:
        def _op():
            with self._connect() as conn:
                row = conn.execute("SELECT count(*) FROM friends WHERE user_uuid = ?", (user_uuid,)).fetchone()
                return row[0] if row else 0
        return await asyncio.to_thread(_op)

    # --- Requests ---
    async def add_request(self, from_uuid: str, to_uuid: str) -> None:
        now = int(time.time())
        def _op():
            with self._connect() as conn:
                conn.execute(
                    "INSERT OR REPLACE INTO requests (from_uuid, to_uuid, created_at) VALUES (?, ?, ?)",
                    (from_uuid, to_uuid, now)
                )
        await asyncio.to_thread(_op)

    async def delete_request(self, from_uuid: str, to_uuid: str) -> None:
        def _op():
            with self._connect() as conn:
                conn.execute("DELETE FROM requests WHERE from_uuid = ? AND to_uuid = ?", (from_uuid, to_uuid))
        await asyncio.to_thread(_op)

    async def has_request(self, from_uuid: str, to_uuid: str) -> bool:
        def _op():
            with self._connect() as conn:
                row = conn.execute(
                    "SELECT 1 FROM requests WHERE from_uuid = ? AND to_uuid = ?",
                    (from_uuid, to_uuid)
                ).fetchone()
                return row is not None
        return await asyncio.to_thread(_op)

    async def log_request_attempt(self, from_uuid: str) -> None:
        now = int(time.time())
        def _op():
            with self._connect() as conn:
                conn.execute("INSERT INTO request_log (from_uuid, sent_at) VALUES (?, ?)", (from_uuid, now))
                conn.execute("DELETE FROM request_log WHERE sent_at < ?", (now - 3600,))
        await asyncio.to_thread(_op)

    async def count_requests_last_hour(self, from_uuid: str) -> int:
        now = int(time.time())
        def _op():
            with self._connect() as conn:
                row = conn.execute(
                    "SELECT count(*) FROM request_log WHERE from_uuid = ? AND sent_at >= ?",
                    (from_uuid, now - 3600)
                ).fetchone()
                return row[0] if row else 0
        return await asyncio.to_thread(_op)

    async def get_incoming_requests(self, user_uuid: str) -> list[dict]:
        def _op():
            with self._connect() as conn:
                cursor = conn.execute(
                    """SELECT r.from_uuid, p.name
                       FROM requests r
                       LEFT JOIN player_names p ON r.from_uuid = p.uuid
                       WHERE r.to_uuid = ?""",
                    (user_uuid,)
                )
                return [{"uuid": row[0], "name": row[1] or "Player"} for row in cursor.fetchall()]
        return await asyncio.to_thread(_op)

    async def get_outgoing_requests(self, user_uuid: str) -> list[dict]:
        def _op():
            with self._connect() as conn:
                cursor = conn.execute(
                    """SELECT r.to_uuid, p.name
                       FROM requests r
                       LEFT JOIN player_names p ON r.to_uuid = p.uuid
                       WHERE r.from_uuid = ?""",
                    (user_uuid,)
                )
                return [{"uuid": row[0], "name": row[1] or "Player"} for row in cursor.fetchall()]
        return await asyncio.to_thread(_op)

    # --- Blocks ---
    async def is_blocked(self, user_uuid: str, target_uuid: str) -> bool:
        def _op():
            with self._connect() as conn:
                row = conn.execute(
                    "SELECT 1 FROM blocks WHERE user_uuid = ? AND blocked_uuid = ?",
                    (user_uuid, target_uuid)
                ).fetchone()
                return row is not None
        return await asyncio.to_thread(_op)

    async def is_any_blocked(self, u1: str, u2: str) -> bool:
        def _op():
            with self._connect() as conn:
                row = conn.execute(
                    "SELECT 1 FROM blocks WHERE (user_uuid = ? AND blocked_uuid = ?) OR (user_uuid = ? AND blocked_uuid = ?)",
                    (u1, u2, u2, u1)
                ).fetchone()
                return row is not None
        return await asyncio.to_thread(_op)

    async def get_blocked(self, user_uuid: str) -> list[str]:
        def _op():
            with self._connect() as conn:
                cursor = conn.execute("SELECT blocked_uuid FROM blocks WHERE user_uuid = ?", (user_uuid,))
                return [row[0] for row in cursor.fetchall()]
        return await asyncio.to_thread(_op)

    async def get_block_count(self, user_uuid: str) -> int:
        def _op():
            with self._connect() as conn:
                row = conn.execute("SELECT count(*) FROM blocks WHERE user_uuid = ?", (user_uuid,)).fetchone()
                return row[0] if row else 0
        return await asyncio.to_thread(_op)

    async def block(self, user_uuid: str, target_uuid: str) -> None:
        now = int(time.time())
        def _op():
            with self._connect() as conn:
                row = conn.execute("SELECT count(*) FROM blocks WHERE user_uuid = ?", (user_uuid,)).fetchone()
                count = row[0] if row else 0
                already = conn.execute(
                    "SELECT 1 FROM blocks WHERE user_uuid = ? AND blocked_uuid = ?",
                    (user_uuid, target_uuid)
                ).fetchone()
                if not already and count >= MAX_BLOCKS_PER_USER:
                    return
                conn.execute(
                    "INSERT OR REPLACE INTO blocks (user_uuid, blocked_uuid, created_at) VALUES (?, ?, ?)",
                    (user_uuid, target_uuid, now)
                )
                conn.execute(
                    "DELETE FROM friends WHERE (user_uuid = ? AND friend_uuid = ?) OR (user_uuid = ? AND friend_uuid = ?)",
                    (user_uuid, target_uuid, target_uuid, user_uuid)
                )
                conn.execute(
                    "DELETE FROM requests WHERE (from_uuid = ? AND to_uuid = ?) OR (from_uuid = ? AND to_uuid = ?)",
                    (user_uuid, target_uuid, target_uuid, user_uuid)
                )
        await asyncio.to_thread(_op)

    async def unblock(self, user_uuid: str, target_uuid: str) -> None:
        def _op():
            with self._connect() as conn:
                conn.execute("DELETE FROM blocks WHERE user_uuid = ? AND blocked_uuid = ?", (user_uuid, target_uuid))
        await asyncio.to_thread(_op)

    # --- Settings ---
    async def get_settings(self, user_uuid: str) -> dict:
        def _op():
            with self._connect() as conn:
                row = conn.execute("SELECT show_server, appear_offline FROM settings WHERE uuid = ?", (user_uuid,)).fetchone()
                if row:
                    return {"showServer": bool(row[0]), "appearOffline": bool(row[1])}
                return {"showServer": True, "appearOffline": False}
        return await asyncio.to_thread(_op)

    async def update_settings(self, user_uuid: str, show_server: bool | None, appear_offline: bool | None) -> dict:
        current = await self.get_settings(user_uuid)
        new_show = current["showServer"] if show_server is None else bool(show_server)
        new_appear = current["appearOffline"] if appear_offline is None else bool(appear_offline)

        def _op():
            with self._connect() as conn:
                conn.execute(
                    """INSERT INTO settings (uuid, show_server, appear_offline) VALUES (?, ?, ?)
                       ON CONFLICT(uuid) DO UPDATE SET show_server = ?, appear_offline = ?""",
                    (user_uuid, int(new_show), int(new_appear), int(new_show), int(new_appear))
                )
        await asyncio.to_thread(_op)
        return {"showServer": new_show, "appearOffline": new_appear}

    # --- Offline DMs ---
    async def save_offline_dm(self, from_uuid: str, to_uuid: str, text: str, ts: int) -> None:
        def _op():
            with self._connect() as conn:
                # Cap at 50 per (sender, recipient) pair
                row_pair = conn.execute(
                    "SELECT count(*) FROM dm_offline WHERE from_uuid = ? AND to_uuid = ?",
                    (from_uuid, to_uuid)
                ).fetchone()
                count_pair = row_pair[0] if row_pair else 0
                if count_pair >= MAX_OFFLINE_DMS_PER_PAIR:
                    excess_pair = count_pair - MAX_OFFLINE_DMS_PER_PAIR + 1
                    conn.execute(
                        """DELETE FROM dm_offline WHERE id IN (
                            SELECT id FROM dm_offline WHERE from_uuid = ? AND to_uuid = ? ORDER BY ts ASC LIMIT ?
                        )""",
                        (from_uuid, to_uuid, excess_pair)
                    )

                # Cap at 100 per recipient: evict the oldest message of the sender with the most queued messages
                while True:
                    row = conn.execute("SELECT count(*) FROM dm_offline WHERE to_uuid = ?", (to_uuid,)).fetchone()
                    count = row[0] if row else 0
                    if count < MAX_OFFLINE_DMS_PER_RECIPIENT:
                        break
                    heaviest = conn.execute(
                        """SELECT from_uuid FROM dm_offline
                           WHERE to_uuid = ?
                           GROUP BY from_uuid
                           ORDER BY count(*) DESC, min(ts) ASC
                           LIMIT 1""",
                        (to_uuid,)
                    ).fetchone()
                    if not heaviest:
                        break
                    h_from = heaviest[0]
                    conn.execute(
                        """DELETE FROM dm_offline WHERE id = (
                            SELECT id FROM dm_offline WHERE to_uuid = ? AND from_uuid = ? ORDER BY ts ASC LIMIT 1
                        )""",
                        (to_uuid, h_from)
                    )
                conn.execute(
                    "INSERT INTO dm_offline (from_uuid, to_uuid, text, ts) VALUES (?, ?, ?, ?)",
                    (from_uuid, to_uuid, text, ts)
                )
        await asyncio.to_thread(_op)

    async def get_and_clear_offline_dms(self, user_uuid: str) -> list[dict]:
        now_ms = int(time.time() * 1000)
        cutoff = now_ms - OFFLINE_DM_TTL_MS
        def _op():
            with self._connect() as conn:
                conn.execute("DELETE FROM dm_offline WHERE ts < ?", (cutoff,))
                cursor = conn.execute(
                    "SELECT from_uuid, text, ts FROM dm_offline WHERE to_uuid = ? ORDER BY ts ASC LIMIT 100",
                    (user_uuid,)
                )
                rows = cursor.fetchall()
                conn.execute("DELETE FROM dm_offline WHERE to_uuid = ?", (user_uuid,))
                return [{"from": r[0], "text": r[1], "ts": r[2]} for r in rows]
        return await asyncio.to_thread(_op)

    # --- Relay Usage ---
    async def record_relay_bytes(self, month: str, count: int) -> None:
        def _op():
            with self._connect() as conn:
                conn.execute(
                    """INSERT INTO relay_usage (month, bytes) VALUES (?, ?)
                       ON CONFLICT(month) DO UPDATE SET bytes = bytes + ?""",
                    (month, count, count)
                )
        await asyncio.to_thread(_op)

    async def get_relay_bytes(self, month: str) -> int:
        def _op():
            with self._connect() as conn:
                row = conn.execute("SELECT bytes FROM relay_usage WHERE month = ?", (month,)).fetchone()
                return row[0] if row else 0
        return await asyncio.to_thread(_op)

    async def record_user_relay_bytes(self, month: str, uuid: str, count: int) -> None:
        def _op():
            with self._connect() as conn:
                conn.execute(
                    """INSERT INTO user_relay_usage (month, uuid, bytes) VALUES (?, ?, ?)
                       ON CONFLICT(month, uuid) DO UPDATE SET bytes = bytes + ?""",
                    (month, uuid, count, count)
                )
        await asyncio.to_thread(_op)

    async def get_user_relay_bytes(self, month: str, uuid: str) -> int:
        def _op():
            with self._connect() as conn:
                row = conn.execute("SELECT bytes FROM user_relay_usage WHERE month = ? AND uuid = ?", (month, uuid)).fetchone()
                return row[0] if row else 0
        return await asyncio.to_thread(_op)


# ---------------------------------------------------------------------------
# Hub & Peers
# ---------------------------------------------------------------------------

class SocialPeer:
    def __init__(self, ws: web.WebSocketResponse, uuid: str, name: str, settings: dict, ip: str):
        self.ws = ws
        self.uuid = uuid
        self.name = name
        self.settings = settings
        self.ip = ip
        self.presence: dict = {"state": "menu"}
        self.gone: bool = False
        self.send_queue: asyncio.Queue[str | None] = asyncio.Queue(maxsize=256)
        self.writer_task: asyncio.Task = asyncio.create_task(self._writer())
        self.rate_tokens: float = 40.0
        self.rate_last: float = time.monotonic()
        self.rate_violations: int = 0

    async def _writer(self) -> None:
        try:
            while True:
                msg = await self.send_queue.get()
                if msg is None:
                    break
                try:
                    await asyncio.wait_for(self.ws.send_str(msg), timeout=10.0)
                except Exception:
                    self.gone = True
                    try:
                        await self.ws.close(code=1011, message=b"send timeout")
                    except Exception:
                        pass
                    break
        except asyncio.CancelledError:
            pass

    def send_json(self, data: dict) -> bool:
        if self.gone or self.ws.closed:
            return False
        try:
            text = json.dumps(data)
            self.send_queue.put_nowait(text)
            return True
        except asyncio.QueueFull:
            self.gone = True
            log.warning("Send queue full for %s, closing", self.uuid)
            try:
                asyncio.create_task(self.ws.close(code=1013, message=b"queue full"))
            except Exception:
                pass
            return False

    async def close(self, code: int = 1000, message: bytes = b"") -> None:
        self.gone = True
        try:
            self.send_queue.put_nowait(None)
        except Exception:
            pass
        if self.writer_task and not self.writer_task.done():
            self.writer_task.cancel()
        try:
            await self.ws.close(code=code, message=message)
        except Exception:
            pass


class SocialHub:
    def __init__(self, insecure: bool, store: SocialStore):
        self.insecure = insecure
        self.store = store
        self.peers: dict[str, SocialPeer] = {}
        self.sessions: dict[str, dict] = {}
        self.ended_sessions: dict[str, dict] = {}
        self.invites: dict[str, dict] = {}
        self.join_requests: dict[str, dict] = {}
        self.ip_counts: dict[str, int] = {}
        self.auth_failures: dict[str, list[float]] = {}
        self.auth_blocked_until: dict[str, float] = {}
        self._last_sweep = time.monotonic()
        self.dm_limits: dict[str, tuple[float, int]] = {}
        self.invite_limits: dict[str, tuple[float, int]] = {}
        self.join_limits: dict[str, tuple[float, int]] = {}
        self.signal_limits: dict[str, tuple[float, int]] = {}
        self.turn_limits: dict[str, tuple[float, int]] = {}

    def drop_session(self, sid: str, reason: str, notify: tuple[str, ...]) -> None:
        """Every way a P2P session ends: keep it 60 s for late p2p_end byte reports, revoke its TURN credentials, tell the peers."""
        sess = self.sessions.pop(sid, None)
        if sess is None:
            return
        self.signal_limits.pop(sid, None)
        sess["ended"] = True
        sess["ended_at"] = time.time()
        self.ended_sessions[sid] = sess
        if sess.get("turn_users"):
            task = asyncio.create_task(revoke_turn_credentials(sess["turn_users"]))
            _BACKGROUND.add(task)
            task.add_done_callback(_BACKGROUND.discard)
        for uuid in notify:
            p = self.peers.get(uuid)
            if p and not p.gone:
                p.send_json({"t": "p2p_end", "sid": sid, "reason": reason})

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
        if len(valid) >= 5:
            self.auth_blocked_until[ip_key] = now + 60.0
            self.auth_failures.pop(ip_key, None)
            while len(self.auth_blocked_until) > MAX_AUTH_DICT_ENTRIES:
                self.auth_blocked_until.pop(next(iter(self.auth_blocked_until)), None)
        else:
            self.auth_failures[ip_key] = valid
            while len(self.auth_failures) > MAX_AUTH_DICT_ENTRIES:
                self.auth_failures.pop(next(iter(self.auth_failures)), None)

    def clear_auth_failure(self, ip: str) -> None:
        ip_key = get_auth_ip_key(ip)
        self.auth_failures.pop(ip_key, None)
        self.auth_blocked_until.pop(ip_key, None)

    def maybe_sweep(self) -> None:
        now = time.monotonic()
        if now - self._last_sweep >= 60.0:
            self.sweep_expired()
            self._last_sweep = now

    def sweep_expired(self) -> None:
        now = time.time()
        now_mono = time.monotonic()
        for inv_id, inv in list(self.invites.items()):
            if now > inv["expires_at"]:
                del self.invites[inv_id]
        for req_id, req in list(self.join_requests.items()):
            if now > req["expires_at"]:
                del self.join_requests[req_id]
        for s_id, s_data in list(self.ended_sessions.items()):
            if now - s_data.get("ended_at", 0) > 60.0:
                del self.ended_sessions[s_id]

        for ip_k, fails in list(self.auth_failures.items()):
            valid = [t for t in fails if now_mono - t <= 60.0]
            if valid:
                self.auth_failures[ip_k] = valid
            else:
                del self.auth_failures[ip_k]

        for ip_k, until in list(self.auth_blocked_until.items()):
            if now_mono >= until:
                del self.auth_blocked_until[ip_k]

        while len(self.auth_failures) > MAX_AUTH_DICT_ENTRIES:
            self.auth_failures.pop(next(iter(self.auth_failures)), None)
        while len(self.auth_blocked_until) > MAX_AUTH_DICT_ENTRIES:
            self.auth_blocked_until.pop(next(iter(self.auth_blocked_until)), None)

    async def cleanup_peer(self, peer: SocialPeer) -> None:
        try:
            await peer.close()
        finally:
            await self.unregister(peer)

    def check_rate_limit(self, limits_dict: dict, uuid: str, limit: int, window: float) -> bool:
        now = time.monotonic()
        w_start, count = limits_dict.get(uuid, (now, 0))
        if now - w_start >= window:
            limits_dict[uuid] = (now, 1)
            return True
        if count >= limit:
            return False
        limits_dict[uuid] = (w_start, count + 1)
        return True

    def find_peer_by_name(self, name: str) -> SocialPeer | None:
        lower = name.lower()
        for p in self.peers.values():
            if not p.gone and p.name.lower() == lower:
                return p
        return None

    async def register(self, peer: SocialPeer) -> None:
        old = self.peers.get(peer.uuid)
        self.peers[peer.uuid] = peer
        if old:
            await old.close(code=4001, message=b"replaced")

    async def unregister(self, peer: SocialPeer) -> None:
        if self.peers.get(peer.uuid) is not peer:
            return

        del self.peers[peer.uuid]
        peer.gone = True

        # End active sessions
        for sid, sess in list(self.sessions.items()):
            if sess["host"] == peer.uuid or sess["guest"] == peer.uuid:
                other_uuid = sess["guest"] if sess["host"] == peer.uuid else sess["host"]
                self.drop_session(sid, "peer_disconnected", (other_uuid,))

        # Invalidate pending invites / join requests
        for inv_id, inv in list(self.invites.items()):
            if inv["host"] == peer.uuid or inv["guest"] == peer.uuid:
                del self.invites[inv_id]

        for req_id, req in list(self.join_requests.items()):
            if req["host"] == peer.uuid or req["guest"] == peer.uuid:
                del self.join_requests[req_id]

        # Broadcast offline presence to friends (shielded)
        friends = await self.store.get_friends(peer.uuid)
        for f in friends:
            f_uuid = f["uuid"]
            if await self.store.is_any_blocked(peer.uuid, f_uuid):
                continue
            f_peer = self.peers.get(f_uuid)
            if f_peer and not f_peer.gone:
                f_peer.send_json({"t": "presence", "uuid": peer.uuid, "state": "offline"})

    async def broadcast_presence(self, peer: SocialPeer) -> None:
        friends = await self.store.get_friends(peer.uuid)
        for f in friends:
            f_uuid = f["uuid"]
            if await self.store.is_any_blocked(peer.uuid, f_uuid):
                continue
            f_peer = self.peers.get(f_uuid)
            if f_peer and not f_peer.gone:
                payload = self.format_presence_for(peer, f_peer)
                f_peer.send_json(payload)

    def format_presence_for(self, subject: SocialPeer, viewer: SocialPeer) -> dict:
        if subject.settings.get("appearOffline", False):
            return {"t": "presence", "uuid": subject.uuid, "state": "offline"}

        payload = {
            "t": "presence",
            "uuid": subject.uuid,
            "state": subject.presence.get("state", "menu")
        }
        if subject.presence.get("game"):
            payload["game"] = subject.presence["game"]
        if subject.settings.get("showServer", True) and subject.presence.get("server"):
            payload["server"] = subject.presence["server"]
        if subject.presence.get("world"):
            payload["world"] = subject.presence["world"]
        return payload

    async def build_hello(self, peer: SocialPeer) -> dict:
        friends_raw = await self.store.get_friends(peer.uuid)
        friends = []
        for f in friends_raw:
            f_uuid = f["uuid"]
            pres = {"state": "offline"}
            if not await self.store.is_any_blocked(peer.uuid, f_uuid):
                f_peer = self.peers.get(f_uuid)
                if f_peer and not f_peer.gone:
                    fp = self.format_presence_for(f_peer, peer)
                    del fp["t"]
                    del fp["uuid"]
                    pres = fp
            friends.append({
                "uuid": f_uuid,
                "name": f["name"],
                "nick": f["nick"],
                "presence": pres,
                "since": f["since"]
            })

        incoming = await self.store.get_incoming_requests(peer.uuid)
        outgoing = await self.store.get_outgoing_requests(peer.uuid)
        blocked = await self.store.get_blocked(peer.uuid)
        settings = await self.store.get_settings(peer.uuid)
        dms = await self.store.get_and_clear_offline_dms(peer.uuid)

        return {
            "t": "hello",
            "friends": friends,
            "requests": {
                "in": incoming,
                "out": outgoing
            },
            "blocked": blocked,
            "settings": settings,
            "dms": dms
        }

    async def start_p2p(self, host_peer: SocialPeer, guest_peer: SocialPeer, relay_only: bool = False) -> None:
        sid = secrets.token_hex(12)
        month_str = time.strftime("%Y-%m", time.gmtime())
        relay_bytes = await self.store.get_relay_bytes(month_str)
        host_bytes = await self.store.get_user_relay_bytes(month_str, host_peer.uuid)
        guest_bytes = await self.store.get_user_relay_bytes(month_str, guest_peer.uuid)

        turn_list = []
        relay_status = "none"
        if relay_bytes >= RELAY_BUDGET_BYTES or host_bytes >= USER_RELAY_BUDGET_BYTES or guest_bytes >= USER_RELAY_BUDGET_BYTES:
            relay_status = "budget"
        elif not (self.check_rate_limit(self.turn_limits, host_peer.uuid, TURN_MINTS_PER_DAY, 86400.0)
                  and self.check_rate_limit(self.turn_limits, guest_peer.uuid, TURN_MINTS_PER_DAY, 86400.0)):
            relay_status = "budget"  # per-account daily cap on credential sets: direct P2P still works
        else:
            try:
                turn_list = await mint_turn_credentials()
                relay_status = "ok" if turn_list else "none"
            except Exception as e:
                log.warning("TURN credential minting error: %s", type(e).__name__)
                turn_list = []
                relay_status = "none"

        # ATOMIC session replace and insert: all awaits are done before this block
        for s_id, s_data in list(self.sessions.items()):
            if s_data["guest"] == guest_peer.uuid:
                self.drop_session(s_id, "replaced", (s_data["host"], s_data["guest"]))

        ice_config = {
            "stun": [STUN_SERVER],
            "turn": turn_list,
            "relayOnly": bool(relay_only),
            "relayStatus": relay_status
        }

        self.sessions[sid] = {
            "sid": sid,
            "host": host_peer.uuid,
            "guest": guest_peer.uuid,
            "created_at": time.time(),
            "has_turn": bool(turn_list),
            "turn_users": sorted({t["user"] for t in turn_list}),
            "reports": {},
            "recorded_bytes": 0
        }

        host_peer.send_json({
            "t": "p2p_start",
            "sid": sid,
            "peer": guest_peer.uuid,
            "role": "host",
            "ice": ice_config
        })
        guest_peer.send_json({
            "t": "p2p_start",
            "sid": sid,
            "peer": host_peer.uuid,
            "role": "guest",
            "ice": ice_config
        })

    async def close_all(self, app: web.Application) -> None:
        for p in list(self.peers.values()):
            await p.close(code=1001, message=b"server shutdown")


# ---------------------------------------------------------------------------
# Handshake & Dispatcher
# ---------------------------------------------------------------------------

async def read_ws(ws: web.WebSocketResponse) -> tuple[str | None, dict | None]:
    try:
        msg = await ws.receive()
    except Exception:
        return "error", None

    if msg.type != aiohttp.WSMsgType.TEXT:
        return "close", None
    try:
        data = json.loads(msg.data)
    except (ValueError, RecursionError, TypeError):
        return "malformed", None
    return "ok", data if isinstance(data, dict) else None


async def authenticate(hub: SocialHub, ws: web.WebSocketResponse, ip: str) -> SocialPeer | None:
    if hub.is_ip_auth_blocked(ip):
        return None

    nonce = secrets.token_hex(16)
    await ws.send_str(json.dumps({"t": "challenge", "nonce": nonce}))

    status, msg = await read_ws(ws)
    if status == "close":
        # Pre-auth disconnect is NOT an auth failure (Rule 5)
        return None
    if status != "ok" or not msg or msg.get("t") != "auth":
        hub.record_auth_failure(ip)
        return None

    uuid_str = plain_uuid(msg.get("uuid"))
    name = msg.get("name")
    sid = server_id(nonce)

    if not uuid_str or not isinstance(name, str) or not NAME_RE.fullmatch(name) or msg.get("serverId") != sid:
        hub.record_auth_failure(ip)
        return None

    if not hub.insecure:
        try:
            async with _MOJANG_SEMAPHORE:
                joined = await has_joined(name, sid)
        except Exception as e:
            log.warning("Mojang hasJoined failed: %s", type(e).__name__)
            joined = None
        if joined != uuid_str:
            hub.record_auth_failure(ip)
            log.info("Social auth refused for %s", uuid_str)
            return None

    # Auth throttle per IP is NOT reset by another account's success (Rule 5)
    await hub.store.save_name(uuid_str, name)
    settings = await hub.store.get_settings(uuid_str)
    peer = SocialPeer(ws=ws, uuid=uuid_str, name=name, settings=settings, ip=ip)
    log.info("Social auth ok: %s", uuid_str)
    return peer


async def serve(hub: SocialHub, peer: SocialPeer) -> None:
    store = hub.store

    while True:
        status, msg = await read_ws(peer.ws)
        if status == "malformed":
            await peer.close(code=4002, message=b"malformed message")
            break
        if status != "ok" or msg is None:
            break

        # Token bucket rate limiting: 20 msgs/s, burst capacity 40
        now = time.monotonic()
        peer.rate_tokens = min(40.0, peer.rate_tokens + (now - peer.rate_last) * 20.0)
        peer.rate_last = now

        if peer.rate_tokens < 1.0:
            peer.rate_violations += 1
            peer.rate_tokens -= 1.0
            if peer.rate_violations > 20 or peer.rate_tokens < -20.0:
                await peer.close(code=4008, message=b"rate abuse")
                break
            peer.send_json({"t": "error", "code": "rate", "ref": ""})
            continue
        else:
            peer.rate_tokens -= 1.0
            if peer.rate_violations > 0:
                peer.rate_violations -= 1

        kind = msg.get("t")
        if not isinstance(kind, str):
            peer.send_json({"t": "error", "code": "bad", "ref": ""})
            continue

        try:
            if kind == "presence":
                ok_pres, pres = validate_presence(
                    msg.get("state"),
                    msg.get("game"),
                    msg.get("server"),
                    msg.get("world")
                )
                if not ok_pres or pres is None:
                    peer.send_json({"t": "error", "code": "bad", "ref": "presence"})
                    continue

                old_world = peer.presence.get("world")
                peer.presence = pres

                # Invalidate pending invites if hosting closed
                if old_world and (peer.presence.get("state") != "hosting" or not peer.presence.get("world")):
                    for inv_id, inv in list(hub.invites.items()):
                        if inv["host"] == peer.uuid:
                            del hub.invites[inv_id]

                await hub.broadcast_presence(peer)

            elif kind == "friend_add":
                name = msg.get("name")
                if not isinstance(name, str) or not NAME_RE.fullmatch(name) or len(name) > 16:
                    peer.send_json({"t": "error", "code": "bad", "ref": str(name or "")})
                    continue

                # Rate limit (20 / hour) counted before lookup
                recent_count = await store.count_requests_last_hour(peer.uuid)
                if recent_count >= FRIEND_REQUEST_RATE_LIMIT:
                    peer.send_json({"t": "error", "code": "rate", "ref": name})
                    continue

                await store.log_request_attempt(peer.uuid)

                # Friend limit check (500)
                cur_count = await store.friend_count(peer.uuid)
                if cur_count >= MAX_FRIENDS:
                    peer.send_json({"t": "error", "code": "limit", "ref": name})
                    continue

                # Resolve name: check active peers, then DB cache (refresh if >24h old), then Mojang
                target_uuid = None
                canonical_name = name
                target_peer = hub.find_peer_by_name(name)
                if target_peer:
                    target_uuid = target_peer.uuid
                    canonical_name = target_peer.name
                else:
                    target_row = await store.get_name_row(name)
                    now_ts = int(time.time())
                    if target_row:
                        u, cname, updated_at = target_row
                        if now_ts - updated_at < 24 * 3600:
                            target_uuid = u
                            canonical_name = cname
                        else:
                            # Stale cache: refresh from Mojang on lookup
                            profile = await lookup_mojang_profile(name)
                            if isinstance(profile, dict):
                                target_uuid = profile["id"]
                                canonical_name = profile["name"]
                                await store.save_name(target_uuid, canonical_name)
                            elif profile is None:
                                target_uuid = None
                            else:
                                # Fall back to the cache only if Mojang has network failure (False)
                                target_uuid = u
                                canonical_name = cname
                    else:
                        profile = await lookup_mojang_profile(name)
                        if isinstance(profile, dict):
                            target_uuid = profile["id"]
                            canonical_name = profile["name"]
                            await store.save_name(target_uuid, canonical_name)

                if not target_uuid:
                    peer.send_json({"t": "error", "code": "not_found", "ref": name})
                    continue

                if target_uuid == peer.uuid:
                    peer.send_json({"t": "error", "code": "self", "ref": name})
                    continue

                if await store.are_friends(peer.uuid, target_uuid):
                    peer.send_json({"t": "error", "code": "already", "ref": name})
                    continue

                # Block check: if target blocked peer, silent drop to avoid block oracle
                if await store.is_blocked(target_uuid, peer.uuid):
                    peer.send_json({"t": "request_sent", "uuid": target_uuid, "name": canonical_name})
                    continue

                if await store.is_blocked(peer.uuid, target_uuid):
                    peer.send_json({"t": "error", "code": "blocked", "ref": name})
                    continue

                if await store.has_request(peer.uuid, target_uuid):
                    peer.send_json({"t": "error", "code": "already", "ref": name})
                    continue

                # Mutual auto-accept
                if await store.has_request(target_uuid, peer.uuid):
                    t_count = await store.friend_count(target_uuid)
                    if t_count >= MAX_FRIENDS:
                        peer.send_json({"t": "error", "code": "limit", "ref": name})
                        continue
                    await store.add_friend(peer.uuid, target_uuid)
                    peer.send_json({"t": "friend_added", "uuid": target_uuid, "name": canonical_name})
                    t_peer = hub.peers.get(target_uuid)
                    if t_peer and not t_peer.gone:
                        t_peer.send_json({"t": "friend_added", "uuid": peer.uuid, "name": peer.name})
                        # P13: send each side the other's current presence
                        peer.send_json(hub.format_presence_for(t_peer, peer))
                        t_peer.send_json(hub.format_presence_for(peer, t_peer))
                    else:
                        peer.send_json({"t": "presence", "uuid": target_uuid, "state": "offline"})
                else:
                    await store.add_request(peer.uuid, target_uuid)
                    peer.send_json({"t": "request_sent", "uuid": target_uuid, "name": canonical_name})
                    t_peer = hub.peers.get(target_uuid)
                    if t_peer and not t_peer.gone:
                        t_peer.send_json({"t": "request_in", "uuid": peer.uuid, "name": peer.name})

            elif kind == "friend_accept":
                t_uuid = plain_uuid(msg.get("uuid"))
                if not t_uuid:
                    peer.send_json({"t": "error", "code": "bad", "ref": ""})
                    continue

                if not await store.has_request(t_uuid, peer.uuid):
                    peer.send_json({"t": "error", "code": "not_found", "ref": t_uuid})
                    continue

                if await store.friend_count(peer.uuid) >= MAX_FRIENDS or await store.friend_count(t_uuid) >= MAX_FRIENDS:
                    peer.send_json({"t": "error", "code": "limit", "ref": t_uuid})
                    continue

                await store.add_friend(peer.uuid, t_uuid)
                t_name = await store.get_name(t_uuid) or "Player"
                peer.send_json({"t": "friend_added", "uuid": t_uuid, "name": t_name})

                t_peer = hub.peers.get(t_uuid)
                if t_peer and not t_peer.gone:
                    t_peer.send_json({"t": "friend_added", "uuid": peer.uuid, "name": peer.name})
                    # P13: send each side the other's current presence
                    peer.send_json(hub.format_presence_for(t_peer, peer))
                    t_peer.send_json(hub.format_presence_for(peer, t_peer))
                else:
                    peer.send_json({"t": "presence", "uuid": t_uuid, "state": "offline"})

            elif kind == "friend_decline":
                t_uuid = plain_uuid(msg.get("uuid"))
                if t_uuid:
                    await store.delete_request(t_uuid, peer.uuid)

            elif kind == "friend_remove":
                t_uuid = plain_uuid(msg.get("uuid"))
                if t_uuid and await store.are_friends(peer.uuid, t_uuid):
                    await store.remove_friend(peer.uuid, t_uuid)
                    peer.send_json({"t": "friend_removed", "uuid": t_uuid})
                    t_peer = hub.peers.get(t_uuid)
                    if t_peer and not t_peer.gone:
                        t_peer.send_json({"t": "friend_removed", "uuid": peer.uuid})
                        t_peer.send_json({"t": "presence", "uuid": peer.uuid, "state": "offline"})
                        peer.send_json({"t": "presence", "uuid": t_uuid, "state": "offline"})

                    # End active sessions between pair (Rule 3)
                    for s_id, s_data in list(hub.sessions.items()):
                        if (s_data["host"] == peer.uuid and s_data["guest"] == t_uuid) or (s_data["host"] == t_uuid and s_data["guest"] == peer.uuid):
                            hub.drop_session(s_id, "unfriended", (s_data["host"], s_data["guest"]))

                    # Invalidate in-flight invites and join requests
                    for inv_id, inv in list(hub.invites.items()):
                        if (inv["host"] == peer.uuid and inv["guest"] == t_uuid) or (inv["host"] == t_uuid and inv["guest"] == peer.uuid):
                            del hub.invites[inv_id]

                    for req_id, req in list(hub.join_requests.items()):
                        if (req["host"] == peer.uuid and req["guest"] == t_uuid) or (req["host"] == t_uuid and req["guest"] == peer.uuid):
                            del hub.join_requests[req_id]

            elif kind == "block":
                t_uuid = plain_uuid(msg.get("uuid"))
                if not t_uuid:
                    peer.send_json({"t": "error", "code": "bad", "ref": ""})
                    continue
                if t_uuid == peer.uuid:
                    peer.send_json({"t": "error", "code": "self", "ref": t_uuid})
                    continue

                if not await store.is_blocked(peer.uuid, t_uuid):
                    if await store.get_block_count(peer.uuid) >= MAX_BLOCKS_PER_USER:
                        peer.send_json({"t": "error", "code": "limit", "ref": t_uuid})
                        continue

                were_friends = await store.are_friends(peer.uuid, t_uuid)
                await store.block(peer.uuid, t_uuid)

                # End active sessions between pair
                for s_id, s_data in list(hub.sessions.items()):
                    if (s_data["host"] == peer.uuid and s_data["guest"] == t_uuid) or (s_data["host"] == t_uuid and s_data["guest"] == peer.uuid):
                        hub.drop_session(s_id, "blocked", (s_data["host"], s_data["guest"]))

                # Invalidate in-flight invites and join requests
                for inv_id, inv in list(hub.invites.items()):
                    if (inv["host"] == peer.uuid and inv["guest"] == t_uuid) or (inv["host"] == t_uuid and inv["guest"] == peer.uuid):
                        del hub.invites[inv_id]

                for req_id, req in list(hub.join_requests.items()):
                    if (req["host"] == peer.uuid and req["guest"] == t_uuid) or (req["host"] == t_uuid and req["guest"] == peer.uuid):
                        del hub.join_requests[req_id]

                # Lows: block sends friend_removed only if they were friends
                if were_friends:
                    peer.send_json({"t": "friend_removed", "uuid": t_uuid})
                    t_peer = hub.peers.get(t_uuid)
                    if t_peer and not t_peer.gone:
                        t_peer.send_json({"t": "friend_removed", "uuid": peer.uuid})

            elif kind == "unblock":
                t_uuid = plain_uuid(msg.get("uuid"))
                if t_uuid:
                    await store.unblock(peer.uuid, t_uuid)

            elif kind == "nick":
                t_uuid = plain_uuid(msg.get("uuid"))
                nick = msg.get("nick")
                if not t_uuid:
                    peer.send_json({"t": "error", "code": "bad", "ref": ""})
                    continue
                if nick is not None and (not isinstance(nick, str) or len(nick) > 32):
                    peer.send_json({"t": "error", "code": "bad", "ref": "nick"})
                    continue
                await store.set_nick(peer.uuid, t_uuid, nick)

            elif kind == "dm":
                to_uuid = plain_uuid(msg.get("to"))
                text = msg.get("text")
                if not to_uuid or not isinstance(text, str):
                    peer.send_json({"t": "error", "code": "bad", "ref": ""})
                    continue

                if len(text) > DM_MAX_CHARS:
                    peer.send_json({"t": "error", "code": "limit", "ref": to_uuid})
                    continue

                if not await store.are_friends(peer.uuid, to_uuid):
                    peer.send_json({"t": "error", "code": "not_friend", "ref": to_uuid})
                    continue

                if await store.is_any_blocked(peer.uuid, to_uuid):
                    peer.send_json({"t": "error", "code": "blocked", "ref": to_uuid})
                    continue

                # Rate limit (30 / min) surviving reconnect
                if not hub.check_rate_limit(hub.dm_limits, peer.uuid, DM_RATE_LIMIT, 60.0):
                    peer.send_json({"t": "error", "code": "rate", "ref": to_uuid})
                    continue

                now_ms = int(time.time() * 1000)
                target = hub.peers.get(to_uuid)
                if target and not target.gone:
                    target.send_json({"t": "dm", "from": peer.uuid, "text": text, "ts": now_ms})
                else:
                    await store.save_offline_dm(peer.uuid, to_uuid, text, now_ms)

            elif kind == "invite":
                to_uuid = plain_uuid(msg.get("to"))
                raw_world = msg.get("world")
                server = msg.get("server")
                if not to_uuid:
                    peer.send_json({"t": "error", "code": "bad", "ref": ""})
                    continue

                if not await store.are_friends(peer.uuid, to_uuid):
                    peer.send_json({"t": "error", "code": "not_friend", "ref": to_uuid})
                    continue

                if await store.is_any_blocked(peer.uuid, to_uuid):
                    peer.send_json({"t": "error", "code": "blocked", "ref": to_uuid})
                    continue

                # Rate limit (30 / hour) surviving reconnect
                if not hub.check_rate_limit(hub.invite_limits, peer.uuid, INVITE_RATE_LIMIT, 3600.0):
                    peer.send_json({"t": "error", "code": "rate", "ref": to_uuid})
                    continue

                target = hub.peers.get(to_uuid)
                if not target or target.gone or target.settings.get("appearOffline", False):
                    peer.send_json({"t": "error", "code": "offline", "ref": to_uuid})
                    continue

                if server:
                    if not isinstance(server, str) or len(server) > 255:
                        peer.send_json({"t": "error", "code": "bad", "ref": "server"})
                        continue
                    target.send_json({"t": "invite", "from": peer.uuid, "server": server})
                    continue

                ok_w, w = validate_world(raw_world or peer.presence.get("world"))
                if not ok_w or w is None:
                    peer.send_json({"t": "error", "code": "bad", "ref": "world"})
                    continue

                hub.sweep_expired()
                host_inv_count = sum(1 for iv in hub.invites.values() if iv["host"] == peer.uuid)
                if host_inv_count >= MAX_PENDING_INVITES_PER_HOST:
                    peer.send_json({"t": "error", "code": "limit", "ref": "invites"})
                    continue

                invite_id = secrets.token_hex(8)
                hub.invites[invite_id] = {
                    "invite_id": invite_id,
                    "host": peer.uuid,
                    "guest": to_uuid,
                    "world": w,
                    "expires_at": time.time() + INVITE_TTL_SECONDS
                }
                target.send_json({
                    "t": "invite",
                    "inviteId": invite_id,
                    "from": peer.uuid,
                    "world": w
                })

            elif kind == "invite_decline":
                invite_id = msg.get("inviteId")
                if not isinstance(invite_id, str):
                    peer.send_json({"t": "error", "code": "bad", "ref": ""})
                    continue
                inv = hub.invites.get(invite_id)
                # M1: invite_decline only by the invitee
                if not inv or inv["guest"] != peer.uuid:
                    peer.send_json({"t": "error", "code": "not_found", "ref": invite_id})
                    continue
                del hub.invites[invite_id]

            elif kind == "invite_accept":
                invite_id = msg.get("inviteId")
                if not isinstance(invite_id, str):
                    peer.send_json({"t": "error", "code": "bad", "ref": ""})
                    continue

                inv = hub.invites.get(invite_id)
                if not inv or time.time() > inv["expires_at"]:
                    peer.send_json({"t": "error", "code": "expired", "ref": invite_id})
                    continue

                # M1: invite_accept only by the invitee
                if inv["guest"] != peer.uuid:
                    peer.send_json({"t": "error", "code": "not_found", "ref": invite_id})
                    continue

                host_uuid = inv["host"]
                host_peer = hub.peers.get(host_uuid)
                if not host_peer or host_peer.gone or host_peer.settings.get("appearOffline", False) or host_peer.presence.get("state") != "hosting":
                    peer.send_json({"t": "error", "code": "offline", "ref": invite_id})
                    continue

                if not await store.are_friends(peer.uuid, host_uuid):
                    peer.send_json({"t": "error", "code": "not_friend", "ref": invite_id})
                    continue

                if await store.is_any_blocked(peer.uuid, host_uuid):
                    peer.send_json({"t": "error", "code": "blocked", "ref": invite_id})
                    continue

                # Re-validate against host's current presence and world (Rule 2)
                current_w = host_peer.presence.get("world")
                if not current_w or not isinstance(current_w, dict):
                    peer.send_json({"t": "error", "code": "expired", "ref": invite_id})
                    continue

                inv_w = inv.get("world") or {}
                if current_w.get("id") != inv_w.get("id"):
                    peer.send_json({"t": "error", "code": "expired", "ref": invite_id})
                    continue

                scope = str(current_w.get("scope", "friends")).lower()
                if scope in ("off", "lan"):
                    peer.send_json({"t": "error", "code": "offline", "ref": invite_id})
                    continue

                slots = current_w.get("slots", 8)
                used = current_w.get("used", 0)
                if used >= slots and slots > 0:
                    peer.send_json({"t": "error", "code": "full", "ref": invite_id})
                    continue

                if not are_games_compatible(host_peer.presence.get("game"), peer.presence.get("game")):
                    peer.send_json({"t": "error", "code": "incompatible", "ref": invite_id})
                    continue

                # Only consume the invite on successful session establishment (Rule 2)
                del hub.invites[invite_id]

                relay_only = bool(current_w.get("relayOnly", False))
                await hub.start_p2p(host_peer, peer, relay_only=relay_only)

            elif kind == "join_request":
                host_uuid = plain_uuid(msg.get("host"))
                if not host_uuid:
                    peer.send_json({"t": "error", "code": "bad", "ref": ""})
                    continue

                if not await store.are_friends(peer.uuid, host_uuid):
                    peer.send_json({"t": "error", "code": "not_friend", "ref": host_uuid})
                    continue

                if await store.is_any_blocked(peer.uuid, host_uuid):
                    peer.send_json({"t": "error", "code": "blocked", "ref": host_uuid})
                    continue

                host_peer = hub.peers.get(host_uuid)
                if not host_peer or host_peer.gone or host_peer.settings.get("appearOffline", False) or host_peer.presence.get("state") != "hosting":
                    peer.send_json({"t": "error", "code": "offline", "ref": host_uuid})
                    continue

                # Rate limit (30 / hour) checked AFTER host is verified online/hosting (Item 10)
                if not hub.check_rate_limit(hub.join_limits, peer.uuid, JOIN_REQUEST_RATE_LIMIT, 3600.0):
                    peer.send_json({"t": "error", "code": "rate", "ref": host_uuid})
                    continue

                world = host_peer.presence.get("world") or {}
                scope = str(world.get("scope", "friends")).lower()
                if scope != "friends":
                    peer.send_json({"t": "error", "code": "invite_only", "ref": host_uuid})
                    continue

                slots = world.get("slots", 8)
                used = world.get("used", 0)
                if used >= slots and slots > 0:
                    peer.send_json({"t": "error", "code": "full", "ref": host_uuid})
                    continue

                if not are_games_compatible(host_peer.presence.get("game"), peer.presence.get("game")):
                    peer.send_json({"t": "error", "code": "incompatible", "ref": host_uuid})
                    continue

                hub.sweep_expired()

                # N2: at most ONE pending join request per (guest, host) pair: a new one replaces the old
                for req_id, req in list(hub.join_requests.items()):
                    if req["guest"] == peer.uuid and req["host"] == host_uuid:
                        del hub.join_requests[req_id]

                host_req_count = sum(1 for jr in hub.join_requests.values() if jr["host"] == host_uuid)
                if host_req_count >= MAX_PENDING_JOIN_REQUESTS_PER_HOST:
                    peer.send_json({"t": "error", "code": "limit", "ref": "join_requests"})
                    continue

                req_id = secrets.token_hex(8)
                hub.join_requests[req_id] = {
                    "req_id": req_id,
                    "host": host_uuid,
                    "guest": peer.uuid,
                    "expires_at": time.time() + JOIN_REQUEST_TTL_SECONDS
                }
                host_peer.send_json({
                    "t": "join_request",
                    "reqId": req_id,
                    "from": peer.uuid,
                    "game": peer.presence.get("game")
                })

            elif kind == "join_deny":
                req_id = msg.get("reqId")
                if not isinstance(req_id, str):
                    peer.send_json({"t": "error", "code": "bad", "ref": ""})
                    continue
                req = hub.join_requests.get(req_id)
                if not req or req["host"] != peer.uuid:
                    peer.send_json({"t": "error", "code": "not_found", "ref": req_id})
                    continue
                del hub.join_requests[req_id]
                guest_peer = hub.peers.get(req["guest"])
                if guest_peer and not guest_peer.gone:
                    guest_peer.send_json({"t": "join_denied", "reqId": req_id})

            elif kind == "join_accept":
                req_id = msg.get("reqId")
                if not isinstance(req_id, str):
                    peer.send_json({"t": "error", "code": "bad", "ref": ""})
                    continue

                req = hub.join_requests.get(req_id)
                if not req or time.time() > req["expires_at"]:
                    peer.send_json({"t": "error", "code": "expired", "ref": req_id})
                    continue

                # M1: join_accept only by the host of that request
                if req["host"] != peer.uuid:
                    peer.send_json({"t": "error", "code": "not_found", "ref": req_id})
                    continue

                # Re-validate host state and guest friendship
                if peer.presence.get("state") != "hosting" or not peer.presence.get("world"):
                    peer.send_json({"t": "error", "code": "offline", "ref": req_id})
                    continue

                guest_uuid = req["guest"]
                if not await store.are_friends(peer.uuid, guest_uuid):
                    peer.send_json({"t": "error", "code": "not_friend", "ref": req_id})
                    continue

                if await store.is_any_blocked(peer.uuid, guest_uuid):
                    peer.send_json({"t": "error", "code": "blocked", "ref": req_id})
                    continue

                world = peer.presence.get("world") or {}
                scope = str(world.get("scope", "friends")).lower()
                if scope != "friends":
                    peer.send_json({"t": "error", "code": "invite_only", "ref": req_id})
                    continue

                slots = world.get("slots", 8)
                used = world.get("used", 0)
                if used >= slots and slots > 0:
                    peer.send_json({"t": "error", "code": "full", "ref": req_id})
                    continue

                guest_peer = hub.peers.get(guest_uuid)
                if not guest_peer or guest_peer.gone:
                    peer.send_json({"t": "error", "code": "offline", "ref": req_id})
                    continue

                if not are_games_compatible(peer.presence.get("game"), guest_peer.presence.get("game")):
                    peer.send_json({"t": "error", "code": "incompatible", "ref": req_id})
                    continue

                del hub.join_requests[req_id]
                relay_only = world.get("relayOnly", False)
                await hub.start_p2p(peer, guest_peer, relay_only=relay_only)

            elif kind == "signal":
                sid = msg.get("sid")
                data = msg.get("data")
                if not isinstance(sid, str) or not isinstance(data, dict):
                    peer.send_json({"t": "error", "code": "bad", "ref": ""})
                    continue

                data_str = json.dumps(data)
                if len(data_str) > MAX_SIGNAL_DATA_BYTES:
                    peer.send_json({"t": "error", "code": "limit", "ref": sid})
                    continue

                session = hub.sessions.get(sid)
                if not session or (session["host"] != peer.uuid and session["guest"] != peer.uuid):
                    peer.send_json({"t": "error", "code": "not_found", "ref": sid})
                    continue

                other_uuid = session["guest"] if session["host"] == peer.uuid else session["host"]
                # Re-check friendship and block on every signal message (Rule 3)
                if not await store.are_friends(peer.uuid, other_uuid):
                    peer.send_json({"t": "error", "code": "not_friend", "ref": sid})
                    continue

                if await store.is_any_blocked(peer.uuid, other_uuid):
                    peer.send_json({"t": "error", "code": "blocked", "ref": sid})
                    continue

                if not hub.check_rate_limit(hub.signal_limits, sid, MAX_SIGNALS_PER_SEC, 1.0):
                    peer.send_json({"t": "error", "code": "rate", "ref": sid})
                    continue

                other = hub.peers.get(other_uuid)
                if other and not other.gone:
                    other.send_json({"t": "signal", "sid": sid, "data": data})

            elif kind == "p2p_end":
                sid = msg.get("sid")
                reason = msg.get("reason", "normal")
                relayed = msg.get("relayed", False)
                raw_bytes = msg.get("bytes", 0)

                if not isinstance(sid, str):
                    peer.send_json({"t": "error", "code": "bad", "ref": ""})
                    continue

                if not isinstance(reason, str) or len(reason) > 64:
                    peer.send_json({"t": "error", "code": "bad", "ref": "reason"})
                    continue

                session = hub.sessions.get(sid) or hub.ended_sessions.get(sid)
                if not session or (session["host"] != peer.uuid and session["guest"] != peer.uuid):
                    peer.send_json({"t": "error", "code": "not_found", "ref": sid})
                    continue

                if raw_bytes is not None and raw_bytes != 0:
                    if not isinstance(raw_bytes, int) or isinstance(raw_bytes, bool) or raw_bytes < 0:
                        peer.send_json({"t": "error", "code": "bad", "ref": "bytes"})
                        continue

                clamped_bytes = 0
                if relayed is True and session["has_turn"] and isinstance(raw_bytes, int) and raw_bytes > 0:
                    elapsed = max(0.0, time.time() - session["created_at"])
                    elapsed_capped = min(elapsed, MAX_RELAY_SESSION_DURATION)
                    max_allowed = min(int(elapsed_capped * RELAY_MAX_BYTES_PER_SEC), MAX_RELAY_SESSION_BYTES)
                    clamped_bytes = min(raw_bytes, max_allowed)

                reports = session.setdefault("reports", {})
                own_delta = clamped_bytes - reports.get(peer.uuid, 0)
                if own_delta > 0:
                    reports[peer.uuid] = clamped_bytes

                # The global budget counts the session once (the larger report); each player's monthly
                # quota takes only their own report, so a friend cannot use it up with an inflated one.
                new_max = max(reports.values(), default=0)
                delta = new_max - session.get("recorded_bytes", 0)
                month_str = time.strftime("%Y-%m", time.gmtime())
                if delta > 0:
                    session["recorded_bytes"] = new_max
                    await store.record_relay_bytes(month_str, delta)
                if own_delta > 0:
                    await store.record_user_relay_bytes(month_str, peer.uuid, own_delta)

                other_uuid = session["guest"] if session["host"] == peer.uuid else session["host"]
                hub.drop_session(sid, reason, (other_uuid,))

            elif kind == "settings":
                raw_show = msg.get("showServer")
                raw_appear = msg.get("appearOffline")

                if raw_show is not None and not isinstance(raw_show, bool):
                    peer.send_json({"t": "error", "code": "bad", "ref": "showServer"})
                    continue
                if raw_appear is not None and not isinstance(raw_appear, bool):
                    peer.send_json({"t": "error", "code": "bad", "ref": "appearOffline"})
                    continue

                updated = await store.update_settings(peer.uuid, raw_show, raw_appear)
                peer.settings = updated
                await hub.broadcast_presence(peer)

            else:
                peer.send_json({"t": "error", "code": "bad", "ref": str(kind)})

        except Exception as e:
            log.warning("Error processing %s from %s: %s", kind, peer.uuid, type(e).__name__)
            peer.send_json({"t": "error", "code": "bad", "ref": str(kind)})


async def handle_social_ws(request: web.Request) -> web.WebSocketResponse:
    hub: SocialHub = request.app["social_hub"]
    hub.maybe_sweep()
    ip = get_client_ip(request)

    if hub.is_ip_auth_blocked(ip):
        ws = web.WebSocketResponse()
        await ws.prepare(request)
        await ws.close(code=4003, message=b"auth throttled")
        return ws

    # Per-IP connection limit check (H2)
    if hub.ip_counts.get(ip, 0) >= MAX_CONCURRENT_SOCKETS_PER_IP:
        ws = web.WebSocketResponse()
        await ws.prepare(request)
        await ws.close(code=1008, message=b"too many connections")
        return ws

    hub.ip_counts[ip] = hub.ip_counts.get(ip, 0) + 1
    peer = None
    try:
        ws = web.WebSocketResponse(
            heartbeat=30.0,
            receive_timeout=90.0,
            max_msg_size=WS_MAX_MSG_SIZE
        )
        await ws.prepare(request)

        try:
            peer = await asyncio.wait_for(authenticate(hub, ws, ip), timeout=AUTH_TIMEOUT)
        except asyncio.TimeoutError:
            # Idle timeout before auth is NOT an auth failure (Rule 5)
            await ws.close(code=4003, message=b"auth timeout")
            return ws

        if not peer:
            await ws.close(code=4003, message=b"unauthorized")
            return ws

        hello_data = await hub.build_hello(peer)
        peer.send_json(hello_data)

        await hub.register(peer)
        await hub.broadcast_presence(peer)
        await serve(hub, peer)
    finally:
        cnt = hub.ip_counts.get(ip, 1) - 1
        if cnt <= 0:
            hub.ip_counts.pop(ip, None)
        else:
            hub.ip_counts[ip] = cnt
        if peer:
            await asyncio.shield(hub.cleanup_peer(peer))

    return ws


def mount(app: web.Application, insecure: bool = False, db_path: Path | str | None = None) -> None:
    if insecure and os.getenv("LADS_SOCIAL_DEV") != "1":
        raise ValueError("insecure=True requires LADS_SOCIAL_DEV=1 environment variable")

    store = SocialStore(db_path)
    hub = SocialHub(insecure=insecure, store=store)
    app["social_store"] = store
    app["social_hub"] = hub
    app.router.add_get("/social/ws", handle_social_ws)
    app.on_shutdown.append(hub.close_all)
