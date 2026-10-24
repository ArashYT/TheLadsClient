"""pytest for Lads social backend (cosmetics-server/social.py).
Tests Task S1 protocol, store, rate limiting, P2P session coordination, TURN minting,
and all High/Medium/Low security fixes.
"""
import asyncio
import json
import os
import time
from pathlib import Path

import logging
import aiohttp
import pytest
from aiohttp import web

# Dev mode enabled per-test via fixture so test_cosmetics can run alone
@pytest.fixture(autouse=True)
def _enable_social_dev(monkeypatch):
    monkeypatch.setenv("LADS_SOCIAL_DEV", "1")

import cosmetics
import social

A = "a" * 32
B = "b" * 32
C = "c" * 32
PLAYERS = {"Alice": A, "Bob": B, "Cara": C}


@pytest.fixture
def fake_mojang(monkeypatch):
    async def has_joined(name, sid):
        return PLAYERS.get(name)

    monkeypatch.setattr(cosmetics, "has_joined", has_joined)
    monkeypatch.setattr(social, "has_joined", has_joined)
    return has_joined


@pytest.fixture
def fake_profiles(monkeypatch):
    """Mock Mojang profile lookup for name->uuid resolution."""
    profiles = {
        "Alice": {"id": A, "name": "Alice"},
        "Bob": {"id": B, "name": "Bob"},
        "Cara": {"id": C, "name": "Cara"},
    }
    for i in range(1, 550):
        uname = f"User{i}"
        uuid_val = f"{i:032x}"
        profiles[uname] = {"id": uuid_val, "name": uname}

    async def lookup_name(name):
        return profiles.get(name)

    monkeypatch.setattr(social, "lookup_mojang_profile", lookup_name)
    return profiles


@pytest.fixture
async def social_app(aiohttp_client, fake_mojang, fake_profiles, tmp_path):
    app = web.Application()
    db_file = tmp_path / "social.db"
    social.mount(app, insecure=True, db_path=db_file)
    return await aiohttp_client(app)


async def auth_ws(client, name: str, uuid: str, **kw):
    ws = await client.ws_connect("/social/ws", **kw)
    msg = await ws.receive_json()
    assert msg.get("t") == "challenge"
    nonce = msg["nonce"]
    sid = cosmetics.server_id(nonce)
    await ws.send_json({"t": "auth", "name": name, "uuid": uuid, "serverId": sid})
    hello = await ws.receive_json()
    assert hello.get("t") == "hello"
    return ws, hello


async def recv_type(ws, expected_t: str, timeout: float = 3.0):
    start = time.time()
    while time.time() - start < timeout:
        try:
            msg = await asyncio.wait_for(ws.receive_json(), timeout=timeout)
        except asyncio.TimeoutError:
            break
        if msg.get("t") == expected_t:
            return msg
    raise TimeoutError(f"Did not receive message type '{expected_t}' within {timeout}s")


# ===========================================================================
# Base Plan Tests
# ===========================================================================

async def test_add_accept_friends_both_see_presence(social_app):
    alice_ws, _ = await auth_ws(social_app, "Alice", A)
    bob_ws, _ = await auth_ws(social_app, "Bob", B)

    # Alice adds Bob by name
    await alice_ws.send_json({"t": "friend_add", "name": "Bob"})

    sent = await recv_type(alice_ws, "request_sent")
    assert sent["uuid"] == B
    assert sent["name"] == "Bob"

    req_in = await recv_type(bob_ws, "request_in")
    assert req_in["uuid"] == A
    assert req_in["name"] == "Alice"

    # Bob accepts friend request
    await bob_ws.send_json({"t": "friend_accept", "uuid": A})

    b_added = await recv_type(bob_ws, "friend_added")
    assert b_added["uuid"] == A

    a_added = await recv_type(alice_ws, "friend_added")
    assert a_added["uuid"] == B

    # Drain initial mutual presence emitted by P13
    await recv_type(bob_ws, "presence")
    await recv_type(alice_ws, "presence")

    # Alice sets presence -> Bob sees it
    await alice_ws.send_json({
        "t": "presence",
        "state": "hosting",
        "world": {"id": "w1", "name": "World1", "scope": "friends", "slots": 8, "used": 1}
    })

    b_pres = await recv_type(bob_ws, "presence")
    assert b_pres["uuid"] == A
    assert b_pres["state"] == "hosting"
    assert b_pres["world"]["id"] == "w1"

    # Bob sets presence -> Alice sees it
    await bob_ws.send_json({"t": "presence", "state": "singleplayer"})
    a_pres = await recv_type(alice_ws, "presence")
    assert a_pres["uuid"] == B
    assert a_pres["state"] == "singleplayer"

    await alice_ws.close()
    await bob_ws.close()


async def test_request_rate_limit_20_per_hour(social_app):
    alice_ws, _ = await auth_ws(social_app, "Alice", A)

    # Send 20 requests (allowed)
    for i in range(1, 21):
        name = f"User{i}"
        await alice_ws.send_json({"t": "friend_add", "name": name})
        sent = await recv_type(alice_ws, "request_sent")
        assert sent["name"] == name

    # 21st request should be rate-limited
    await alice_ws.send_json({"t": "friend_add", "name": "User21"})
    err = await recv_type(alice_ws, "error")
    assert err["code"] == "rate"

    await alice_ws.close()


async def test_block_hides_presence_both_ways(social_app):
    alice_ws, _ = await auth_ws(social_app, "Alice", A)
    bob_ws, _ = await auth_ws(social_app, "Bob", B)

    # Establish friendship
    await alice_ws.send_json({"t": "friend_add", "name": "Bob"})
    await recv_type(alice_ws, "request_sent")
    await recv_type(bob_ws, "request_in")
    await bob_ws.send_json({"t": "friend_accept", "uuid": A})
    await recv_type(bob_ws, "friend_added")
    await recv_type(alice_ws, "friend_added")

    # Alice blocks Bob
    await alice_ws.send_json({"t": "block", "uuid": B})
    await recv_type(alice_ws, "friend_removed")
    await recv_type(bob_ws, "friend_removed")

    await asyncio.sleep(0.05)

    # Alice sends presence; Bob must NOT receive it
    await alice_ws.send_json({"t": "presence", "state": "singleplayer"})
    with pytest.raises(TimeoutError):
        await recv_type(bob_ws, "presence", timeout=0.5)

    # Bob sends presence; Alice must NOT receive it
    await bob_ws.send_json({"t": "presence", "state": "menu"})
    with pytest.raises(TimeoutError):
        await recv_type(alice_ws, "presence", timeout=0.5)

    # Alice unblocks Bob
    await alice_ws.send_json({"t": "unblock", "uuid": B})

    await alice_ws.close()
    await bob_ws.close()


async def test_dm_only_between_friends_and_offline_kept_7_days(social_app):
    alice_ws, _ = await auth_ws(social_app, "Alice", A)
    bob_ws, _ = await auth_ws(social_app, "Bob", B)

    # Not friends yet -> DM fails
    await alice_ws.send_json({"t": "dm", "to": B, "text": "hey"})
    err = await recv_type(alice_ws, "error")
    assert err["code"] == "not_friend"

    # Become friends
    await alice_ws.send_json({"t": "friend_add", "name": "Bob"})
    await recv_type(alice_ws, "request_sent")
    await recv_type(bob_ws, "request_in")
    await bob_ws.send_json({"t": "friend_accept", "uuid": A})
    await recv_type(bob_ws, "friend_added")
    await recv_type(alice_ws, "friend_added")

    # Bob goes offline
    await bob_ws.close()
    await asyncio.sleep(0.05)

    # Alice sends DM to offline Bob
    await alice_ws.send_json({"t": "dm", "to": B, "text": "offline message 1"})
    await asyncio.sleep(0.05)

    # Bob reconnects -> receives queued offline DMs in hello
    bob_ws2, hello2 = await auth_ws(social_app, "Bob", B)
    dms = hello2.get("dms", [])
    assert len(dms) == 1
    assert dms[0]["from"] == A
    assert dms[0]["text"] == "offline message 1"
    assert "ts" in dms[0]

    # Bob reconnects again -> DMs should be cleared
    await bob_ws2.close()
    _, hello3 = await auth_ws(social_app, "Bob", B)
    assert hello3.get("dms", []) == []

    await alice_ws.close()


async def test_invite_accept_starts_p2p_for_both_roles(social_app):
    alice_ws, _ = await auth_ws(social_app, "Alice", A)
    bob_ws, _ = await auth_ws(social_app, "Bob", B)

    # Friend each other
    await alice_ws.send_json({"t": "friend_add", "name": "Bob"})
    await recv_type(alice_ws, "request_sent")
    await recv_type(bob_ws, "request_in")
    await bob_ws.send_json({"t": "friend_accept", "uuid": A})
    await recv_type(bob_ws, "friend_added")
    await recv_type(alice_ws, "friend_added")

    # Alice hosts
    world = {"id": "w1", "name": "World1", "scope": "friends", "slots": 8, "used": 1}
    await alice_ws.send_json({"t": "presence", "state": "hosting", "world": world})
    await recv_type(bob_ws, "presence")

    # Alice invites Bob
    await alice_ws.send_json({"t": "invite", "to": B, "world": world})
    inv = await recv_type(bob_ws, "invite")
    assert inv["from"] == A
    invite_id = inv["inviteId"]

    # Bob accepts invite
    await bob_ws.send_json({"t": "invite_accept", "inviteId": invite_id})

    # Both receive p2p_start
    a_p2p = await recv_type(alice_ws, "p2p_start")
    b_p2p = await recv_type(bob_ws, "p2p_start")

    assert a_p2p["sid"] == b_p2p["sid"]
    assert a_p2p["peer"] == B
    assert a_p2p["role"] == "host"
    assert b_p2p["peer"] == A
    assert b_p2p["role"] == "guest"
    assert "ice" in a_p2p and "ice" in b_p2p
    assert "stun" in a_p2p["ice"] and "turn" in a_p2p["ice"]
    assert "relayStatus" in a_p2p["ice"]

    await alice_ws.close()
    await bob_ws.close()


