# Lads Friends + P2P Hosting (Phase 2) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. In this project the lanes run as Antigravity sessions (one lane = one worktree/branch) under `AGY-P2P-RULES.md`; Claude reviews every lane.

**Goal:** Lads players can friend each other, see presence, message, invite / request to join, and play in each other's singleplayer world peer to peer (direct or Cloudflare TURN relay) on 1.8.9, 1.20.1 and 26.3.

**Architecture:** A `/social/ws` WebSocket service (`cosmetics-server/social.py`, same aiohttp app and Mojang auth as cosmetics) holds the friend graph, presence, DMs and relays ICE signaling. In the game, `com.thelads.core.client.social` (common) holds the client, state, screens' logic and a dependency-free P2P transport (STUN/TURN/ICE-lite + reliable multiplexed stream) that pipes Minecraft TCP between a loopback-bound integrated server and a loopback proxy on the guest. Each version implements `SocialBridge` (publish, login filter, connect, screens, toasts).

**Tech Stack:** Java 17 source in common (1.8.9 runs Java 25 via 8to25; common is downgraded for the 1.8.9 jar, so no Java 17+ *library* jars), Python 3.12 aiohttp + sqlite3 + pytest, Forge 1.8.9 / Fabric 1.20.1 / Fabric 26.3 mixins.

**Spec:** `docs/superpowers/specs/2026-10-23-friends-p2p-design.md` (read it first; the "Calls made after the builder check" section is binding).

## Global Constraints

- Contract files from Task 0 are frozen: change a signature only by asking Claude (write the request in your report).
- Only friends (Lads graph) can DM, invite, request to join or get signaling/TURN credentials; Mojang friends (26.3) are display-only.
- Guest side: an active P2P session is ALWAYS an unknown server for `HypixelSafetyManager` (bypass key `p2p:<hostUUID>/<worldId>`), never trusted by the 127.0.0.1 address.
- Friends/Invite scope binds the integrated server to `InetAddress.getLoopbackAddress()` only; LAN scope keeps vanilla binding; no LAN broadcast in Friends/Invite scope.
- Host login filter: reject any UUID not accepted for the current session with the text `You weren't invited to this world`.
- Limits (server-enforced): 500 friends, friend requests 20/hour, DM 256 chars and 30/minute, invites 30/hour, join requests 30/hour, offline DMs kept 7 days, invite/join-request expiry 5 minutes.
- TURN: credentials only from the backend (`CF_TURN_KEY_ID`, `CF_TURN_API_TOKEN` env), TTL 6 h, monthly relay budget 900 GB (client-reported bytes); no key or budget spent -> `turn: []`.
- STUN server: `stun.cloudflare.com:3478`.
- No new library jars in TheLadsCore (transport is in-house); Python backend may only use what cosmetics.py already imports.
- Anti-ban: no social/P2P feature ever sends a Minecraft packet to a server the player is connected to; chat click actions are intercepted client-side (`/lads:social <id>` never leaves the client).
- QA-only offline auth: integrated server online-mode is relaxed only when the env `LADS_QA_P2P_OFFLINE=1` AND the QA harness marker is present; backend `--dev-insecure-auth` only on 127.0.0.1.

## Review Focus

1. Host closes the world / quits mid-session -> guests get `Host closed the world` (not a timeout hang), host's proxy threads and ICE sockets are closed (test: `P2PSessionTest.hostCloseEndsGuestStreamWithinOneSecond`).
2. A guest whose UUID was kicked/banned or not invited tries again with a stale invite -> refused by both backend (`test_social.py::test_expired_or_revoked_invite_refused`) and host login filter (`HostSessionTest.loginFilterRejectsUnknownUuid`).
3. Player on a different MC version / pack tries to join -> Join greyed with reason, backend never starts a session it can tell is incompatible (`SocialStateTest.joinDisabledOnVersionMismatch`).
4. Network drops for 10-30 s (Wi-Fi blip) -> WS reconnects with backoff and restores presence; P2P stream survives packet loss up to 10% without corruption (`ReliableStreamTest.survivesTenPercentLossInOrder`).
5. Two invites at once / accepting an invite while already in another world -> confirm dialog, old world left cleanly, only one session per guest (`SocialStateTest.secondJoinReplacesFirstAfterConfirm`).

---

## File map

