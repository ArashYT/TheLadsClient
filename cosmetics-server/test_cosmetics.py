"""pytest -q (pytest-aiohttp; pytest.ini sets asyncio_mode=auto).
Tests both Phase 1 emote relay, Phase 2 backend, and security review hardening:
- Tebex fail-closed webhook signature verification
- HTTP equip authorization (preventing unauthenticated IDOR)
- HTTP link-code generation authorization (preventing unauthenticated link hijacking)
- Single-use link token redemption (preventing token replay)
"""
import asyncio
import hashlib
import hmac
import json
import re
import sqlite3

import aiohttp
import pytest
from aiohttp import web

import cosmetics

A, B, C = "a" * 32, "b" * 32, "c" * 32
PLAYERS = {"Alice": A, "Bob": B, "Cara": C}
ROOM, OTHER = "1" * 64, "2" * 64
TEST_SECRET = "test_tebex_and_auth_secret_xyz_123"


@pytest.fixture
def fake_mojang(monkeypatch):
    joins = []

    async def has_joined(name, sid):
        joins.append((name, sid))
        return PLAYERS.get(name)

    monkeypatch.setattr(cosmetics, "has_joined", has_joined)
    return joins


@pytest.fixture
async def client(aiohttp_client, fake_mojang, tmp_path):
    app = web.Application()
    db_file = tmp_path / "cosmetics.db"
    cosmetics.setup(app, db_path=db_file, tebex_secret=TEST_SECRET, auth_secret=TEST_SECRET)
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


def make_tebex_sig(payload_bytes: bytes, secret: str, two_step: bool = True) -> str:
    if two_step:
        bhash = hashlib.sha256(payload_bytes).hexdigest()
        return hmac.new(secret.encode("utf-8"), bhash.encode("utf-8"), hashlib.sha256).hexdigest()
    else:
        return hmac.new(secret.encode("utf-8"), payload_bytes, hashlib.sha256).hexdigest()


# ---------------------------------------------------------------------------
# Phase 1 Relay & Auth Tests
# ---------------------------------------------------------------------------

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
    assert body == {"owned": ["lads:groove"], "equipped": {}}
    assert (await client.get("/cosmetics/profile/nope")).status == 400


async def test_dev_insecure_auth_skips_mojang(aiohttp_client, monkeypatch, tmp_path):
    async def boom(name, sid):
        raise AssertionError("Mojang must not be asked in dev mode")

    monkeypatch.setattr(cosmetics, "has_joined", boom)
    app = web.Application()
    cosmetics.setup(app, insecure=True, db_path=tmp_path / "dev.db")
    ws, players = await login(await aiohttp_client(app), "LadsQA", "d" * 32)
    assert players == []


def test_dev_insecure_auth_refuses_public_bind():
    with pytest.raises(SystemExit):
        cosmetics.main(["--dev-insecure-auth", "--host", "0.0.0.0"])


# ---------------------------------------------------------------------------
# Phase 2: SQLite Store Unit Tests
# ---------------------------------------------------------------------------

def test_sqlite_schema_initialization(tmp_path):
    db_file = tmp_path / "schema_test.db"
    store = cosmetics.CosmeticsStore(db_file)

    with store._get_connection() as conn:
        cur = conn.cursor()
        cur.execute("SELECT name FROM sqlite_master WHERE type='table';")
        tables = {row[0] for row in cur.fetchall()}
        assert {"entitlements", "equipped", "transactions", "link_codes"}.issubset(tables)

        cur.execute("SELECT name FROM sqlite_master WHERE type='index';")
        indices = {row[0] for row in cur.fetchall()}
        assert "idx_entitlements_uuid" in indices
        assert "idx_entitlements_item" in indices
        assert "idx_equipped_uuid" in indices
        assert "idx_transactions_uuid" in indices
        assert "idx_link_codes_uuid" in indices


def test_sqlite_grant_and_revoke(tmp_path):
    store = cosmetics.CosmeticsStore(tmp_path / "grant_test.db")
    assert not store.has_entitlement_sync(A, "lads:cape_aurora")

    store.grant_sync(A, "lads:cape_aurora", source="tebex")
    assert store.has_entitlement_sync(A, "lads:cape_aurora")

    profile = store.get_profile_sync(A)
    assert "lads:cape_aurora" in profile["owned"]
    assert "lads:groove" in profile["owned"]

    # Revoke item
    store.revoke_sync(A, "lads:cape_aurora")
    assert not store.has_entitlement_sync(A, "lads:cape_aurora")
    profile = store.get_profile_sync(A)
    assert "lads:cape_aurora" not in profile["owned"]

    # Re-grant item (un-revokes)
    store.grant_sync(A, "lads:cape_aurora", source="admin")
    assert store.has_entitlement_sync(A, "lads:cape_aurora")