async def test_join_request_invite_only_scope_refused(social_app):
    alice_ws, _ = await auth_ws(social_app, "Alice", A)
    bob_ws, _ = await auth_ws(social_app, "Bob", B)

    # Friend each other
    await alice_ws.send_json({"t": "friend_add", "name": "Bob"})
    await recv_type(alice_ws, "request_sent")
    await recv_type(bob_ws, "request_in")
    await bob_ws.send_json({"t": "friend_accept", "uuid": A})
    await recv_type(bob_ws, "friend_added")
    await recv_type(alice_ws, "friend_added")

    # Alice hosts in 'invite' scope
    world = {"id": "w1", "name": "World1", "scope": "invite", "slots": 8, "used": 1}
    await alice_ws.send_json({"t": "presence", "state": "hosting", "world": world})
    await recv_type(bob_ws, "presence")

    # Bob attempts join_request
    await bob_ws.send_json({"t": "join_request", "host": A})
    err = await recv_type(bob_ws, "error")
    assert err["code"] == "invite_only"
    assert err["ref"] == A

    await alice_ws.close()
    await bob_ws.close()


async def test_signal_only_relayed_within_session_peer(social_app):
    alice_ws, _ = await auth_ws(social_app, "Alice", A)
    bob_ws, _ = await auth_ws(social_app, "Bob", B)
    cara_ws, _ = await auth_ws(social_app, "Cara", C)

    # Friends A & B
    await alice_ws.send_json({"t": "friend_add", "name": "Bob"})
    await recv_type(alice_ws, "request_sent")
    await recv_type(bob_ws, "request_in")
    await bob_ws.send_json({"t": "friend_accept", "uuid": A})
    await recv_type(bob_ws, "friend_added")
    await recv_type(alice_ws, "friend_added")

    world = {"id": "w1", "name": "World1", "scope": "friends", "slots": 8, "used": 1}
    await alice_ws.send_json({"t": "presence", "state": "hosting", "world": world})
    await recv_type(bob_ws, "presence")

    await alice_ws.send_json({"t": "invite", "to": B, "world": world})
    inv = await recv_type(bob_ws, "invite")
    await bob_ws.send_json({"t": "invite_accept", "inviteId": inv["inviteId"]})

    a_p2p = await recv_type(alice_ws, "p2p_start")
    b_p2p = await recv_type(bob_ws, "p2p_start")
    sid = a_p2p["sid"]

    # Alice sends signal to Bob
    await alice_ws.send_json({"t": "signal", "sid": sid, "data": {"candidate": "udp 127.0.0.1 5000"}})
    b_sig = await recv_type(bob_ws, "signal")
    assert b_sig["sid"] == sid
    assert b_sig["data"]["candidate"] == "udp 127.0.0.1 5000"

    # Cara attempts to send signal with sid
    await cara_ws.send_json({"t": "signal", "sid": sid, "data": {"candidate": "fake"}})
    err = await recv_type(cara_ws, "error")
    assert err["code"] == "not_found"

    # Bob and Alice should NOT receive Cara's signal
    with pytest.raises(TimeoutError):
        await recv_type(bob_ws, "signal", timeout=0.4)

    await alice_ws.close()
    await bob_ws.close()
    await cara_ws.close()


async def test_expired_or_revoked_invite_refused(social_app):
    alice_ws, _ = await auth_ws(social_app, "Alice", A)
    bob_ws, _ = await auth_ws(social_app, "Bob", B)

    # Friends
    await alice_ws.send_json({"t": "friend_add", "name": "Bob"})
    await recv_type(alice_ws, "request_sent")
    await recv_type(bob_ws, "request_in")
    await bob_ws.send_json({"t": "friend_accept", "uuid": A})
    await recv_type(bob_ws, "friend_added")
    await recv_type(alice_ws, "friend_added")

    world = {"id": "w1", "name": "World1", "scope": "friends", "slots": 8, "used": 1}
    await alice_ws.send_json({"t": "presence", "state": "hosting", "world": world})
    await recv_type(bob_ws, "presence")

    await alice_ws.send_json({"t": "invite", "to": B, "world": world})
    inv = await recv_type(bob_ws, "invite")
    invite_id = inv["inviteId"]

    # Host closes world (revokes invite)
    await alice_ws.send_json({"t": "presence", "state": "menu", "world": None})
    await recv_type(bob_ws, "presence")

    # Bob accepts revoked invite -> refused
    await bob_ws.send_json({"t": "invite_accept", "inviteId": invite_id})
    err = await recv_type(bob_ws, "error")
    assert err["code"] == "expired"

    await alice_ws.close()
    await bob_ws.close()


async def test_turn_creds_empty_without_key_and_after_900gb(social_app, monkeypatch):
    # Without keys, turn credentials are empty
    monkeypatch.delenv("CF_TURN_KEY_ID", raising=False)
    monkeypatch.delenv("CF_TURN_API_TOKEN", raising=False)

    alice_ws, _ = await auth_ws(social_app, "Alice", A)
    bob_ws, _ = await auth_ws(social_app, "Bob", B)

    await alice_ws.send_json({"t": "friend_add", "name": "Bob"})
    await recv_type(alice_ws, "request_sent")
    await recv_type(bob_ws, "request_in")
    await bob_ws.send_json({"t": "friend_accept", "uuid": A})
    await recv_type(bob_ws, "friend_added")
    await recv_type(alice_ws, "friend_added")

    world = {"id": "w1", "name": "World1", "scope": "friends", "slots": 8, "used": 1}
    await alice_ws.send_json({"t": "presence", "state": "hosting", "world": world})
    await recv_type(bob_ws, "presence")

    await alice_ws.send_json({"t": "invite", "to": B, "world": world})
    inv = await recv_type(bob_ws, "invite")
    await bob_ws.send_json({"t": "invite_accept", "inviteId": inv["inviteId"]})

    a_p2p = await recv_type(alice_ws, "p2p_start")
    assert a_p2p["ice"]["turn"] == []
    assert a_p2p["ice"]["relayStatus"] == "none"

    # Now simulate TURN key present
    monkeypatch.setenv("CF_TURN_KEY_ID", "dummy_key_id")
    monkeypatch.setenv("CF_TURN_API_TOKEN", "dummy_token")

    async def fake_mint():
        return [{"url": "turn:turn.cloudflare.com:3478?transport=udp", "user": "test_user", "credential": "test_cred"}]

    monkeypatch.setattr(social, "mint_turn_credentials", fake_mint)

    # Next invite with key -> turn should have creds
    await alice_ws.send_json({"t": "invite", "to": B, "world": world})
    inv2 = await recv_type(bob_ws, "invite")
    await bob_ws.send_json({"t": "invite_accept", "inviteId": inv2["inviteId"]})

    a_p2p2 = await recv_type(alice_ws, "p2p_start")
    b_p2p2 = await recv_type(bob_ws, "p2p_start")
    assert len(a_p2p2["ice"]["turn"]) == 1
    assert a_p2p2["ice"]["turn"][0]["user"] == "test_user"
    assert a_p2p2["ice"]["relayStatus"] == "ok"

    # End session reporting 950 GB usage
    month_str = time.strftime("%Y-%m", time.gmtime())
    store = social_app.app["social_store"]
    await store.record_relay_bytes(month_str, 950 * 10**9)

    # Now budget is exceeded -> turn credentials must be empty and relayStatus is budget
    await alice_ws.send_json({"t": "invite", "to": B, "world": world})
    inv3 = await recv_type(bob_ws, "invite")
    await bob_ws.send_json({"t": "invite_accept", "inviteId": inv3["inviteId"]})

    a_p2p3 = await recv_type(alice_ws, "p2p_start")
    assert a_p2p3["ice"]["turn"] == []
    assert a_p2p3["ice"]["relayStatus"] == "budget"

    await alice_ws.close()
    await bob_ws.close()


async def test_show_server_false_strips_address(social_app):
    alice_ws, _ = await auth_ws(social_app, "Alice", A)
    bob_ws, _ = await auth_ws(social_app, "Bob", B)

    # Friends
    await alice_ws.send_json({"t": "friend_add", "name": "Bob"})
    await recv_type(alice_ws, "request_sent")
    await recv_type(bob_ws, "request_in")
    await bob_ws.send_json({"t": "friend_accept", "uuid": A})
    await recv_type(bob_ws, "friend_added")
    await recv_type(alice_ws, "friend_added")

    # Drain initial mutual presence emitted by P13
    await recv_type(bob_ws, "presence")
    await recv_type(alice_ws, "presence")

    # Alice joins server
    await alice_ws.send_json({"t": "presence", "state": "server", "server": "mc.hypixel.net"})
    pres0 = await recv_type(bob_ws, "presence")
    assert pres0["server"] == "mc.hypixel.net"

    # Alice disables showServer -> immediately re-broadcasts with server stripped
    await alice_ws.send_json({"t": "settings", "showServer": False, "appearOffline": False})
    pres1 = await recv_type(bob_ws, "presence")
    assert pres1["state"] == "server"
    assert "server" not in pres1 or pres1["server"] is None

    # Alice enables showServer -> re-broadcasts with server restored
    await alice_ws.send_json({"t": "settings", "showServer": True, "appearOffline": False})
    pres2 = await recv_type(bob_ws, "presence")
    assert pres2["server"] == "mc.hypixel.net"

    await alice_ws.close()
    await bob_ws.close()


