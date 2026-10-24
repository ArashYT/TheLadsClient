"""Lads social relay & backend (Task S1): friends graph, presence, DMs,
invites, join requests, ICE signaling, and Cloudflare TURN minting.

Mounted on the cosmetics aiohttp server at /social/ws.
"""
from __future__ import annotations

import asyncio
import json
import logging
import os
import re
import secrets
import sqlite3
import time
from pathlib import Path
from typing import Optional, Dict, Any, List, Set

import aiohttp
from aiohttp import web

from cosmetics import plain_uuid, server_id, has_joined, NAME_RE

log = logging.getLogger("social")

MAX_FRIENDS = 500
FRIEND_REQUEST_RATE_LIMIT = 20  # per hour
DM_RATE_LIMIT = 30              # per minute
DM_MAX_CHARS = 256
INVITE_RATE_LIMIT = 30          # per hour
JOIN_REQUEST_RATE_LIMIT = 30    # per hour
OFFLINE_DM_TTL_MS = 7 * 86400 * 1000  # 7 days
INVITE_TTL_SECONDS = 300        # 5 minutes
JOIN_REQUEST_TTL_SECONDS = 300  # 5 minutes
RELAY_BUDGET_BYTES = 900 * 1024 * 1024 * 1024  # 900 GB monthly budget
STUN_SERVER = "stun:stun.cloudflare.com:3478"

_PROFILE_CACHE: dict[str, tuple[float, dict | None]] = {}


async def lookup_mojang_profile(name: str) -> dict | None:
    """Resolve Minecraft username to {id: plain_uuid, name: canonical_name}."""
    now = time.monotonic()
    if name in _PROFILE_CACHE:
        ts, cached = _PROFILE_CACHE[name]
        if now - ts < 3600:
            return cached
    url = f"https://api.mojang.com/users/profiles/minecraft/{name}"
    try:
        async with aiohttp.ClientSession(timeout=aiohttp.ClientTimeout(total=5)) as session:
            async with session.get(url) as resp:
                if resp.status == 200:
                    data = await resp.json(content_type=None)
                    if isinstance(data, dict) and "id" in data and "name" in data:
                        res = {"id": plain_uuid(data["id"]), "name": data["name"]}
                        _PROFILE_CACHE[name] = (now, res)
                        return res
                _PROFILE_CACHE[name] = (now, None)
                return None
    except Exception as e:
        log.warning("Mojang profile lookup failed for %s: %s", name, e)
        return None


