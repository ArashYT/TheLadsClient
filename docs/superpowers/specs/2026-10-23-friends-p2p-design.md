# Lads Friends + P2P World Hosting: design

Date: 2026-10-23. Owner: Arash. Status: owner answered the key questions (below); the remaining calls are marked **[Claude's call]** for morning review.

## Why

Mojang showed Friends + peer-to-peer world sharing in 26.2 snapshots 7-8 (May 2026), then pulled the online part in 26.2-pre1: 26.2/26.3 shipped only a Friends List and a Multiplayer Options screen with OFF/LAN. Lunar, Feather and Badlion all ship friends, presence, invites and (Feather, Lunar) "host your world". The owner wants the full experience, polished, on every Lads version: friends list, seamless game invites and joining, the host's resource packs (all of them) shared peer to peer, and as many host options as possible.

Success: two Lads players on the same Minecraft version, behind ordinary home routers, can friend each other, see each other online, and join each other's singleplayer world in two clicks, with the host's packs applied automatically, without port forwarding, IP sharing or a server.

## Owner decisions (2026-10-23)

- Relay fallback when a direct connection fails: **Cloudflare Realtime TURN** (1,000 GB/month free, then $0.05/GB). Hides the host's IP.
- Who can join a hosted world: **Lads Client friends only**. No public address for vanilla players.
- Friends source: **our own Lads friends graph on all versions, plus the player's Mojang friends shown on 26.3**.
- Voice chat: **later**, after friends + P2P ship.
- Ship **in phases**, one patch video per phase: Phase 1 competitor parity (separate lanes), **Phase 2 friends + presence + invites + hosting**, **Phase 3 resource-pack sharing + parties + extras**.

## Goals

1. Friends: add by Minecraft name, requests (accept/decline), remove, block, notes/nicknames; online status, what the friend is doing (menus, singleplayer, hosting a joinable world, on a server - server address shown only if the friend allows it), MC version, Lads version.
2. Direct messages between friends (out of band, never in Minecraft chat).
3. Invites and join requests: host invites a friend (toast + chat line with [Join]/[Decline] + friends screen); a friend asks to join a hosted world (host toast with Accept/Deny, configurable auto-accept). Server invites too: "Join me on <server>" for a friend on a public server (just connects to the address).
4. Hosting ("Multiplayer Options"): open the current singleplayer world to Off / LAN / Friends / Invite only, with host options (list below).
5. Seamless join: one click from the friends screen, toast or chat; if the player is in another world, confirm then switch; progress screen ("Connecting to Arash's world... direct / relayed").
6. Works on 1.8.9 (Forge), 1.20.1, 26.3; same feature set; guests must be on the host's Minecraft version (friends screen shows it and greys Join otherwise).
7. Phase 3: share ALL of the host's enabled resource packs (and the world's own resources.zip) peer to peer, applied in the host's order for the session only, restored on leave; parties.

## Non-goals / anti-goals

- No public join address, no vanilla-client guests (owner).
- No cross-version joining in Phase 2 (a "switch version" relaunch through the launcher is a Phase 3 idea).
- No voice chat yet.
- Never a ban vector: a P2P session must never make the guest's risky modules turn on against a real server (see Security).
- Never leak the host's IP to non-friends; never let a non-friend reach the integrated server.
- Not a 24/7 server: when the host leaves, the world closes for everyone (say so in the UI).

## Architecture

```
 host game (mod, common/)                 bot.arashyt.ca (Arash-HP, CF tunnel)              guest game (mod, common/)
 FriendsClient --WSS /social/ws--------->  social.py (aiohttp, same process as  <----------WSS---- FriendsClient
   presence, requests, DMs, invites          cosmetics.py, SQLite, Mojang hasJoined)            presence, invites
   ICE signaling relay ------------------>   + Cloudflare TURN credential minting <-------- ICE signaling
 P2PTransport (ice4j)  <==== direct UDP (host/srflx) or Cloudflare TURN relay ====>  P2PTransport (ice4j)
   reliable mux stream                                                               reliable mux stream
   -> 127.0.0.1:<integrated LAN port>                                                <- 127.0.0.1:<local proxy port>
 integrated server (bound to loopback in Friends/Invite scope)                       vanilla connect screen -> local proxy
```

