# Lads cosmetics relay & backend (Phase 2)

`cosmetics.py` handles cosmetic entitlements, equipping, and emote relays for Lads Client players per Minecraft server ("room").
The client side connects via `TheLadsCore/common/.../client/cosmetics/net/CosmeticsLink.java`.

## Routes

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| `GET` | `/cosmetics/health` | None | Service health status, active player count, active room count. |
| `GET` | `/cosmetics/ws` | Mojang `hasJoined` | Authenticated background WebSocket connection for emote/equip sync and in-game link codes. |
| `GET` | `/cosmetics/profile/{uuid}` | None | Returns owned cosmetics and currently equipped items (`{"owned":[...],"equipped":{...}}`). |
| `POST` | `/cosmetics/equip` | `Bearer <token>` | HTTP equip endpoint: `{"uuid":"...","slot":"...","item":"..."}` (requires auth; normal players equip via authenticated WS). |
| `POST` | `/cosmetics/tebex` | `X-Tebex-Signature` | Tebex webhook endpoint (fail-closed, signature-verified, idempotent by transaction ID, handles payments and refunds). |
| `GET` | `/cosmetics/link/start?uuid={uuid}` | `Bearer <token>` | Generates a 6-character link code valid for 10 minutes (requires auth; in-game players generate via WS). |
| `GET` | `/cosmetics/link/verify?code={code}` | `Bearer <token>` | Verifies and resolves a link code to its bound UUID (requires auth; webhooks resolve directly). |

## WebSocket Protocol (Frames <= 1 KB)

1. **Authentication:**
   - Server: `{"t":"challenge","nonce":"<hex32>"}`
   - Client: joins `serverId = server_id(nonce)` on Mojang session server, sends `{"t":"auth","name":"...","uuid":"...","serverId":"..."}`
   - Server verifies Mojang `hasJoined`. (Closes after 15 s if unauthenticated).
2. **Room Management:**
   - Client: `{"t":"room","room":"<sha256(host:port)>" | null}`
   - Server: sends `{"t":"snapshot","players":[{"uuid","emote","age"}]}` to client; room receives `join`/`leave`.
3. **Emote Relay:**
   - Client: `{"t":"emote","id":"lads:groove" | null}`
   - Room receives `{"t":"state","uuid":"...","emote":"...","age":0}`.
4. **Equip Cosmetics:**
   - Client: `{"t":"equip","slot":"cape","item":"lads:cape_aurora"}` (or `{"item": null}` to unequip). Allowed slots: `emote`, `cape`, `hat`, `glasses`.
   - Server validates ownership against SQLite entitlements.
   - Room receives `{"t":"equip","uuid":"...","slot":"cape","item":"..."}`.
   - Sender receives confirmation `{"t":"equip","uuid":"...","slot":"cape","item":"...","status":"ok"}`.
5. **In-Game Link Code:**
   - Client sends: `{"t":"link"}` (or `{"t":"link_start"}`)
   - Server responds: `{"t":"link_code","code":"ABC123","uuid":"...","expires_in":600}`.
   - Max one active link code per UUID (new code replaces previous code).

## SQLite Store

Storage uses SQLite with WAL mode (`PRAGMA journal_mode=WAL; PRAGMA busy_timeout=5000;`) and asynchronous thread delegation (`asyncio.to_thread`):

- `entitlements(uuid, item_id, source, granted_at, revoked_at)`: tracks permanent grants, transaction IDs, and revocations.
- `equipped(uuid, slot, item_id)`: tracks active cosmetic per allowed slot (`emote`, `cape`, `hat`, `glasses`).
- `transactions(transaction_id, event_type, uuid, items_json, created_at, updated_at)`: guarantees webhook idempotency.
- `link_codes(code, uuid, created_at, expires_at)`: ephemeral 6-char link codes with 10-minute TTL (consumed atomically upon checkout redemption).

Default database file is `cosmetics.db` (configurable via `--db` or `COSMETICS_DB` environment variable).

## Tebex Webhooks

Configured with `TEBEX_WEBHOOK_SECRET` environment variable or `--tebex-secret`.
- **Fail-Closed Security:** Fails closed (401 Unauthorized) if `TEBEX_WEBHOOK_SECRET` is unset or if the incoming signature header is missing or invalid.
- Validates the `X-Tebex-Signature` / `X-Signature` header using the two-step SHA-256 + HMAC-SHA256 algorithm per Tebex documentation.
- `validation.webhook`: echoes `{ "id": ... }` to pass endpoint validation tests.
- `payment.completed`: extracts buyer UUID and items, atomically consumes single-use link codes, grants entitlements idempotently (re-deliveries return `already_processed`).
- `payment.refunded` / `payment.dispute.opened` / `payment.chargeback`: revokes entitlements, clears `equipped`, and broadcasts unequip to connected peers in room.

## Staff Admin CLI

Run directly via `cosmetics.py`:

```bash
# Grant cosmetic item
python cosmetics.py grant <uuid> <item_id> [--source <source>] [--db <db_path>]

# Revoke cosmetic item
python cosmetics.py revoke <uuid> <item_id> [--db <db_path>]

# List active entitlements, revoked entitlements, and equipped items
python cosmetics.py list <uuid> [--db <db_path>]
```

## Running Locally

```bash
pip install aiohttp pytest pytest-aiohttp
python -m pytest -v                                   # full test suite (41 tests)
python cosmetics.py --port 8095 --dev-insecure-auth   # offline QA server
```
