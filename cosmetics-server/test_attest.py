"""Attest tokens (anti-spoof handshake, LadsClientAPI PROTOCOL.md section 6): the relay signs "Mojang proved this account to me"
for a game server's nonce. pytest -q test_attest.py"""
import base64

import pytest
from aiohttp import web
from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
from cryptography.hazmat.primitives.serialization import Encoding, NoEncryption, PrivateFormat

import cosmetics
from test_cosmetics import A, B, closed, fake_mojang  # noqa: F401 (fixture)

NONCE, SID = "0123456789abcdef" * 2, "fedcba9876543210" * 2
# The same key and token are in the plugin's AttestTest (Java): both sides must agree on every byte.
VECTOR_KEY = Ed25519PrivateKey.from_private_bytes(bytes(range(32)))
VECTOR_PUBLIC = "A6EHv/POEL4dcN0Y50vAmWfk1jCbpQ1fHdyGZBJVMbg="
VECTOR_TOKEN = "v1.2000000120.mneOUEZocqYGZghZLGLXfCSjdJrGOWBbiyGgAsAzf0yLUG9_kV_vMhbZjYRPZIUC0gif9MuIbT7yKFkoGZ5lCA"


def signature(token: str) -> bytes:
    version, exp, sig = token.split(".")
    assert version == "v1"
    return base64.urlsafe_b64decode(sig + "=" * (-len(sig) % 4))


def expiry(token: str) -> int:
    return int(token.split(".")[1])


def checks(token: str, key, uuid=A, nonce=NONCE, sid=SID) -> bool:
    try:
        key.public_key().verify(signature(token), cosmetics.attest_message(uuid, nonce, sid, expiry(token)))
        return True
    except InvalidSignature:
        return False


@pytest.fixture
def key_file(tmp_path):
    path = tmp_path / "attest.pem"
    path.write_bytes(VECTOR_KEY.private_bytes(Encoding.PEM, PrivateFormat.PKCS8, NoEncryption()))
    return path


async def relay(aiohttp_client, tmp_path, **kwargs):
    app = web.Application()
    cosmetics.setup(app, db_path=tmp_path / "cosmetics.db", **kwargs)
    return await aiohttp_client(app)


async def login(client, name="Alice", uuid=A):
    ws = await client.ws_connect("/cosmetics/ws")
    challenge = await ws.receive_json(timeout=2)
    await ws.send_json({"t": "auth", "name": name, "uuid": uuid, "serverId": cosmetics.server_id(challenge["nonce"])})
    return ws, challenge


def test_vector_matches_the_plugin():
    assert cosmetics.attest_public_key(VECTOR_KEY) == VECTOR_PUBLIC
    assert cosmetics.attest_message(A, NONCE, SID, 2000000120) == f"lads-attest-v1|{A}|{NONCE}|{SID}|2000000120".encode()
    assert cosmetics.attest_token(VECTOR_KEY, A, NONCE, SID, now=2000000000) == VECTOR_TOKEN


def test_token_is_bound_to_uuid_nonce_server_and_expiry():
    token = cosmetics.attest_token(VECTOR_KEY, A, NONCE, SID, now=1000)
    assert expiry(token) == 1000 + cosmetics.ATTEST_TTL
    assert checks(token, VECTOR_KEY)
    assert not checks(token, VECTOR_KEY, uuid=B)
    assert not checks(token, VECTOR_KEY, nonce=SID)
    assert not checks(token, VECTOR_KEY, sid=NONCE)
    assert not checks(token.replace(str(expiry(token)), str(expiry(token) + 1)), VECTOR_KEY)  # a later expiry breaks the signature
    assert not checks(token, Ed25519PrivateKey.generate())


async def test_relay_signs_for_the_proven_uuid_only(aiohttp_client, tmp_path, fake_mojang, key_file):
    client = await relay(aiohttp_client, tmp_path, attest_key=key_file)
    ws, challenge = await login(client)
    assert challenge["attest"] is True
    # A uuid in the request counts for nothing: the token names the account Mojang proved on this socket.
    await ws.send_json({"t": "attest", "nonce": NONCE, "sid": SID, "uuid": B})
    reply = await ws.receive_json(timeout=2)
    assert reply["t"] == "attest" and reply["nonce"] == NONCE and "error" not in reply
    assert checks(reply["token"], VECTOR_KEY, uuid=A) and not checks(reply["token"], VECTOR_KEY, uuid=B)
    await ws.close()


