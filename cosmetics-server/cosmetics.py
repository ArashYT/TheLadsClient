"""Lads cosmetics relay (1.9.11): which Lads Client players are emoting, per Minecraft server ("room"), so every Lads player
on the same server sees it. One WebSocket per player, JSON text frames (<= 1 KB in), state in memory only: a restart just makes
clients reconnect and resend. Mount into the bot's aiohttp app with setup(app) (README.md); dev: python cosmetics.py --help.
Logs uuids only, never names, tokens or IPs.
"""
from __future__ import annotations

import argparse
import asyncio
import hashlib
import json
import logging
import re
import secrets
import time

import aiohttp
from aiohttp import web

FREE = ["lads:groove", "lads:wave", "lads:clap", "lads:cheer", "lads:bow", "lads:facepalm", "lads:salute", "lads:shrug"]  # everyone owns these
EMOTES = frozenset(FREE)  # allow-list (the client's EmoteLibrary): any other id is rejected
EQUIPPED: dict[str, dict] = {}  # uuid -> {slot: id}; empty until capes/hats exist
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
        pass  # a dead socket: its own handler notices and cleans up


async def broadcast(peers, msg: dict) -> None:
    text = json.dumps(msg, separators=(",", ":"))
    await asyncio.gather(*(send(p, text) for p in peers))


class Hub:
    def __init__(self, insecure: bool = False):
        self.insecure = insecure
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
        if room:  # in before any await, so no state sent meanwhile is missed
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


async def read(ws) -> dict | None:
    msg = await ws.receive()
    if msg.type != aiohttp.WSMsgType.TEXT:
        return None  # close, error (frame over MAX_FRAME) or binary
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
    if old:  # one socket per player: the new one wins
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
            if emote is not None and not (isinstance(emote, str) and emote in EMOTES):
                await send(peer, {"t": "error", "error": "unknown emote"})
                continue
            await hub.emote(peer, emote)
        else:
            return  # protocol error: close


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
    return web.json_response({"owned": FREE, "equipped": EQUIPPED.get(uuid, {})})


async def close_all(app: web.Application) -> None:
    await asyncio.gather(*(p.ws.close(code=1001, message=b"restart") for p in list(app[HUB].peers.values())))


HUB = web.AppKey("cosmetics_hub", Hub)


def setup(app: web.Application, insecure: bool = False) -> None:
    app[HUB] = Hub(insecure)
    app.router.add_get("/cosmetics/health", handle_health)
    app.router.add_get("/cosmetics/ws", handle_ws)
    app.router.add_get("/cosmetics/profile/{uuid}", handle_profile)
    app.on_shutdown.append(close_all)


def main(argv=None) -> None:
    parser = argparse.ArgumentParser(description="Lads cosmetics relay, standalone (the bot mounts it with setup(app)).")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8095)
    parser.add_argument("--dev-insecure-auth", action="store_true",
                        help="QA only: trust the client's name and uuid without asking Mojang (offline QA accounts); 127.0.0.1 only")
    args = parser.parse_args(argv)
    if args.dev_insecure_auth and args.host != "127.0.0.1":
        parser.error("--dev-insecure-auth refuses to start unless bound to 127.0.0.1")
    logging.basicConfig(level=logging.DEBUG, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
    app = web.Application()
    setup(app, args.dev_insecure_auth)
    if args.dev_insecure_auth:
        log.warning("dev mode: Mojang verification is OFF (127.0.0.1 only)")
    web.run_app(app, host=args.host, port=args.port)


if __name__ == "__main__":
    main()
