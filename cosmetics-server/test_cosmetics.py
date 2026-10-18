"""pytest -q (pytest-aiohttp; pytest.ini sets asyncio_mode=auto). Mojang is faked: has_joined answers from PLAYERS."""
import asyncio
import re

import aiohttp
import pytest
from aiohttp import web

import cosmetics

A, B, C = "a" * 32, "b" * 32, "c" * 32
PLAYERS = {"Alice": A, "Bob": B, "Cara": C}
ROOM, OTHER = "1" * 64, "2" * 64


@pytest.fixture
def fake_mojang(monkeypatch):
    joins = []

    async def has_joined(name, sid):
        joins.append((name, sid))
        return PLAYERS.get(name)

    monkeypatch.setattr(cosmetics, "has_joined", has_joined)
    return joins


@pytest.fixture
async def client(aiohttp_client, fake_mojang):
    app = web.Application()
    cosmetics.setup(app)
    return await aiohttp_client(app)


async def login(client, name, uuid, room=ROOM, headers=None):
    ws = await client.ws_connect("/cosmetics/ws", headers=headers)
    challenge = await ws.receive_json(timeout=2)
    assert challenge["t"] == "challenge" and len(challenge["nonce"]) == 32
    await ws.send_json({"t": "auth", "name": name, "uuid": uuid, "serverId": cosmetics.server_id(challenge["nonce"])})
    await ws.send_json({"t": "room", "room": room})
    snapshot = await ws.receive_json(timeout=2)
    assert snapshot["t"] == "snapshot"
    return ws, snapshot["players"]


async def silent(ws):
    with pytest.raises(asyncio.TimeoutError):
        await ws.receive(timeout=0.2)


async def closed(ws):
    while (msg := await ws.receive(timeout=2)).type == aiohttp.WSMsgType.TEXT:
        pass
    assert msg.type in (aiohttp.WSMsgType.CLOSE, aiohttp.WSMsgType.CLOSED, aiohttp.WSMsgType.CLOSING)
    return ws.close_code


def test_server_id_matches_java_vectors():
    # Same vectors as CosmeticsLinkTest: Minecraft-style signed hex, so negative digests start with '-'.
    assert cosmetics.server_id("0" * 32) == "7344bf77af790171ed8daccf461e5258dc0d3f54"
    assert cosmetics.server_id("0" * 31 + "1") == "-3e76dc0d4181c87686d0e839cfbe74994ab51a04"