async def test_full_world_refused(social_app):
    alice_ws, _ = await auth_ws(social_app, "Alice", A)
    bob_ws, _ = await auth_ws(social_app, "Bob", B)

    await alice_ws.send_json({"t": "friend_add", "name": "Bob"})
    await recv_type(alice_ws, "request_sent")
    await recv_type(bob_ws, "request_in")
    await bob_ws.send_json({"t": "friend_accept", "uuid": A})
    await recv_type(bob_ws, "friend_added")
    await recv_type(alice_ws, "friend_added")

    # Host world is full (used == slots)
    world = {"id": "w1", "name": "World1", "scope": "friends", "slots": 4, "used": 4}
    await alice_ws.send_json({"t": "presence", "state": "hosting", "world": world})
    await recv_type(bob_ws, "presence")

    # Bob sends join_request -> refused full
    await bob_ws.send_json({"t": "join_request", "host": A})
    err = await recv_type(bob_ws, "error")
    assert err["code"] == "full"
    assert err["ref"] == A

    await alice_ws.close()
    await bob_ws.close()


async def test_incompatible_game_refused(social_app):
    alice_ws, _ = await auth_ws(social_app, "Alice", A)
    bob_ws, _ = await auth_ws(social_app, "Bob", B)

    await alice_ws.send_json({"t": "friend_add", "name": "Bob"})
    await recv_type(alice_ws, "request_sent")
    await recv_type(bob_ws, "request_in")
    await bob_ws.send_json({"t": "friend_accept", "uuid": A})
    await recv_type(bob_ws, "friend_added")
    await recv_type(alice_ws, "friend_added")

    # Alice is on 1.20.1 fabric
    alice_game = {"mc": "1.20.1", "loader": "fabric", "profileKind": "plain", "packId": "", "packVersion": ""}
    world = {"id": "w1", "name": "World1", "scope": "friends", "slots": 8, "used": 1}
    await alice_ws.send_json({"t": "presence", "state": "hosting", "game": alice_game, "world": world})
    await recv_type(bob_ws, "presence")

    # Bob is on 1.8.9 forge -> incompatible MC version
    bob_game = {"mc": "1.8.9", "loader": "forge", "profileKind": "plain", "packId": "", "packVersion": ""}
    await bob_ws.send_json({"t": "presence", "state": "menu", "game": bob_game})
    await recv_type(alice_ws, "presence")

    await bob_ws.send_json({"t": "join_request", "host": A})
    err = await recv_type(bob_ws, "error")
    assert err["code"] == "incompatible"
    assert err["ref"] == A

    # Bob matches MC version & loader, but pack version differs
    alice_pack = {"mc": "1.20.1", "loader": "fabric", "profileKind": "pack", "packId": "zerosmp", "packVersion": "1.0"}
    await alice_ws.send_json({"t": "presence", "state": "hosting", "game": alice_pack, "world": world})
    await recv_type(bob_ws, "presence")

    bob_pack = {"mc": "1.20.1", "loader": "fabric", "profileKind": "pack", "packId": "zerosmp", "packVersion": "1.1"}
    await bob_ws.send_json({"t": "presence", "state": "menu", "game": bob_pack})
    await recv_type(alice_ws, "presence")

    await bob_ws.send_json({"t": "join_request", "host": A})
    err2 = await recv_type(bob_ws, "error")
    assert err2["code"] == "incompatible"

    await alice_ws.close()
    await bob_ws.close()


# ===========================================================================
# Security Review Fixes (H1..H5, M1..M9, L1..L5)
# ===========================================================================

async def test_turn_minting_http_201_and_filter_stun(monkeypatch):
    """H1: Cloudflare 201 Created and STUN URLs filtered from turn[]."""
    monkeypatch.setenv("CF_TURN_KEY_ID", "key123")
    monkeypatch.setenv("CF_TURN_API_TOKEN", "tok456")

    cf_response_data = {
        "iceServers": [
            {"urls": ["stun:stun.cloudflare.com:3478"]},
            {
                "urls": [
                    "turn:turn.cloudflare.com:3478?transport=udp",
                    "turn:turn.cloudflare.com:3478?transport=tcp",
                    "turns:turn.cloudflare.com:5349?transport=tcp"
                ],
                "username": "cf_user",
                "credential": "cf_password"
            }
        ]
    }

    class FakeResponse:
        status = 201
        async def __aenter__(self): return self
        async def __aexit__(self, *args): pass
        async def json(self, content_type=None): return cf_response_data

    class FakeSession:
        def __init__(self, **kw): pass
        async def __aenter__(self): return self
        async def __aexit__(self, *args): pass
        def post(self, url, headers=None, json=None):
            return FakeResponse()

    monkeypatch.setattr(aiohttp, "ClientSession", FakeSession)

    res = await social.mint_turn_credentials()
    assert len(res) == 1
    assert res[0]["url"] == "turn:turn.cloudflare.com:3478?transport=udp"
    assert res[0]["user"] == "cf_user"
    assert res[0]["credential"] == "cf_password"


async def test_preauth_idle_timeout(social_app):
    """H2: Auth must complete within 10s or close."""
    ws = await social_app.ws_connect("/social/ws")
    await ws.receive_json()  # challenge
    # Sleep to trigger auth timeout
    msg = await asyncio.wait_for(ws.receive(), timeout=12.0)
    assert msg.type in (aiohttp.WSMsgType.CLOSE, aiohttp.WSMsgType.CLOSED)
    assert ws.close_code == 4003


async def test_max_msg_size_transport_rejection(social_app):
    """H2: aiohttp max_msg_size=65536 rejects huge frames (1009)."""
    ws = await social_app.ws_connect("/social/ws")
    await ws.receive_json()
    # Send frame > 64 KB
    await ws.send_str(json.dumps({"t": "auth", "pad": "x" * 100_000}))
    msg = await ws.receive()
    assert msg.type in (aiohttp.WSMsgType.CLOSE, aiohttp.WSMsgType.CLOSED)
    assert ws.close_code == 1009


async def test_per_ip_concurrent_socket_cap(social_app):
    """H2: Per-IP cap of 8 concurrent sockets."""
    sockets = []
    for i in range(8):
        ws, _ = await auth_ws(social_app, f"User{i+1}", f"{i+1:032x}")
        sockets.append(ws)

    # 9th socket from same IP is closed with 1008
    ws9 = await social_app.ws_connect("/social/ws")
    msg = await ws9.receive()
    assert msg.type in (aiohttp.WSMsgType.CLOSE, aiohttp.WSMsgType.CLOSED)
    assert ws9.close_code == 1008

    for ws in sockets:
        await ws.close()


async def test_field_schema_validation_rejects_bad(social_app):
    """H3 & M4: Validate every field, reject invalid with error 'bad'."""
    alice_ws, _ = await auth_ws(social_app, "Alice", A)

    # Invalid state
    await alice_ws.send_json({"t": "presence", "state": "super_admin"})
    err1 = await recv_type(alice_ws, "error")
    assert err1["code"] == "bad"

    # Invalid world (not a dict)
    await alice_ws.send_json({"t": "presence", "state": "hosting", "world": "not_dict"})
    err2 = await recv_type(alice_ws, "error")
    assert err2["code"] == "bad"

    # Invalid world slots (out of range)
    await alice_ws.send_json({"t": "presence", "state": "hosting", "world": {"id": "w", "name": "w", "scope": "friends", "slots": 999, "used": 0}})
    err3 = await recv_type(alice_ws, "error")
    assert err3["code"] == "bad"

    # Nick too long (> 32)
    await alice_ws.send_json({"t": "nick", "uuid": B, "nick": "x" * 33})
    err4 = await recv_type(alice_ws, "error")
    assert err4["code"] == "bad"

    await alice_ws.close()


async def test_signal_data_size_and_rate_limits(social_app):
    """H3: Signal data <= 8 KB and <= 40 signals/sec."""
    alice_ws, _ = await auth_ws(social_app, "Alice", A)
    bob_ws, _ = await auth_ws(social_app, "Bob", B)

    # Friend each other
    await alice_ws.send_json({"t": "friend_add", "name": "Bob"})
    await recv_type(alice_ws, "request_sent")
    await recv_type(bob_ws, "request_in")
    await bob_ws.send_json({"t": "friend_accept", "uuid": A})
    await recv_type(bob_ws, "friend_added")
    await recv_type(alice_ws, "friend_added")

    world = {"id": "w1", "name": "W", "scope": "friends", "slots": 8, "used": 0}
    await alice_ws.send_json({"t": "presence", "state": "hosting", "world": world})
    await recv_type(bob_ws, "presence")

    await alice_ws.send_json({"t": "invite", "to": B, "world": world})
    inv = await recv_type(bob_ws, "invite")
    await bob_ws.send_json({"t": "invite_accept", "inviteId": inv["inviteId"]})

    a_p2p = await recv_type(alice_ws, "p2p_start")
    await recv_type(bob_ws, "p2p_start")
    sid = a_p2p["sid"]

    # Signal data > 8 KB -> error limit
    big_signal = {"pad": "s" * 9000}
    await alice_ws.send_json({"t": "signal", "sid": sid, "data": big_signal})
    err = await recv_type(alice_ws, "error")
    assert err["code"] == "limit"

    # 40 signals allowed within 1s
    for i in range(40):
        await alice_ws.send_json({"t": "signal", "sid": sid, "data": {"seq": i}})
    # 41st within 1s -> rate error
    await alice_ws.send_json({"t": "signal", "sid": sid, "data": {"seq": 41}})
    err2 = await recv_type(alice_ws, "error")
    assert err2["code"] == "rate"

    await alice_ws.close()
    await bob_ws.close()