def test_sqlite_equipped_and_auto_unequip(tmp_path):
    store = cosmetics.CosmeticsStore(tmp_path / "equip_test.db")
    store.grant_sync(A, "lads:cape_aurora", source="tebex")
    store.set_equipped_sync(A, "cape", "lads:cape_aurora")

    profile = store.get_profile_sync(A)
    assert profile["equipped"] == {"cape": "lads:cape_aurora"}

    # Overwrite slot
    store.grant_sync(A, "lads:cape_founder", source="tebex")
    store.set_equipped_sync(A, "cape", "lads:cape_founder")
    assert store.get_profile_sync(A)["equipped"]["cape"] == "lads:cape_founder"

    # Revoke active item -> auto-unequips from equipped table
    store.revoke_sync(A, "lads:cape_founder")
    assert store.get_profile_sync(A)["equipped"] == {}

    # Manual unequip
    store.set_equipped_sync(A, "cape", "lads:cape_aurora")
    store.remove_equipped_sync(A, "cape")
    assert store.get_profile_sync(A)["equipped"] == {}


def test_sqlite_transaction_idempotency(tmp_path):
    store = cosmetics.CosmeticsStore(tmp_path / "tx_test.db")
    tx1 = store.record_transaction_sync("tbx-101", "payment.completed", A, ["lads:cape_aurora"])
    assert tx1["already_processed"] is False

    # Second delivery with same transaction ID returns already_processed = True
    tx2 = store.record_transaction_sync("tbx-101", "payment.completed", A, ["lads:cape_aurora"])
    assert tx2["already_processed"] is True

    # Refund transaction
    store.grant_sync(A, "lads:cape_aurora", source="tebex:tbx-101")
    store.set_equipped_sync(A, "cape", "lads:cape_aurora")
    refund = store.refund_transaction_sync("tbx-101", "payment.refunded")
    assert refund["already_refunded"] is False
    assert refund["uuid"] == A
    assert not store.has_entitlement_sync(A, "lads:cape_aurora")
    assert store.get_profile_sync(A)["equipped"] == {}

    # Duplicate refund attempt
    refund2 = store.refund_transaction_sync("tbx-101", "payment.refunded")
    assert refund2["already_refunded"] is True


def test_sqlite_link_codes(tmp_path):
    store = cosmetics.CosmeticsStore(tmp_path / "link_test.db")
    code = store.create_link_code_sync(A, ttl=600)
    assert len(code) == 6
    assert all(c in cosmetics.LINK_ALPHABET for c in code)

    # Lookup succeeds
    assert store.get_uuid_for_link_code_sync(code) == A
    assert store.get_uuid_for_link_code_sync(code.lower()) == A

    # Expired code returns None
    code_exp = store.create_link_code_sync(B, ttl=-1)
    assert store.get_uuid_for_link_code_sync(code_exp) is None


# ---------------------------------------------------------------------------
# Phase 2: Profile & Equip Relay Tests (WebSocket & HTTP)
# ---------------------------------------------------------------------------

async def test_profile_endpoint_with_entitlements(client):
    app = client.app
    store: cosmetics.CosmeticsStore = app[cosmetics.STORE]
    await store.grant(A, "lads:cape_aurora", source="tebex")
    await store.set_equipped(A, "cape", "lads:cape_aurora")

    res = await client.get(f"/cosmetics/profile/{A}")
    assert res.status == 200
    data = await res.json()
    assert "lads:groove" in data["owned"]
    assert "lads:cape_aurora" in data["owned"]
    assert data["equipped"] == {"cape": "lads:cape_aurora"}