@pytest.mark.parametrize("bad", [{"nonce": "xyz", "sid": SID}, {"nonce": NONCE}, {"nonce": NONCE, "sid": "a|b" * 8},
                                 {"nonce": NONCE.upper(), "sid": SID}, {"nonce": 5, "sid": SID}])
async def test_malformed_attest_closes(aiohttp_client, tmp_path, fake_mojang, key_file, bad):
    client = await relay(aiohttp_client, tmp_path, attest_key=key_file)
    ws, _ = await login(client)
    await ws.send_json({"t": "attest", **bad})
    await closed(ws)


async def test_attest_rate_limit(aiohttp_client, tmp_path, fake_mojang, key_file, monkeypatch):
    monkeypatch.setattr(cosmetics, "MAX_RATE", 100)
    client = await relay(aiohttp_client, tmp_path, attest_key=key_file)
    ws, _ = await login(client)
    for _ in range(cosmetics.MAX_ATTESTS):
        await ws.send_json({"t": "attest", "nonce": NONCE, "sid": SID})
        assert "token" in await ws.receive_json(timeout=2)
    await ws.send_json({"t": "attest", "nonce": NONCE, "sid": SID})
    assert await ws.receive_json(timeout=2) == {"t": "attest", "nonce": NONCE, "error": "rate"}
    await ws.close()


async def test_no_key_no_tokens(aiohttp_client, tmp_path, fake_mojang, monkeypatch):
    monkeypatch.delenv("LADS_ATTEST_KEY", raising=False)
    client = await relay(aiohttp_client, tmp_path)
    ws, challenge = await login(client)
    assert "attest" not in challenge
    await ws.send_json({"t": "attest", "nonce": NONCE, "sid": SID})
    assert await ws.receive_json(timeout=2) == {"t": "attest", "nonce": NONCE, "error": "unavailable"}
    await ws.close()


async def test_key_from_env(aiohttp_client, tmp_path, fake_mojang, key_file, monkeypatch):
    monkeypatch.setenv("LADS_ATTEST_KEY", str(key_file))
    client = await relay(aiohttp_client, tmp_path)
    ws, challenge = await login(client)
    assert challenge.get("attest") is True
    await ws.close()


async def test_insecure_relay_never_signs(aiohttp_client, tmp_path, key_file, monkeypatch):
    monkeypatch.setenv("LADS_SOCIAL_DEV", "1")
    client = await relay(aiohttp_client, tmp_path, insecure=True, attest_key=key_file)
    ws, challenge = await login(client, name="Mallory", uuid=B)  # no Mojang check in dev mode: any name and uuid get in
    assert "attest" not in challenge
    await ws.send_json({"t": "attest", "nonce": NONCE, "sid": SID})
    assert await ws.receive_json(timeout=2) == {"t": "attest", "nonce": NONCE, "error": "unavailable"}
    await ws.close()


def test_bad_key_file_turns_attest_off(tmp_path):
    bad = tmp_path / "bad.pem"
    bad.write_text("not a key")
    assert cosmetics.load_attest_key(bad) is None
    assert cosmetics.load_attest_key(tmp_path / "missing.pem") is None
    assert cosmetics.load_attest_key(None) is None


def test_attest_key_cli_creates_once_and_prints_the_public_key(tmp_path, capsys):
    path = tmp_path / "relay.pem"
    assert cosmetics.cli_attest_key(str(path)) == 0
    first = capsys.readouterr().out
    pem = path.read_bytes()
    assert "created" in first and "public key: " in first and b"PRIVATE KEY" in pem
    assert cosmetics.cli_attest_key(str(path)) == 0
    second = capsys.readouterr().out
    assert path.read_bytes() == pem and "created" not in second  # never overwritten
    assert second.strip() == "public key: " + cosmetics.attest_public_key(cosmetics.load_attest_key(path))
    assert b"PRIVATE" not in second.encode() and len(base64.b64decode(second.split(": ")[1])) == 32