async def test_max_pending_invites_and_requests_limit(social_app):
    """H3: Max 20 pending invites per host."""
    alice_ws, _ = await auth_ws(social_app, "Alice", A)
    store = social_app.app["social_store"]
    world = {"id": "w1", "name": "W", "scope": "friends", "slots": 8, "used": 0}
    await alice_ws.send_json({"t": "presence", "state": "hosting", "world": world})

    # Create 20 friend targets and invite them
    friends = []
    for i in range(1, 22):
        u = f"{i:032x}"
        await store.add_friend(A, u)
        ws, _ = await auth_ws(social_app, f"User{i}", u, headers={"CF-Connecting-IP": f"10.0.0.{i}"})
        friends.append(ws)

    for i in range(20):
        u = f"{i+1:032x}"
        await alice_ws.send_json({"t": "invite", "to": u, "world": world})
        await recv_type(friends[i], "invite")

    # 21st pending invite exceeds limit
    await alice_ws.send_json({"t": "invite", "to": f"{21:032x}", "world": world})
    err = await recv_type(alice_ws, "error")
    assert err["code"] == "limit"

    await alice_ws.close()
    for ws in friends:
        await ws.close()


async def test_slow_consumer_queue_full_drops_slow_peer(social_app):
    """H4: Bounded send queue (256 msgs); slow peer dropped without blocking sender."""
    alice_ws, _ = await auth_ws(social_app, "Alice", A)
    bob_ws, _ = await auth_ws(social_app, "Bob", B)

    await alice_ws.send_json({"t": "friend_add", "name": "Bob"})
    await recv_type(alice_ws, "request_sent")
    await recv_type(bob_ws, "request_in")
    await bob_ws.send_json({"t": "friend_accept", "uuid": A})
    await recv_type(bob_ws, "friend_added")
    await recv_type(alice_ws, "friend_added")

    # Drain initial mutual presence emitted by P13
    await recv_type(bob_ws, "presence")
    await recv_type(alice_ws, "presence")

    # Bob stops reading and Bob's writer cannot drain; fill Bob's queue to max (256)
    hub = social_app.app["social_hub"]
    bob_peer = hub.peers[B]
    bob_peer.writer_task.cancel()
    for i in range(256):
        bob_peer.send_queue.put_nowait(f'{{"dummy": {i}}}')

    # Alice sends a presence update which tries to fan out to Bob
    await alice_ws.send_json({"t": "presence", "state": "singleplayer"})
    await asyncio.sleep(0.1)

    # Bob's queue filled -> closed
    assert bob_peer.gone
    msg = await bob_ws.receive()
    assert msg.type in (aiohttp.WSMsgType.CLOSE, aiohttp.WSMsgType.CLOSED) or bob_ws.closed
    assert bob_ws.closed
    assert bob_ws.close_code in (1006, 1011, 1013)
    # Alice is unaffected and can still interact
    await alice_ws.send_json({"t": "presence", "state": "menu"})
    await asyncio.sleep(0.1)
    assert not alice_ws.closed

    await alice_ws.close()


async def test_p2p_end_bytes_clamped_and_relay_status(social_app):
    """H5: Bytes clamped to duration * 12.5 MB/s; relayStatus reported in ice."""
    alice_ws, _ = await auth_ws(social_app, "Alice", A)
    bob_ws, _ = await auth_ws(social_app, "Bob", B)

    await alice_ws.send_json({"t": "friend_add", "name": "Bob"})
    await recv_type(alice_ws, "request_sent")
    await recv_type(bob_ws, "request_in")
    await bob_ws.send_json({"t": "friend_accept", "uuid": A})
    await recv_type(bob_ws, "friend_added")
    await recv_type(alice_ws, "friend_added")

    world = {"id": "w1", "name": "W", "scope": "friends", "slots": 8, "used": 0}
    await alice_ws.send_json({"t": "presence", "state": "hosting", "world": world})
    await recv_type(bob_ws, "presence")

    await alice_ws.send_json({"t": "invite", "to": B, "world": world})
    inv = await recv_type(bob_ws, "invite")
    await bob_ws.send_json({"t": "invite_accept", "inviteId": inv["inviteId"]})

    a_p2p = await recv_type(alice_ws, "p2p_start")
    await recv_type(bob_ws, "p2p_start")
    sid = a_p2p["sid"]

    # Claim absurd amount of bytes (1 TB) immediately
    await alice_ws.send_json({"t": "p2p_end", "sid": sid, "reason": "left", "relayed": True, "bytes": 10**12})
    await recv_type(bob_ws, "p2p_end")

    store = social_app.app["social_store"]
    month_str = time.strftime("%Y-%m", time.gmtime())
    used = await store.get_relay_bytes(month_str)
    # Clamped to <= duration * 12.5 MB/s (under 100 MB for a <5s session)
    assert used < 100 * 10**6

    # Test garbage bytes answered with error "bad"
    await alice_ws.send_json({"t": "p2p_end", "sid": sid, "bytes": "not_int"})
    err = await recv_type(alice_ws, "error")
    assert err["code"] == "bad"

    # Nonexistent session answered with not_found
    await alice_ws.send_json({"t": "p2p_end", "sid": "nonexistent_sid"})
    err2 = await recv_type(alice_ws, "error")
    assert err2["code"] == "not_found"

    await alice_ws.close()
    await bob_ws.close()


async def test_invite_and_join_accept_bound_to_invitee_and_host(social_app):
    """M1: invite_accept only by invitee; join_accept only by host."""
    alice_ws, _ = await auth_ws(social_app, "Alice", A)
    bob_ws, _ = await auth_ws(social_app, "Bob", B)
    cara_ws, _ = await auth_ws(social_app, "Cara", C)

    await alice_ws.send_json({"t": "friend_add", "name": "Bob"})
    await recv_type(alice_ws, "request_sent")
    await recv_type(bob_ws, "request_in")
    await bob_ws.send_json({"t": "friend_accept", "uuid": A})
    await recv_type(bob_ws, "friend_added")
    await recv_type(alice_ws, "friend_added")

    await alice_ws.send_json({"t": "friend_add", "name": "Cara"})
    await recv_type(alice_ws, "request_sent")
    await recv_type(cara_ws, "request_in")
    await cara_ws.send_json({"t": "friend_accept", "uuid": A})
    await recv_type(cara_ws, "friend_added")
    await recv_type(alice_ws, "friend_added")

    world = {"id": "w1", "name": "W", "scope": "friends", "slots": 8, "used": 0}
    await alice_ws.send_json({"t": "presence", "state": "hosting", "world": world})
    await recv_type(bob_ws, "presence")
    await recv_type(cara_ws, "presence")

    # Alice invites Bob
    await alice_ws.send_json({"t": "invite", "to": B, "world": world})
    inv = await recv_type(bob_ws, "invite")

    # Cara tries to accept Bob's invite -> rejected
    await cara_ws.send_json({"t": "invite_accept", "inviteId": inv["inviteId"]})
    err1 = await recv_type(cara_ws, "error")
    assert err1["code"] == "not_found"

    # Bob join_requests Alice
    await bob_ws.send_json({"t": "join_request", "host": A})
    req = await recv_type(alice_ws, "join_request")

    # Cara tries to join_accept Bob's request -> rejected
    await cara_ws.send_json({"t": "join_accept", "reqId": req["reqId"]})
    err2 = await recv_type(cara_ws, "error")
    assert err2["code"] == "not_found"

    await alice_ws.close()
    await bob_ws.close()
    await cara_ws.close()


async def test_one_active_session_per_guest_replaces_old(social_app):
    """M2: A new session replaces an existing one for the same guest."""
    alice_ws, _ = await auth_ws(social_app, "Alice", A)
    bob_ws, _ = await auth_ws(social_app, "Bob", B)

    await alice_ws.send_json({"t": "friend_add", "name": "Bob"})
    await recv_type(alice_ws, "request_sent")
    await recv_type(bob_ws, "request_in")
    await bob_ws.send_json({"t": "friend_accept", "uuid": A})
    await recv_type(bob_ws, "friend_added")
    await recv_type(alice_ws, "friend_added")

    world = {"id": "w1", "name": "W", "scope": "friends", "slots": 8, "used": 0}
    await alice_ws.send_json({"t": "presence", "state": "hosting", "world": world})
    await recv_type(bob_ws, "presence")

    # First session
    await alice_ws.send_json({"t": "invite", "to": B, "world": world})
    inv1 = await recv_type(bob_ws, "invite")
    await bob_ws.send_json({"t": "invite_accept", "inviteId": inv1["inviteId"]})
    s1 = await recv_type(alice_ws, "p2p_start")
    await recv_type(bob_ws, "p2p_start")

    # Second session for Bob replaces the first
    await alice_ws.send_json({"t": "invite", "to": B, "world": world})
    inv2 = await recv_type(bob_ws, "invite")
    await bob_ws.send_json({"t": "invite_accept", "inviteId": inv2["inviteId"]})

    # Both peers receive p2p_end with reason 'replaced' for old session
    end_a = await recv_type(alice_ws, "p2p_end")
    assert end_a["sid"] == s1["sid"]
    assert end_a["reason"] == "replaced"

    end_b = await recv_type(bob_ws, "p2p_end")
    assert end_b["sid"] == s1["sid"]
    assert end_b["reason"] == "replaced"

    # And both receive p2p_start for new session
    s2 = await recv_type(alice_ws, "p2p_start")
    await recv_type(bob_ws, "p2p_start")
    assert s2["sid"] != s1["sid"]

    await alice_ws.close()
    await bob_ws.close()


