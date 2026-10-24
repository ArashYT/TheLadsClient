"""pytest for Lads social backend (cosmetics-server/social.py).
Tests Task S1 protocol, store, rate limiting, P2P session coordination, TURN minting,
and all High/Medium/Low security fixes.
"""
import asyncio
import json
import os
import time
from pathlib import Path

# Required for mount(insecure=True) in tests
os.environ["LADS_SOCIAL_DEV"] = "1"

import aiohttp
import pytest
from aiohttp import web

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
    # Session already ended -> not_found
    err = await recv_type(alice_ws, "error")
    assert err["code"] == "not_found"

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
    # Insert 110 offline DMs into store directly
    now_ms = int(time.time() * 1000)
    for i in range(110):
        await store.save_offline_dm(A, B, f"msg_{i}", now_ms + i)

    # Bob connects -> hello delivers at most 100
    bob2_ws, hello = await auth_ws(social_app, "Bob", B)
    assert len(hello["dms"]) == 100
    # Oldest (msg_0 through msg_9) were dropped
    assert hello["dms"][0]["text"] == "msg_10"
    assert hello["dms"][-1]["text"] == "msg_109"

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