async def test_ws_equip_owned_item_broadcasts_to_room(client):
    app = client.app
    store: cosmetics.CosmeticsStore = app[cosmetics.STORE]
    await store.grant(A, "lads:cape_aurora", source="tebex")

    a, _ = await login(client, "Alice", A)
    b, _ = await login(client, "Bob", B)
    c, _ = await login(client, "Cara", C, room=OTHER)
    await a.receive_json(timeout=2)  # Alice gets Bob's join

    # Alice equips her owned cape over the authenticated WebSocket
    await a.send_json({"t": "equip", "slot": "cape", "item": "lads:cape_aurora"})

    # Sender gets confirmation
    conf = await a.receive_json(timeout=2)
    assert conf["t"] == "equip" and conf["slot"] == "cape" and conf["item"] == "lads:cape_aurora" and conf.get("status") == "ok"

    # Bob in same room receives the broadcast
    broadcast_msg = await b.receive_json(timeout=2)
    assert broadcast_msg == {"t": "equip", "uuid": A, "slot": "cape", "item": "lads:cape_aurora"}

    # Cara in OTHER room gets nothing
    await silent(c)

    # Verify persisted in SQLite
    profile = await store.get_profile(A)
    assert profile["equipped"]["cape"] == "lads:cape_aurora"


async def test_ws_equip_unowned_item_rejected(client):
    a, _ = await login(client, "Alice", A)
    b, _ = await login(client, "Bob", B)
    await a.receive_json(timeout=2)  # join

    # Alice attempts to equip an unowned item
    await a.send_json({"t": "equip", "slot": "cape", "item": "lads:cape_dragon"})
    err = await a.receive_json(timeout=2)
    assert err == {"t": "error", "error": "item not owned"}

    # Bob receives no broadcast
    await silent(b)

    # SQLite store was not updated
    store: cosmetics.CosmeticsStore = client.app[cosmetics.STORE]
    profile = await store.get_profile(A)
    assert "cape" not in profile["equipped"]


async def test_ws_unequip_item(client):
    app = client.app
    store: cosmetics.CosmeticsStore = app[cosmetics.STORE]
    await store.grant(A, "lads:cape_aurora", source="tebex")
    await store.set_equipped(A, "cape", "lads:cape_aurora")

    a, _ = await login(client, "Alice", A)
    b, _ = await login(client, "Bob", B)
    await a.receive_json(timeout=2)

    # Alice un-equips cape over WS
    await a.send_json({"t": "equip", "slot": "cape", "item": None})

    conf = await a.receive_json(timeout=2)
    assert conf["t"] == "equip" and conf["slot"] == "cape" and conf["item"] is None

    b_msg = await b.receive_json(timeout=2)
    assert b_msg == {"t": "equip", "uuid": A, "slot": "cape", "item": None}

    profile = await store.get_profile(A)
    assert "cape" not in profile["equipped"]


async def test_http_post_equip(client):
    app = client.app
    store: cosmetics.CosmeticsStore = app[cosmetics.STORE]
    await store.grant(A, "lads:cape_aurora", source="admin")
    auth_headers = {"Authorization": f"Bearer {TEST_SECRET}"}

    # Equipping owned item with auth succeeds
    res = await client.post("/cosmetics/equip", json={"uuid": A, "slot": "cape", "item": "lads:cape_aurora"}, headers=auth_headers)
    assert res.status == 200
    data = await res.json()
    assert data["status"] == "ok" and data["item"] == "lads:cape_aurora"

    # Equipping unowned item returns 403
    res_bad = await client.post("/cosmetics/equip", json={"uuid": A, "slot": "hat", "item": "lads:hat_sombrero"}, headers=auth_headers)
    assert res_bad.status == 403

    # Invalid UUID returns 400
    res_inv = await client.post("/cosmetics/equip", json={"uuid": "bad-uuid", "slot": "cape", "item": None}, headers=auth_headers)
    assert res_inv.status == 400


# ---------------------------------------------------------------------------
# Phase 2: In-Game Link Code Flow Tests
# ---------------------------------------------------------------------------

async def test_ws_link_code_generation(client):
    a, _ = await login(client, "Alice", A)
    await a.send_json({"t": "link"})
    res = await a.receive_json(timeout=2)
    assert res["t"] == "link_code"
    code = res["code"]
    assert len(code) == 6
    assert res["uuid"] == A
    assert res["expires_in"] == 600

    # Unauthenticated HTTP GET /cosmetics/link/{code} route is removed -> returns 404
    verify_res = await client.get(f"/cosmetics/link/{code}")
    assert verify_res.status == 404

    # Code is bound to A in store
    store: cosmetics.CosmeticsStore = client.app[cosmetics.STORE]
    assert await store.get_uuid_for_link_code(code) == A