- **Backend**: new `cosmetics-server/social.py`, mounted on the existing aiohttp app as `/social/ws` (cosmetics.py only gets the route + shared auth import; social owns its tables). Auth: reuse the existing Mojang `hasJoined` handshake from `cosmetics.py:985 authenticate`. Storage: SQLite tables `friends`, `requests`, `blocks`, `dm_offline` (7-day TTL), `settings`. Presence is in memory. Signaling messages are relayed only between two authenticated players who are friends (and, for a join, only when the host's scope allows it).
- **TURN**: the backend mints short-lived Cloudflare TURN credentials (`POST https://rtc.live.cloudflare.com/v1/turn/keys/{key}/credentials/generate`, TTL ~ session + margin) only for a pair with an accepted join; the API key never reaches clients. STUN: `stun.cloudflare.com:3478`. **Open item:** a Cloudflare Realtime TURN key must be created in the owner's Cloudflare account; until it exists the system works direct-only and says "relay unavailable" on failure.
- **Client (all versions)**: everything in `TheLadsCore/common` under `com.thelads.core.client.social`: `FriendsClient` (WSS, reuses `cosmetics/net/WebSocket.java` and the CosmeticsLink auth/reconnect pattern), `SocialState` (friends, presence, invites, DMs), `P2PTransport` (ice4j: all 3 versions run Java >= 21 - 1.8.9 runs Java 25 via 8to25, verified `ProfileService.cs:546`), `PackShare` (Phase 3). Per-version code is only: screens' platform glue (draw through the existing MenuGraphics bridge), the publish/unpublish hook, the integrated-server login check, the connect call, keybinds, title/pause buttons.
- **Transport**: ICE (ice4j, Apache-2.0) gathers host, server-reflexive and TURN candidates; one UDP component; on top a reliable ordered byte stream with multiplexing (stream 0 = Minecraft connection, stream 1 = control, stream 2+ = pack transfers). **[Claude's call]** reliability layer = ice4j's PseudoTCP if a spike shows >= 4 MB/s on loopback and smooth chunk loading at 12 chunks; otherwise KCP (Apache-2.0 Java port). Encryption: DTLS is not needed for confidentiality of the MC stream (Minecraft encrypts the login after online-mode auth), but the control/pack streams carry no secrets; ICE credentials are per-session and exchanged over WSS.
- **Hosting path**: host calls the version's LAN publish (`shareToLAN(GameType,boolean)` 1.8.9, `publishServer(GameType,boolean,int)` 1.20.1, `publishServer(MultiplayerScope,boolean,int)` 26.3). In Friends/Invite scope the server socket is bound to 127.0.0.1 only (LAN scope keeps vanilla behaviour); each accepted guest gets its own ICE session whose stream 0 is piped to that loopback port. The integrated server stays online-mode, so Mojang verifies the guest's identity; additionally the host's login hook rejects any UUID that was not accepted for this session (whitelist), with "You weren't invited to this world".
- **Join path**: guest's P2PTransport opens 127.0.0.1:<ephemeral> and the version's normal connect call targets it with the host's name shown instead of the address. One ICE session per join.

## Security

- Only authenticated (Mojang hasJoined) players use `/social/ws`. Friend requests rate-limited (e.g. 20/hour), blocks hide presence both ways, max 500 friends, DM length cap 256 chars, server-side validation of every field.
- Signaling and TURN credentials are only issued for friend pairs with an accepted invite/request in the host's current scope. Invite-only: only invited UUIDs. Kicked/banned UUIDs are refused for the session (ban = persistent per world).
- Host IP: shared only with accepted guests' clients through ICE candidates; host option "Always use relay (hide my IP)" forces TURN-only. **[Claude's call]** default OFF (direct is faster), recommend ON in the tooltip for streamers.
- **Anti-ban (permanent rule)**: the guest's local proxy is 127.0.0.1, which `HypixelSafetyManager` would treat as trusted (`HypixelSafetyManager.java:147`). The guest cannot prove the host is really an integrated server (brands are the same as dedicated servers and a modified host controls every byte), so **a P2P session always counts as an unknown server** for the guest: risky modules (minimap, Stack Refill, use-while-mining, every GATED module) are off, and the player can re-enable them only through the existing red per-server bypass screen, keyed to `p2p:<hostUUID>/<worldId>`. The 127.x trust must not apply to the tunnel port (classify by the active P2P session, not by address). Unit test required. The host itself is in singleplayer, so its own rules are unchanged.
- Resource packs (Phase 3): SHA-1 verified, size cap per pack (250 MB) and per session (1 GB), zip only, never executed, cached by hash under `.theladsclient/p2p-packs`, guest consent prompt with sizes ("Download 3 packs, 84 MB?") and "always from this friend".

## Host options (Multiplayer Options screen)

Scope: Off / LAN / Friends / Invite only. Then **[Claude's call, all in Phase 2 unless marked]**:
- Join approval: auto-accept friends / ask me each time / invite only.
- Max players (1-16, default 8), default game mode for guests, allow cheats (guests' op level 0-4, default 0), per-player menu (game mode, op, kick, ban, mute in chat, teleport to me).
- PvP on/off, difficulty, hardcore lock respected, common gamerules (keepInventory, doDaylightCycle, doWeatherCycle, mobGriefing, doFireTick, doMobSpawning, naturalRegeneration, playersSleepingPercentage where the version has it).
- Guest view/simulation distance cap (default = host's).
- "Always use relay (hide my IP)".
- Backup the world before opening (default ON) and on close.
- Show me as "joinable" to friends (default ON in Friends scope).
- Resource packs (Phase 3): share all enabled packs (default ON), require them, per-pack toggles.
- Welcome message, world icon/name shown in invites.
- Persist per world (stored next to the world: `lads-host.json`).

## UX

- Friends screen: key O on 1.8.9/1.20.1; on 26.3 **[Claude's call]** ours replaces the vanilla Friends screen (same O key, same title/pause buttons) and shows a display-only "Mojang friends" section read from authlib `FriendsService` (see "Calls made after the builder check").
- Tabs: Friends (online first, status line, version, [Join]/[Invite]/[Message]/[...]), Requests, Blocked, Add (by name with Mojang lookup).
- Toasts (top-right, Lads style, keybind to accept the newest), chat lines with clickable [Join]/[Accept]/[Decline].
- Status privacy: show server address to friends (on/off), appear offline, DMs from friends only (only friends can DM anyway in Phase 2).
- Title screen + pause screen: Friends button with an online-count badge; pause screen "Multiplayer Options" replaces "Open to LAN" on every version.

## Phases and verification

- **Phase 2**: backend (social.py + tests), FriendsClient + SocialState (+ unit tests against a local social.py), Friends screen x3 versions, Multiplayer Options + host options x3, P2PTransport + mux (+ loopback throughput test, forced-relay test), host login whitelist x3, guest join path x3, HypixelSafetyManager "friend world" kind (+ unit test), toasts/chat links. QA: host on Arash-PC, guest on Legion, per version: direct path and forced-relay path, invite flow, join-request flow, kick/ban, host leaves.
- **Phase 3**: PackShare (all enabled packs + world resources.zip, order, restore on leave), parties (group join), cross-version "switch version" via launcher deep link, polish.

## Calls made after the builder check (2026-10-24, owner asleep; marked for review)

- **Instances/modpacks [Claude's call]**: presence reports `{mcVersion, loader, profileKind (plain|pack), packId, packVersion, modsHash}`. Join is enabled only when MC version + loader + pack id + pack version match (plain profiles: MC version + loader; a different user mod set shows a warning, not a block, since the server's own handshake reports missing mods clearly). Greyed Join shows the reason ("Needs Zero SMP 0.4.0"). Launching the matching instance from an invite = Phase 3. Hosting must work inside Zero SMP (Forge 1.20.1 via Connector) and Lads SMP 26.3; QA covers plain profiles first, then one pack per modern version.
- **Mojang friends on 26.3 [Claude's call]**: display-only section (online/offline from authlib `FriendsService`) with an "Add on Lads" button when that player uses Lads; no invites/join requests through Mojang friendship (the backend cannot verify it without the player's Microsoft token, which never leaves the client). A "Manage Mojang friends" button opens the vanilla Friends screen.
- **TURN key / cost [Claude's call, owner to confirm]**: the owner creates the Cloudflare Realtime TURN key (Claude has no Cloudflare login on this PC). Phase 2 does not ship without it (CGNAT players need relay). The backend counts relay sessions' allocated time and stops issuing relay credentials at 900 GB/month (stays inside the free 1,000 GB; no spending) until the owner says otherwise; when capped, relay-needed joins fail with "Relay is busy this month, try LAN or ask the host".
- **QA accounts [Claude's call, owner to confirm]**: automated QA runs the real backend code locally (`--dev-insecure-auth`, loopback only) with offline QA accounts, and the integrated server's online-mode check is relaxed only when the QA harness sets `LADS_QA_P2P_OFFLINE=1` (never read in release builds' normal path; unit test proves the flag is ignored without the harness marker). The forced-relay path is tested once the TURN key exists. A real two-account test through bot.arashyt.ca and a real cross-network (phone hotspot) test need the owner: open questions for the morning.

## Left to the builder

Exact class names and file layout inside `client/social`, the KCP-vs-PseudoTCP spike result, screen layouts within the existing Lads menu style, SQLite schema details, backoff/timeouts, how the 26.3 vanilla Friends screen is replaced (mixin vs screen swap).