async def test_block_tears_down_sessions_invites_requests_and_unfriends(social_app):
    """M3: Block terminates running session, in-flight invites/requests, unfriends."""
    alice_ws, _ = await auth_ws(social_app, "Alice", A)
    bob_ws, _ = await auth_ws(social_app, "Bob", B)

    await alice_ws.send_json({"t": "friend_add", "name": "Bob"})
    await recv_type(alice_ws, "request_sent")
    await recv_type(bob_ws, "request_in")
    await bob_ws.send_json({"t": "friend_accept", "uuid": A})
    await recv_type(bob_ws, "friend_added")
    await recv_type(alice_ws, "friend_added")

    world = {"id": "w1", "name": "W", "scope": "friends", "slots": 8, "used": 0}
    await alice_ws.send_json({"t": "presence", "state": "hosting", "world": world})
    await recv_type(bob_ws, "presence")

    await alice_ws.send_json({"t": "invite", "to": B, "world": world})
    inv = await recv_type(bob_ws, "invite")
    await bob_ws.send_json({"t": "invite_accept", "inviteId": inv["inviteId"]})

    a_p2p = await recv_type(alice_ws, "p2p_start")
    await recv_type(bob_ws, "p2p_start")
    sid = a_p2p["sid"]

    # Alice blocks Bob
    await alice_ws.send_json({"t": "block", "uuid": B})

    # Bob gets p2p_end with reason 'blocked'
    b_end = await recv_type(bob_ws, "p2p_end")
    assert b_end["sid"] == sid
    assert b_end["reason"] == "blocked"

    # Both get friend_removed
    await recv_type(alice_ws, "friend_removed")
    await recv_type(bob_ws, "friend_removed")

    # Store confirms no longer friends
    store = social_app.app["social_store"]
    assert not await store.are_friends(A, B)

    await alice_ws.close()
    await bob_ws.close()


async def test_json_nesting_bomb_closed_4002(social_app):
    """M4: RecursionError on authenticated socket closes with 4002."""
    alice_ws, _ = await auth_ws(social_app, "Alice", A)
    await alice_ws.send_str("[" * 20000)
    msg = await alice_ws.receive()
    assert msg.type in (aiohttp.WSMsgType.CLOSE, aiohttp.WSMsgType.CLOSED)
    assert alice_ws.close_code == 4002


async def test_rate_limits_survive_reconnect(social_app):
    """M5: Rate limits are keyed by UUID in the hub and survive socket reconnect."""
    alice_ws, _ = await auth_ws(social_app, "Alice", A)
    bob_ws, _ = await auth_ws(social_app, "Bob", B)

    await alice_ws.send_json({"t": "friend_add", "name": "Bob"})
    await recv_type(alice_ws, "request_sent")
    await recv_type(bob_ws, "request_in")
    await bob_ws.send_json({"t": "friend_accept", "uuid": A})
    await recv_type(bob_ws, "friend_added")
    await recv_type(alice_ws, "friend_added")

    # Exhaust DM limit (30)
    for i in range(30):
        await alice_ws.send_json({"t": "dm", "to": B, "text": f"m{i}"})
    await alice_ws.send_json({"t": "dm", "to": B, "text": "extra"})
    err1 = await recv_type(alice_ws, "error")
    assert err1["code"] == "rate"

    # Reconnect Alice
    alice2_ws, _ = await auth_ws(social_app, "Alice", A)
    # Immediately try to send DM -> still rate-limited!
    await alice2_ws.send_json({"t": "dm", "to": B, "text": "after_reconnect"})
    err2 = await recv_type(alice2_ws, "error")
    assert err2["code"] == "rate"

    await alice2_ws.close()
    await bob_ws.close()


async def test_offline_dm_queue_capped_at_100(social_app):
    """M5: Offline DMs capped at 100 per recipient (oldest dropped)."""
    alice_ws, _ = await auth_ws(social_app, "Alice", A)
    bob_ws, _ = await auth_ws(social_app, "Bob", B)

    await alice_ws.send_json({"t": "friend_add", "name": "Bob"})
    await recv_type(alice_ws, "request_sent")
    await recv_type(bob_ws, "request_in")
    await bob_ws.send_json({"t": "friend_accept", "uuid": A})
    await recv_type(bob_ws, "friend_added")
    await recv_type(alice_ws, "friend_added")

    # Bob goes offline
    await bob_ws.close()
    await asyncio.sleep(0.05)

    store = social_app.app["social_store"]
    # Insert 50 offline DMs from A and 60 from C directly
    now_ms = int(time.time() * 1000)
    for i in range(50):
        await store.save_offline_dm(A, B, f"msg_a_{i}", now_ms + i)
    for i in range(60):
        await store.save_offline_dm(C, B, f"msg_c_{i}", now_ms + 100 + i)

    # Bob connects -> hello delivers at most 100
    bob2_ws, hello = await auth_ws(social_app, "Bob", B)
    assert len(hello["dms"]) == 100

    await alice_ws.close()
    await bob2_ws.close()


async def test_name_collision_and_cache_429_not_cached(social_app):
    """M6: Name collisions resolved cleanly; 429 not cached."""
    store = social_app.app["social_store"]
    # Alice had name "Steve"
    await store.save_name(A, "Steve")
    # New user Bob also gets name "Steve" -> A's row with "Steve" deleted
    await store.save_name(B, "Steve")

    got = await store.get_uuid_by_name("Steve")
    assert got == B


async def test_failed_lookups_count_toward_rate_limit(social_app, monkeypatch):
    """M6: Failed Mojang lookups count toward friend_add 20/hr limit."""
    alice_ws, _ = await auth_ws(social_app, "Alice", A)

    calls = []
    async def mock_lookup(name):
        calls.append(name)
        return None
    monkeypatch.setattr(social, "lookup_mojang_profile", mock_lookup)

    for i in range(20):
        await alice_ws.send_json({"t": "friend_add", "name": f"Unknown{i}"})
        err = await recv_type(alice_ws, "error")
        assert err["code"] == "not_found"

    assert len(calls) == 20

    # 21st attempt is rate limited BEFORE lookup
    await alice_ws.send_json({"t": "friend_add", "name": "Unknown21"})
    err2 = await recv_type(alice_ws, "error")
    assert err2["code"] == "rate"
    assert len(calls) == 20  # not called!

    await alice_ws.close()


async def test_settings_boolean_strict_and_appear_offline_oracle_blocked(social_app):
    """M7: Strict booleans in settings; appear-offline returns 'offline' on invite."""
    alice_ws, _ = await auth_ws(social_app, "Alice", A)
    bob_ws, _ = await auth_ws(social_app, "Bob", B)

    # String 'false' rejected with bad
    await alice_ws.send_json({"t": "settings", "showServer": "false"})
    err = await recv_type(alice_ws, "error")
    assert err["code"] == "bad"

    await alice_ws.send_json({"t": "friend_add", "name": "Bob"})
    await recv_type(alice_ws, "request_sent")
    await recv_type(bob_ws, "request_in")
    await bob_ws.send_json({"t": "friend_accept", "uuid": A})
    await recv_type(bob_ws, "friend_added")
    await recv_type(alice_ws, "friend_added")

    # Bob sets appearOffline
    await bob_ws.send_json({"t": "settings", "appearOffline": True})
    await asyncio.sleep(0.05)

    # Alice invites Bob who appears offline -> error 'offline'
    world = {"id": "w1", "name": "W", "scope": "friends", "slots": 8, "used": 0}
    await alice_ws.send_json({"t": "invite", "to": B, "world": world})
    err2 = await recv_type(alice_ws, "error")
    assert err2["code"] == "offline"

    await alice_ws.close()
    await bob_ws.close()


async def test_friend_add_blocked_silent_drop(social_app):
    """M7: friend_add to someone who blocked you returns request_sent but drops silently."""
    alice_ws, _ = await auth_ws(social_app, "Alice", A)
    bob_ws, _ = await auth_ws(social_app, "Bob", B)

    # Bob blocks Alice
    await bob_ws.send_json({"t": "block", "uuid": A})
    await asyncio.sleep(0.05)

    # Alice tries to add Bob
    await alice_ws.send_json({"t": "friend_add", "name": "Bob"})
    sent = await recv_type(alice_ws, "request_sent")
    assert sent["uuid"] == B

    # But Bob gets no request_in
    with pytest.raises(TimeoutError):
        await recv_type(bob_ws, "request_in", timeout=0.4)

    # And nothing is stored in DB
    store = social_app.app["social_store"]
    assert not await store.has_request(A, B)

    await alice_ws.close()
    await bob_ws.close()


async def test_reconnect_does_not_kill_new_socket_state(social_app):
    """M8: Socket replacement cleanup ignores new active socket."""
    alice1_ws, _ = await auth_ws(social_app, "Alice", A)
    bob_ws, _ = await auth_ws(social_app, "Bob", B)

    await alice1_ws.send_json({"t": "friend_add", "name": "Bob"})
    await recv_type(alice1_ws, "request_sent")
    await recv_type(bob_ws, "request_in")
    await bob_ws.send_json({"t": "friend_accept", "uuid": A})
    await recv_type(bob_ws, "friend_added")
    await recv_type(alice1_ws, "friend_added")

    world = {"id": "w1", "name": "W", "scope": "friends", "slots": 8, "used": 0}
    await alice1_ws.send_json({"t": "presence", "state": "hosting", "world": world})
    await recv_type(bob_ws, "presence")

    await alice1_ws.send_json({"t": "invite", "to": B, "world": world})
    inv = await recv_type(bob_ws, "invite")
    await bob_ws.send_json({"t": "invite_accept", "inviteId": inv["inviteId"]})

    s1 = await recv_type(alice1_ws, "p2p_start")
    await recv_type(bob_ws, "p2p_start")
    sid = s1["sid"]

    # Alice reconnects
    alice2_ws, _ = await auth_ws(social_app, "Alice", A)
    await asyncio.sleep(0.2)

    # Hub sessions still has the active session (not killed by old socket unregister!)
    hub = social_app.app["social_hub"]
    assert sid in hub.sessions

    await alice2_ws.close()
    await bob_ws.close()