async def test_http_link_start_and_verify(client):
    auth_headers = {"Authorization": f"Bearer {TEST_SECRET}"}
    start_res = await client.get(f"/cosmetics/link/start?uuid={B}", headers=auth_headers)
    assert start_res.status == 200
    s_data = await start_res.json()
    code = s_data["code"]
    assert len(code) == 6
    assert s_data["uuid"] == B

    # Unauthenticated verify returns 401
    unauth_res = await client.get(f"/cosmetics/link/verify?code={code}")
    assert unauth_res.status == 401

    # Authenticated query param verify succeeds
    check_res = await client.get(f"/cosmetics/link/verify?code={code}", headers=auth_headers)
    assert check_res.status == 200
    assert (await check_res.json())["uuid"] == B

    # Non-existent code returns 404
    missing_res = await client.get("/cosmetics/link/verify?code=ZZZZZZ", headers=auth_headers)
    assert missing_res.status == 404


# ---------------------------------------------------------------------------
# Phase 2: Tebex Webhook Tests
# ---------------------------------------------------------------------------

async def test_tebex_webhook_handshake(client):
    payload = {
        "id": "val-987654",
        "type": "validation.webhook",
        "date": "2026-10-18T16:00:00Z"
    }
    payload_bytes = json.dumps(payload).encode("utf-8")
    sig = make_tebex_sig(payload_bytes, TEST_SECRET)

    res = await client.post("/cosmetics/tebex", data=payload_bytes, headers={"X-Tebex-Signature": sig, "Content-Type": "application/json"})
    assert res.status == 200
    assert (await res.json()) == {"id": "val-987654"}


async def test_tebex_webhook_signatures(aiohttp_client, tmp_path):
    secret = "test_tebex_secret_xyz"
    app = web.Application()
    cosmetics.setup(app, db_path=tmp_path / "sig.db", tebex_secret=secret)
    cl = await aiohttp_client(app)

    payload = json.dumps({
        "id": "val-1",
        "type": "validation.webhook"
    }).encode("utf-8")

    # 1. Valid two-step signature
    sig_two_step = make_tebex_sig(payload, secret, two_step=True)
    res1 = await cl.post("/cosmetics/tebex", data=payload, headers={"X-Tebex-Signature": sig_two_step, "Content-Type": "application/json"})
    assert res1.status == 200

    # 2. Valid single-step signature via X-Signature header
    sig_single = make_tebex_sig(payload, secret, two_step=False)
    res2 = await cl.post("/cosmetics/tebex", data=payload, headers={"X-Signature": sig_single, "Content-Type": "application/json"})
    assert res2.status == 200

    # 3. Invalid signature
    res_bad = await cl.post("/cosmetics/tebex", data=payload, headers={"X-Tebex-Signature": "wrongsig", "Content-Type": "application/json"})
    assert res_bad.status == 401

    # 4. Missing signature
    res_missing = await cl.post("/cosmetics/tebex", data=payload, headers={"Content-Type": "application/json"})
    assert res_missing.status == 401


async def test_tebex_payment_completed_and_idempotency(client):
    payload = {
        "id": "evt-1001",
        "type": "payment.completed",
        "subject": {
            "transaction_id": "tbx-pay-999",
            "player": {
                "uuid": A,
                "name": "Alice"
            },
            "packages": [
                {"id": 1, "name": "lads:cape_aurora"}
            ]
        }
    }
    payload_bytes = json.dumps(payload).encode("utf-8")
    sig = make_tebex_sig(payload_bytes, TEST_SECRET)
    headers = {"X-Tebex-Signature": sig, "Content-Type": "application/json"}

    # First delivery: grants cosmetic
    res1 = await client.post("/cosmetics/tebex", data=payload_bytes, headers=headers)
    assert res1.status == 200
    data1 = await res1.json()
    assert data1["status"] == "granted"
    assert data1["transaction_id"] == "tbx-pay-999"
    assert "lads:cape_aurora" in data1["items"]

    # Verify profile now reflects ownership
    p_res = await client.get(f"/cosmetics/profile/{A}")
    assert "lads:cape_aurora" in (await p_res.json())["owned"]

    # Second delivery with same transaction ID: idempotent already_processed
    res2 = await client.post("/cosmetics/tebex", data=payload_bytes, headers=headers)
    assert res2.status == 200
    assert (await res2.json())["status"] == "already_processed"