async def mint_turn_credentials() -> list[dict]:
    """Mint short-lived Cloudflare Realtime TURN credentials if key and token are configured."""
    key_id = os.getenv("CF_TURN_KEY_ID")
    token = os.getenv("CF_TURN_API_TOKEN")
    if not key_id or not token:
        return []

    url = f"https://rtc.live.cloudflare.com/v1/turn/keys/{key_id}/credentials/generate-ice-servers"
    headers = {
        "Authorization": f"Bearer {token}",
        "Content-Type": "application/json"
    }
    body = {"ttl": 21600}
    try:
        async with aiohttp.ClientSession(timeout=aiohttp.ClientTimeout(total=5)) as session:
            async with session.post(url, headers=headers, json=body) as resp:
                if resp.status == 200:
                    data = await resp.json(content_type=None)
                    turn_servers = []
                    for s in data.get("iceServers", []):
                        username = s.get("username", "")
                        credential = s.get("credential", "")
                        urls = s.get("urls") or [s.get("url")]
                        for u in urls:
                            if u:
                                turn_servers.append({"url": u, "user": username, "credential": credential})
                    return turn_servers
                else:
                    log.warning("CF TURN mint failed with status %d", resp.status)
                    return []
    except Exception as e:
        log.warning("CF TURN request failed: %s", e)
        return []


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
        if db_path is not None:
            p = Path(db_path)
            if p.name == "cosmetics.db":
                p = p.with_name("social.db")
            elif p.is_dir():
                p = p / "social.db"
            self.db_path = p
        else:
            env_db = os.getenv("SOCIAL_DB")
            self.db_path = Path(env_db) if env_db else Path("social.db")

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

                CREATE TABLE IF NOT EXISTS player_names (
                    uuid TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    updated_at INTEGER NOT NULL
                );
                CREATE INDEX IF NOT EXISTS idx_player_names_name ON player_names(name COLLATE NOCASE);
            """)

    # --- Names ---
    async def save_name(self, uuid: str, name: str) -> None:
        def _op():
            with self._connect() as conn:
                conn.execute(
                    """INSERT INTO player_names (uuid, name, updated_at) VALUES (?, ?, ?)
                       ON CONFLICT(uuid) DO UPDATE SET name = ?, updated_at = ?""",
                    (uuid, name, int(time.time()), name, int(time.time()))
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
                row = conn.execute("SELECT uuid FROM player_names WHERE name = ? COLLATE NOCASE", (name,)).fetchone()
                return row[0] if row else None
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

    async def friend_count(self, user_uuid: str) -> int:
        def _op():
            with self._connect() as conn:
                row = conn.execute("SELECT count(*) FROM friends WHERE user_uuid = ?", (user_uuid,)).fetchone()
                return row[0] if row else 0
        return await asyncio.to_thread(_op)

    async def add_friend(self, u1: str, u2: str) -> None:
        now_ms = int(time.time() * 1000)
        def _op():
            with self._connect() as conn:
                conn.execute(
                    """INSERT OR IGNORE INTO friends (user_uuid, friend_uuid, nick, since)
                       VALUES (?, ?, NULL, ?), (?, ?, NULL, ?)""",
                    (u1, u2, now_ms, u2, u1, now_ms)
                )
                conn.execute("DELETE FROM requests WHERE (from_uuid = ? AND to_uuid = ?) OR (from_uuid = ? AND to_uuid = ?)",
                             (u1, u2, u2, u1))
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

    # --- Requests ---
    async def add_request(self, from_uuid: str, to_uuid: str) -> None:
        now = int(time.time())
        def _op():
            with self._connect() as conn:
                conn.execute(
                    "INSERT OR REPLACE INTO requests (from_uuid, to_uuid, created_at) VALUES (?, ?, ?)",
                    (from_uuid, to_uuid, now)
                )
                conn.execute("INSERT INTO request_log (from_uuid, sent_at) VALUES (?, ?)", (from_uuid, now))
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

    async def delete_request(self, from_uuid: str, to_uuid: str) -> None:
        def _op():
            with self._connect() as conn:
                conn.execute("DELETE FROM requests WHERE from_uuid = ? AND to_uuid = ?", (from_uuid, to_uuid))
        await asyncio.to_thread(_op)

    async def count_requests_last_hour(self, from_uuid: str) -> int:
        cutoff = int(time.time()) - 3600
        def _op():
            with self._connect() as conn:
                row = conn.execute(
                    "SELECT count(*) FROM request_log WHERE from_uuid = ? AND sent_at > ?",
                    (from_uuid, cutoff)
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

    async def block(self, user_uuid: str, target_uuid: str) -> None:
        now = int(time.time())
        def _op():
            with self._connect() as conn:
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
                conn.execute(
                    "INSERT INTO dm_offline (from_uuid, to_uuid, text, ts) VALUES (?, ?, ?, ?)",
                    (from_uuid, to_uuid, text, ts)
                )
        await asyncio.to_thread(_op)

    async def get_and_clear_offline_dms(self, user_uuid: str) -> list[dict]:
        cutoff = int(time.time() * 1000) - OFFLINE_DM_TTL_MS
        def _op():
            with self._connect() as conn:
                # Prune old
                conn.execute("DELETE FROM dm_offline WHERE ts < ?", (cutoff,))
                cursor = conn.execute(
                    "SELECT from_uuid, text, ts FROM dm_offline WHERE to_uuid = ? AND ts >= ? ORDER BY ts ASC",
                    (user_uuid, cutoff)
                )
                dms = [{"from": row[0], "text": row[1], "ts": row[2]} for row in cursor.fetchall()]
                conn.execute("DELETE FROM dm_offline WHERE to_uuid = ?", (user_uuid,))
                return dms
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


# ---------------------------------------------------------------------------
# Hub & Peers
# ---------------------------------------------------------------------------

class SocialPeer:
    def __init__(self, ws: web.WebSocketResponse, uuid: str, name: str, settings: dict):
        self.ws = ws
        self.uuid = uuid
        self.name = name
        self.presence: dict = {"state": "menu"}
        self.settings = settings
        self.dm_window: float = time.monotonic()
        self.dm_count: int = 0
        self.invite_window: float = time.monotonic()
        self.invite_count: int = 0
        self.join_req_window: float = time.monotonic()
        self.join_req_count: int = 0
        self.gone: bool = False


class SocialHub:
    def __init__(self, insecure: bool, store: SocialStore):
        self.insecure = insecure
        self.store = store
        self.peers: dict[str, SocialPeer] = {}
        self.sessions: dict[str, dict] = {}
        self.invites: dict[str, dict] = {}
        self.join_requests: dict[str, dict] = {}

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
            old.gone = True
            try:
                await old.ws.close(code=4001, message=b"replaced")
            except Exception:
                pass

    async def unregister(self, peer: SocialPeer) -> None:
        if self.peers.get(peer.uuid) is peer:
            del self.peers[peer.uuid]
        peer.gone = True

        # End active sessions
        for sid, sess in list(self.sessions.items()):
            if sess["host"] == peer.uuid or sess["guest"] == peer.uuid:
                del self.sessions[sid]
                other_uuid = sess["guest"] if sess["host"] == peer.uuid else sess["host"]
                other = self.peers.get(other_uuid)
                if other and not other.gone:
                    try:
                        await other.ws.send_json({"t": "p2p_end", "sid": sid, "reason": "peer_disconnected"})
                    except Exception:
                        pass

        # Invalidate pending invites / join requests
        for inv_id, inv in list(self.invites.items()):
            if inv["host"] == peer.uuid or inv["guest"] == peer.uuid:
                del self.invites[inv_id]

        for req_id, req in list(self.join_requests.items()):
            if req["host"] == peer.uuid or req["guest"] == peer.uuid:
                del self.join_requests[req_id]

        # Broadcast offline presence to friends
        friends = await self.store.get_friends(peer.uuid)
        for f in friends:
            f_uuid = f["uuid"]
            if await self.store.is_any_blocked(peer.uuid, f_uuid):
                continue
            f_peer = self.peers.get(f_uuid)
            if f_peer and not f_peer.gone:
                try:
                    await f_peer.ws.send_json({"t": "presence", "uuid": peer.uuid, "state": "offline"})
                except Exception:
                    pass

    async def broadcast_presence(self, peer: SocialPeer) -> None:
        friends = await self.store.get_friends(peer.uuid)
        for f in friends:
            f_uuid = f["uuid"]
            if await self.store.is_any_blocked(peer.uuid, f_uuid):
                continue
            f_peer = self.peers.get(f_uuid)
            if f_peer and not f_peer.gone:
                payload = self.format_presence_for(peer, f_peer)
                try:
                    await f_peer.ws.send_json(payload)
                except Exception:
                    pass

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
        month_str = time.strftime("%Y-%m")
        relay_bytes = await self.store.get_relay_bytes(month_str)

        turn_list = []
        if relay_bytes < RELAY_BUDGET_BYTES:
            try:
                turn_list = await mint_turn_credentials()
            except Exception as e:
                log.warning("TURN credential minting error: %s", e)
                turn_list = []

        ice_config = {
            "stun": [STUN_SERVER],
            "turn": turn_list,
            "relayOnly": relay_only
        }

        self.sessions[sid] = {
            "sid": sid,
            "host": host_peer.uuid,
            "guest": guest_peer.uuid,
            "created_at": time.time()
        }

        await host_peer.ws.send_json({
            "t": "p2p_start",
            "sid": sid,
            "peer": guest_peer.uuid,
            "role": "host",
            "ice": ice_config
        })
        await guest_peer.ws.send_json({
            "t": "p2p_start",
            "sid": sid,
            "peer": host_peer.uuid,
            "role": "guest",
            "ice": ice_config
        })

    async def close_all(self, app: web.Application) -> None:
        for p in list(self.peers.values()):
            try:
                await p.ws.close(code=1001, message=b"server shutdown")
            except Exception:
                pass


# ---------------------------------------------------------------------------
# Handshake & Dispatcher
# ---------------------------------------------------------------------------

async def read_ws(ws: web.WebSocketResponse) -> dict | None:
    msg = await ws.receive()
    if msg.type != aiohttp.WSMsgType.TEXT:
        return None
    try:
        data = json.loads(msg.data)
    except ValueError:
        return None
    return data if isinstance(data, dict) else None


async def authenticate(hub: SocialHub, ws: web.WebSocketResponse) -> SocialPeer | None:
    nonce = secrets.token_hex(16)
    await ws.send_str(json.dumps({"t": "challenge", "nonce": nonce}))
    msg = await read_ws(ws)
    if not msg or msg.get("t") != "auth":
        return None
    uuid_str = plain_uuid(msg.get("uuid"))
    name = msg.get("name")
    sid = server_id(nonce)
    if not uuid_str or not isinstance(name, str) or not NAME_RE.fullmatch(name) or msg.get("serverId") != sid:
        return None
    if not hub.insecure:
        try:
            joined = await has_joined(name, sid)
        except Exception as e:
            log.warning("Mojang hasJoined failed: %s", e)
            joined = None
        if joined != uuid_str:
            log.info("Social auth refused for %s (%s)", name, uuid_str)
            return None

    await hub.store.save_name(uuid_str, name)
    settings = await hub.store.get_settings(uuid_str)
    peer = SocialPeer(ws=ws, uuid=uuid_str, name=name, settings=settings)
    await hub.register(peer)
    log.info("Social auth ok: %s (%s)", name, uuid_str)
    return peer


async def serve(hub: SocialHub, peer: SocialPeer) -> None:
    store = hub.store

    while (msg := await read_ws(peer.ws)) is not None:
        kind = msg.get("t")

        if kind == "presence":
            old_world = peer.presence.get("world")
            peer.presence = {
                "state": msg.get("state", "menu"),
                "game": msg.get("game"),
                "server": msg.get("server"),
                "world": msg.get("world")
            }
            # If host stopped hosting, invalidate pending invites from this host
            if old_world and (peer.presence.get("state") != "hosting" or not peer.presence.get("world")):
                for inv_id, inv in list(hub.invites.items()):
                    if inv["host"] == peer.uuid:
                        del hub.invites[inv_id]

            await hub.broadcast_presence(peer)

        elif kind == "friend_add":
            name = msg.get("name")
            if not isinstance(name, str) or not NAME_RE.fullmatch(name):
                await peer.ws.send_json({"t": "error", "code": "not_found", "ref": name or ""})
                continue

            # Rate limit check (20 / hour)
            recent_count = await store.count_requests_last_hour(peer.uuid)
            if recent_count >= FRIEND_REQUEST_RATE_LIMIT:
                await peer.ws.send_json({"t": "error", "code": "rate", "ref": name})
                continue

            # Friend limit check (500)
            cur_count = await store.friend_count(peer.uuid)
            if cur_count >= MAX_FRIENDS:
                await peer.ws.send_json({"t": "error", "code": "limit", "ref": name})
                continue

            # Resolve name
            target_uuid = None
            canonical_name = name
            target_peer = hub.find_peer_by_name(name)
            if target_peer:
                target_uuid = target_peer.uuid
                canonical_name = target_peer.name
            else:
                target_uuid = await store.get_uuid_by_name(name)

            if not target_uuid:
                profile = await lookup_mojang_profile(name)
                if profile:
                    target_uuid = profile["id"]
                    canonical_name = profile["name"]
                    await store.save_name(target_uuid, canonical_name)

            if not target_uuid:
                await peer.ws.send_json({"t": "error", "code": "not_found", "ref": name})
                continue

            if target_uuid == peer.uuid:
                await peer.ws.send_json({"t": "error", "code": "self", "ref": name})
                continue

            if await store.are_friends(peer.uuid, target_uuid):
                await peer.ws.send_json({"t": "error", "code": "already", "ref": name})
                continue

            if await store.is_any_blocked(peer.uuid, target_uuid):
                await peer.ws.send_json({"t": "error", "code": "blocked", "ref": name})
                continue

            if await store.has_request(peer.uuid, target_uuid):
                await peer.ws.send_json({"t": "error", "code": "already", "ref": name})
                continue

            # If target already sent a request -> auto accept mutual request
            if await store.has_request(target_uuid, peer.uuid):
                await store.add_friend(peer.uuid, target_uuid)
                await peer.ws.send_json({"t": "friend_added", "uuid": target_uuid, "name": canonical_name})
                t_peer = hub.peers.get(target_uuid)
                if t_peer and not t_peer.gone:
                    await t_peer.ws.send_json({"t": "friend_added", "uuid": peer.uuid, "name": peer.name})
            else:
                await store.add_request(peer.uuid, target_uuid)
                await peer.ws.send_json({"t": "request_sent", "uuid": target_uuid, "name": canonical_name})
                t_peer = hub.peers.get(target_uuid)
                if t_peer and not t_peer.gone:
                    await t_peer.ws.send_json({"t": "request_in", "uuid": peer.uuid, "name": peer.name})

        elif kind == "friend_accept":
            t_uuid = plain_uuid(msg.get("uuid"))
            if not t_uuid:
                await peer.ws.send_json({"t": "error", "code": "not_found", "ref": ""})
                continue

            if not await store.has_request(t_uuid, peer.uuid):
                await peer.ws.send_json({"t": "error", "code": "not_found", "ref": t_uuid})
                continue

            if await store.friend_count(peer.uuid) >= MAX_FRIENDS or await store.friend_count(t_uuid) >= MAX_FRIENDS:
                await peer.ws.send_json({"t": "error", "code": "limit", "ref": t_uuid})
                continue

            await store.add_friend(peer.uuid, t_uuid)
            t_name = await store.get_name(t_uuid) or "Player"
            await peer.ws.send_json({"t": "friend_added", "uuid": t_uuid, "name": t_name})

            t_peer = hub.peers.get(t_uuid)
            if t_peer and not t_peer.gone:
                await t_peer.ws.send_json({"t": "friend_added", "uuid": peer.uuid, "name": peer.name})

        elif kind == "friend_decline":
            t_uuid = plain_uuid(msg.get("uuid"))
            if t_uuid:
                await store.delete_request(t_uuid, peer.uuid)

        elif kind == "friend_remove":
            t_uuid = plain_uuid(msg.get("uuid"))
            if t_uuid and await store.are_friends(peer.uuid, t_uuid):
                await store.remove_friend(peer.uuid, t_uuid)
                await peer.ws.send_json({"t": "friend_removed", "uuid": t_uuid})
                t_peer = hub.peers.get(t_uuid)
                if t_peer and not t_peer.gone:
                    await t_peer.ws.send_json({"t": "friend_removed", "uuid": peer.uuid})
                    await t_peer.ws.send_json({"t": "presence", "uuid": peer.uuid, "state": "offline"})
                    await peer.ws.send_json({"t": "presence", "uuid": t_uuid, "state": "offline"})

        elif kind == "block":
            t_uuid = plain_uuid(msg.get("uuid"))
            if not t_uuid:
                continue
            if t_uuid == peer.uuid:
                await peer.ws.send_json({"t": "error", "code": "self", "ref": t_uuid})
                continue
            await store.block(peer.uuid, t_uuid)

        elif kind == "unblock":
            t_uuid = plain_uuid(msg.get("uuid"))
            if t_uuid:
                await store.unblock(peer.uuid, t_uuid)

        elif kind == "nick":
            t_uuid = plain_uuid(msg.get("uuid"))
            nick = msg.get("nick")
            if t_uuid:
                await store.set_nick(peer.uuid, t_uuid, str(nick) if nick is not None else None)

        elif kind == "dm":
            to_uuid = plain_uuid(msg.get("to"))
            text = msg.get("text", "")
            if not to_uuid or not isinstance(text, str):
                continue

            if len(text) > DM_MAX_CHARS:
                await peer.ws.send_json({"t": "error", "code": "limit", "ref": to_uuid})
                continue

            if not await store.are_friends(peer.uuid, to_uuid):
                await peer.ws.send_json({"t": "error", "code": "not_friend", "ref": to_uuid})
                continue

            if await store.is_any_blocked(peer.uuid, to_uuid):
                await peer.ws.send_json({"t": "error", "code": "blocked", "ref": to_uuid})
                continue

            now = time.monotonic()
            if now - peer.dm_window >= 60:
                peer.dm_window, peer.dm_count = now, 0
            peer.dm_count += 1
            if peer.dm_count > DM_RATE_LIMIT:
                await peer.ws.send_json({"t": "error", "code": "rate", "ref": to_uuid})
                continue

            now_ms = int(time.time() * 1000)
            target = hub.peers.get(to_uuid)
            if target and not target.gone:
                await target.ws.send_json({"t": "dm", "from": peer.uuid, "text": text, "ts": now_ms})
            else:
                await store.save_offline_dm(peer.uuid, to_uuid, text, now_ms)

        elif kind == "invite":
            to_uuid = plain_uuid(msg.get("to"))
            world = msg.get("world")
            server = msg.get("server")
            if not to_uuid:
                continue

            if not await store.are_friends(peer.uuid, to_uuid):
                await peer.ws.send_json({"t": "error", "code": "not_friend", "ref": to_uuid})
                continue

            if await store.is_any_blocked(peer.uuid, to_uuid):
                await peer.ws.send_json({"t": "error", "code": "blocked", "ref": to_uuid})
                continue

            now = time.monotonic()
            if now - peer.invite_window >= 3600:
                peer.invite_window, peer.invite_count = now, 0
            peer.invite_count += 1
            if peer.invite_count > INVITE_RATE_LIMIT:
                await peer.ws.send_json({"t": "error", "code": "rate", "ref": to_uuid})
                continue

            target = hub.peers.get(to_uuid)
            if not target or target.gone:
                await peer.ws.send_json({"t": "error", "code": "offline", "ref": to_uuid})
                continue

            if server:
                await target.ws.send_json({"t": "invite", "from": peer.uuid, "server": server})
                continue

            invite_id = secrets.token_hex(8)
            hub.invites[invite_id] = {
                "invite_id": invite_id,
                "host": peer.uuid,
                "guest": to_uuid,
                "world": world or peer.presence.get("world"),
                "expires_at": time.time() + INVITE_TTL_SECONDS
            }
            await target.ws.send_json({
                "t": "invite",
                "inviteId": invite_id,
                "from": peer.uuid,
                "world": hub.invites[invite_id]["world"]
            })

        elif kind == "invite_decline":
            invite_id = msg.get("inviteId")
            if invite_id in hub.invites:
                del hub.invites[invite_id]

        elif kind == "invite_accept":
            invite_id = msg.get("inviteId")
            inv = hub.invites.get(invite_id)
            if not inv or time.time() > inv["expires_at"]:
                await peer.ws.send_json({"t": "error", "code": "expired", "ref": invite_id or ""})
                continue

            host_uuid = inv["host"]
            host_peer = hub.peers.get(host_uuid)
            if not host_peer or host_peer.gone or host_peer.presence.get("state") != "hosting" or not host_peer.presence.get("world"):
                await peer.ws.send_json({"t": "error", "code": "expired", "ref": invite_id})
                continue

            if not await store.are_friends(peer.uuid, host_uuid):
                await peer.ws.send_json({"t": "error", "code": "not_friend", "ref": invite_id})
                continue

            world = host_peer.presence.get("world") or {}
            slots = world.get("slots", 8)
            used = world.get("used", 0)
            if used >= slots and slots > 0:
                await peer.ws.send_json({"t": "error", "code": "full", "ref": invite_id})
                continue

            if not are_games_compatible(host_peer.presence.get("game"), peer.presence.get("game")):
                await peer.ws.send_json({"t": "error", "code": "incompatible", "ref": invite_id})
                continue

            del hub.invites[invite_id]
            await hub.start_p2p(host_peer, peer, relay_only=world.get("relayOnly", False))

        elif kind == "join_request":
            host_uuid = plain_uuid(msg.get("host"))
            if not host_uuid:
                continue

            if not await store.are_friends(peer.uuid, host_uuid):
                await peer.ws.send_json({"t": "error", "code": "not_friend", "ref": host_uuid})
                continue

            if await store.is_any_blocked(peer.uuid, host_uuid):
                await peer.ws.send_json({"t": "error", "code": "blocked", "ref": host_uuid})
                continue

            host_peer = hub.peers.get(host_uuid)
            if not host_peer or host_peer.gone or host_peer.presence.get("state") != "hosting" or not host_peer.presence.get("world"):
                await peer.ws.send_json({"t": "error", "code": "offline", "ref": host_uuid})
                continue

            world = host_peer.presence.get("world") or {}
            scope = str(world.get("scope", "friends")).lower()
            if scope != "friends":
                await peer.ws.send_json({"t": "error", "code": "invite_only", "ref": host_uuid})
                continue

            slots = world.get("slots", 8)
            used = world.get("used", 0)
            if used >= slots and slots > 0:
                await peer.ws.send_json({"t": "error", "code": "full", "ref": host_uuid})
                continue

            if not are_games_compatible(host_peer.presence.get("game"), peer.presence.get("game")):
                await peer.ws.send_json({"t": "error", "code": "incompatible", "ref": host_uuid})
                continue

            now = time.monotonic()
            if now - peer.join_req_window >= 3600:
                peer.join_req_window, peer.join_req_count = now, 0
            peer.join_req_count += 1
            if peer.join_req_count > JOIN_REQUEST_RATE_LIMIT:
                await peer.ws.send_json({"t": "error", "code": "rate", "ref": host_uuid})
                continue

            req_id = secrets.token_hex(8)
            hub.join_requests[req_id] = {
                "req_id": req_id,
                "host": host_uuid,
                "guest": peer.uuid,
                "expires_at": time.time() + JOIN_REQUEST_TTL_SECONDS
            }
            await host_peer.ws.send_json({
                "t": "join_request",
                "reqId": req_id,
                "from": peer.uuid,
                "game": peer.presence.get("game")
            })

        elif kind == "join_deny":
            req_id = msg.get("reqId")
            req = hub.join_requests.pop(req_id, None)
            if req:
                guest_peer = hub.peers.get(req["guest"])
                if guest_peer and not guest_peer.gone:
                    await guest_peer.ws.send_json({"t": "join_denied", "reqId": req_id})

        elif kind == "join_accept":
            req_id = msg.get("reqId")
            req = hub.join_requests.pop(req_id, None)
            if not req or time.time() > req["expires_at"]:
                await peer.ws.send_json({"t": "error", "code": "expired", "ref": req_id or ""})
                continue

            guest_uuid = req["guest"]
            guest_peer = hub.peers.get(guest_uuid)
            if not guest_peer or guest_peer.gone:
                await peer.ws.send_json({"t": "error", "code": "offline", "ref": req_id})
                continue

            world = peer.presence.get("world") or {}
            await hub.start_p2p(peer, guest_peer, relay_only=world.get("relayOnly", False))

        elif kind == "signal":
            sid = msg.get("sid")
            data = msg.get("data")
            session = hub.sessions.get(sid)
            if not session:
                await peer.ws.send_json({"t": "error", "code": "not_found", "ref": sid or ""})
                continue

            if peer.uuid != session["host"] and peer.uuid != session["guest"]:
                await peer.ws.send_json({"t": "error", "code": "not_found", "ref": sid})
                continue

            other_uuid = session["guest"] if peer.uuid == session["host"] else session["host"]
            if not await store.are_friends(peer.uuid, other_uuid):
                await peer.ws.send_json({"t": "error", "code": "not_friend", "ref": sid})
                continue

            target_peer = hub.peers.get(other_uuid)
            if target_peer and not target_peer.gone:
                await target_peer.ws.send_json({"t": "signal", "sid": sid, "data": data})

        elif kind == "p2p_end":
            sid = msg.get("sid")
            reason = msg.get("reason", "closed")
            relayed = bool(msg.get("relayed", False))
            byte_count = int(msg.get("bytes", 0))

            session = hub.sessions.pop(sid, None)
            if session:
                if relayed and byte_count > 0:
                    month_str = time.strftime("%Y-%m")
                    await store.record_relay_bytes(month_str, byte_count)

                other_uuid = session["guest"] if peer.uuid == session["host"] else session["host"]
                target_peer = hub.peers.get(other_uuid)
                if target_peer and not target_peer.gone:
                    await target_peer.ws.send_json({"t": "p2p_end", "sid": sid, "reason": reason})

        elif kind == "settings":
            show_server = msg.get("showServer")
            appear_offline = msg.get("appearOffline")
            old_appear = peer.settings.get("appearOffline", False)
            res = await store.update_settings(peer.uuid, show_server, appear_offline)
            peer.settings = res
            if res.get("appearOffline") != old_appear:
                await hub.broadcast_presence(peer)


async def handle_social_ws(request: web.Request) -> web.WebSocketResponse:
    ws = web.WebSocketResponse(heartbeat=30.0)
    await ws.prepare(request)

    hub: SocialHub = request.app["social_hub"]
    peer = await authenticate(hub, ws)
    if not peer:
        await ws.close(code=4003, message=b"unauthorized")
        return ws

    try:
        hello_data = await hub.build_hello(peer)
        await ws.send_json(hello_data)
        await hub.broadcast_presence(peer)
        await serve(hub, peer)
    finally:
        await hub.unregister(peer)

    return ws


def mount(app: web.Application, insecure: bool = False, db_path: Path | str | None = None) -> None:
    store = SocialStore(db_path)
    hub = SocialHub(insecure=insecure, store=store)
    app["social_store"] = store
    app["social_hub"] = hub
    app.router.add_get("/social/ws", handle_social_ws)
    app.on_shutdown.append(hub.close_all)
