# LadsClientAPI wire protocol v1 (FROZEN 2026-10-18)

Lanes implement exactly this. Changing it requires Claude's sign-off. Design reasons are in PLAN-LadsClientAPI.md.

## 1. Transport

- Channel: `lads:api` (same string on 1.8.9, 1.20.1, 26.3; Via passes it unchanged).
- Payload: one UTF-8 JSON object per plugin message, no length prefix, no compression. `"t"` = message type.
- Limits: client->server <= 32,000 bytes; server->client <= 524,288 bytes. Bigger = dropped by the receiver.
- Both sides ignore unknown `t` values and unknown fields (forward compatible). Malformed JSON = dropped, never a crash/kick.
- The **client never sends first**. It does not register `lads:api` in `minecraft:register`/`REGISTER`. It only sends after a valid
  server `hello` in the current session.
- Paper: before the first send to a player, the server adds `lads:api` to `((CraftPlayer) p).channels()` (live set; reflective
  call on the public method), otherwise `sendPluginMessage` drops it. Velocity forwards `lads:api` both ways as is.
- Velocity `proxy-rules-servers: [name, ...]` (plugin 1.4.0): the proxy itself runs hello/policy for a player while the player's backend is
  listed (rules.yml `worlds.<backend name>`, for a backend with no plugin such as PicoLimbo), and forwards `lads:api` untouched for every
  other backend. On a switch the proxy sends `reset` (before the new backend's JoinGame); a listed target then gets a fresh hello, any other
  drops the proxy's session so that backend's own plugin takes over. The client answers a valid hello on `lads:api` whether or not any
  backend registered the channel. `proxy-rules: true` still means every backend.

## 2. Server -> client

### hello
```json
{"t":"hello","v":1,"server":"Lads SMP","caps":["policy","presence","reset"]}
```
- `v`: server protocol version (int). `server`: display name for the GUI, <= 32 chars (client truncates). `caps`: informative.
  Plugin 1.5.0 also lists the minigame messages it may send (`rumble`, `camera`, `fx`, `lock`, `waypoint`, `telegraph`, `hud`, `audio`,
  `profile`, `run`, `ghost`, `action`, `dialog`) and, when `input-device: true` in config.yml (default), `input`. Of these only `input`
  is more than information (section 9). Sections 8 to 21.
- Sent 1 tick after join (Paper `PlayerJoinEvent` + 1 tick, Fabric `ServerPlayConnectionEvents.JOIN`, Forge `PlayerLoggedInEvent`),
  again after 60 ticks if no client `hello` came back, then never again for that connection. Every new backend (proxy switch)
  starts over.
- Optional additions (client 1.13.0; older clients ignore them, a hello may leave them out): `game`: the game or lobby the player
  is in, <= 32 chars. `hub`: the command that goes to the hub, e.g. `/hub` (one word plus at most one argument). The client acts
  on both only on The Lads Network (`arashyt.ca`), and drops `game` on a server switch or reset until the next hello.
- Optional additions (plugin 1.2.0, client 1.13.9; section 6): `nonce` and `sid`, 32 hex characters each (a client takes 16-64).
  `nonce` is a random number for this player's session on this server, `sid` a random id of this server run. Older clients
  ignore both and stay unverified.

### policy
Full snapshot. Replaces the previous one entirely (never a diff). Sent after the client's `hello`, then on every change
(world change, permission-group change, reload, API call).
```json
{"t":"policy","rev":7,"unlisted":"inherit",
 "modules":{"Fullbright":"allow","XaeroMinimap":"block","Crosshair":"on"},
 "options":{
   "Fullbright":{"Brightness":{"max":10}},
   "Crosshair":{"Style":{"value":"Cross","lock":true},"Color":{"value":"#FF0000"}},
   "1.7 Animations":{"1.7 sword block pose":{"value":false}}
 },
 "notice":"Lads SMP: fullbright capped at 10%"}
```
- `rev`: int >= 1, increases per player per connection. Client echoes it in `ack`.
- `silent` (optional bool, plugin 1.6.0, client 1.14.2): `true` = the client shows no "limits N Lads settings" toast for this snapshot and closes one that is up. Old clients ignore it. Set by `LadsScope.silent(true)` while any active scope is silent.
  The HUD option `Hide Crosshair` (`options.HUD`, client 1.14.2) hides the vanilla crosshair entirely: `LadsScope.hideCrosshair()`.
  View lock (client 1.14.9, cap `guiview`, 26.3 only): `HUD` options `Lock View` (bool), `Lock Yaw`/`Lock Pitch` (degrees the camera is frozen at), `Cursor Gain` (canvas px per degree), `Cursor Size` (canvas px), `Click X`/`Click Y` (0..1). While `Lock View` is on the camera shows the frozen rotation and the client draws the page cursor itself (glyph U+E001 of `lads:ladsgui`) at gain times the player's own yaw/pitch; the server must not spawn its shader cursor for such players. Session only, never saved.
- `unlisted`: `inherit` (default; Lads built-in rules) | `allow` | `block` for modules not in `modules`.
  Since client 1.12.0 `allow` via `unlisted` is the same as `inherit` (anti-ban: it lifted gated automation the list missed):
  a module whose safety block should lift must be listed by name.
- `modules`: name -> `allow` | `block` | `on`.
  - `block`: module off; player can't turn it on; no "own risk" bypass.
  - `allow`: lifts the client's own safety block (HypixelSafetyManager BANNED/UNKNOWN) on this server; player still chooses.
  - `on`: forced on. Only for server-controllable modules (not automation, not Discord RPC). Otherwise ignored + reported.
    Options of those modules, and Chat's Translate Chat / Translate To, take no rules either (ignored + reported).
- `options`: module name -> option name -> rule (nested because option names contain dots). Rule fields, all optional:
  | field | Bool / PlayerAction | Slider | Dropdown | Color |
  |---|---|---|---|---|
  | `value` | `true`/`false` | number (clamped to the option's own min/max, then step) | choice string, exact | `"#RRGGBB"` or `"#AARRGGBB"` (alpha raised to >= 0x40) |
  | `min`/`max` | - | numbers; player's own value clamped into [min,max] ∩ option range | - | - |
  | `choices` | - | - | list of allowed choice strings; own value not in list -> first allowed | - |
  | `lock` | greys the option in the GUI (implied when `value` is set) | same | same | same |
  Text, IntText, Action, Sound*, Profiles, ParticleReduction options: **never** controllable -> ignored + reported.
  Out-of-range dropdown/choice, NaN/Inf numbers, bad colors: that rule ignored + reported; the rest still applies.
- Name matching: exact first, then case-insensitive, against registered module names and that module's option names.
  Unknown names: ignored + reported.
- `notice`: optional, <= 256 chars, shown once in the join toast.
- Scopes (plugin 1.1.0, no wire change): which rules go into a snapshot is the server's business. The plugin merges, later wins:
  rules.yml `modules` -> `worlds.<world>` -> `gamemodes.<survival|creative|adventure|spectator>` (Paper) -> `groups.<permission>`
  -> each scope the player is in, oldest first (`scopes.<name>`, then the rules a plugin gave that scope) -> per-player API
  overrides. A scope is switched on and off per player by a plugin (`LadsPlayer.scope("duel")`, `.expireIn(seconds)`, `.end()`)
  or by `/ladsapi scope <player> <name> [seconds|off|--keep]`. When a world, game mode or scope changes or a timer runs out, the server
  sends the new full snapshot, so every client version follows scopes and the client keeps no timers.
- Per-player settings (plugin 1.4.0, no wire change): `/ladsapi set <player> <target> <rule>` lasts until the player logs out (the
  session is dropped on quit; on a Paper backend a proxy server switch is a quit there and a join on the next backend, on a
  Velocity proxy with `proxy-rules: true` the session, and so the rule, survives the switch). With `--keep` anywhere after the
  player the rule is also saved in `plugins/LadsClientAPI/players.yml` (by UUID, last known name beside it) and applied on every
  later login: it is the base of the per-player layer, and a temporary `set` on the same module or option wins while the session
  lasts. `/ladsapi scope <player> <name> --keep` keeps a scope the same way (not with seconds; `off` removes it).
  `/ladsapi clear <player> [Module]` drops temporary and kept rules (all of them, or one module; no module also drops kept
  scopes) and works for an offline player's kept rules by name or UUID. `inspect` lists kept rules and scopes apart from
  temporary ones. players.yml is written atomically, read at start and on `/ladsapi reload`, validated like rules.yml, and
  rules.yml is never touched. A `worlds` key ending in `*` matches every world with that prefix (`"witp_*"`; an exact name wins).
- Settings that are no module's (client 1.13.9, no wire change): two names are accepted in `options` only, never in `modules`.
  An older client reports them as unknown (ignored), like any other name.
  - `HUD`: `Scale` (slider 0-16, 0 = follow the GUI scale), `Text Shadow`, `Soft Shadow`, `Backgrounds` (bools).
  - `Minecraft` (1.20.1, 1.21.11, 26.x; ignored + reported on 1.8.9): `FOV` (30-110), `Brightness` (0-100 %, so `max: 100` also
    caps a gamma above 100 %), `Render Distance` (2-32), `View Bobbing`, `Entity Shadows`.
    Since client 1.13.28 also every vanilla option and keybind (mods' keys too) by its options.txt name, typed as the client
    sees it: on/off options take `true`/`false`; number options a number (whole numbers in their vanilla range, 0.0-1.0 for
    percent sliders like `gamma`, `chatOpacity`, `soundCategory_master`), `min`/`max` work too; choice options a lowercase name
    (`renderClouds`: `off`/`fast`/`fancy`, `particles`: `all`/`decreased`/`minimal`), `choices` works too; keybinds
    `key_<name>` take the key as options.txt writes it (`key.keyboard.f`, `key.mouse.left`, `key.keyboard.unknown` = unbound).
    Names differ by version: fancy leaves are `cutoutLeaves: true` on 1.21.11 and 26.x (`graphicsPreset` there) but
    `graphicsMode: fancy` on 1.20.1; `enableVsync`, `maxFps` (260 = unlimited) on all. Never: `fullscreen`,
    `exclusiveFullscreen`, `macFullscreenMenuVisibility`, `preferredGraphicsBackend`, `soundDevice`, `telemetryOptInExtra`,
    `onboardAccessibility`. Names this game version does not have are reported as ignored. Changing a rule reruns vanilla's
    own reaction (chunk reload, GUI resize, sound volume). Example:
    `"Minecraft":{"renderClouds":{"value":"off"},"cutoutLeaves":{"value":true},"key_key.drop":{"value":"key.keyboard.unknown"}}`.
  Example: `"options":{"Minecraft":{"Brightness":{"max":100}},"HUD":{"Backgrounds":{"value":false}}}`.
  As with module options the value only lives in the snapshot: options.txt and the Lads config keep the player's own value.

### presence
```json
{"t":"presence","full":true,"add":["069a79f4-44e9-4726-a5be-fca90e38aaf5"],"remove":[]}
```
- UUIDs (dashed or 32 hex) of Lads players on this server, for the badge. `full:true` replaces the set, else add/remove.
  Max 1,000 entries per message.

### toast
```json
{"t":"toast","title":"The Lads Network","message":"Bedwars event in 5 minutes"}
```
- A toast on the player's client (title <= 64, message <= 256 chars), sent by `/ladsapi announce`. Unless "Show server notice" is off.

### reset
```json
{"t":"reset"}
```
- Drop policy and presence now. Sent by a proxy install on every backend switch (`ServerConnectedEvent`), and by a server on plugin disable.

### spectate
```json
{"t":"spectate","mode":"pov","target":"069a79f4-44e9-4726-a5be-fca90e38aaf5","name":"Steve","switch":true}
```
- Plugin 1.3.0, client 1.13.9; section 7. Older clients ignore it and follow the server's camera packets as before.

## 3. Client -> server

### hello (reply only)
```json
{"t":"hello","v":1,"lads":"1.9.13","mc":"26.3"}
```
- Sent once per server `hello` received (max 2 per backend/reset, max 10 per connection). Nothing else about the player: no mods, settings, hardware (the one exception is the device class of section 9, only when the server hello asks for it).
- Client 1.13.9: when the server hello carried `nonce` and `sid`, one more hello follows once the relay answered, the same
  fields plus `"token":"v1.<exp>.<sig>"` (section 6). At most one per answered server hello. An older plugin ignores it: its
  session is acked already.
- Client 1.13.9, optional `"caps":["spectate"]`: what this build of the client does itself. It names code that is in the
  game version, never a setting, a mod or anything about the player. `spectate`: section 7; client 1.14.0 adds the minigame
  caps of section 8. The plugin keeps at most 32 names (16 before 1.5.0) of `[a-z0-9_-]{1,24}` from the first hello it accepts and
  skips the rest (`LadsPlayer.can`). It is said, not
  proven: a plugin picks how to show something by it and grants nothing for it.

### ack
```json
{"t":"ack","rev":7,"ignored":["Fullbright.Foo","DiscordRpc"],"refused":["Crosshair"]}
```
- After applying each `policy`. `ignored`: unknown or never-controllable entries (`Module` or `Module.Option`), max 64.
  `refused`: forced changes the player turned off with "Let servers change my Lads settings" (only `on` and `value` rules are
  refusable; `block`, `min`/`max`, `choices` always apply), max 64.

### spectate (request)
```json
{"t":"spectate","act":"next"}
```
- Client 1.13.9; section 7. Only on a key press, and only while the server's `spectate` state offers `switch`. An older plugin ignores it.

## 4. Client state rules

- One `ServerPolicy` snapshot (or none) + one presence set + `serverName` + `sessionOk` flag (set by a valid server hello).
- Complete reset (drop all, `sessionOk=false`, clear hello reply cap) on: client disconnect.
- Server switch / reset: server `reset`, JoinGame/Login packet, 26.3 start of configuration phase,
  or a Respawn whose hashed seed differs from the last JoinGame/Respawn seed clears the active policy,
  presence and toast, resets the per-backend hello reply counter, but keeps `sessionOk=true`.
  A new server `hello` drops the old policy, then replies. A Respawn with the same seed (death) is not a reset.
- Dimension change (e.g. 1.8.9 JoinGame/Respawn): strips by-name `allow` rules (anti-ban), keeping blocks and limits.
- Rate limit: token bucket with refill rate of 8 incoming messages per second and a burst capacity of 16 tokens; extra messages dropped (the next full `policy` fixes any gap).
- Nothing from a policy is ever written to disk. Config files only ever hold the player's own values.
- The spectate state (section 7) goes wherever the policy goes: on a server `hello`, a reset, a server switch and a disconnect.
  So does all minigame state (sections 8 to 21): camera, fx, lock, waypoints, telegraphs, HUD widgets, audio, profile, run, ghosts,
  actions and open dialogs. Minigame messages have their own token bucket (section 8), not the 8 per second of the line above.

## 5. Version handling

- `v` = protocol major. Client speaks v1. If the server's `v` is higher, the client still replies with `v:1`; the server must
  then speak v1 to it. A server receiving `v` it doesn't know treats the player as non-Lads.

## 6. Client verification (plugin 1.2.0, client 1.13.9)

Any program can send the client's `hello`, fake `minecraft:brand` or register `lads:api`, so none of them proves the Lads Client.
A verified session adds a proof from the Lads relay (`bot.arashyt.ca`, cosmetics-server/cosmetics.py), where the client's
Minecraft account is proven the way a server proves it (Mojang `join` / `hasJoined`). Only optional fields were added: v stays 1.

1. The server hello carries `nonce` (one per session) and `sid` (one per server start).
2. The client replies with its normal hello at once, so policy and presence do not wait. Then it asks the relay over its
   cosmetics socket: `{"t":"attest","nonce":"...","sid":"..."}`.
3. The relay answers `{"t":"attest","nonce":"...","token":"v1.<exp>.<sig>"}`. `exp` is unix seconds, 120 s ahead. `sig` is the
   base64url (no padding) Ed25519 signature over the UTF-8 text `lads-attest-v1|<uuid, 32 hex>|<nonce>|<sid>|<exp>`, where the
   uuid is the account Mojang proved to the relay on that socket, never one the client names.
4. The client sends the second hello with `token`. The plugin builds the same text from the player's uuid, its own nonce and
   sid and the token's `exp`, checks the signature with the relay's public key, then the expiry (60 s of clock difference
   allowed), and marks the session verified. A token for another player, hello or server fails the signature. Only the first
   8 tokens of a session are checked.

The relay link can come up late (a relay restart, a slow network). The client keeps its request until the relay answers and
gives up only on a relay that was asked and stayed silent for 30 s, so the token may follow the first hello by minutes; the
plugin takes it at any time in the session. A world change on the same server keeps the wait. Another backend, a proxy
`reset` or a disconnect ends it: a token that arrives later is dropped, never sent to a server that did not ask.

- For servers: `LadsPlayer.verified()`, `LadsClientAPI.verified(uuid)`, `%ladsapi_verified%`, Paper `LadsPlayerVerifiedEvent`,
  `/ladsapi status` and `inspect`. Perks belong to verified players: a perk plugin asks `LadsClientAPI.perks(uuid)`, which is
  `verified(uuid)` unless the server sets `perks-need-verified: false` (then any client that answered the hello, for the time
  before every player has a client that can prove itself) or does not check at all. `require-lads-client: true` kicks a client that is still
  unverified `verify-timeout` (15) seconds after it joined, with `require-verified-message`. `verify-client: false` turns all
  of it off: no nonce is sent and the hello is all there is, as before 1.2.0.
- Unverified, never kicked for it unless `require-lads-client` is on: a client before 1.13.9, a client that cannot reach the
  relay, an account Mojang cannot prove (offline accounts; a server or proxy in offline mode, whose uuids are not Mojang's),
  a server on Java below 15 (no Ed25519: it sends no nonce).
- Kept: the client never sends first. It asks the relay only after a server hello with a nonce, as often as it replies to
  hellos at most, and tells it two random strings: nothing about the server, and the relay stores and logs neither. The game
  server learns nothing it did not know. No new message type on `lads:api`.
- Keys: the relay's private key is one file on the relay host, never in git, a jar or a plugin. The public key is in the plugin
  (`AttestVerifier.RELAY_KEYS`); `relay-public-keys` in config.yml replaces it, for a key change without a plugin update.
- The limit: a token proves that a Mojang-proven session of this account is connected to the Lads relay right now. It does not
  prove which program runs on the player's PC, and nothing can on a PC the player owns: someone who rebuilds the relay
  protocol with their own account gets a token too. It stops the cheap fakes (a copied hello, a brand, a channel).

## 7. Spectating (plugin 1.3.0, client 1.13.9)

A game plugin's spectator mode is made of ordinary packets: the spectator flies invisible, the server points the camera at the
watched player (`SetCamera`) and mirrors that player's hotbar and health. That keeps working for every client. The `spectate`
message only tells a Lads Client what those packets mean, so it can hold the camera, draw a spectator HUD and offer keys for
the next and previous target. v stays 1: one new message type each way, which older plugins and clients ignore.

Server -> client, the full state every time (never a diff):
```json
{"t":"spectate","mode":"pov","target":"069a79f4-44e9-4726-a5be-fca90e38aaf5","name":"Steve","switch":true}
{"t":"spectate","mode":"free","switch":true}
{"t":"spectate","mode":"off"}
```
- `mode`: `pov` (the camera is in the target's eyes), `free` (spectating in free flight, no target), `off`.
- `target` (pov): uuid, dashed or 32 hex, of the player entity the server's camera packet points at. The client attaches its
  camera to that entity while it is in its world, and to nothing else.
- `name`: optional text for the HUD, <= 32 chars (the client cuts it and strips formatting codes). Without it the HUD shows the
  entity's own name. An NPC's shown name can differ from its entity name.
- `switch`: optional, `true` when the server takes the request below. Anything but a JSON `true` is false.
- A message the client cannot use is `off`: an unknown `mode` from a newer plugin, a `pov` without a valid `target`.
- The plugin sends a state only when it differs from the last one, and sends the current one again after every later `hello` of
  the session (a world change), because the client dropped it there.

Client -> server:
```json
{"t":"spectate","act":"next"}
```
- `act`: `next` or `prev`. Nothing else: the client names no player, the server picks the target and answers with a new state
  (or with nothing). Sent only when the player presses the key, at most one per 200 ms, and only while the current state is
  not `off` and has `switch`. The plugin drops it from a session that was not offered the switch.
- With `proxy-rules: true` the proxy takes every client message, so the request never reaches a backend: no switcher there.

Which camera the game plugin uses (plugin 1.4.0):
- A client that does not say `caps: ["spectate"]` (vanilla, Bedrock, an older Lads Client, a game version without the camera
  code) is shown the plugin's own spectator mode, unchanged. PvPArena moves that spectator into the target's eyes every tick,
  because a vanilla client with its camera on another player draws only that player's held item.
- A client that says it gets the vanilla camera packet on the target instead. It holds the camera there and draws hotbar,
  hearts and bars from its own player, which the server fills with the target's as before. The plugin sends this client
  nothing a vanilla client would not get in the same mode (PvPArena: `pov.java-self-camera: false`).

What stays as it is:
- No information is added. The client learns one uuid, of an entity the server already shows it and already points its camera
  at, and a name for it. It gets no positions, no inventories, no list of players. Hotbar, health and effects on screen are
  the ones the server mirrors to every spectator.
- The client does nothing by itself: no camera change without a state from the server in this session, no packet without a
  key press. Leaving is the game plugin's own way (PvPArena: sneak), which a vanilla client has too.
- The client never sends first: the request follows a server `hello` and a server `spectate` of the same session.

For servers: `LadsPlayer.spectate(target, name, canSwitch)`, `spectateFree(canSwitch)`, `spectateEnd()`; a call that changes
nothing sends nothing, so a spectator loop may call it every tick. Paper fires `LadsSpectateRequestEvent` (`isNext()`); other
platforms use `SessionManager.addSpectateListener`.
## 8. Minigame features (plugin 1.5.0, client 1.14.0)

Servers that run a game (PvPArena, parkour, Bedwars) can control a Lads Client player's screen and input in ways vanilla
packets cannot: cinematic cameras, screen effects, input locks, markers on the ground, HUD widgets, sounds, a run timer, server
keys and dialogs. Every feature is optional and has its own message type. v stays 1. A feature works only when both sides said
so: the client names a cap in its hello `caps` (section 3), and the plugin sends that feature's messages only to a session that
did. A client, version, Bedrock player or vanilla player without the cap gets nothing and just plays the game without it.

| client cap | server -> client | client -> server |
|---|---|---|
| `input` | (server hello cap `input` asks for it) | `input` (9) |
| `rumble` | `rumble` (10) | - |
| `camera` | `camera` (11) | - |
| `fx` | `fx` (12) | - |
| `lock` | `lock` (13) | - |
| `waypoint` | `waypoint` (14) | - |
| `telegraph` | `telegraph` (15) | - |
| `hud` | `hud` (16) | - |
| `audio` | `audio` (17) | - |
| `profile` | `profile` (18) | - |
| `run` | `run` (19) | - |
| `ghost` | `ghost` (19) | - |
| `action` | `actions` (20) | `action` (20) |
| `dialog` | `dialog` (21) | `dialog` (21) |

The server hello `caps` lists the same names (informative), except `input`, which is the request for section 9.

### 8.1 Rules for every feature

- **Gating.** The client acts on a feature message only when it named that cap itself and a valid server `hello` of this
  session came first. The plugin sends one only to a session whose client named the cap, and drops a client message of a
  feature the client did not name.
- **Client never sends first, never unasked.** A client message follows a server hello and the server message that offers it
  (`input`: the hello's `caps`; `action`: an `actions` list with that id; `dialog`: an open dialog with that id). Each is sent
  only on a real key press, click or device change by the player, never by code on a timer.
- **Memory only.** Nothing is written to disk by the client because of a server message. State is dropped exactly where policy
  and spectate state are dropped (section 4): server `hello`, `reset`, server switch, disconnect. The plugin sends its current
  state again after every later hello (full-state messages, no diffs).
- **Everything with a duration is clamped and ends by itself.** `ms` is relative to the moment the client received the message,
  on the client's own clock. A dead or silent server can never leave a player stuck.
- **Full state.** `camera`, `fx`, `lock`, `profile`, `actions` replace what the previous message of that type did. The list
  messages (`waypoint`, `telegraph`, `hud`, `audio`, `ghost`, `dialog`) carry one `op` and change one item by `id`. The plugin
  sends a full-state message only when it differs from what the client has.
- **Numbers.** JSON numbers, finite. Out of range = clamped. Wrong type, NaN or a missing optional field = the default named
  below. A missing required field, an unknown `op`/`mode`/`shape`/`kind`/`style` or a bad `id` = the message (in a list: that
  item) is ignored: never a crash, never a kick (one exception: an unknown `camera` mode ends the running shot, section 11).
  Arrays are cut to their maximum. Unknown fields are ignored.
- **Names.** `id`: `[a-z0-9_-]{1,32}` (24 for actions and buttons). Text: formatting codes and control characters stripped,
  cut at the limit shown. Item and sound ids: `namespace:path`, no namespace = `minecraft`. Colors: `"#RRGGBB"`; a bad color
  takes the field's default.
- **Coordinates.** x, y, z are doubles in the dimension the player is in now. `yaw`, `pitch` in degrees as in the game: yaw 0
  looks toward +Z (south), 90 toward -X (west); pitch -90 up, +90 down. Yaw interpolates along the shorter arc.
- **Rate limits.** Minigame messages from the server share one client token bucket of 40/s, burst 80, per session; extra ones are
  dropped. Client -> server limits are in sections 9, 20, 21; the plugin applies them too and drops the rest silently. These
  messages do not use the 8/s `hello`/`ack` limiter.
- **Accessibility.** A client with a reduce-flashes option ignores `flash` and `pulse_ms` (section 12). The player's own volume and
  vibration settings always apply and are never exceeded.
- **Never added by any of this:** no client packet except sections 9, 20, 21 (a key press, a click, one device class); no
  automated input or clicks; no information the server did not itself hand the client in the message or the client does not
  already render. See "What stays as it is" under each feature.

## 9. Input device: `input` (cap `input`)

The one deliberate exception to "nothing about hardware" in section 3: a game that shows key prompts needs to know whether the
player holds a keyboard or a gamepad.

Server hello `caps` contains `input` (plugin `input-device: true`, the default). Without it the client sends no `input`.
```json
{"t":"input","device":"gamepad","pad":"playstation"}
{"t":"input","device":"kbm"}
```
- `device`: `kbm` or `gamepad` (the device the player used last). `pad` (only with `gamepad`): `xbox`, `playstation`, `switch`,
  `generic`: a class, never a device name, vendor id, GUID or serial.
- Sent once after every server `hello` that carried `input`, and again when device or pad class changes: at most 1 per 5 s, the
  latest value wins. The plugin takes the first one after each hello, then at most 1 per 4 s, and drops the rest or anything
  when the hello did not carry `input`. Unknown `device` = dropped; unknown `pad` = `generic`.
- What stays as it is: one word about the device the player just used. Nothing about the PC, mods or settings. Servers with
  `input-device: false` never ask.

## 10. Rumble: `rumble` (cap `rumble`)

```json
{"t":"rumble","low":0.8,"high":0.4,"ms":250}
```
- `low` heavy motor, `high` light motor, both 0-1 (default 0). `ms` 1-2000, required. A new rumble replaces a running one;
  both 0 stops it. Not a state: not sent again after a hello.
- Played only while a gamepad is the active device and the player's vibration setting is on (scaled by it). Else dropped.
- What stays as it is: output only, nothing is sent back.

## 11. Camera: `camera` (cap `camera`)

Visual only: the real position, hitbox, movement and packets are untouched. The client draws only the chunks and entities it
already has.
```json
{"t":"camera","mode":"spline","ms":5000,"ease":"smooth","fov":70,"bars":true,"hide_hud":true,
 "points":[{"x":100.5,"y":75,"z":200.5,"yaw":180,"pitch":10},{"x":120,"y":68,"z":210,"yaw":220,"pitch":5}]}
{"t":"camera","mode":"orbit","x":0.5,"y":65,"z":0.5,"radius":6,"height":2,"period_ms":8000,"clockwise":false,"ms":10000}
{"t":"camera","mode":"orbit","entity":"069a79f4-44e9-4726-a5be-fca90e38aaf5","radius":4,"ms":6000}
{"t":"camera","mode":"fixed","x":10,"y":70,"z":10,"yaw":90,"pitch":20,"ms":3000}
{"t":"camera","mode":"fixed","x":10,"y":70,"z":10,"look_at":{"x":0,"y":65,"z":0},"ms":3000}
{"t":"camera","mode":"off"}
```
- `mode`: `spline`, `orbit`, `fixed`, `off`. Full state: a new message replaces the running shot; `off` ends it. Unknown mode = `off`.
- `ms` 100-30000, required except for `off`. The shot ends by itself after `ms` and the view returns at once.
- `spline`: `points` 1-32 of `{x,y,z,yaw,pitch}` (x,y,z required; yaw/pitch default to the previous point's, the first to the
  player's own). One point = `fixed`. Catmull-Rom path, equal time per segment. `ease`: `linear` (default) or `smooth`.
- `orbit`: centre `x,y,z`, or `entity` (uuid of an entity the client already has; the shot is ignored/ends if it has not/unloads).
  `radius` 1-64 (default 6), `height` -32..64 above the centre (default 2), `period_ms` 1000-60000 (default 8000) per turn,
  `clockwise` (default false). Starts on the +Z side of the centre and looks at it.
- `fixed`: stands at `x,y,z` (required), looks along `yaw`,`pitch` (default: the player's own), or at `look_at` `{x,y,z}`, or
  `look_entity` (uuid; if not loaded the previous direction is kept).
- All modes: `fov` 30-110 (default: the player's own); `bars` (default false) 12 % letterbox, 300 ms fade; `hide_hud`
  (default false) hides vanilla and Lads HUD and the held item while the shot runs.
- A camera does not lock input (use `lock`, section 13). Esc/pause menu and disconnect always work. A point more than 512 blocks
  from the player's real position is clamped to that distance.
- What stays as it is: it only looks at what the client already has. Nothing is loaded, revealed or sent.

## 12. Screen effects: `fx` (cap `fx`)

```json
{"t":"fx","ms":3000,"fade_ms":300,"shader":"blur","vignette":{"color":"#FF0000","intensity":0.6,"pulse_ms":800},
 "tint":{"color":"#2244FF","intensity":0.2},"flash":{"color":"#FFFFFF","ms":400}}
{"t":"fx"}
```
- Full state. `{"t":"fx"}` clears at once. Without a valid `ms` only `flash` plays.
- `ms` 1-60000: how long the lasting effects (`shader`, `vignette`, `tint`) stay; a new message restarts the time. `fade_ms` 0-5000
  (default 0, at most half of `ms`): fade in and out.
- `shader`: `none` (default), `blur`, `grayscale`, `sepia`, `invert`. A shader the game version cannot do is skipped, the rest applies.
- `vignette` `{color (#000000), intensity 0-1 (0.5), pulse_ms 0-10000 (0 = steady; else intensity swings 50-100 %)}`.
- `tint` `{color, intensity 0-1 (0.3)}`, drawn at most at 80 % opacity: a lasting effect never covers the screen fully.
- `flash` `{color (#FFFFFF), ms 1-2000 (300)}`: one-shot per message that has it, fades out linearly. Not state: not resent after a
  hello. Ignored with reduce-flashes.
- What stays as it is: visual only, nothing hidden is shown. Blindness does the same to a vanilla player.

## 13. Input lock: `lock` (cap `lock`)

Replaces Slowness 255 and teleport spam for countdowns and cutscenes.
```json
{"t":"lock","ms":5000,"move":true,"jump":true,"attack":true,"use":true}
{"t":"lock"}
```
- Booleans (default false; anything but JSON `true` is false): `move`, `jump`, `sneak`, `sprint`, `look` (yaw and pitch stay
  fixed), `attack`, `use`, `drop`, `hotbar` (number keys, scroll, swap hands), `inventory`.
- `ms` 1-15000, required; the lock ends by itself. A new message replaces it. No `ms`, all false, or `{"t":"lock"}` = unlock.
- Never blocked, whatever is set: Esc and the pause menu, chat and commands, the Lads GUI key, disconnecting. The server still
  has to enforce movement and attacks itself.
- What stays as it is: it only removes input the player could give. Nothing is generated, no packet is sent.

## 14. Waypoints: `waypoint` (cap `waypoint`)

```json
{"t":"waypoint","op":"set","id":"flag_red","x":10.5,"y":65,"z":-3.5,"label":"Red flag","color":"#FF5555",
 "icon":"minecraft:red_banner","distance":true,"beam":true,"through_walls":false}
{"t":"waypoint","op":"set","id":"mate_steve","entity":"069a79f4-44e9-4726-a5be-fca90e38aaf5","label":"Steve","icon":"diamond"}
{"t":"waypoint","op":"remove","id":"flag_red"}
{"t":"waypoint","op":"clear"}
```
- `op`: `set` (adds or replaces the same `id`), `remove`, `clear`. Max 64 per player; a new id beyond that is ignored.
- Position `x,y,z`, or `entity` (uuid; wins when both are there). An entity marker follows the entity only while the client has it
  loaded; otherwise it draws nothing and learns no position. `through_walls` is ignored for entity waypoints.
- `label` <= 32 chars, `color` (#FFFFFF), `icon`: an item id, or a glyph `diamond` (default), `flag`, `bed`, `skull`, `star`,
  `arrow`, `shield`, `sword`, `question`, `exclamation`, `circle`. `distance` (default true) shows "12 m". `beam` (false) a light
  column. `through_walls` (false) draws a coordinate marker behind blocks too.
- They do not expire; the server removes them or the session state ends.
- What stays as it is: a coordinate marker shows a position the server just chose to show (flag, bed). An entity marker shows
  nothing the client does not render. No radar. Where the client's own safety rules block radar-like information
  (HypixelSafetyManager) `through_walls` is not drawn.

## 15. Telegraphs: `telegraph` (cap `telegraph`)

```json
{"t":"telegraph","op":"set","id":"slam1","shape":"circle","x":10.5,"y":64,"z":8.5,"radius":4,"color":"#FF0000","ms":1500,"fill_ms":1200}
{"t":"telegraph","op":"set","id":"beam","shape":"line","x":0,"y":64,"z":0,"yaw":90,"length":20,"width":2,"ms":2000}
{"t":"telegraph","op":"remove","id":"slam1"}
{"t":"telegraph","op":"clear"}
```
- `op`: `set` (default), `remove`, `clear`. Max 64; a new id beyond that is ignored.
- `shape` (required): `circle` (centre, `radius` 0.5-64), `ring` (plus `inner` 0-radius, default 0.75 x radius), `cone` (apex at
  x,y,z, `radius` 0.5-64 as length, `angle` 1-360 full opening, default 90, along `yaw`), `line` (from x,y,z along `yaw`,
  `length` 0.5-64, `width` 0.1-64, default 1), `rect` (centre, `length` along `yaw`, `width`). `yaw` default 0.
- `color` (#FF0000), outline 80 % and fill 35 % opacity. `ms` 100-30000, required: drawn flat at `y` + 0.02 with depth testing and
  gone after `ms`. `fill_ms` 0-`ms` (0 = filled at once): the fill grows outward and is complete at `fill_ms` (the hit lands).
- What stays as it is: only geometry the server just sent, depth-tested, never through blocks, no entity information.

## 16. HUD widgets: `hud` (cap `hud`)

```json
{"t":"hud","op":"set","id":"rocket","kind":"cooldown","title":"Rocket Blast","icon":"minecraft:firework_rocket","remaining_ms":3200,"total_ms":8000,"charges":2,"slot":1}
{"t":"hud","op":"set","id":"rounds","kind":"pips","title":"Rounds","states":[{"color":"#55FF55","filled":true},{"filled":false}]}
{"t":"hud","op":"set","id":"cp","kind":"meter","title":"Point A","value":0.62,"color":"#5555FF","left":"RED","right":"BLUE"}
{"t":"hud","op":"set","id":"info","kind":"text","title":"Bed status","lines":["Red: Active","Blue: Broken"]}
{"t":"hud","op":"set","id":"clock","kind":"timer","title":"Round","ms":90000,"direction":"down"}
{"t":"hud","op":"remove","id":"rocket"}
{"t":"hud","op":"clear"}
```
- `op`: `set` (default), `remove`, `clear`. Max 16 widgets; a new id beyond that is ignored. A widget stays until removed.
- Common: `title` <= 24, `color` (#FFFFFF), `slot` 0-15 (order in the group), `group` (`[a-z0-9_-]{1,16}`, default `main`, max 4
  groups). Each group is one element of the Lads HUD editor; the player places, scales and hides it.
- `cooldown`: `icon` (item id), `remaining_ms` 0-3,600,000, `total_ms` 1-3,600,000, `charges` 0-99 (optional).
- `pips`: `states` <= 12 of `{color (#FFFFFF), filled (false)}`.
- `meter`: `value` 0-1, `left`, `right` labels <= 16 chars.
- `text`: `lines` <= 6 of <= 48 chars.
- `timer`: `ms` 0-86,400,000, `direction` `down` (default, stops at 0:00) or `up`.
- `cooldown` and `timer` are counted by the client from receipt. Unknown `kind` = ignored.
- What stays as it is: numbers and words the server sent, in a box the player placed or hid. Nothing is sent back.

## 17. Audio: `audio` (cap `audio`)

The sound is an event the client already has (vanilla or an installed resource pack). Nothing is fetched: no URL, file or stream.
```json
{"t":"audio","op":"play","id":"bgm","sound":"lads:music.arena","category":"music","volume":0.7,"pitch":1,"loop":true,"fade_ms":2000,"music_pause":true,"ms":600000}
{"t":"audio","op":"play","id":"boom","sound":"minecraft:entity.generic.explode","category":"sfx","x":10,"y":64,"z":5}
{"t":"audio","op":"fade","id":"bgm","volume":0.2,"fade_ms":3000}
{"t":"audio","op":"stop","id":"bgm","fade_ms":1500}
{"t":"audio","op":"stop"}
```
- `play`: `id`, `sound` (`ns:path`, <= 128 chars, `[a-z0-9_./-]`, unknown event ignored), `category` `music` | `ambient` | `sfx`
  (default; plays in the player's Players slider), `volume` 0-1 (1) times the player's own volume, `pitch` 0.5-2 (1), `loop`
  (false), `fade_ms` 0-10000 (0), `x,y,z` (all or none; none = non-positional), `music_pause` (false: vanilla music held quiet),
  `ms` 1-3,600,000 (600,000: a loop ends after it). The same `id` restarts.
- `fade`: `id`, `volume` 0-1, `fade_ms` 1-30000; volume 0 then stops it. `stop`: `id` (none = all), `fade_ms` 0-10000.
- Max 8 at once; a `play` beyond that is ignored.
- What stays as it is: sound only.

## 18. Game profile: `profile` (cap `profile`)

```json
{"t":"profile","name":"PvP"}
{"t":"profile"}
```
- Full state. `name` <= 32 chars: switch to the player's own saved Lads profile with that name (case-insensitive) for this session.
  Nothing happens when the player has none of that name or turned server-driven changes off; the client never answers, so the
  server learns nothing about their profiles. `{"t":"profile"}` returns to the profile active before.
- Memory only; the previous profile returns on reset, server switch, disconnect or a hello without a new `profile`. Server policy
  and HypixelSafetyManager still apply on top.
- What stays as it is: the profile is the player's own; the server only names it.

## 19. Parkour and speedruns: `run` and `ghost` (caps `run`, `ghost`)

The server is the timer authority; the client only shows it and sends nothing.
```json
{"t":"run","op":"start","course":"easy_1","name":"Easy 1","checkpoints":10,"time_ms":0,"best":[5100,9800,14200],"best_total":61000}
{"t":"run","op":"split","course":"easy_1","cp":2,"time_ms":14650}
{"t":"run","op":"finish","course":"easy_1","time_ms":60512}
{"t":"run","op":"reset","course":"easy_1"}
```
- `op`: `start`, `split`, `finish`, `reset`. `course` `[a-z0-9_-]{1,32}` required, except on `reset`, where none = whatever runs. One run at a time; `start` replaces.
- `start`: `time_ms` 0-86,400,000 (0), counted up by the client from there. `name` <= 24, `checkpoints` 0-255 (0), `best` <= 255 cumulative
  ms values (personal best per checkpoint), `best_total`; without them no delta shows.
- `split` (`cp` 0-254, `time_ms`): corrects the clock; shows the time and the delta against `best[cp]` for 3 s (green `-0.42`,
  red `+0.18`).
- `finish` (`time_ms`): stops the clock, shows the result (delta against `best_total`) for 120 s. `reset`: hides the run. A run with no
  message for 30 min is dropped.
- The client may record and keep the player's own best locally per course and server; it never leaves the PC.
```json
{"t":"ghost","op":"play","id":"pb","course":"easy_1","name":"Record: Steve","skin":"069a79f4-44e9-4726-a5be-fca90e38aaf5",
 "part":0,"parts":2,"frames":[[0,100.5,64,200.5,90,0],[250,101.1,64,200.5,90,0]]}
{"t":"ghost","op":"stop","id":"pb"}
```
- `op` `play` (`id`, `course` required) or `stop` (`id`; none = all). Max 3 ghosts.
- `frames`: `[t_ms, x, y, z, yaw, pitch]`, `t_ms` 0-3,600,000 strictly increasing; <= 600 per message, 2,400 per ghost in `parts`
  (`part` 0-based, `parts` 1-4, default 1). A bad frame ends the list there. The ghost arms when all parts arrived in order; an
  incomplete one is dropped after 10 s.
- Replayed as a translucent player model on the run clock: starts at a `run` `start` of the same `course`, follows clock
  corrections, vanishes 1 s after its last frame, stops on `finish`, `reset`, `stop`. `name` <= 24 is its name tag. `skin`: used only
  if the client already knows it, else the default skin; nothing is fetched.
- The ghost is no entity: no collision, not sent to the server, seen by nobody else.
- What stays as it is: numbers and a recorded path the server chose to share; no live position of any player.

## 20. Server actions: `actions` and `action` (cap `action`)

```json
{"t":"actions","actions":[{"id":"ultimate","name":"Cast Ultimate","key":"key.keyboard.r","pad":"y"}]}
{"t":"actions","actions":[]}
```
- Full state, <= 8. `id` `[a-z0-9_-]{1,24}` unique; `name` <= 24; `key` (optional) an options.txt key name; `pad` (optional) by position:
  `a b x y lb rb lt rt ls rs dpad_up dpad_down dpad_left dpad_right`. The client lists them in Controls under "Server". A key
  another action already uses is not taken: the action starts unbound. Never written to options.txt; the client may remember the
  player's own rebind per server and id.
- Client -> server on the player's real press and release, only in-world with no screen and no text field:
```json
{"t":"action","id":"ultimate","down":true}
```
  `id` in the current list, `down` true on press, false on release. At most 20 per second. The plugin drops an unknown id, a
  non-boolean `down`, and anything past 20/s.
- What stays as it is: a key the player pressed with an id the server made up. Nothing about other keys, nothing automatic.

## 21. Dialogs: `dialog` (cap `dialog`)

```json
{"t":"dialog","op":"open","id":"vote1","style":"vote","title":"Pick the next map","body":"Vote ends in 20 s",
 "buttons":[{"id":"a","label":"Desert","color":"#FFAA00","icon":"minecraft:sand"},{"id":"b","label":"Snow"}],"ms":20000}
{"t":"dialog","op":"close","id":"vote1"}
```
- `op`: `open` (default; the same `id` replaces) or `close`. Max 4 open; a new id beyond that is ignored. `style`: `toast` (corner card),
  `vote` (card, click or number key), `modal` (centered, takes the mouse; Esc dismisses). Unknown style = ignored.
- `title` <= 64, `body` <= 256, `buttons` <= 6 of `{id (24), label <= 24, color, icon}` (unique ids). `ms` 1000-60000 (15000): it
  disappears by itself.
- Client -> server, once per dialog, on a click, number key or dismissal:
```json
{"t":"dialog","id":"vote1","choice":"a"}
{"t":"dialog","id":"vote1","choice":"dismiss"}
```
  `choice` = a button id or `dismiss` (Esc, close). Nothing is sent when it expired or was closed. The plugin accepts it only for
  a dialog open for that session (then closes it), at most 4/s, and only a button id of it or `dismiss`. When `ms` runs out
  unanswered the plugin itself calls the callback with `timeout` (never from the wire).
- What stays as it is: only a click or key by the player sends anything, only a button the server named. Esc, chat, pause and
  disconnect are never blocked.

## 22. Velocity and proxy rules

A proxy that rules for a backend (`proxy-rules`, `proxy-rules-servers`; section 1) owns the player's session, so it takes the
client's `input`, `action` and `dialog` itself, like `spectate`; they do not reach the backend, and the proxy does not forward
them. The backend has no session for that player, so its feature calls are no-ops and `LadsClientAPI.player(uuid)` is empty
there. With proxy rules the Minigame API is for plugins on the proxy (`SessionManager` listeners; no Velocity event yet). A
backend not ruled by the proxy keeps its own plugin and all features; `lads:api` passes through untouched. After a switch the
proxy sends its state again after the new hello.

## 23. For servers: the Minigame API (plugin 1.5.0)

Every call returns `false` (or does nothing) without the cap, clamps to the limits above, sends nothing when the state is
unchanged, and is sent again after a later hello. Main thread only.
```java
LadsClientAPI.get().player(player.getUniqueId()).ifPresent(lp -> {
    lp.rumble(0.6, 0.2, 150);
    lp.screenFx(ScreenFx.create().ms(5000).shader("grayscale").vignette("#8A0303", 0.6, 800));
    lp.lockInput(InputLock.create().move().jump().attack().use(), 3000);
    lp.camera().spline(List.of(new CameraPoint(100, 75, 200, 180, 10), new CameraPoint(120, 68, 210, 220, 5)))
      .ms(5000).smooth().bars().hideHud().play();
    lp.hud().cooldown("rocket", "Rocket Blast", "minecraft:firework_rocket", 8000, 8000, 2);
    lp.waypoints().set("flag_red", 10.5, 65, -3.5, "Red flag", "#FF5555");
    lp.telegraphs().circle("slam", 10, 64, 8, 4, "#FF0000", 1500, 1200);
    lp.actions(List.of(new ServerAction("ultimate", "Cast Ultimate", "key.keyboard.r", "y")));
    lp.dialog(Dialog.vote("vote1", "Pick the next map").button("a", "Desert").button("b", "Snow").ms(20000),
              (p, choice) -> tally(p, choice));
    String hint = lp.glyph("[R]", "[Y]", "[Triangle]", "[X]");
});
// Paper events: LadsActionEvent, LadsDialogResponseEvent, LadsInputDeviceEvent. Others: SessionManager.addActionListener etc.
```
`/ladsapi demo <player> <feature> [args]` (admin) fires a sample of every message at a player for QA; `demo <player> off` clears them.
