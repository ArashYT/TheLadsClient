"""pytest for Lads social backend (cosmetics-server/social.py).
Tests Task S1 protocol, store, rate limiting, P2P session coordination, and TURN minting.
"""
import asyncio
import os
import time
from pathlib import Path

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


async def auth_ws(client, name: str, uuid: str):
    ws = await client.ws_connect("/social/ws")
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

    # Small pause to process
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
    assert err["code"] in ("not_found", "not_friend")

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

    # Now simulate TURN key present
    monkeypatch.setenv("CF_TURN_KEY_ID", "dummy_key_id")
    monkeypatch.setenv("CF_TURN_API_TOKEN", "dummy_token")

    async def fake_mint():
        return [{"url": "turn:turn.cloudflare.com:3478", "user": "test_user", "credential": "test_cred"}]

    monkeypatch.setattr(social, "mint_turn_credentials", fake_mint)

    # Next invite with key -> turn should have creds
    await alice_ws.send_json({"t": "invite", "to": B, "world": world})
    inv2 = await recv_type(bob_ws, "invite")
    await bob_ws.send_json({"t": "invite_accept", "inviteId": inv2["inviteId"]})

    a_p2p2 = await recv_type(alice_ws, "p2p_start")
    b_p2p2 = await recv_type(bob_ws, "p2p_start")
    assert len(a_p2p2["ice"]["turn"]) == 1
    assert a_p2p2["ice"]["turn"][0]["user"] == "test_user"

    # End session reporting 950 GB usage
    month_str = time.strftime("%Y-%m")
    store = social_app.app["social_store"]
    await store.record_relay_bytes(month_str, 950 * 1024 * 1024 * 1024)

    # Now budget is exceeded -> turn credentials must be empty
    await alice_ws.send_json({"t": "invite", "to": B, "world": world})
    inv3 = await recv_type(bob_ws, "invite")
    await bob_ws.send_json({"t": "invite_accept", "inviteId": inv3["inviteId"]})

    a_p2p3 = await recv_type(alice_ws, "p2p_start")
    assert a_p2p3["ice"]["turn"] == []

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

    # Alice disables showServer
    await alice_ws.send_json({"t": "settings", "showServer": False, "appearOffline": False})
    await asyncio.sleep(0.05)

    # Alice joins server
    await alice_ws.send_json({"t": "presence", "state": "server", "server": "mc.hypixel.net"})
    pres1 = await recv_type(bob_ws, "presence")
    assert pres1["state"] == "server"
    assert "server" not in pres1 or pres1["server"] is None

    # Alice enables showServer
    await alice_ws.send_json({"t": "settings", "showServer": True, "appearOffline": False})
    await asyncio.sleep(0.05)

    await alice_ws.send_json({"t": "presence", "state": "server", "server": "mc.hypixel.net"})
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


async def test_friend_remove_notifies_both(social_app):
    alice_ws, _ = await auth_ws(social_app, "Alice", A)
    bob_ws, _ = await auth_ws(social_app, "Bob", B)

    await alice_ws.send_json({"t": "friend_add", "name": "Bob"})
    await recv_type(alice_ws, "request_sent")
    await recv_type(bob_ws, "request_in")
    await bob_ws.send_json({"t": "friend_accept", "uuid": A})
    await recv_type(bob_ws, "friend_added")
    await recv_type(alice_ws, "friend_added")

    # Alice removes Bob
    await alice_ws.send_json({"t": "friend_remove", "uuid": B})
    a_rem = await recv_type(alice_ws, "friend_removed")
    assert a_rem["uuid"] == B

    b_rem = await recv_type(bob_ws, "friend_removed")
    assert b_rem["uuid"] == A

    await alice_ws.close()
    await bob_ws.close()


async def test_nick_and_friend_decline(social_app):
    alice_ws, _ = await auth_ws(social_app, "Alice", A)
    bob_ws, _ = await auth_ws(social_app, "Bob", B)

    # Alice adds Bob
    await alice_ws.send_json({"t": "friend_add", "name": "Bob"})
    await recv_type(alice_ws, "request_sent")
    await recv_type(bob_ws, "request_in")

    # Bob declines
    await bob_ws.send_json({"t": "friend_decline", "uuid": A})
    await asyncio.sleep(0.05)

    store = social_app.app["social_store"]
    assert not await store.has_request(A, B)

    # Now Alice and Bob become friends
    await bob_ws.send_json({"t": "friend_add", "name": "Alice"})
    await recv_type(bob_ws, "request_sent")
    await recv_type(alice_ws, "request_in")
    await alice_ws.send_json({"t": "friend_accept", "uuid": B})
    await recv_type(alice_ws, "friend_added")
    await recv_type(bob_ws, "friend_added")

    # Alice sets nick for Bob
    await alice_ws.send_json({"t": "nick", "uuid": B, "nick": "Bobby"})
    await asyncio.sleep(0.05)

    friends = await store.get_friends(A)
    bob_entry = next(f for f in friends if f["uuid"] == B)
    assert bob_entry["nick"] == "Bobby"

    await alice_ws.close()
    await bob_ws.close()


