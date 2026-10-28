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

### presence
```json
{"t":"presence","full":true,"add":["069a79f4-44e9-4726-a5be-fca90e38aaf5"],"remove":[]}
```
- UUIDs (dashed or 32 hex) of Lads players on this server, for the badge. `full:true` replaces the set, else add/remove.
  Max 1,000 entries per message.

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
- Sent once per server `hello` received (max 2 per connection). Nothing else about the player: no mods, settings, hardware.

### ack
```json
{"t":"ack","rev":7,"ignored":["Fullbright.Foo","DiscordRpc"],"refused":["Crosshair"]}
```
- After applying each `policy`. `ignored`: unknown or never-controllable entries (`Module` or `Module.Option`), max 64.
  `refused`: forced changes the player turned off with "Let servers change my Lads settings" (only `on` and `value` rules are
  refusable; `block`, `min`/`max`, `choices` always apply), max 64.

## 4. Client state rules

- One `ServerPolicy` snapshot (or none) + one presence set + `serverName` + `sessionOk` flag (set by a valid server hello).
- Reset (drop all, `sessionOk=false`) on: disconnect; every JoinGame/Login packet; 26.3 start of configuration phase;
  server `reset`; a new server `hello` (drops the old policy, then replies); a Respawn whose hashed seed differs from the last
  JoinGame/Respawn seed. A Respawn with the same seed (death, dimension change) is not a reset.
- Rate limit: max 8 incoming messages per second; extra ones dropped (the next full `policy` fixes any gap).
- Nothing from a policy is ever written to disk. Config files only ever hold the player's own values.

## 5. Version handling

- `v` = protocol major. Client speaks v1. If the server's `v` is higher, the client still replies with `v:1`; the server must
  then speak v1 to it. A server receiving `v` it doesn't know treats the player as non-Lads.
