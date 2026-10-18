# Lads cosmetics relay (1.9.11)

`cosmetics.py` lets every Lads Client player on the same Minecraft server see each other's emotes. The client side is
`TheLadsCore/common/.../client/cosmetics/net/CosmeticsLink.java`. State is in memory only; a restart makes clients reconnect.

Routes: `GET /cosmetics/health`, WebSocket `GET /cosmetics/ws`, `GET /cosmetics/profile/{uuid}` (`{"owned":[...],"equipped":{}}`).
Protocol (JSON text frames, client frames <= 1 KB):

1. server `{"t":"challenge","nonce":hex}`; client joins `serverId = server_id(nonce)` on Mojang's session server, then
   `{"t":"auth","name","uuid","serverId"}`; server checks Mojang `hasJoined`. No auth within 15 s = closed.
2. client `{"t":"room","room":sha256("host:port") | null}` -> `{"t":"snapshot","players":[{"uuid","emote","age"}]}`; the room gets `join`/`leave`.
3. client `{"t":"emote","id":"lads:groove" | null}` -> the room (not the sender) gets `{"t":"state","uuid","emote","age":0}`.

Limits: 1 socket per uuid (new replaces old, old closed with 4001), 10 sockets per IP (`CF-Connecting-IP`), 8 messages/s, frames
over 1 KB close, ping every 30 s, 90 s idle close, unknown emote ids rejected (`EMOTES`). Logs uuids only.

## Wire into bot.py (not done here: the live bot is owner-deployed)

1. Copy `cosmetics.py` next to `bot.py` (both on Arash-HP `C:\Servers\TheLadsBot`). No new dependency (aiohttp is already there).
2. In `start_web_server()`, after `app = web.Application()` and before `runner = web.AppRunner(app)`, add its own try block so a
   broken import can never take the other routes down:

   ```python
           try:
               import cosmetics
               cosmetics.setup(app)
           except Exception as e:
               print(f"  Note: cosmetics routes not loaded: {e}", flush=True)
   ```
3. Restart the bot; check `https://bot.arashyt.ca/cosmetics/health`. Cloudflare tunnels pass WebSockets through by default.
4. Before release: add the paragraph in `PRIVACY-NOTE.md` to the site's privacy page.

## Run and test locally

```
pip install aiohttp pytest pytest-aiohttp
python -m pytest -q                                   # fake Mojang, no network
python cosmetics.py --port 8095 --dev-insecure-auth   # QA with offline accounts; refuses any host but 127.0.0.1
```

QA clients: `LADS_COSMETICS_URL=ws://127.0.0.1:8095/cosmetics/ws` (ws:// is accepted only for localhost; it also skips the
client's Mojang join). With that URL only: `LADS_COSMETICS_QA_SERVER=host:port` puts the client in that room in any world
(singleplayer too) and `LADS_COSMETICS_QA_EMOTE=lads:groove` plays that emote every 15 s.