async def test_dm_rate_limit_and_length_cap(social_app):
    alice_ws, _ = await auth_ws(social_app, "Alice", A)
    bob_ws, _ = await auth_ws(social_app, "Bob", B)

    # Become friends
    await alice_ws.send_json({"t": "friend_add", "name": "Bob"})
    await recv_type(alice_ws, "request_sent")
    await recv_type(bob_ws, "request_in")
    await bob_ws.send_json({"t": "friend_accept", "uuid": A})
    await recv_type(bob_ws, "friend_added")
    await recv_type(alice_ws, "friend_added")

    # DM > 256 characters -> error limit
    long_msg = "x" * 257
    await alice_ws.send_json({"t": "dm", "to": B, "text": long_msg})
    err = await recv_type(alice_ws, "error")
    assert err["code"] == "limit"

    # Send 30 valid DMs
    for i in range(30):
        await alice_ws.send_json({"t": "dm", "to": B, "text": f"m{i}"})
        dm_in = await recv_type(bob_ws, "dm")
        assert dm_in["text"] == f"m{i}"

    # 31st DM within 1 min -> error rate
    await alice_ws.send_json({"t": "dm", "to": B, "text": "rate_exceeded"})
    err2 = await recv_type(alice_ws, "error")
    assert err2["code"] == "rate"

    await alice_ws.close()
    await bob_ws.close()


async def test_join_request_accept_flow(social_app):
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

    # Bob sends join request
    await bob_ws.send_json({"t": "join_request", "host": A})
    req = await recv_type(alice_ws, "join_request")
    assert req["from"] == B
    req_id = req["reqId"]

    # Alice accepts
    await alice_ws.send_json({"t": "join_accept", "reqId": req_id})

    # Both receive p2p_start
    a_p2p = await recv_type(alice_ws, "p2p_start")
    b_p2p = await recv_type(bob_ws, "p2p_start")
    assert a_p2p["sid"] == b_p2p["sid"]
    assert a_p2p["role"] == "host"
    assert b_p2p["role"] == "guest"

    await alice_ws.close()
    await bob_ws.close()


async def test_join_request_deny_flow(social_app):
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

    # Bob requests
    await bob_ws.send_json({"t": "join_request", "host": A})
    req = await recv_type(alice_ws, "join_request")
    req_id = req["reqId"]

    # Alice denies
    await alice_ws.send_json({"t": "join_deny", "reqId": req_id})
    denied = await recv_type(bob_ws, "join_denied")
    assert denied["reqId"] == req_id

    await alice_ws.close()
    await bob_ws.close()


async def test_p2p_end_notifies_peer_and_records_relay_bytes(social_app):
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
    await recv_type(bob_ws, "p2p_start")
    sid = a_p2p["sid"]

    # Bob ends session
    await bob_ws.send_json({"t": "p2p_end", "sid": sid, "reason": "left_world", "relayed": True, "bytes": 1048576})
    ended = await recv_type(alice_ws, "p2p_end")
    assert ended["sid"] == sid
    assert ended["reason"] == "left_world"

    # Verify relay bytes recorded
    store = social_app.app["social_store"]
    month_str = time.strftime("%Y-%m")
    assert await store.get_relay_bytes(month_str) == 1048576

    await alice_ws.close()
    await bob_ws.close()


async def test_max_friends_limit_500(social_app):
    alice_ws, _ = await auth_ws(social_app, "Alice", A)
    store = social_app.app["social_store"]

    # Populate 500 friends directly into store for Alice
    for i in range(1, 501):
        u = f"{i:032x}"
        await store.add_friend(A, u)

    assert await store.friend_count(A) == 500

    # Alice tries to add 501st friend
    await alice_ws.send_json({"t": "friend_add", "name": "User501"})
    err = await recv_type(alice_ws, "error")
    assert err["code"] == "limit"

    await alice_ws.close()


async def test_auth_failures_and_peer_replacement(social_app):
    # Missing auth
    ws = await social_app.ws_connect("/social/ws")
    await ws.receive_json()  # challenge
    await ws.send_json({"t": "ping"})
    msg = await ws.receive()
    assert msg.type in (aiohttp.WSMsgType.CLOSE, aiohttp.WSMsgType.CLOSED)

    # Valid connect for Alice
    alice1_ws, _ = await auth_ws(social_app, "Alice", A)

    # Connecting second socket for Alice should replace the first
    alice2_ws, _ = await auth_ws(social_app, "Alice", A)

    # First socket should be closed with code 4001
    close_msg = await alice1_ws.receive()
    assert close_msg.type in (aiohttp.WSMsgType.CLOSE, aiohttp.WSMsgType.CLOSED)
    assert close_msg.data == 4001

    await alice2_ws.close()