```
cosmetics-server/social.py                         (S1) social service: store, hub, handlers, TURN minting
cosmetics-server/test_social.py                    (S1) pytest
cosmetics-server/cosmetics.py                      (S1) +2 lines: mount social routes on the app
TheLadsCore/common/src/main/java/com/thelads/core/client/social/
  SocialProtocol.java      (T0, frozen) message type constants + JSON helpers
  SocialBridge.java        (T0, frozen) per-version hooks
  HostOptions.java         (T0, frozen) host settings record + JSON (de)serialization to lads-host.json
  Presence.java            (T0, frozen) presence record
  FriendEntry.java         (T0, frozen) friend record
  SocialClient.java        (S3) WS connect/auth/reconnect (CosmeticsLink pattern, reuses cosmetics/net/WebSocket)
  SocialState.java         (S3) friends, requests, presence, DMs, invites; listeners; join/host orchestration
  HostSession.java         (S3) host side: accepted UUIDs, per-guest P2P sessions -> loopback port
  GuestSession.java        (S3) guest side: P2P session -> loopback proxy -> SocialBridge.connectToLocal
  p2p/StunMessage.java     (S2) RFC 5389 encode/decode, MESSAGE-INTEGRITY, FINGERPRINT, XOR-MAPPED-ADDRESS
  p2p/TurnClient.java      (S2) RFC 8656 Allocate/Refresh/CreatePermission/ChannelBind/ChannelData (long-term creds)
  p2p/IceAgent.java        (S2) ICE-lite style: gather host/srflx/relay, exchange, checks, nominate
  p2p/ReliableStream.java  (S2) ARQ over datagrams: seq/ack/SACK, retransmit, window, keepalive
  p2p/Mux.java             (S2) stream ids over one ReliableStream (0 = Minecraft, 1 = control)
  p2p/P2PSession.java      (T0 interface, S2 impl) the only API other code uses
  ui/FriendsScreenLogic.java, ui/HostScreenLogic.java, ui/SocialToasts.java  (S4) common GUI via MenuGraphics
TheLadsCore/v1_8_9/.../social/   (S5) SocialBridge189, screens, mixins (publish, login filter, buttons, keybind)
TheLadsCore/v1_20_1/.../social/  (S6) SocialBridge1201, ...
TheLadsCore/v26_3/.../v26_2/social/ (S7) SocialBridge26, ... + vanilla Friends screen replacement + Mojang friends section
TheLadsCore/common/.../client/HypixelSafetyManager.java  (S3) +p2pSession hook (one method + one check)
```

Lanes (parallel after Task 0): S1 backend, S2 transport, S3 client core, S4 common UI, S5 1.8.9, S6 1.20.1, S7 26.3. S5-S7 code against the T0 contract and stub `SocialState` calls until S3/S4 merge.

---

### Task 0 (Claude): contract files

**Files:** Create the five `(T0, frozen)` files above and `p2p/P2PSession.java`; no logic beyond records/constants/JSON helpers.

**Interfaces (Produces):**
- `SocialProtocol`: `URL_DEFAULT = "wss://bot.arashyt.ca/social/ws"`, message `t` values listed under Task S1, `static JsonObject msg(String t, Object... kv)`.
- `SocialBridge` (static `get()`/`set()`, defaults are no-ops):
  `boolean canHost()`, `int openToFriends(HostOptions o)` (port or -1), `void openToLan(HostOptions o)`, `void closeHosting(String reason)`, `void applyHostOptions(HostOptions o)`, `List<GuestInfo> guests()`, `void guestAction(UUID id, GuestAction a)`, `void setLoginFilter(Predicate<UUID> allowed)`, `void connectToLocal(int port, String displayName)`, `void connectToServer(String address)`, `boolean inWorldOrServer()`, `void leaveWorld(Runnable then)`, `GameInfo gameInfo()`, `void openFriendsScreen()`, `void openHostScreen()`, `void toast(String title, String body, String acceptAction, String denyAction)`, `void chatAction(String text, String actionId)`, `String worldId()`, `String worldName()`.
  Records: `GuestInfo(UUID id, String name, int ping, boolean op, String gameMode)`, `enum GuestAction {KICK, BAN, OP, DEOP, SURVIVAL, CREATIVE, ADVENTURE, SPECTATOR, TP_TO_ME, MUTE}`, `GameInfo(String mc, String loader, String profileKind, String packId, String packVersion, String modsHash, String lads)`.