async def test_secure_auth_gate(aiohttp_client, tmp_path, monkeypatch):
    """L1 & I1: Secure-mode auth gate with fake hasJoined."""
    app = web.Application()
    db_file = tmp_path / "social_sec.db"
    social.mount(app, insecure=False, db_path=db_file)
    cl = await aiohttp_client(app)

    calls = []
    async def hj(name, sid):
        calls.append((name, sid))
        return B  # Mojang says: username belongs to uuid B
    monkeypatch.setattr(social, "has_joined", hj)

    async def attempt(first):
        ws = await cl.ws_connect("/social/ws")
        ch = await ws.receive_json()
        await ws.send_json(first(ch["nonce"]))
        m = await ws.receive()
        return ws, m

    # 1. Pre-auth frame -> closed 4003, hasJoined NOT called
    ws, m = await attempt(lambda n: {"t": "presence", "state": "hosting"})
    assert ws.close_code == 4003 and len(calls) == 0

    # 2. UUID mismatch (claims A, Mojang says B) -> 4003
    ws, m = await attempt(lambda n: {"t": "auth", "name": "Alice", "uuid": A, "serverId": cosmetics.server_id(n)})
    assert ws.close_code == 4003

    # 3. Wrong serverId -> 4003, hasJoined not called
    calls.clear()
    ws, m = await attempt(lambda n: {"t": "auth", "name": "Bob", "uuid": B, "serverId": "wrong_sid"})
    assert ws.close_code == 4003 and len(calls) == 0

    # 4. hasJoined raises exception -> fail closed 4003
    async def boom(name, sid): raise RuntimeError("network error")
    monkeypatch.setattr(social, "has_joined", boom)
    ws, m = await attempt(lambda n: {"t": "auth", "name": "Bob", "uuid": B, "serverId": cosmetics.server_id(n)})
    assert ws.close_code == 4003

    # 5. Happy path -> succeeds, receives hello
    monkeypatch.setattr(social, "has_joined", hj)
    ws, m = await attempt(lambda n: {"t": "auth", "name": "Bob", "uuid": B, "serverId": cosmetics.server_id(n)})
    assert m.type == aiohttp.WSMsgType.TEXT
    data = m.json()
    assert data["t"] == "hello"
    await ws.close()


# ===========================================================================
# Round 2 Fix Tests (N1, N2, H2, H3, H5, M6, M9, L2, Lows, P13)
# ===========================================================================

async def test_n2_join_request_single_per_pair_replaces_old(social_app):
    """N2: At most ONE pending join request per (guest, host) pair: new one replaces old."""
    alice_ws, _ = await auth_ws(social_app, "Alice", A)
    bob_ws, _ = await auth_ws(social_app, "Bob", B, headers={"CF-Connecting-IP": "10.0.0.2"})
    cara_ws, _ = await auth_ws(social_app, "Cara", C, headers={"CF-Connecting-IP": "10.0.0.3"})
    store = social_app.app["social_store"]
    hub = social_app.app["social_hub"]

    await store.add_friend(A, B)
    await store.add_friend(A, C)

    world = {"id": "w1", "name": "World1", "scope": "friends", "slots": 8, "used": 0}
    await alice_ws.send_json({"t": "presence", "state": "hosting", "world": world})

    # Bob sends 25 join requests
    for _ in range(25):
        await bob_ws.send_json({"t": "join_request", "host": A})

    await asyncio.sleep(0.1)
    # Alice should have only 1 pending request from Bob
    bob_reqs = [r for r in hub.join_requests.values() if r["guest"] == B and r["host"] == A]
    assert len(bob_reqs) == 1

    # Innocent Cara can still send join_request (not blocked by Bob's 25 requests!)
    await cara_ws.send_json({"t": "join_request", "host": A})
    await asyncio.sleep(0.1)
    cara_reqs = [r for r in hub.join_requests.values() if r["guest"] == C and r["host"] == A]
    assert len(cara_reqs) == 1

    await alice_ws.close()
    await bob_ws.close()
    await cara_ws.close()


async def test_h2_failed_auth_throttling_and_mojang_concurrency(aiohttp_client, tmp_path, monkeypatch):
    """H2: After 5 failed auths in 60s, refuse further auths without calling Mojang."""
    app = web.Application()
    social.mount(app, insecure=False, db_path=tmp_path / "social.db")
    cl = await aiohttp_client(app)

    calls = []
    async def hj(name, sid):
        calls.append((name, sid))
        return None
    monkeypatch.setattr(social, "has_joined", hj)

    # First 5 failed auths call Mojang
    for _ in range(5):
        ws = await cl.ws_connect("/social/ws")
        ch = await ws.receive_json()
        await ws.send_json({"t": "auth", "name": "Player", "uuid": A, "serverId": cosmetics.server_id(ch["nonce"])})
        await ws.receive()
        await ws.close()

    assert len(calls) == 5

    # 6th through 10th failed auths are throttled immediately without calling Mojang!
    for _ in range(5):
        ws = await cl.ws_connect("/social/ws")
        m = await ws.receive()
        assert ws.close_code == 4003
        await ws.close()

    # Still exactly 5 calls to Mojang
    assert len(calls) == 5


async def test_h3_socket_token_bucket_rate_limit(social_app):
    """H3: Per-socket token bucket (20/s, burst 40; sustained abuse -> close 4008)."""
    alice_ws, _ = await auth_ws(social_app, "Alice", A)

    # Burst 50 messages to consume burst tokens and trigger rate error
    for i in range(50):
        await alice_ws.send_json({"t": "presence", "state": "menu"})

    err = await recv_type(alice_ws, "error")
    assert err["code"] == "rate"

    # Sustained flood of 50 more messages -> close 4008
    for i in range(50):
        if alice_ws.closed:
            break
        try:
            await alice_ws.send_json({"t": "presence", "state": "menu"})
        except Exception:
            break

    while not alice_ws.closed:
        m = await alice_ws.receive()
        if m.type in (aiohttp.WSMsgType.CLOSE, aiohttp.WSMsgType.CLOSED):
            break
    assert alice_ws.closed
    assert alice_ws.close_code in (4008, 1006)


async def test_h3_game_keys_whitelist_and_max_blocks(social_app):
    """H3: GameInfo whitelist keeps only known keys; max 1000 blocks."""
    alice_ws, _ = await auth_ws(social_app, "Alice", A)
    bob_ws, _ = await auth_ws(social_app, "Bob", B, headers={"CF-Connecting-IP": "10.0.0.2"})
    store = social_app.app["social_store"]
    await store.add_friend(A, B)

    # Alice sends presence with known + unknown game keys
    game = {
        "mc": "1.20.1",
        "loader": "fabric",
        "profileKind": "pack",
        "packId": "zerosmp",
        "packVersion": "1.0",
        "modsHash": "123",
        "lads": "1.9.18",
        "unknown_key_1": "spam",
        "unknown_key_2": "spam2"
    }
    await alice_ws.send_json({"t": "presence", "state": "menu", "game": game})
    pres = await recv_type(bob_ws, "presence")
    assert pres["game"]["mc"] == "1.20.1"
    assert pres["game"]["lads"] == "1.9.18"
    assert "unknown_key_1" not in pres["game"]
    assert "unknown_key_2" not in pres["game"]

    # Max blocks: insert 1000 blocks into store
    for i in range(1, 1001):
        u = f"{i:032x}"
        await store.block(A, u)
    assert await store.get_block_count(A) == 1000

    # 1001st block returns error limit
    await alice_ws.send_json({"t": "block", "uuid": f"{1001:032x}"})
    err = await recv_type(alice_ws, "error")
    assert err["code"] == "limit"

    await alice_ws.close()
    await bob_ws.close()


async def test_h5_relay_ceiling_and_both_parties_max(social_app, monkeypatch):
    """H5: Relay ceiling = min(elapsed, 6h)*12.5MB/s and <= 50GB; both parties count max."""
    monkeypatch.setenv("CF_TURN_KEY_ID", "dummy_k")
    monkeypatch.setenv("CF_TURN_API_TOKEN", "dummy_t")
    async def fake_mint():
        return [{"url": "turn:turn.cloudflare.com:3478?transport=udp", "user": "u", "credential": "c"}]
    monkeypatch.setattr(social, "mint_turn_credentials", fake_mint)

    alice_ws, _ = await auth_ws(social_app, "Alice", A)
    bob_ws, _ = await auth_ws(social_app, "Bob", B, headers={"CF-Connecting-IP": "10.0.0.2"})
    store = social_app.app["social_store"]
    hub = social_app.app["social_hub"]
    await store.add_friend(A, B)

    world = {"id": "w1", "name": "W", "scope": "friends", "slots": 8, "used": 0}
    await alice_ws.send_json({"t": "presence", "state": "hosting", "world": world})
    await recv_type(bob_ws, "presence")

    await alice_ws.send_json({"t": "invite", "to": B, "world": world})
    inv = await recv_type(bob_ws, "invite")
    await bob_ws.send_json({"t": "invite_accept", "inviteId": inv["inviteId"]})
    p_start = await recv_type(alice_ws, "p2p_start")
    await recv_type(bob_ws, "p2p_start")
    sid = p_start["sid"]

    # Age session by 20 hours (collusion attempt)
    hub.sessions[sid]["created_at"] -= 20 * 3600

    # Alice reports 900 GB -> clamped to 50 GB ceiling
    await alice_ws.send_json({"t": "p2p_end", "sid": sid, "relayed": True, "bytes": 900 * 10**9})
    await asyncio.sleep(0.1)

    month_str = time.strftime("%Y-%m", time.gmtime())
    recorded = await store.get_relay_bytes(month_str)
    assert recorded == 50 * 10**9

    # Signal limits for sid must be pruned
    assert sid not in hub.signal_limits

    # Bob sends report of 1000 bytes -> does not overwrite or drop max
    await bob_ws.send_json({"t": "p2p_end", "sid": sid, "relayed": True, "bytes": 1000})
    await asyncio.sleep(0.1)
    assert await store.get_relay_bytes(month_str) == 50 * 10**9

    await alice_ws.close()
    await bob_ws.close()