async def test_tebex_payment_completed_resolves_link_code(client):
    # Alice generates a link code over WebSocket / store
    store: cosmetics.CosmeticsStore = client.app[cosmetics.STORE]
    code = await store.create_link_code(A)

    # Tebex sends webhook where customer provided the link code
    payload = {
        "id": "evt-link-1",
        "type": "payment.completed",
        "subject": {
            "transaction_id": "tbx-link-pay-1",
            "customer": {
                "link_code": code
            },
            "packages": [
                {"id": 55, "name": "lads:hat_wizard"}
            ]
        }
    }
    payload_bytes = json.dumps(payload).encode("utf-8")
    sig = make_tebex_sig(payload_bytes, TEST_SECRET)
    headers = {"X-Tebex-Signature": sig, "Content-Type": "application/json"}

    res = await client.post("/cosmetics/tebex", data=payload_bytes, headers=headers)
    assert res.status == 200
    data = await res.json()
    assert data["status"] == "granted"
    assert data["uuid"] == A
    assert "lads:hat_wizard" in data["items"]

    # Alice owns the hat
    profile = await store.get_profile(A)
    assert "lads:hat_wizard" in profile["owned"]


async def test_tebex_refund_revokes_and_unequips(client):
    store: cosmetics.CosmeticsStore = client.app[cosmetics.STORE]

    # 1. Purchase item
    buy_payload = {
        "id": "evt-buy-1",
        "type": "payment.completed",
        "subject": {
            "transaction_id": "tbx-refund-1",
            "player": {"uuid": A},
            "packages": [{"id": 1, "name": "lads:cape_aurora"}]
        }
    }
    buy_bytes = json.dumps(buy_payload).encode("utf-8")
    buy_sig = make_tebex_sig(buy_bytes, TEST_SECRET)
    await client.post("/cosmetics/tebex", data=buy_bytes, headers={"X-Tebex-Signature": buy_sig, "Content-Type": "application/json"})
    assert "lads:cape_aurora" in (await store.get_profile(A))["owned"]

    # 2. Player equips the item
    await store.set_equipped(A, "cape", "lads:cape_aurora")
    assert (await store.get_profile(A))["equipped"]["cape"] == "lads:cape_aurora"

    # 3. Webhook sends payment.refunded
    refund_payload = {
        "id": "evt-ref-1",
        "type": "payment.refunded",
        "subject": {
            "transaction_id": "tbx-refund-1"
        }
    }
    ref_bytes = json.dumps(refund_payload).encode("utf-8")
    ref_sig = make_tebex_sig(ref_bytes, TEST_SECRET)
    res = await client.post("/cosmetics/tebex", data=ref_bytes, headers={"X-Tebex-Signature": ref_sig, "Content-Type": "application/json"})
    assert res.status == 200
    data = await res.json()
    assert data["status"] == "revoked"

    # Entitlement is revoked and slot unequipped
    profile = await store.get_profile(A)
    assert "lads:cape_aurora" not in profile["owned"]
    assert "cape" not in profile["equipped"]

    # Duplicate refund returns already_refunded
    res_dup = await client.post("/cosmetics/tebex", data=ref_bytes, headers={"X-Tebex-Signature": ref_sig, "Content-Type": "application/json"})
    assert res_dup.status == 200
    assert (await res_dup.json())["status"] == "already_refunded"


# ---------------------------------------------------------------------------
# Phase 2: Staff Admin CLI Tests
# ---------------------------------------------------------------------------

def test_admin_cli_functions(tmp_path, capsys):
    db_file = tmp_path / "admin_cli.db"

    # 1. Grant
    rc_grant = cosmetics.cli_grant(A, "lads:cape_aurora", source="staff_grant", db_path=db_file)
    assert rc_grant == 0
    out = capsys.readouterr().out
    assert "Granted 'lads:cape_aurora'" in out

    # 2. List
    rc_list = cosmetics.cli_list(A, db_path=db_file)
    assert rc_list == 0
    out_list = capsys.readouterr().out
    assert "lads:cape_aurora" in out_list
    assert "staff_grant" in out_list

    # 3. Revoke
    rc_revoke = cosmetics.cli_revoke(A, "lads:cape_aurora", db_path=db_file)
    assert rc_revoke == 0
    out_rev = capsys.readouterr().out
    assert "Revoked 'lads:cape_aurora'" in out_rev

    # 4. List again shows revoked
    cosmetics.cli_list(A, db_path=db_file)
    out_after = capsys.readouterr().out
    assert "Revoked Entitlements:" in out_after
    assert "lads:cape_aurora" in out_after

    # 5. Invalid UUID exits with 1
    assert cosmetics.cli_grant("bad-uuid", "lads:cape_aurora", db_path=db_file) == 1