async def test_auth_success_uses_mojang_with_derived_server_id(client, fake_mojang):
    ws, players = await login(client, "Alice", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    assert players == []
    assert len(fake_mojang) == 1 and fake_mojang[0][0] == "Alice" and re.fullmatch(r"-?[0-9a-f]{1,40}", fake_mojang[0][1])
    health = await (await client.get("/cosmetics/health")).json()
    assert health == {"status": "ok", "players": 1, "rooms": 1}
    await ws.close()


@pytest.mark.parametrize("name,uuid,bad_sid", [("Mallory", A, False), ("Alice", B, False), ("Alice", A, True), ("bad name!", A, False)])
async def test_auth_failure_closes_without_data(client, name, uuid, bad_sid):
    ws = await client.ws_connect("/cosmetics/ws")
    challenge = await ws.receive_json(timeout=2)
    sid = "0" if bad_sid else cosmetics.server_id(challenge["nonce"])
    await ws.send_json({"t": "auth", "name": name, "uuid": uuid, "serverId": sid})
    await closed(ws)


async def test_unauthenticated_socket_closed_after_timeout(client, monkeypatch):
    monkeypatch.setattr(cosmetics, "AUTH_TIMEOUT", 0.3)
    ws = await client.ws_connect("/cosmetics/ws")
    assert (await ws.receive_json(timeout=2))["t"] == "challenge"
    await closed(ws)  # never answered the challenge


async def test_relay_excludes_sender_and_other_rooms(client):
    a, _ = await login(client, "Alice", A)
    b, players = await login(client, "Bob", B)
    c, _ = await login(client, "Cara", C, room=OTHER)
    assert players == [{"uuid": A, "emote": None, "age": 0}]
    assert await a.receive_json(timeout=2) == {"t": "join", "uuid": B}
    await a.send_json({"t": "emote", "id": "lads:groove"})
    assert await b.receive_json(timeout=2) == {"t": "state", "uuid": A, "emote": "lads:groove", "age": 0}
    await silent(a)
    await silent(c)
    await a.send_json({"t": "emote", "id": None})
    assert await b.receive_json(timeout=2) == {"t": "state", "uuid": A, "emote": None, "age": 0}


async def test_late_joiner_gets_snapshot_with_age(client):
    a, _ = await login(client, "Alice", A)
    await a.send_json({"t": "emote", "id": "lads:groove"})
    await asyncio.sleep(0.15)
    b, players = await login(client, "Bob", B)
    assert len(players) == 1 and players[0]["uuid"] == A and players[0]["emote"] == "lads:groove"
    assert 100 <= players[0]["age"] < 5000


async def test_leave_and_room_change_tell_the_room(client):
    a, _ = await login(client, "Alice", A)
    b, _ = await login(client, "Bob", B)
    await a.receive_json(timeout=2)  # join
    await b.send_json({"t": "room", "room": OTHER})
    assert (await b.receive_json(timeout=2)) == {"t": "snapshot", "players": []}
    assert await a.receive_json(timeout=2) == {"t": "leave", "uuid": B}
    await b.send_json({"t": "room", "room": ROOM})
    await b.receive_json(timeout=2)
    assert await a.receive_json(timeout=2) == {"t": "join", "uuid": B}
    await b.close()
    assert await a.receive_json(timeout=2) == {"t": "leave", "uuid": B}


async def test_unknown_emote_rejected(client):
    a, _ = await login(client, "Alice", A)
    b, _ = await login(client, "Bob", B)
    await a.receive_json(timeout=2)
    await a.send_json({"t": "emote", "id": "lads:hack"})
    assert (await a.receive_json(timeout=2))["t"] == "error"
    await silent(b)


async def test_one_socket_per_uuid_new_replaces_old(client):
    old, _ = await login(client, "Alice", A)
    b, _ = await login(client, "Bob", B)
    new, players = await login(client, "Alice", A)
    assert await closed(old) == 4001
    assert players == [{"uuid": B, "emote": None, "age": 0}]
    await new.send_json({"t": "emote", "id": "lads:groove"})
    msgs = [await b.receive_json(timeout=2) for _ in range(3)]
    assert [m["t"] for m in msgs] == ["leave", "join", "state"] and all(m["uuid"] == A for m in msgs)


async def test_rate_limit_closes(client):
    a, _ = await login(client, "Alice", A)
    for _ in range(cosmetics.MAX_RATE + 3):
        await a.send_json({"t": "emote", "id": None})
    assert await closed(a) == 1008


async def test_oversized_frame_closes(client):
    a, _ = await login(client, "Alice", A)
    await a.send_str('{"t":"emote","id":"' + "x" * 2000 + '"}')
    await closed(a)


async def test_max_sockets_per_ip(client, monkeypatch):
    monkeypatch.setattr(cosmetics, "MAX_PER_IP", 2)
    first = [await client.ws_connect("/cosmetics/ws", headers={"CF-Connecting-IP": "203.0.113.5"}) for _ in range(2)]
    with pytest.raises(aiohttp.WSServerHandshakeError) as refused:
        await client.ws_connect("/cosmetics/ws", headers={"CF-Connecting-IP": "203.0.113.5"})
    assert refused.value.status == 429
    other = await client.ws_connect("/cosmetics/ws", headers={"CF-Connecting-IP": "203.0.113.6"})
    await first[0].close()
    await asyncio.sleep(0.1)
    again = await client.ws_connect("/cosmetics/ws", headers={"CF-Connecting-IP": "203.0.113.5"})
    for ws in (*first, other, again):
        await ws.close()


async def test_profile(client):
    body = await (await client.get("/cosmetics/profile/" + A)).json()
    assert body == {"owned": cosmetics.FREE, "equipped": {}} and "lads:wave" in body["owned"]
    assert (await client.get("/cosmetics/profile/nope")).status == 400


async def test_dev_insecure_auth_skips_mojang(aiohttp_client, monkeypatch):
    async def boom(name, sid):
        raise AssertionError("Mojang must not be asked in dev mode")

    monkeypatch.setattr(cosmetics, "has_joined", boom)
    app = web.Application()
    cosmetics.setup(app, insecure=True)
    ws, players = await login(await aiohttp_client(app), "LadsQA", "d" * 32)
    assert players == []


def test_dev_insecure_auth_refuses_public_bind():
    with pytest.raises(SystemExit):
        cosmetics.main(["--dev-insecure-auth", "--host", "0.0.0.0"])