- `HostOptions` record (defaults = spec): `scope` (OFF|LAN|FRIENDS|INVITE), `approval` (AUTO_FRIENDS|ASK|INVITE_ONLY), `maxPlayers=8`, `guestGameMode="survival"`, `guestOpLevel=0`, `pvp=true`, `difficulty` (null = keep), `gamerules` Map<String,String>, `viewDistanceCap=0` (0 = host's), `relayOnly=false`, `backupOnOpen=true`, `showJoinable=true`, `welcome=""`; `static HostOptions load(Path worldDir)`, `void save(Path worldDir)` (file `lads-host.json`).
- `Presence(String state, GameInfo game, String server, WorldAd world)`; `WorldAd(String id, String name, String scope, int slots, int used)`; state values `menu|singleplayer|hosting|server|offline`.
- `FriendEntry(UUID id, String name, String nick, Presence presence, long since)`.
- `P2PSession` interface: `static CompletableFuture<P2PSession> start(String sid, boolean host, IceConfig cfg, Consumer<JsonObject> sendSignal)`, `void onSignal(JsonObject data)`, `InputStream in(int stream)`, `OutputStream out(int stream)`, `boolean relayed()`, `long bytes()`, `void close(String reason)`, `void onClose(Consumer<String> reason)`; `IceConfig(List<String> stun, List<TurnServer> turn, boolean relayOnly)`, `TurnServer(String url, String user, String credential)`.

- [ ] Write the files, `:common:compileJava` passes, commit `p2p: contract (social bridge, protocol, host options, P2PSession)`.

### Task S1: social backend

**Files:** Create `cosmetics-server/social.py`, `cosmetics-server/test_social.py`; Modify `cosmetics-server/cosmetics.py` (route mount only).

**Protocol (exact; client side in S3 must match):** after the cosmetics-style `challenge`/`auth` handshake (reuse `authenticate`-equivalent logic: `server_id`, `plain_uuid`, `has_joined`, `NAME_RE` imported from cosmetics), server sends `hello {friends:[{uuid,name,nick,presence,since}], requests:{in:[{uuid,name}],out:[...]}, blocked:[uuid], settings:{showServer,appearOffline}, dms:[{from,text,ts}]}`.
Client -> server `t`: `presence{state,game,server,world}`, `friend_add{name}`, `friend_accept{uuid}`, `friend_decline{uuid}`, `friend_remove{uuid}`, `block{uuid}`, `unblock{uuid}`, `nick{uuid,nick}`, `dm{to,text}`, `invite{to,world}` (or `invite{to,server}` for a public-server invite: no session, guest just connects), `invite_accept{inviteId}`, `invite_decline{inviteId}`, `join_request{host}`, `join_accept{reqId}`, `join_deny{reqId}`, `signal{sid,data}`, `p2p_end{sid,reason,relayed,bytes}`, `settings{showServer,appearOffline}`.
Server -> client `t`: `presence{uuid,...}`, `request_in{uuid,name}`, `request_sent{uuid,name}`, `friend_added{uuid,name}`, `friend_removed{uuid}`, `dm{from,text,ts}`, `invite{inviteId,from,world}`, `join_request{reqId,from,game}`, `join_denied{reqId}`, `p2p_start{sid,peer,role,ice:{stun:[],turn:[{url,user,credential}],relayOnly}}`, `signal{sid,data}`, `p2p_end{sid,reason}`, `error{code,ref}` with codes `not_found|self|already|blocked|limit|rate|not_friend|expired|invite_only|full|incompatible|offline`.
Name lookup: `https://api.mojang.com/users/profiles/minecraft/<name>` (5 s timeout, cache 1 h).
Presence fan-out: only to online friends; `showServer=false` strips `server`; `appearOffline` sends `state:"offline"`; blocked pairs never see each other.

- [ ] Tests first (pytest, aiohttp test client, `insecure=True`): `test_add_accept_friends_both_see_presence`, `test_request_rate_limit_20_per_hour`, `test_block_hides_presence_both_ways`, `test_dm_only_between_friends_and_offline_kept_7_days`, `test_invite_accept_starts_p2p_for_both_roles`, `test_join_request_invite_only_scope_refused`, `test_signal_only_relayed_within_session_peer`, `test_expired_or_revoked_invite_refused`, `test_turn_creds_empty_without_key_and_after_900gb`, `test_show_server_false_strips_address`, `test_full_world_refused`, `test_incompatible_game_refused` (game mc/loader/packId/packVersion differ).
- [ ] Implement `social.py`: `SocialStore(db_path)` (tables friends, requests, blocks, dm_offline, settings, relay_usage(month, bytes)), `SocialHub` (online peers, sessions sid->{host,guest,expires}), `async def handle_social_ws(request)`, `def mount(app, insecure: bool, db_path: Path)`; TURN minting `POST https://rtc.live.cloudflare.com/v1/turn/keys/{CF_TURN_KEY_ID}/credentials/generate-ice-servers` with `{"ttl": 21600}` and `Authorization: Bearer {CF_TURN_API_TOKEN}`.
- [ ] `pytest cosmetics-server -q` all pass (existing cosmetics tests too); commit.

### Task S2: P2P transport (no dependencies)

**Files:** `p2p/StunMessage.java`, `p2p/TurnClient.java`, `p2p/IceAgent.java`, `p2p/ReliableStream.java`, `p2p/Mux.java`, `p2p/P2PSessionImpl.java` + tests in `common/src/test/java/com/thelads/core/client/social/p2p/`.

**Interfaces:** Produces the `P2PSession.start` implementation (Task 0 contract). Signal payloads: `{"ufrag","pwd","candidates":[{"type":"host|srflx|relay","ip","port","prio"}],"end":bool}`.

- [ ] Tests first: `StunMessageTest.roundTripsBindingWithIntegrityAndFingerprint` (RFC 5769 test vectors), `TurnClientTest.allocateAgainstFakeTurnServer` (in-test UDP fake), `IceAgentTest.loopbackHostCandidatesConnect`, `IceAgentTest.relayOnlyUsesRelayCandidate` (fake TURN), `ReliableStreamTest.survivesTenPercentLossInOrder` (lossy in-memory link, 5 MB random payload, SHA-256 equal), `ReliableStreamTest.throughputAtLeast4MBpsOnLoopback`, `MuxTest.streamsIndependentAndCloseIsolated`, `P2PSessionTest.hostCloseEndsGuestStreamWithinOneSecond`.
- [ ] Implement. ReliableStream: 1200-byte max datagram payload, selective ack, RTO from RTT (min 50 ms), window 256 packets, keepalive every 5 s, dead after 15 s silence; no allocation per packet in steady state (reuse buffers). One reader thread + one timer thread per session, daemon, MIN priority + 1.
- [ ] `:common:test --tests *p2p*` pass; commit.

### Task S3: client core

**Files:** `SocialClient.java`, `SocialState.java`, `HostSession.java`, `GuestSession.java`, `HypixelSafetyManager.java` (hook), tests `SocialClientTest` (against a local `social.py --dev-insecure-auth` started by the test if `python` is available, else skipped with a message), `SocialStateTest`, `HostSessionTest`, `HypixelSafetyManagerP2PTest`.

**Interfaces:** Consumes Task 0 contract, S2 `P2PSession`. Produces for S4: `SocialState.get()`, `List<FriendEntry> friends()`, `List<FriendEntry> incoming()`, `outgoing()`, `blocked()`, `void addFriend(String name)`, `accept(UUID)`, `decline(UUID)`, `remove(UUID)`, `block(UUID)`, `unblock(UUID)`, `nick(UUID,String)`, `sendDm(UUID,String)`, `List<Dm> dms(UUID)`, `invite(UUID)` (hosting: world invite; on a server: server invite when showServer allows), `acceptInvite(String inviteId)`, `requestJoin(UUID host)`, `answerJoin(String reqId, boolean yes)`, `startHosting(HostOptions)`, `stopHosting()`, `HostOptions hostOptions()`, `JoinCheck canJoin(FriendEntry)` (`record JoinCheck(boolean ok, String reason)`), `void addListener(Runnable)`, `String connectionStatus()`. `HypixelSafetyManager.setP2PSession(String keyOrNull)`; while set, the session counts as an unknown server and per-server bypass uses the key.

- [ ] Tests first: `SocialStateTest.joinDisabledOnVersionMismatch`, `SocialStateTest.secondJoinReplacesFirstAfterConfirm`, `SocialStateTest.autoAcceptOnlyFriendsInFriendsScope`, `HostSessionTest.loginFilterRejectsUnknownUuid`, `HostSessionTest.maxPlayersEnforced`, `HypixelSafetyManagerP2PTest.p2pSessionIsUnknownServerEvenOnLoopback`, `SocialClientTest.reconnectsWithBackoffAndRestoresPresence`.
- [ ] Implement (SocialClient copies CosmeticsLink's connect/auth/backoff/ping pattern and the `local` QA endpoint rule; endpoint override `-Dlads.social.url` / `LADS_SOCIAL_URL`, wss:// or ws://localhost only). Presence publishes on state change + every 60 s.
- [ ] Tests pass; commit.

### Task S4: common UI

**Files:** `ui/FriendsScreenLogic.java`, `ui/HostScreenLogic.java`, `ui/SocialToasts.java` + tests (layout/hit-testing like `CosmeticsScreenTest`).

**Interfaces:** Consumes S3 `SocialState` API (stub it behind a small interface in tests). Produces: `FriendsScreenLogic(Callbacks)` and `HostScreenLogic(Callbacks)` drawn through the existing MenuGraphics bridge exactly like `CosmeticsScreen` (per-version wrappers only forward render/input), `SocialToasts.show(...)`, `SocialToasts.acceptNewest()/denyNewest()`.
Screens per spec UX: Friends (tabs Friends/Requests/Blocked/Add, rows with status line, version, Join/Invite/Message/... menu, DM pane), Host (scope, approval, max players, guest game mode, op level, PvP, difficulty, gamerules list, view cap, relay only, backup, show joinable, welcome, guests list with per-player actions, Open/Close button).
- [ ] Tests first: `FriendsScreenLogicTest.onlineFriendsSortedFirstThenByName`, `FriendsScreenLogicTest.joinButtonDisabledShowsReason`, `HostScreenLogicTest.optionsRoundTripToHostOptions`, `SocialToastsTest.newestFirstAndExpiresAfter15s`.
- [ ] Implement in the Lads menu style (reuse existing widgets: grep `CosmeticsScreen`, `LadsSettingsScreen` helpers); commit.

### Task S5 / S6 / S7: per-version glue (one lane each: 1.8.9, 1.20.1, 26.3)

**Files:** new `social/` package per version: `SocialBridge<v>`, `FriendsScreen<v>`, `HostScreen<v>` (wrappers like `CosmeticsScreen189/1201/26`), mixins: publish on loopback (1.8.9 `NetworkSystem.addLanEndpoint(InetAddress,int)`; 1.20.1/26.3 `ServerConnectionListener.startTcpServerListener(InetAddress,int)`), login filter (1.8.9 `ServerConfigurationManager.allowUserToConnect`, 1.20.1 `PlayerList.canPlayerLogin`, 26.3 its equivalent - javap-verify), pause menu: "Open to LAN" -> "Multiplayer Options" (our HostScreen), title + pause "Friends" button with online badge (next to the existing Cosmetics buttons in `GuiIngameMenuMixin`/`LadsTitleScreen189`, `PauseScreenMixin`/`TitleScreenMixin`), keybind O (26.3: replace vanilla Friends screen, keep "Manage Mojang friends" button to vanilla's), chat click intercept `/lads:social <id>`, `backupOnOpen` zips the world to `saves/../lads-backups/<world>-<yyyyMMdd-HHmm>.zip` before publishing (keep newest 5), toasts drawn on the HUD layer, `SocialBridge.set(...)` at client init next to `LadsGameBridge.set`. 26.3 extra: Mojang friends section via authlib `FriendsService` (display-only).
- [ ] javap-verify every mixin target name/descriptor from the mapped jar; list them in the report.
- [ ] Build the version; commit. In-game QA is Claude's (two instances, offline QA mode).

### Task Q (Claude): integration + QA
- [ ] Merge S1-S7 into `work/p2p-social`, full builds + tests.
- [ ] Local backend (`--dev-insecure-auth`), host on Arash-PC + guest on Legion, per version: friend add/accept, presence, DM, invite flow, join-request flow, direct path, kick/ban, host closes world, relay path once TURN key exists; screenshots to `artifacts/p2p/progress/<date>/`.