def test_main_cli_admin_subcommands(tmp_path, capsys):
    db_file = str(tmp_path / "main_admin.db")

    # 1. cosmetics.main(["grant", ...])
    with pytest.raises(SystemExit) as exc1:
        cosmetics.main(["grant", A, "lads:cape_aurora", "--source", "test_source", "--db", db_file])
    assert exc1.value.code == 0
    assert "Granted 'lads:cape_aurora'" in capsys.readouterr().out

    # 2. cosmetics.main(["list", ...])
    with pytest.raises(SystemExit) as exc2:
        cosmetics.main(["list", A, "--db", db_file])
    assert exc2.value.code == 0
    assert "lads:cape_aurora" in capsys.readouterr().out

    # 3. cosmetics.main(["revoke", ...])
    with pytest.raises(SystemExit) as exc3:
        cosmetics.main(["revoke", A, "lads:cape_aurora", "--db", db_file])
    assert exc3.value.code == 0
    assert "Revoked 'lads:cape_aurora'" in capsys.readouterr().out


# ---------------------------------------------------------------------------
# Post-Review Security Hardening Tests (Round 1 & Round 2)
# ---------------------------------------------------------------------------

async def test_security_tebex_fails_closed_when_secret_unset(aiohttp_client, tmp_path):
    """Security Hole 1: When TEBEX_WEBHOOK_SECRET is not configured or signature is missing,
    the endpoint must fail closed (401) and never accept unsigned/bogus requests.
    """
    app = web.Application()
    cosmetics.setup(app, db_path=tmp_path / "nosecret.db", tebex_secret=None)
    cl = await aiohttp_client(app)

    payload = json.dumps({
        "id": "exploit-1",
        "type": "payment.completed",
        "subject": {
            "transaction_id": "fake-1",
            "player": {"uuid": A},
            "packages": [{"id": 1, "name": "lads:cape_aurora"}]
        }
    }).encode("utf-8")

    res_nosig = await cl.post("/cosmetics/tebex", data=payload, headers={"Content-Type": "application/json"})
    assert res_nosig.status == 401
    assert (await res_nosig.json())["error"] == "webhook secret not configured"

    res_fakesig = await cl.post("/cosmetics/tebex", data=payload, headers={"X-Tebex-Signature": "fakesig123", "Content-Type": "application/json"})
    assert res_fakesig.status == 401


async def test_security_http_equip_unauthorized_idor_rejected(client):
    """Security Hole 2: Unauthenticated HTTP callers cannot call POST /cosmetics/equip
    to forcibly equip or unequip cosmetics for arbitrary player UUIDs (IDOR).
    Normal players equip exclusively over the authenticated WebSocket session.
    """
    res_noauth = await client.post("/cosmetics/equip", json={
        "uuid": A,
        "slot": "cape",
        "item": None
    })
    assert res_noauth.status == 401
    assert (await res_noauth.json())["error"] == "unauthorized"

    res_badtoken = await client.post("/cosmetics/equip", json={
        "uuid": A,
        "slot": "cape",
        "item": None
    }, headers={"Authorization": "Bearer invalid_secret_token"})
    assert res_badtoken.status == 401
    assert (await res_badtoken.json())["error"] == "unauthorized"


async def test_security_http_link_start_unauthorized_rejected(client):
    """Security Hole 3: Unauthenticated HTTP callers cannot call GET /cosmetics/link/start?uuid=...
    to hijack account linking by generating link codes for arbitrary victim UUIDs.
    In-game players generate link codes over the authenticated WebSocket session.
    """
    res_noauth = await client.get(f"/cosmetics/link/start?uuid={A}")
    assert res_noauth.status == 401
    assert (await res_noauth.json())["error"] == "unauthorized"

    res_badtoken = await client.get(f"/cosmetics/link/start?uuid={A}", headers={"Authorization": "Bearer badtoken"})
    assert res_badtoken.status == 401
    assert (await res_badtoken.json())["error"] == "unauthorized"


