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

## 2. Server -> client

### hello
```json
{"t":"hello","v":1,"server":"Lads SMP","caps":["policy","presence","reset"]}
```
- `v`: server protocol version (int). `server`: display name for the GUI, <= 32 chars (client truncates). `caps`: informative.
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
  or by `/ladsapi scope <player> <name> [seconds|off]`. When a world, game mode or scope changes or a timer runs out, the server
  sends the new full snapshot, so every client version follows scopes and the client keeps no timers.
- Settings that are no module's (client 1.13.9, no wire change): two names are accepted in `options` only, never in `modules`.
  An older client reports them as unknown (ignored), like any other name.
  - `HUD`: `Scale` (slider 0-16, 0 = follow the GUI scale), `Text Shadow`, `Soft Shadow`, `Backgrounds` (bools).
  - `Minecraft` (1.20.1, 1.21.11, 26.x; ignored + reported on 1.8.9): `FOV` (30-110), `Brightness` (0-100 %, so `max: 100` also
    caps a gamma above 100 %), `Render Distance` (2-32), `View Bobbing`, `Entity Shadows`.
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
- Drop policy and presence now. Sent by a proxy install on every `ServerPostConnectEvent`, and by a server on plugin disable.

## 3. Client -> server

### hello (reply only)
```json
{"t":"hello","v":1,"lads":"1.9.13","mc":"26.3"}
```
- Sent once per server `hello` received (max 2 per backend/reset, max 10 per connection). Nothing else about the player: no mods, settings, hardware.
- Client 1.13.9: when the server hello carried `nonce` and `sid`, one more hello follows once the relay answered, the same
  fields plus `"token":"v1.<exp>.<sig>"` (section 6). At most one per answered server hello. An older plugin ignores it: its
  session is acked already.

### ack
```json
{"t":"ack","rev":7,"ignored":["Fullbright.Foo","DiscordRpc"],"refused":["Crosshair"]}
```
- After applying each `policy`. `ignored`: unknown or never-controllable entries (`Module` or `Module.Option`), max 64.
  `refused`: forced changes the player turned off with "Let servers change my Lads settings" (only `on` and `value` rules are
  refusable; `block`, `min`/`max`, `choices` always apply), max 64.

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

- For servers: `LadsPlayer.verified()`, `LadsClientAPI.verified(uuid)`, `%ladsapi_verified%`, Paper `LadsPlayerVerifiedEvent`,
  `/ladsapi status` and `inspect`. Perks belong to verified players. `require-lads-client: true` kicks a client that is still
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