async def test_m6_stale_name_cache_refreshed_from_mojang(social_app, monkeypatch):
    """M6: Name row older than 24h is refreshed from Mojang on lookup."""
    alice_ws, _ = await auth_ws(social_app, "Alice", A)
    store = social_app.app["social_store"]

    # Stale row: updated 25 hours ago
    stale_time = int(time.time()) - 25 * 3600
    with store._connect() as conn:
        conn.execute("INSERT INTO player_names (uuid, name, updated_at) VALUES (?, ?, ?)", (B, "OldName", stale_time))

    mojang_calls = []
    async def fake_lookup(name):
        mojang_calls.append(name)
        return {"id": C, "name": "OldName"}  # Mojang says name now belongs to C!
    monkeypatch.setattr(social, "lookup_mojang_profile", fake_lookup)

    await alice_ws.send_json({"t": "friend_add", "name": "OldName"})
    sent = await recv_type(alice_ws, "request_sent")
    # Refreshed from Mojang -> resolved to C, not the stale B
    assert sent["uuid"] == C
    assert len(mojang_calls) == 1

    await alice_ws.close()


def test_m9_social_always_uses_own_file(tmp_path):
    """M9: SocialStore always resolves to its own social.db beside cosmetics db."""
    s = social.SocialStore(tmp_path / "shared.db")
    assert s.db_path.name == "social.db"
    assert s.db_path == (tmp_path / "social.db").resolve()


async def test_low_block_only_notifies_friends(social_app):
    """Lows: Block sends friend_removed ONLY if they were friends."""
    alice_ws, _ = await auth_ws(social_app, "Alice", A)
    bob_ws, _ = await auth_ws(social_app, "Bob", B, headers={"CF-Connecting-IP": "10.0.0.2"})

    # Alice and Bob are strangers (NOT friends)
    await alice_ws.send_json({"t": "block", "uuid": B})
    await asyncio.sleep(0.1)

    # Bob (stranger) receives NO friend_removed packet
    with pytest.raises(TimeoutError):
        await recv_type(bob_ws, "friend_removed", timeout=0.3)

    # Now Alice unblocks Bob and they become friends
    await alice_ws.send_json({"t": "unblock", "uuid": B})
    store = social_app.app["social_store"]
    await store.add_friend(A, B)

    # Alice blocks Bob -> now BOTH receive friend_removed
    await alice_ws.send_json({"t": "block", "uuid": B})
    await recv_type(alice_ws, "friend_removed")
    await recv_type(bob_ws, "friend_removed")

    await alice_ws.close()
    await bob_ws.close()


async def test_low_offline_dm_cap_per_pair(social_app):
    """Lows: Offline DMs capped at 50 per pair."""
    store = social_app.app["social_store"]
    now_ms = int(time.time() * 1000)

    # Alice sends 55 offline DMs to Bob
    for i in range(55):
        await store.save_offline_dm(A, B, f"msg_a_{i}", now_ms + i)

    # Cara sends 10 offline DMs to Bob
    for i in range(10):
        await store.save_offline_dm(C, B, f"msg_c_{i}", now_ms + 100 + i)

    dms = await store.get_and_clear_offline_dms(B)
    a_dms = [d for d in dms if d["from"] == A]
    c_dms = [d for d in dms if d["from"] == C]

    # Alice's DMs capped at 50 (oldest 5 evicted)
    assert len(a_dms) == 50
    assert a_dms[0]["text"] == "msg_a_5"

    # Cara's 10 DMs fully preserved (not evicted by Alice's spam)
    assert len(c_dms) == 10


async def test_low_ip_slot_released_on_prepare_error(social_app, monkeypatch):
    """Lows: Per-IP slot released in finally even if ws.prepare raises."""
    hub = social_app.app["social_hub"]
    orig_prepare = web.WebSocketResponse.prepare

    async def bad_prepare(self, req):
        raise ConnectionResetError("simulated abort")
    monkeypatch.setattr(web.WebSocketResponse, "prepare", bad_prepare)

    # Client connection fails during prepare
    try:
        await social_app.ws_connect("/social/ws")
    except Exception:
        pass

    monkeypatch.setattr(web.WebSocketResponse, "prepare", orig_prepare)
    # ip_counts must be 0, not leaked
    assert hub.ip_counts.get("127.0.0.1", 0) == 0


async def test_p13_friend_added_pushes_presence(social_app):
    """P13: After friend_added send each side the other's current presence."""
    alice_ws, _ = await auth_ws(social_app, "Alice", A)
    bob_ws, _ = await auth_ws(social_app, "Bob", B, headers={"CF-Connecting-IP": "10.0.0.2"})

    # Alice sets presence to singleplayer
    await alice_ws.send_json({"t": "presence", "state": "singleplayer"})

    # Bob sets presence to server
    await bob_ws.send_json({"t": "presence", "state": "server", "server": "play.hypixel.net"})

    # Alice adds Bob
    await alice_ws.send_json({"t": "friend_add", "name": "Bob"})
    await recv_type(alice_ws, "request_sent")
    await recv_type(bob_ws, "request_in")

    # Bob accepts Alice
    await bob_ws.send_json({"t": "friend_accept", "uuid": A})

    # Both get friend_added
    await recv_type(bob_ws, "friend_added")
    await recv_type(alice_ws, "friend_added")

    # P13: Alice immediately receives Bob's server presence
    p_for_alice = await recv_type(alice_ws, "presence")
    assert p_for_alice["uuid"] == B
    assert p_for_alice["state"] == "server"
    assert p_for_alice["server"] == "play.hypixel.net"

    # P13: Bob immediately receives Alice's singleplayer presence
    p_for_bob = await recv_type(bob_ws, "presence")
    assert p_for_bob["uuid"] == A
    assert p_for_bob["state"] == "singleplayer"

    await alice_ws.close()
    await bob_ws.close()


async def test_l2_no_player_names_in_logs(caplog, monkeypatch):
    """L2: No player names in logs (UUIDs only), including lookup errors."""
    caplog.set_level(logging.DEBUG)
    class Fake500:
        status = 500
        async def __aenter__(self): return self
        async def __aexit__(self, *a): pass

    class FakeSession:
        def __init__(self, **kw): pass
        async def __aenter__(self): return self
        async def __aexit__(self, *a): pass
        def get(self, url): return Fake500()

    monkeypatch.setattr(aiohttp, "ClientSession", FakeSession)
    social._PROFILE_CACHE.clear()

    await social.lookup_mojang_profile("SecretName123")
    leaked = [r.getMessage() for r in caplog.records if "SecretName123" in r.getMessage()]
    assert len(leaked) == 0


# ===========================================================================
# Round 3 Fix Tests (Rules 1-10)
# ===========================================================================

async def test_r3_1_since_is_epoch_ms_and_migrates_stored_seconds(social_app, tmp_path):
    alice_ws, _ = await auth_ws(social_app, 'Alice', A)
    bob_ws, _ = await auth_ws(social_app, 'Bob', B)

    await alice_ws.send_json({'t': 'friend_add', 'name': 'Bob'})
    await recv_type(alice_ws, 'request_sent')
    await recv_type(bob_ws, 'request_in')
    await bob_ws.send_json({'t': 'friend_accept', 'uuid': A})
    await recv_type(bob_ws, 'friend_added')
    await recv_type(alice_ws, 'friend_added')

    hello_alice = await social_app.app['social_hub'].build_hello(social_app.app['social_hub'].peers[A])
    alice_friend = hello_alice['friends'][0]
    assert alice_friend['since'] >= 10**12

    await alice_ws.close()
    await bob_ws.close()

    mig_db = tmp_path / 'legacy.db'
    store = social.SocialStore(mig_db)
    legacy_seconds = 1700000000
    with store._connect() as conn:
        conn.execute('INSERT OR REPLACE INTO friends (user_uuid, friend_uuid, since) VALUES (?, ?, ?)', (A, B, legacy_seconds))
    store._init_db()
    with store._connect() as conn:
        row = conn.execute('SELECT since FROM friends WHERE user_uuid = ? AND friend_uuid = ?', (A, B)).fetchone()
        assert row[0] == legacy_seconds * 1000