async def test_security_link_code_single_use_consumed_on_purchase(client):
    """Security Hardening 4: Link codes are single-use tokens that are atomically consumed
    upon checkout fulfillment, preventing token replay attacks.
    """
    store: cosmetics.CosmeticsStore = client.app[cosmetics.STORE]
    code = await store.create_link_code(A)

    payload = {
        "id": "evt-sec-link",
        "type": "payment.completed",
        "subject": {
            "transaction_id": "tbx-sec-link-1",
            "customer": {"link_code": code},
            "packages": [{"id": 55, "name": "lads:hat_wizard"}]
        }
    }
    payload_bytes = json.dumps(payload).encode("utf-8")
    sig = make_tebex_sig(payload_bytes, TEST_SECRET)
    headers = {"X-Tebex-Signature": sig, "Content-Type": "application/json"}

    res1 = await client.post("/cosmetics/tebex", data=payload_bytes, headers=headers)
    assert res1.status == 200
    assert (await res1.json())["status"] == "granted"

    # Code is now consumed: second attempt to resolve or purchase with same code fails
    assert (await store.get_uuid_for_link_code(code)) is None

    # An attacker attempting to replay the code in a new transaction gets 400 (unresolvable UUID)
    replay_payload = {
        "id": "evt-sec-replay",
        "type": "payment.completed",
        "subject": {
            "transaction_id": "tbx-sec-replay-2",
            "customer": {"link_code": code},
            "packages": [{"id": 56, "name": "lads:cape_founder"}]
        }
    }
    replay_bytes = json.dumps(replay_payload).encode("utf-8")
    replay_sig = make_tebex_sig(replay_bytes, TEST_SECRET)
    res2 = await client.post("/cosmetics/tebex", data=replay_bytes, headers={"X-Tebex-Signature": replay_sig, "Content-Type": "application/json"})
    assert res2.status == 400
    assert (await res2.json())["error"] == "missing or unresolvable buyer uuid"


# ---------------------------------------------------------------------------
# Fix Round 2 Tests (M1, M2, M3, L4, L5)
# ---------------------------------------------------------------------------

async def test_fix2_m1_no_auth_token_refuses_admin_endpoints(aiohttp_client, tmp_path, monkeypatch):
    """M1: No COSMETICS_AUTH_TOKEN configured = admin HTTP endpoints refuse all requests.
    Admin bearer does NOT fall back to TEBEX_SECRET.
    """
    monkeypatch.delenv("COSMETICS_AUTH_TOKEN", raising=False)
    app = web.Application()
    cosmetics.setup(app, db_path=tmp_path / "m1.db", tebex_secret="secret_tebex_only")
    cl = await aiohttp_client(app)

    # Calling admin endpoint with tebex secret must be rejected with 401
    r1 = await cl.get(f"/cosmetics/link/start?uuid={A}", headers={"Authorization": "Bearer secret_tebex_only"})
    assert r1.status == 401
    assert (await r1.json())["error"] == "unauthorized"

    # Calling equip with tebex secret must be rejected with 401
    r2 = await cl.post("/cosmetics/equip", json={"uuid": A, "slot": "cape", "item": None}, headers={"Authorization": "Bearer secret_tebex_only"})
    assert r2.status == 401
    assert (await r2.json())["error"] == "unauthorized"


async def test_fix2_m2_tebex_duplicate_webhook_retry_and_atomic_grant(client):
    """M2: Tebex duplicate delivery of the exact same signed webhook must return 200 both times,
    consuming the link code and recording the transaction in one atomic transaction.
    """
    store: cosmetics.CosmeticsStore = client.app[cosmetics.STORE]
    code = await store.create_link_code(A)

    body = json.dumps({
        "id": "evt-dup-retry",
        "type": "payment.completed",
        "subject": {
            "transaction_id": "tx-dup-retry-99",
            "customer": {"link_code": code},
            "products": [{"name": "lads:cape_founder"}]
        }
    }).encode("utf-8")
    sig = make_tebex_sig(body, TEST_SECRET)
    headers = {"X-Signature": sig, "Content-Type": "application/json"}

    # Delivery 1: grants item
    res1 = await client.post("/cosmetics/tebex", data=body, headers=headers)
    assert res1.status == 200
    assert (await res1.json())["status"] == "granted"

    # Delivery 2 (retry with same link code, which is now already deleted):
    # Because tx_id is deduplicated first in the atomic transaction, it returns 200 already_processed!
    res2 = await client.post("/cosmetics/tebex", data=body, headers=headers)
    assert res2.status == 200
    assert (await res2.json())["status"] == "already_processed"


