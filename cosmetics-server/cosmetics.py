"""Lads cosmetics relay & backend (1.9.11/Phase 2): which Lads Client players are emoting and wearing cosmetics,
per Minecraft server ("room"), so every Lads player on the same server sees it.

Phase 2 additions:
- SQLite store for entitlements(uuid, item_id, source, granted_at, revoked_at) and equipped(uuid, slot, item_id).
- Profile GET /cosmetics/profile/{uuid} (everyone owns "lads:groove").
- WebSocket equip message {"t":"equip","slot","item"} and HTTP POST /cosmetics/equip.
- Tebex webhook endpoint POST /cosmetics/tebex (idempotent, signature-verified, payment.completed / refund).
- Link-code flow GET /cosmetics/link/start (and WS {"t":"link"}).
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
from typing import Optional, List, Dict, Any

import aiohttp
from aiohttp import web

EMOTES = frozenset({"lads:groove"})  # allow-list: any other free emote id is rejected unless owned
FREE = ["lads:groove"]  # everyone owns these
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
SLOT_RE = re.compile(r"^[a-z0-9_]{1,32}$")
ITEM_RE = re.compile(r"^[a-z0-9_:\-]{1,64}$")
LINK_ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"
LINK_CODE_TTL = 600  # 10 minutes

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
    async with aiohttp.ClientSession(timeout=aiohttp.ClientTimeout(total=5)) as session:
        async with session.get(HAS_JOINED, params={"username": name, "serverId": sid}) as response:
            if response.status != 200:
                return None
            body = await response.json(content_type=None)
            return plain_uuid(body.get("id")) if isinstance(body, dict) else None


def client_ip(request: web.Request) -> str:
    forwarded = request.headers.get("CF-Connecting-IP")  # set by the Cloudflare tunnel
    return forwarded.split(",")[0].strip() if forwarded else (request.remote or "?")


def verify_tebex_signature(raw_body: bytes, signature: str | None, secret: str | None) -> bool:
    """Validate Tebex webhook signature.
    Per Tebex docs:
    1. SHA-256 hash the raw body to hex string.
    2. HMAC-SHA256 the hex string using the webhook secret key.
    Also accepts standard single-step HMAC-SHA256 for maximum compatibility across Tebex webhook versions.
    """
    if not secret:
        # Dev / unconfigured mode without secret
        return True
    if not signature:
        return False
    sig = signature.strip()
    # Tebex two-step HMAC
    body_hash = hashlib.sha256(raw_body).hexdigest()
    expected_two_step = hmac.new(secret.encode("utf-8"), body_hash.encode("utf-8"), hashlib.sha256).hexdigest()
    if hmac.compare_digest(expected_two_step, sig):
        return True
    # Standard single-step HMAC
    expected_single_step = hmac.new(secret.encode("utf-8"), raw_body, hashlib.sha256).hexdigest()
    if hmac.compare_digest(expected_single_step, sig):
        return True
    return False


def extract_tebex_items(subject: dict) -> list[str]:
    """Extract item IDs from Tebex webhook subject payload (packages, products, or custom fields)."""
    items = []
    # 1. Packages array
    for pkg in subject.get("packages", []):
        if isinstance(pkg, dict):
            custom = pkg.get("custom")
            if isinstance(custom, dict) and custom.get("item_id"):
                items.append(str(custom["item_id"]))
            elif pkg.get("item_id"):
                items.append(str(pkg["item_id"]))
            elif pkg.get("name"):
                name = str(pkg["name"]).strip()
                if ":" in name:
                    items.append(name)
                else:
                    items.append("lads:" + name.lower().replace(" ", "_"))
            elif pkg.get("id"):
                items.append(f"tebex:pkg_{pkg['id']}")
    # 2. Products array
    for prod in subject.get("products", []):
        if isinstance(prod, dict):
            if prod.get("item_id"):
                items.append(str(prod["item_id"]))
            elif prod.get("name"):
                name = str(prod["name"]).strip()
                if ":" in name:
                    items.append(name)
                else:
                    items.append("lads:" + name.lower().replace(" ", "_"))
    # 3. Direct item_id or items list
    if subject.get("item_id"):
        items.append(str(subject["item_id"]))
    if isinstance(subject.get("items"), list):
        for it in subject["items"]:
            if isinstance(it, str):
                items.append(it)
            elif isinstance(it, dict) and it.get("item_id"):
                items.append(str(it["item_id"]))
    # Preserve order and deduplicate
    return list(dict.fromkeys(items))


def extract_buyer_uuid(subject: dict, store: CosmeticsStore | None = None) -> str | None:
    """Extract buyer Minecraft UUID from Tebex payload or resolve via link code."""
    for path in [
        subject.get("player"),
        subject.get("customer"),
        subject.get("custom"),
        subject.get("variables"),
        subject,
    ]:
        if isinstance(path, dict):
            raw = path.get("uuid") or path.get("minecraft_uuid") or path.get("game_id")
            u = plain_uuid(raw)
            if u:
                return u
    # Resolve via link code if present in customer/custom/variables
    if store:
        for path in [subject.get("customer"), subject.get("player"), subject.get("custom"), subject]:
            if isinstance(path, dict):
                code = path.get("link_code") or path.get("code")
                if code:
                    u = store.get_uuid_for_link_code_sync(str(code))
                    if u:
                        return u
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

    # --- Synchronous Worker Methods ---

    def grant_sync(self, uuid: str, item_id: str, source: str = "admin", granted_at: int | None = None) -> None:
        if granted_at is None:
            granted_at = int(time.time())
        with self._get_connection() as conn:
            conn.execute("""
                INSERT INTO entitlements (uuid, item_id, source, granted_at, revoked_at)
                VALUES (?, ?, ?, ?, NULL)
                ON CONFLICT(uuid, item_id) DO UPDATE SET
                    source = excluded.source,
                    granted_at = excluded.granted_at,
                    revoked_at = NULL;
            """, (uuid, item_id, source, granted_at))
            conn.commit()

    def revoke_sync(self, uuid: str, item_id: str, revoked_at: int | None = None) -> None:
        if revoked_at is None:
            revoked_at = int(time.time())
        with self._get_connection() as conn:
            conn.execute("""
                UPDATE entitlements
                SET revoked_at = ?
                WHERE uuid = ? AND item_id = ? AND revoked_at IS NULL;
            """, (revoked_at, uuid, item_id))
            conn.execute("DELETE FROM equipped WHERE uuid = ? AND item_id = ?;", (uuid, item_id))
            conn.commit()

    def has_entitlement_sync(self, uuid: str, item_id: str) -> bool:
        with self._get_connection() as conn:
            cur = conn.cursor()
            cur.execute("""
                SELECT 1 FROM entitlements
                WHERE uuid = ? AND item_id = ? AND revoked_at IS NULL;
            """, (uuid, item_id))
            return cur.fetchone() is not None

    def get_profile_sync(self, uuid: str) -> dict:
        with self._get_connection() as conn:
            cur = conn.cursor()
            cur.execute("""
                SELECT item_id FROM entitlements
                WHERE uuid = ? AND revoked_at IS NULL;
            """, (uuid,))
            owned = [row[0] for row in cur.fetchall()]
            for f in FREE:
                if f not in owned:
                    owned.insert(0, f)
            cur.execute("SELECT slot, item_id FROM equipped WHERE uuid = ?;", (uuid,))
            equipped = {row[0]: row[1] for row in cur.fetchall()}
            return {"owned": owned, "equipped": equipped}

    def set_equipped_sync(self, uuid: str, slot: str, item_id: str) -> None:
        with self._get_connection() as conn:
            conn.execute("""
                INSERT INTO equipped (uuid, slot, item_id)
                VALUES (?, ?, ?)
                ON CONFLICT(uuid, slot) DO UPDATE SET item_id = excluded.item_id;
            """, (uuid, slot, item_id))
            conn.commit()

    def remove_equipped_sync(self, uuid: str, slot: str) -> None:
        with self._get_connection() as conn:
            conn.execute("DELETE FROM equipped WHERE uuid = ? AND slot = ?;", (uuid, slot))
            conn.commit()

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
        now = int(time.time())
        with self._get_connection() as conn:
            cur = conn.cursor()
            cur.execute("SELECT uuid, items_json, event_type FROM transactions WHERE transaction_id = ?;", (transaction_id,))
            row = cur.fetchone()
            if not row:
                # Check if entitlements exist with source = tebex:<transaction_id>
                cur.execute("SELECT uuid, item_id FROM entitlements WHERE source = ?;", (f"tebex:{transaction_id}",))
                ent_rows = cur.fetchall()
                if not ent_rows:
                    return None
                uuid = ent_rows[0][0]
                items = [r[1] for r in ent_rows]
                prev_event = "payment.completed"
            else:
                uuid = row[0]
                items = json.loads(row[1])
                prev_event = row[2]

            if prev_event in ("payment.refunded", "payment.dispute.opened", "payment.disputed", "payment.chargeback"):
                return {"already_refunded": True, "uuid": uuid, "items": items}

            for item_id in items:
                conn.execute("""
                    UPDATE entitlements
                    SET revoked_at = ?
                    WHERE uuid = ? AND item_id = ? AND revoked_at IS NULL;
                """, (now, uuid, item_id))
                conn.execute("DELETE FROM equipped WHERE uuid = ? AND item_id = ?;", (uuid, item_id))

            if row:
                conn.execute("""
                    UPDATE transactions
                    SET event_type = ?, updated_at = ?
                    WHERE transaction_id = ?;
                """, (event_type, now, transaction_id))
            else:
                conn.execute("""
                    INSERT INTO transactions (transaction_id, event_type, uuid, items_json, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?);
                """, (transaction_id, event_type, uuid, json.dumps(items), now, now))
            conn.commit()
            return {"already_refunded": False, "uuid": uuid, "items": items}

    def create_link_code_sync(self, uuid: str, ttl: int = LINK_CODE_TTL) -> str:
        now = int(time.time())
        with self._get_connection() as conn:
            # Clean expired codes
            conn.execute("DELETE FROM link_codes WHERE expires_at < ?;", (now,))
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

    def list_sync(self, uuid: str) -> dict:
        with self._get_connection() as conn:
            cur = conn.cursor()
            cur.execute("""
                SELECT item_id, source, granted_at, revoked_at
                FROM entitlements
                WHERE uuid = ?
                ORDER BY granted_at ASC;
            """, (uuid,))
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
            cur.execute("SELECT slot, item_id FROM equipped WHERE uuid = ?;", (uuid,))
            equipped = {r[0]: r[1] for r in cur.fetchall()}
            return {"uuid": uuid, "entitlements": entitlements, "equipped": equipped}

    # --- Asynchronous Wrappers (asyncio.to_thread) ---

    async def grant(self, uuid: str, item_id: str, source: str = "admin") -> None:
        await asyncio.to_thread(self.grant_sync, uuid, item_id, source)

    async def revoke(self, uuid: str, item_id: str) -> None:
        await asyncio.to_thread(self.revoke_sync, uuid, item_id)

    async def has_entitlement(self, uuid: str, item_id: str) -> bool:
        return await asyncio.to_thread(self.has_entitlement_sync, uuid, item_id)

    async def get_profile(self, uuid: str) -> dict:
        return await asyncio.to_thread(self.get_profile_sync, uuid)

    async def set_equipped(self, uuid: str, slot: str, item_id: str) -> None:
        await asyncio.to_thread(self.set_equipped_sync, uuid, slot, item_id)

    async def remove_equipped(self, uuid: str, slot: str) -> None:
        await asyncio.to_thread(self.remove_equipped_sync, uuid, slot)

    async def record_transaction(self, transaction_id: str, event_type: str, uuid: str, items: list[str]) -> dict:
        return await asyncio.to_thread(self.record_transaction_sync, transaction_id, event_type, uuid, items)

    async def refund_transaction(self, transaction_id: str, event_type: str) -> dict | None:
        return await asyncio.to_thread(self.refund_transaction_sync, transaction_id, event_type)

    async def create_link_code(self, uuid: str, ttl: int = LINK_CODE_TTL) -> str:
        return await asyncio.to_thread(self.create_link_code_sync, uuid, ttl)

    async def get_uuid_for_link_code(self, code: str) -> str | None:
        return await asyncio.to_thread(self.get_uuid_for_link_code_sync, code)

    async def list_cosmetics(self, uuid: str) -> dict:
        return await asyncio.to_thread(self.list_sync, uuid)


# ---------------------------------------------------------------------------
# Peer, Hub & Real-time Relay
# ---------------------------------------------------------------------------

class Peer:
    __slots__ = ("ws", "uuid", "room", "emote", "since", "window", "sent", "gone")

    def __init__(self, ws, uuid):
        self.ws, self.uuid, self.room, self.emote, self.since = ws, uuid, None, None, 0.0
        self.window, self.sent, self.gone = 0.0, 0, False

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
        """Validate ownership, update store, and broadcast to the room."""
        if not (isinstance(slot, str) and SLOT_RE.fullmatch(slot)):
            await send(peer, {"t": "error", "error": "invalid slot"})
            return False

        if item is not None:
            if not (isinstance(item, str) and ITEM_RE.fullmatch(item)):
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


async def authenticate(hub: Hub, ws) -> Peer | None:
    nonce = secrets.token_hex(16)
    await ws.send_str(json.dumps({"t": "challenge", "nonce": nonce}))
    msg = await read(ws)
    if not msg or msg.get("t") != "auth":
        return None
    uuid, name, sid = plain_uuid(msg.get("uuid")), msg.get("name"), server_id(nonce)
    if not uuid or not isinstance(name, str) or not NAME_RE.fullmatch(name) or msg.get("serverId") != sid:
        return None
    if not hub.insecure:
        try:
            joined = await has_joined(name, sid)
        except Exception as e:
            log.warning("mojang hasJoined failed: %s", type(e).__name__)
            joined = None
        if joined != uuid:
            log.info("auth refused %s", uuid)
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
    hub.ips[ip] = hub.ips.get(ip, 0) + 1
    ws = web.WebSocketResponse(heartbeat=HEARTBEAT, receive_timeout=IDLE_TIMEOUT, max_msg_size=MAX_FRAME)
    peer = None
    try:
        await ws.prepare(request)
        peer = await asyncio.wait_for(authenticate(hub, ws), AUTH_TIMEOUT)
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


async def handle_equip_http(request: web.Request) -> web.Response:
    """HTTP POST /cosmetics/equip: equip or unequip an item for a UUID."""
    try:
        body = await request.json()
    except Exception:
        return web.json_response({"error": "invalid json"}, status=400)

    uuid = plain_uuid(body.get("uuid"))
    if not uuid:
        return web.json_response({"error": "bad uuid"}, status=400)

    slot = body.get("slot")
    if not (isinstance(slot, str) and SLOT_RE.fullmatch(slot)):
        return web.json_response({"error": "invalid slot"}, status=400)

    item = body.get("item") if "item" in body else body.get("item_id")
    store: CosmeticsStore = request.app[STORE]

    if item is not None:
        if not (isinstance(item, str) and ITEM_RE.fullmatch(item)):
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
    """Tebex Webhook endpoint: POST /cosmetics/tebex (and /cosmetics/webhook/tebex).
    Validates Tebex HMAC signature, handles endpoint validation, payment.completed (idempotent),
    and refund/chargeback/dispute revoking.
    """
    raw_body = await request.read()
    sig = request.headers.get("X-Tebex-Signature") or request.headers.get("X-Signature") or request.headers.get("X-BC-Sig")
    secret = request.app.get(TEBEX_SECRET) or os.getenv("TEBEX_WEBHOOK_SECRET")

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

    # 2. Payment Completed: Grants items idempotently
    if event_type == "payment.completed":
        buyer_uuid = extract_buyer_uuid(subject, store)
        if not buyer_uuid:
            return web.json_response({"error": "missing or unresolvable buyer uuid"}, status=400)
        items = extract_tebex_items(subject)
        if not items:
            items = [f"tebex:pkg_{subject.get('id', 'item')}"]

        tx_res = await store.record_transaction(tx_id, event_type, buyer_uuid, items)
        if tx_res.get("already_processed"):
            return web.json_response({"status": "already_processed", "transaction_id": tx_id})

        for it in items:
            await store.grant(buyer_uuid, it, source=f"tebex:{tx_id}")

        return web.json_response({
            "status": "granted",
            "transaction_id": tx_id,
            "uuid": buyer_uuid,
            "items": items
        })

    # 3. Refund / Chargeback / Dispute: Revokes items
    elif event_type in ("payment.refunded", "payment.dispute.opened", "payment.disputed", "payment.chargeback", "payment.dispute.lost"):
        res = await store.refund_transaction(tx_id, event_type)
        if res and res.get("already_refunded"):
            return web.json_response({"status": "already_refunded", "transaction_id": tx_id})
        if res:
            buyer_uuid = res["uuid"]
            hub: Hub = request.app[HUB]
            peer = hub.peers.get(buyer_uuid)
            if peer and peer.room:
                for item_id in res["items"]:
                    others = [p for p in hub.rooms[peer.room] if p is not peer]
                    await broadcast(others, {"t": "revoke", "uuid": buyer_uuid, "item": item_id})
            return web.json_response({
                "status": "revoked",
                "transaction_id": tx_id,
                "uuid": buyer_uuid,
                "items": res["items"]
            })
        return web.json_response({"status": "not_found", "transaction_id": tx_id}, status=404)

    return web.json_response({"status": "ignored", "type": event_type})


async def handle_link_start_http(request: web.Request) -> web.Response:
    """GET /cosmetics/link/start: issue 6-character code bound to UUID for 10 minutes."""
    uuid = plain_uuid(request.query.get("uuid"))
    if not uuid:
        return web.json_response({"error": "missing or invalid uuid parameter"}, status=400)
    store: CosmeticsStore = request.app[STORE]
    code = await store.create_link_code(uuid, ttl=LINK_CODE_TTL)
    return web.json_response({"code": code, "uuid": uuid, "expires_in": LINK_CODE_TTL})


async def handle_link_verify_http(request: web.Request) -> web.Response:
    """GET /cosmetics/link/{code} or /cosmetics/link/verify?code=...: look up UUID by link code."""
    code = request.match_info.get("code") or request.query.get("code")
    if not code:
        return web.json_response({"error": "missing code"}, status=400)
    store: CosmeticsStore = request.app[STORE]
    uuid = await store.get_uuid_for_link_code(code)
    if not uuid:
        return web.json_response({"error": "invalid or expired link code"}, status=404)
    return web.json_response({"code": code.strip().upper(), "uuid": uuid, "valid": True})


async def close_all(app: web.Application) -> None:
    await asyncio.gather(*(p.ws.close(code=1001, message=b"restart") for p in list(app[HUB].peers.values())))


HUB = web.AppKey("cosmetics_hub", Hub)
STORE = web.AppKey("cosmetics_store", CosmeticsStore)
TEBEX_SECRET = web.AppKey("tebex_secret", str)


def setup(
    app: web.Application,
    insecure: bool = False,
    db_path: Path | str | None = None,
    tebex_secret: str | None = None
) -> None:
    store = CosmeticsStore(db_path)
    app[STORE] = store
    app[TEBEX_SECRET] = tebex_secret or os.getenv("TEBEX_WEBHOOK_SECRET", "")
    hub = Hub(insecure, store=store)
    app[HUB] = hub

    app.router.add_get("/cosmetics/health", handle_health)
    app.router.add_get("/cosmetics/ws", handle_ws)
    app.router.add_get("/cosmetics/profile/{uuid}", handle_profile)
    app.router.add_post("/cosmetics/equip", handle_equip_http)
    app.router.add_post("/cosmetics/tebex", handle_tebex_webhook)
    app.router.add_post("/cosmetics/webhook/tebex", handle_tebex_webhook)
    app.router.add_get("/cosmetics/link/start", handle_link_start_http)
    app.router.add_get("/cosmetics/link/{code}", handle_link_verify_http)
    app.router.add_get("/cosmetics/link/verify", handle_link_verify_http)
    app.on_shutdown.append(close_all)


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


def cli_list(uuid_raw: str, db_path: Path | str | None = None) -> int:
    uuid = plain_uuid(uuid_raw)
    if not uuid:
        print(f"Error: invalid UUID '{uuid_raw}'", file=sys.stderr)
        return 1
    store = CosmeticsStore(db_path)
    res = store.list_sync(uuid)
    print(f"Cosmetics profile for {uuid}:")
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
        if first in ("grant", "revoke", "list", "admin") or first.startswith("--grant") or first.startswith("--revoke") or first.startswith("--list"):
            admin_parser = argparse.ArgumentParser(description="Lads Cosmetics Staff Admin CLI")
            admin_parser.add_argument("--db", default=None, help="Path to cosmetics.db")

            # Handle 'admin grant ...' or 'grant ...'
            sub = admin_parser.add_subparsers(dest="action")
            grant_p = sub.add_parser("grant", help="Grant cosmetic entitlement")
            grant_p.add_argument("uuid", help="Minecraft player UUID (plain hex or hyphenated)")
            grant_p.add_argument("item_id", help="Cosmetic item ID (e.g. lads:cloak_aurora)")
            grant_p.add_argument("--source", default="admin", help="Grant source (default: admin)")

            revoke_p = sub.add_parser("revoke", help="Revoke cosmetic entitlement")
            revoke_p.add_argument("uuid", help="Minecraft player UUID")
            revoke_p.add_argument("item_id", help="Cosmetic item ID")

            list_p = sub.add_parser("list", help="List cosmetic entitlements and equipped items")
            list_p.add_argument("uuid", help="Minecraft player UUID")

            admin_sub = sub.add_parser("admin", help="Admin subcommands")
            admin_sub_action = admin_sub.add_subparsers(dest="admin_action")
            agrant_p = admin_sub_action.add_parser("grant")
            agrant_p.add_argument("uuid")
            agrant_p.add_argument("item_id")
            agrant_p.add_argument("--source", default="admin")
            arevoke_p = admin_sub_action.add_parser("revoke")
            arevoke_p.add_argument("uuid")
            arevoke_p.add_argument("item_id")
            alist_p = admin_sub_action.add_parser("list")
            alist_p.add_argument("uuid")

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

    # Standard Standalone Server Parser
    parser = argparse.ArgumentParser(description="Lads cosmetics relay & backend, standalone (the bot mounts it with setup(app)).")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8095)
    parser.add_argument("--db", default=None, help="Path to cosmetics.db SQLite file")
    parser.add_argument("--tebex-secret", default=None, help="Tebex webhook secret")
    parser.add_argument("--dev-insecure-auth", action="store_true",
                        help="QA only: trust the client's name and uuid without asking Mojang (offline QA accounts); 127.0.0.1 only")
    args = parser.parse_args(raw_args)
    if args.dev_insecure_auth and args.host != "127.0.0.1":
        parser.error("--dev-insecure-auth refuses to start unless bound to 127.0.0.1")
    logging.basicConfig(level=logging.DEBUG, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
    app = web.Application()
    setup(app, insecure=args.dev_insecure_auth, db_path=args.db, tebex_secret=args.tebex_secret)
    if args.dev_insecure_auth:
        log.warning("dev mode: Mojang verification is OFF (127.0.0.1 only)")
    web.run_app(app, host=args.host, port=args.port)


if __name__ == "__main__":
    main()