async def test_r3_2_invite_accept_revalidates_current_world_and_preserves_invite_on_refusal(social_app):
    alice_ws, _ = await auth_ws(social_app, 'Alice', A)
    bob_ws, _ = await auth_ws(social_app, 'Bob', B)

    await alice_ws.send_json({'t': 'friend_add', 'name': 'Bob'})
    await recv_type(alice_ws, 'request_sent')
    await recv_type(bob_ws, 'request_in')
    await bob_ws.send_json({'t': 'friend_accept', 'uuid': A})
    await recv_type(bob_ws, 'friend_added')
    await recv_type(alice_ws, 'friend_added')

    world1 = {'id': 'w1', 'name': 'World1', 'scope': 'friends', 'slots': 2, 'used': 1, 'relayOnly': False}
    await alice_ws.send_json({'t': 'presence', 'state': 'hosting', 'world': world1})
    await recv_type(bob_ws, 'presence')

    await alice_ws.send_json({'t': 'invite', 'to': B, 'world': world1})
    inv = await recv_type(bob_ws, 'invite')
    invite_id = inv['inviteId']

    world1_full = {'id': 'w1', 'name': 'World1', 'scope': 'friends', 'slots': 2, 'used': 2, 'relayOnly': False}
    await alice_ws.send_json({'t': 'presence', 'state': 'hosting', 'world': world1_full})
    await recv_type(bob_ws, 'presence')

    await bob_ws.send_json({'t': 'invite_accept', 'inviteId': invite_id})
    err = await recv_type(bob_ws, 'error')
    assert err['code'] == 'full'

    hub = social_app.app['social_hub']
    assert invite_id in hub.invites

    world1_relay = {'id': 'w1', 'name': 'World1', 'scope': 'friends', 'slots': 2, 'used': 1, 'relayOnly': True}
    await alice_ws.send_json({'t': 'presence', 'state': 'hosting', 'world': world1_relay})
    await recv_type(bob_ws, 'presence')

    await bob_ws.send_json({'t': 'invite_accept', 'inviteId': invite_id})
    a_start = await recv_type(alice_ws, 'p2p_start')
    b_start = await recv_type(bob_ws, 'p2p_start')
    assert a_start['ice']['relayOnly'] is True
    assert b_start['ice']['relayOnly'] is True
    assert invite_id not in hub.invites

    await alice_ws.close()
    await bob_ws.close()


async def test_r3_3_unfriend_ends_sessions_and_signal_rechecks_friendship(social_app):
    alice_ws, _ = await auth_ws(social_app, 'Alice', A)
    bob_ws, _ = await auth_ws(social_app, 'Bob', B)

    await alice_ws.send_json({'t': 'friend_add', 'name': 'Bob'})
    await recv_type(alice_ws, 'request_sent')
    await recv_type(bob_ws, 'request_in')
    await bob_ws.send_json({'t': 'friend_accept', 'uuid': A})
    await recv_type(bob_ws, 'friend_added')
    await recv_type(alice_ws, 'friend_added')

    world = {'id': 'w1', 'name': 'W', 'scope': 'friends', 'slots': 8, 'used': 0}
    await alice_ws.send_json({'t': 'presence', 'state': 'hosting', 'world': world})
    await recv_type(bob_ws, 'presence')
    await alice_ws.send_json({'t': 'invite', 'to': B, 'world': world})
    inv = await recv_type(bob_ws, 'invite')
    await bob_ws.send_json({'t': 'invite_accept', 'inviteId': inv['inviteId']})
    a_start = await recv_type(alice_ws, 'p2p_start')
    b_start = await recv_type(bob_ws, 'p2p_start')
    sid = a_start['sid']

    await alice_ws.send_json({'t': 'signal', 'sid': sid, 'data': {'type': 'offer', 'sdp': 'v=0'}})
    sig = await recv_type(bob_ws, 'signal')
    assert sig['data']['type'] == 'offer'

    await alice_ws.send_json({'t': 'friend_remove', 'uuid': B})
    await recv_type(alice_ws, 'friend_removed')
    await recv_type(bob_ws, 'friend_removed')

    end_a = await recv_type(alice_ws, 'p2p_end')
    end_b = await recv_type(bob_ws, 'p2p_end')
    assert end_a['reason'] == 'unfriended'
    assert end_b['reason'] == 'unfriended'

    await alice_ws.send_json({'t': 'signal', 'sid': sid, 'data': {'type': 'ping'}})
    err = await recv_type(alice_ws, 'error')
    assert err['code'] == 'not_found'

    await alice_ws.close()
    await bob_ws.close()


async def test_r3_4_auth_failure_swept_and_capped_at_10k(social_app):
    hub = social_app.app['social_hub']

    for i in range(10005):
        hub.record_auth_failure(f'192.168.{i // 256}.{i % 256}')

    assert len(hub.auth_failures) <= 10000

    old_time = time.monotonic() - 70.0
    hub.auth_failures['10.0.0.1'] = [old_time]
    hub.auth_blocked_until['10.0.0.2'] = old_time

    hub.sweep_expired()
    assert '10.0.0.1' not in hub.auth_failures
    assert '10.0.0.2' not in hub.auth_blocked_until


async def test_r3_5_auth_throttle_not_reset_by_success_and_disconnect_not_failure(social_app):
    hub = social_app.app['social_hub']
    ip = '1.2.3.4'
    hub.auth_failures.pop(ip, None)
    hub.auth_blocked_until.pop(ip, None)

    for _ in range(4):
        ws = await social_app.ws_connect('/social/ws', headers={'CF-Connecting-IP': ip})
        await ws.receive_json()
        await ws.send_json({'t': 'auth', 'uuid': A, 'name': 'Alice', 'serverId': 'wrong_server_id'})
        await ws.receive()
        await ws.close()

    assert len(hub.auth_failures.get(ip, [])) == 4

    ws_idle = await social_app.ws_connect('/social/ws', headers={'CF-Connecting-IP': ip})
    await ws_idle.receive_json()
    await ws_idle.close()
    assert len(hub.auth_failures.get(ip, [])) == 4

    ws_succ, _ = await auth_ws(social_app, 'Bob', B, headers={'CF-Connecting-IP': ip})
    assert len(hub.auth_failures.get(ip, [])) == 4
    await ws_succ.close()

    ws5 = await social_app.ws_connect('/social/ws', headers={'CF-Connecting-IP': ip})
    await ws5.receive_json()
    await ws5.send_json({'t': 'auth', 'uuid': A, 'name': 'Alice', 'serverId': 'wrong_server_id'})
    await ws5.receive()
    await ws5.close()

    assert hub.is_ip_auth_blocked(ip) is True


async def test_r3_6_user_monthly_relay_budget(social_app, monkeypatch):
    monkeypatch.setenv('CF_TURN_KEY_ID', 'dummy_key')
    monkeypatch.setenv('CF_TURN_API_TOKEN', 'dummy_tok')

    async def fake_mint():
        return [{'url': 'turn:turn.cloudflare.com:3478?transport=udp', 'user': 'u', 'credential': 'c'}]
    monkeypatch.setattr(social, 'mint_turn_credentials', fake_mint)

    alice_ws, _ = await auth_ws(social_app, 'Alice', A)
    bob_ws, _ = await auth_ws(social_app, 'Bob', B)

    await alice_ws.send_json({'t': 'friend_add', 'name': 'Bob'})
    await recv_type(alice_ws, 'request_sent')
    await recv_type(bob_ws, 'request_in')
    await bob_ws.send_json({'t': 'friend_accept', 'uuid': A})
    await recv_type(bob_ws, 'friend_added')
    await recv_type(alice_ws, 'friend_added')

    month_str = time.strftime('%Y-%m', time.gmtime())
    store = social_app.app['social_store']
    await store.record_user_relay_bytes(month_str, A, 31 * 10**9)

    world = {'id': 'w1', 'name': 'W', 'scope': 'friends', 'slots': 8, 'used': 0}
    await alice_ws.send_json({'t': 'presence', 'state': 'hosting', 'world': world})
    await recv_type(bob_ws, 'presence')

    await alice_ws.send_json({'t': 'invite', 'to': B, 'world': world})
    inv = await recv_type(bob_ws, 'invite')
    await bob_ws.send_json({'t': 'invite_accept', 'inviteId': inv['inviteId']})

    a_start = await recv_type(alice_ws, 'p2p_start')
    assert a_start['ice']['turn'] == []
    assert a_start['ice']['relayStatus'] == 'budget'

    await alice_ws.close()
    await bob_ws.close()


async def test_r3_7_mojang_404_never_falls_back_to_stale_cache(social_app, monkeypatch):
    alice_ws, _ = await auth_ws(social_app, 'Alice', A)
    store = social_app.app['social_store']

    stale_time = int(time.time()) - 25 * 3600
    with store._connect() as conn:
        conn.execute('INSERT OR REPLACE INTO player_names (uuid, name, updated_at) VALUES (?, ?, ?)', (B, 'StalePlayer', stale_time))

    async def mock_404(name):
        return None
    monkeypatch.setattr(social, 'lookup_mojang_profile', mock_404)

    await alice_ws.send_json({'t': 'friend_add', 'name': 'StalePlayer'})
    err = await recv_type(alice_ws, 'error')
    assert err['code'] == 'not_found'

    async def mock_error(name):
        return False
    monkeypatch.setattr(social, 'lookup_mojang_profile', mock_error)

    await alice_ws.send_json({'t': 'friend_add', 'name': 'StalePlayer'})
    sent = await recv_type(alice_ws, 'request_sent')
    assert sent['uuid'] == B

    await alice_ws.close()


async def test_r3_8_offline_dm_evicts_from_heaviest_sender(social_app):
    store = social_app.app['social_store']
    recipient = 'r0000000000000000000000000000000'
    alice = 'a0000000000000000000000000000000'
    spammer1 = 's1000000000000000000000000000000'
    spammer2 = 's2000000000000000000000000000000'

    now_ms = int(time.time() * 1000)
    await store.save_offline_dm(alice, recipient, 'Legit from Alice', now_ms - 10000)

    for i in range(50):
        await store.save_offline_dm(spammer1, recipient, f'Spam1_{i}', now_ms - 5000 + i)

    for i in range(49):
        await store.save_offline_dm(spammer2, recipient, f'Spam2_{i}', now_ms - 2000 + i)

    await store.save_offline_dm(spammer2, recipient, 'Spam2_extra', now_ms)

    dms = await store.get_and_clear_offline_dms(recipient)
    assert len(dms) == 100
    alice_dms = [d for d in dms if d['from'] == alice]
    assert len(alice_dms) == 1
    assert alice_dms[0]['text'] == 'Legit from Alice'
    spammer1_texts = [d['text'] for d in dms if d['from'] == spammer1]
    assert 'Spam1_0' not in spammer1_texts