async def test_fix2_m3_slot_allow_list_and_single_live_link_code(client):
    """M3: Only allowed slots (emote, cape, hat, glasses) are accepted; all others rejected.
    Max one live link code per UUID (generating a new code immediately deletes previous code).
    """
    ws, _ = await login(client, "Alice", A)

    # 1. Invalid slots are rejected over WebSocket
    for bad_slot in ("junk", "shoes", "wings", "pet", "cape2"):
        await ws.send_json({"t": "equip", "slot": bad_slot, "item": "lads:groove"})
        err = await ws.receive_json(timeout=2)
        assert err["t"] == "error"
        assert "invalid slot" in err["error"]

    prof = await (await client.get(f"/cosmetics/profile/{A}")).json()
    assert prof["equipped"] == {}

    # 2. Max one live link code per UUID
    store: cosmetics.CosmeticsStore = client.app[cosmetics.STORE]
    code1 = await store.create_link_code(A)
    assert await store.get_uuid_for_link_code(code1) == A

    code2 = await store.create_link_code(A)
    assert await store.get_uuid_for_link_code(code2) == A
    # Old code is deleted
    assert await store.get_uuid_for_link_code(code1) is None


async def test_fix2_l4_unauthenticated_link_code_route_removed(client):
    """L4: Unauthenticated GET /cosmetics/link/{code} route is removed (returns 404),
    preventing indefinite token querying / oracle attacks.
    """
    store: cosmetics.CosmeticsStore = client.app[cosmetics.STORE]
    code = await store.create_link_code(A)

    # Querying the code via unauthenticated GET returns 404
    r = await client.get(f"/cosmetics/link/{code}")
    assert r.status == 404

    # Querying verify without auth returns 401
    r_verify_unauth = await client.get(f"/cosmetics/link/verify?code={code}")
    assert r_verify_unauth.status == 401


async def test_fix2_l5_real_tebex_customer_username_id_and_catalog_mapping(client):
    """L5: Real Tebex Minecraft payload customer.username.id is resolved to buyer UUID.
    Explicit catalog mapping maps package ID -> cosmetic item ID (<= 64 chars, [a-z0-9_:]),
    ignoring free-text or 300+ character product names. Unknown packages return 200 without grant.
    """
    # 1. Real payload with customer.username.id and package id 1 (Aurora Cloak/Cape)
    real_body = json.dumps({
        "id": "evt-real-tbx",
        "type": "payment.completed",
        "subject": {
            "transaction_id": "tx-real-customer-1",
            "customer": {
                "username": {
                    "id": A,
                    "username": "Alice"
                }
            },
            "products": [
                {"id": 1, "name": "Super Ultra Long Name With SQL Injection ' ; DROP TABLE -- " + "x" * 300}
            ]
        }
    }).encode("utf-8")
    sig = make_tebex_sig(real_body, TEST_SECRET)
    headers = {"X-Signature": sig, "Content-Type": "application/json"}

    res = await client.post("/cosmetics/tebex", data=real_body, headers=headers)
    assert res.status == 200
    data = await res.json()
    assert data["status"] == "granted"
    assert data["uuid"] == A
    # Mapped from catalog ID 1, NOT the 300+ char name
    assert data["items"] == ["lads:cape_aurora"]
    assert len(data["items"][0]) <= 64

    # 2. Unknown package ID is logged and returns 200 with no grant
    unknown_body = json.dumps({
        "id": "evt-unknown-pkg",
        "type": "payment.completed",
        "subject": {
            "transaction_id": "tx-unknown-pkg-2",
            "customer": {"username": {"id": B, "username": "Bob"}},
            "products": [{"id": 99999, "name": "Random Server Donation"}]
        }
    }).encode("utf-8")
    sig_unknown = make_tebex_sig(unknown_body, TEST_SECRET)
    res_unk = await client.post("/cosmetics/tebex", data=unknown_body, headers={"X-Signature": sig_unknown, "Content-Type": "application/json"})
    assert res_unk.status == 200
    unk_data = await res_unk.json()
    assert unk_data["status"] == "granted"
    assert unk_data["items"] == []
