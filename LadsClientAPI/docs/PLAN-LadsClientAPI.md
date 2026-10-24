# LadsClientAPI: plan (draft 1, 2026-10-18)

One jar a server owner drops in (`plugins/` or `mods/`). It works as a Paper plugin, Velocity plugin, Fabric server mod or
Forge server mod. It lets the server allow, block, force or limit every Lads Client module and setting for players on that
server. Everything goes back to the player's own settings when they leave. It also shows a Lads Client badge (the anime girl
logo) next to Lads players in tab and above their heads.

Sources: research reports in this folder (A1 Apollo, A2 other clients, B1 proxy/Via, B2 mod networking, C one jar, D1
settings, D2 badge hooks, E security). Facts below were spot-checked against the code and our servers. Report claims found
**wrong** and corrected here: B1 "Paper sends to unregistered channels" (it doesn't), B1 "Velocity never re-sends JoinGame"
(it does on the fast path), B2 "reset on Respawn" (would wipe rules on death), D1 "module on/off leaks to disk" (fixed in
1.9.12), A2 "registering a channel gets players banned" (no source). Treat the reports as leads, this plan as the decision.

---

## 1. What already exists (reuse, don't rebuild)

| Piece | Where | Use |
|---|---|---|
| Module on/off gate | `Module.isEnabled()` already returns false when `isBlockedByServer()` (`config/Module.java:59-68`) | Server blocks plug in here: no new gate |
| Server rules + per-server bypass | `HypixelSafetyManager` on 1.9.12: `Block {NONE, BANNED, UNKNOWN}`, `blockOf()`, `server_bypasses.json`, `TRUSTED_SERVERS` | Server policy merges into `blockOf()` |
| Typed options with clamping | `SliderOption.clamp()` rejects NaN/Inf and clamps (`SliderOption.java:53-62`), Bool/Dropdown/Color/Text/Action options | Forced and limited values are read through these |
| Lads player presence on every server | Cosmetics relay (`cosmetics-server/cosmetics.py`) already sends `snapshot`/`join`/`leave` per server room. Client handles snapshot/state/leave, not `join` (`CosmeticsLink.java:188-193`) | Badge works on servers WITHOUT the plugin |
| Tab + nametag mixins on all 3 versions | 1.8.9 `GuiPlayerTabOverlayMixin`, `NametagRenderMixin`; 1.20.1 `TabOverlayMixin`, `NametagRenderMixin`; 26.3 `PlayerTabOverlayMixin`, `NametagBackgroundMixin` (D2 report, javap-verified) | Badge drawing hooks |
| Custom payload plumbing | 1.8.9 `NetworkManagerMixin` + `Hypixel189.send`; 1.20.1 `Hypixel1201`; 26.3 `NativeHypixel` (B2 report) | Transport pattern to copy |
| Anime girl logo | `assets/theladscore/icons/window_{16,32,48,128,256}.png` | 64x64 badge = Lanczos downscale of the 256 |
| Client loader detection on Paper | `LadsConnect` plugin (Arash-HP `Servers/LadsConnect`, PAPI `%ladsconnect_*%`) | Keep running; fold in later if useful |
| Reference implementation | Lunar's Apollo-Velocity 1.2.9 runs on our Velocity (`plugins/apollo/mods.yml` lists every Lunar mod + option, blank = player's own) | Config model to copy (design only, clean-room) |
| One-jar precedent | `ViaRewind.jar` on our lobby holds `plugin.yml` + `fabric.mod.json` + `velocity-plugin.json` (checked with `unzip -l`) | Proves the packaging |

---

## 2. Binding rules

1. **Anti-ban (owner's permanent rule).** On a server without LadsClientAPI, the client sends **zero** `lads:*` packets and does
   **not** add `lads:api` to its channel list. It only answers after the server says hello first (Badlion's model, not
   LabyMod's or Lunar's). The server side adds the channel to the player itself (Paper) or sends the raw packet
   (Fabric/Forge); Velocity forwards it as is. So the client never has to announce anything.
2. **Never written to disk.** Server rules are applied when a value is *read*, in memory only. `ConfigManager` keeps saving the
   player's own values. Leaving, a crash, or a server switch can never leave a server's setting behind (E report T-01: a leaked
   forced setting could get a player banned on the next server).
3. **Reset points:** disconnect, every JoinGame packet, every return to the configuration phase (26.3 switch), every new
   server `hello`, and a Respawn whose hashed seed differs from the last one. A plain Respawn is **not** a reset: it also comes on
   death and dimension change. Why the seed check: Velocity 3.6.0 switches 1.8.9/1.20.1 clients either "fast" (new JoinGame)
   or "safe" (Respawn packets only), checked in `ClientPlaySessionHandler.doFast/doSafeClientServerSwitch` of our Velocity jar.
   When LadsClientAPI also runs on the proxy, it sends `reset` on every `ServerPostConnectEvent`, which covers every switch.
4. **Restricting is always allowed, forcing is limited.** Any module can be blocked and any number limited. Forcing a module ON
   or forcing a value only works on options marked server-controllable. Never controllable: Text options, Action buttons (they
   open files/clipboard), Discord RPC, keybinds, account/launcher, file paths and URLs, forcing automation ON (Stack Refill,
   Auto Reconnect, use-while-mining).
5. **No telemetry.** The client's only message is its protocol version, Lads version and Minecraft version. No mod list,
   settings, hardware or IP (LabyMod and Lunar both send mod lists; players hate it).
6. **Validate everything from the server:** numbers clamp to the option's own min/max (NaN/Inf already rejected), a dropdown
   index out of range is rejected (not wrapped: `DropdownOption.clamp` wraps modulo, E report 3.2), colors only RGB with a
   minimum alpha, max payload 512 KB, max 4 policy messages per second, unknown modules/options ignored and reported back.

---

## 3. How it works (one connection)

```
Player joins (any version, direct or through Velocity + Via)
  server: on join (+1 tick)      --- hello {v:1, server:"Lads SMP", caps:[policy,presence]} --->  client
          resend once after 3 s if no reply                                                   (vanilla/Lunar clients ignore it)
  client: only now replies       <--- hello {v:1, lads:"1.9.13", mc:"26.3"} ---
  server: builds this player's policy (config + world + permission groups + API overrides)
                                 --- policy {rev:7, unlisted:"inherit", modules:{...}, options:{...}, notice:"..."} --->
  client: applies it in memory, shows one toast "This server limits 3 Lads settings (view)", replies
                                 <--- ack {rev:7, ignored:["OldMod.Foo"]} ---
  later:  world change / reload / API call -> new full policy (replaces the old one, never a diff)
          presence {add:[uuid], remove:[uuid]} for badges
  leave / server switch / crash -> client drops the policy: player's own settings are back instantly
```

Encoding: UTF-8 JSON object per payload, `{"t":"<type>", ...}`. Gson exists on every platform (1.8.9's old Gson 2.2.4
included). Payloads are small (a full policy for ~70 modules is well under 32 KB), so no pagination. Client->server stays under
32,767 bytes (1.8.9's hard limit); server->client under 1 MiB (we cap at 512 KB).

Transport facts (checked in our own server jars, not just docs):
- Channel **`lads:api`**: 8 chars (1.8.9 allows 20), valid namespaced id, and ViaVersion passes ids that already have a colon
  through unchanged (B1, `MappingData.validateNewChannel`).
- **Paper 26.3** `CraftPlayer.sendPluginMessage` drops the message unless the client registered the channel
  (`channels().contains(channel)`, javap on `versions/26.3/paper-26.3.jar`; B1 claimed the opposite and was wrong). The public
  `CraftPlayer.channels()` returns the connection's live set, so the plugin adds `lads:api` to it (one reflective call) and
  then sends normally. Incoming messages need no client registration.
- **Velocity 3.6.0** forwards plugin messages on channels it doesn't own in both directions, with no client-registration check
  (`BackendPlaySessionHandler.handle(PluginMessagePacket)` -> `getFromId == null` -> generic forward; javap on
  `velocity-3.6.0-SNAPSHOT-613.jar`). So a backend-only install works through our proxy unchanged.
- **Fabric 26.3 server**: register the payload codec (`PayloadTypeRegistry`), send with `ServerPlayNetworking.send` without the
  `canSend` gate (`canSend` is false because the client never announces the channel). **Forge 1.20.1 server**:
  `EventNetworkChannel` with `acceptMissingOr` so vanilla/Fabric/non-Lads clients still join; send the vanilla packet.
- Lads Client sends **no** custom brand today (Fabric sends `fabric`, Forge `forge`, 1.8.9 `fml,forge`). Keep it that way.

---

## 4. Policy format (what the server sends)

```json
{"t":"policy","rev":7,"unlisted":"inherit",
 "modules":{"Fullbright":"allow","XaeroMinimap":"block","Minimap":"allow","Crosshair":"on"},
 "options":{"Fullbright.Brightness":{"max":10},
            "Crosshair.Style":{"value":"Cross","lock":true},
            "XaeroMinimap.Radar":{"value":false}},
 "notice":"Lads SMP: fullbright capped at 10%"}
```

- Module states: `block` (off, player can't turn it on), `allow` (lifts the client's own safety block for this server; player
  still chooses), `on` (forced on; server-controllable modules only). Not listed = `unlisted` rule: `inherit` (Lads built-in
  rules, default), `allow` or `block`.
- Option rules: `value` (forced), `min`/`max` (sliders), `choices` (allowed dropdown subset), `lock` (greyed out in the GUI).
- Merge order in `HypixelSafetyManager.blockOf()`: never-list -> server `block` (new `Block.SERVER`, **no** player bypass: the
  server said no) -> server `allow` (treated as "server known to allow it", returns NONE) -> today's rules + player bypass.

---

## 5. Client side (TheLadsCore)

| Part | What | Where |
|---|---|---|
| `ServerPolicy` | Immutable snapshot in one `volatile` field; `blocks(module)`, `allows(module)`, `forcedOn(module)`, `apply(option, ownValue)`. Allocation-free lookups (asked per frame) | `common/.../client/ladsapi/` |
| Read-time hooks | `SliderOption.getValue/getIntValue`, `BoolOption.get`, `DropdownOption.getIndex/getValue`, `ColorOption.getColor` consult `ServerPolicy` only when a policy is active (one volatile null check otherwise). New `ownValue()` per type = the player's raw value | `common/.../config/*Option.java` |
| Write-back fixes (D1, verified) | `save()` must read raw fields: already true for Bool/Slider/Dropdown/Text, **not** `IntTextOption.save()` (calls `get()`, line 36) -> fix. Settings screen `startEdit` and arrow stepping read `getValue()` then `setValue()` (`LadsSettingsScreen` ~1821/1844) -> use `ownValue()`; forced/locked options aren't editable. Module on/off is already safe (`ConfigManager.java:74` saves `isEnabledSetting()`) | common |
| Controllability flags | Per option: `controllable` (force ok) / `limitOnly` / `never`. Default: Bool/Slider/Dropdown/Color = controllable, Text/Action = never, plus the never-list from rule 4 | Option base class |
| Protocol + `LadsApiClient` | Parse/validate JSON, rate-limit, build replies; version adapters call `onPayload(byte[])` and give it a `send(byte[])` | common |
| Transport 26.3 | Register only the payload **codec** (not a global receiver, which Fabric would announce); catch it in a HEAD mixin on `ClientPacketListener.handleCustomPayload`; reset on configuration start + DISCONNECT | v26_3 |
| Transport 1.20.1 | HEAD mixin on `ClientPacketListener.handleCustomPayload` (raw buffer era); reset on `handleLogin` + DISCONNECT. Same jar runs on Forge via Connector | v1_20_1 |
| Transport 1.8.9 | Existing `NetworkManagerMixin` (`S3FPacketCustomPayload`, channel name <= 20 chars); reset on `handleJoinGame` + `cleanup/onDisconnect` | v1_8_9 |
| GUI | Lock icon + tooltip on limited options ("Limited by Lads SMP: max 10%"); blocked modules show "Blocked by this server". One toast per policy, never one per mod | common MenuGraphics |
| Player setting | General -> "Let servers change my Lads settings": ON by default. Off = forced *changes* are refused (blocks and limits still apply; they only take things away) | common |
| Schema export | Unit test / Gradle task dumps every module + option (type, default, range, choices, flags) to `ladsapi-schema.json`; the server jar embeds it to generate its config | common test |

Inventory (D1, `D1-settings-inventory.csv`): **73 modules, 526 options** (437 on every version, 89 version-specific). That CSV is
the first draft of the schema.

Vanilla options (FOV, gamma, render distance...) are out of v1. One exception goes in: when a server blocks or limits Fullbright,
cap the vanilla gamma input at 1.0 too. 1.8.9 accepts any gamma from options.txt, and 26.3/1.20.1 compute
`Math.max(vanilla, fullbright)` (`NativeFeatures.java:143`, `NativeFeatures1201.java:234-237`), so an edited options.txt
would otherwise get around the cap. Newer vanilla probably rejects gamma > 1 on load anyway; QA checks it.

Already fine as is: `ParticleCore` copies values into static fields, but every tick, so a new policy applies within a tick. Crosshair
profiles save raw values, which is the player's own (correct). HUD positions/scales/groups (`HudSettings`) are never
server-controllable; hiding a HUD element works because every HUD element is a Module.

---

## 6. Server side: the LadsClientAPI jar

### 6.1 Layout (new top-level folder `LadsClientAPI/` next to `TheLadsCore/`)

```
LadsClientAPI/
  common/            Java 17, no Minecraft deps: config, schema merge, policy builder, protocol, rate limits
  platform-paper/    Bukkit/Paper API (compileOnly): join/quit/world-change, channel add + sendPluginMessage, commands, PAPI
  platform-velocity/ Velocity API: proxy mode (network-wide + per-backend sections), legacy channel id for 1.8.9
  platform-fabric/   Fabric 26.3 server: PayloadTypeRegistry codec, raw send, ServerPlayConnectionEvents
  platform-forge/    Forge 1.20.1 server: EventNetworkChannel with acceptMissingOr (vanilla/Fabric clients still join)
  build.gradle       merges everything into ONE jar: plugin.yml + paper-plugin.yml + velocity-plugin.json
                     + fabric.mod.json + META-INF/mods.toml
```

- Whole jar compiled `--release 17`: Forge 1.20.1's class scanner crashes on Java 25 classes (C report). Java 25 servers run it fine.
- Sinytra Connector skips any jar with `mods.toml` (`ConnectorLocator`, C + B2 reports), so Zero SMP loads it once, as Forge.
- Hybrid servers (Arclight/Mohist): first entrypoint to start claims the jar, the others stay off.
- Legacy Spigot 1.8.8 on Java 8: not supported (1.8.9 *players* are supported through Via on modern servers).
- A backend install is enough (Velocity forwards `lads:api`). Optional proxy install: sends `reset` on every server switch and can
  hold network-wide rules for backends that don't run the jar. If both run, backends own the policy and the proxy only resets
  (avoids Apollo's proxy/backend desync bugs #78/#112). Our network: Paper lobby first, proxy in phase 4.

### 6.2 Config (YAML; SnakeYAML shaded and relocated for Fabric/Forge)

`config.yml` (general):
```yaml
enabled: true
unlisted-modules: inherit      # inherit | allow | block
notify-players: true           # the one toast on join
let-players-refuse-changes: true
require-lads-client: false     # kick non-Lads players (private servers)
min-client-version: ""         # e.g. "1.9.13"
badge:
  presence: true               # tell Lads clients who else runs Lads (badge)
  placeholder: "&c✿ "          # %ladsapi_badge% for TAB/LuckPerms (vanilla viewers); empty = off
debug: false
```

`rules.yml` (auto-generated from the embedded schema: every Lads module and option listed, blank = player's own, a comment with
type/range/default; regenerating adds new modules without touching what the owner set):
```yaml
modules:
  Fullbright:
    state:                     # allow | block | on   (blank = inherit)
    Brightness: { max: 10 }    # slider 0-100 %, default 100
  XaeroMinimap:
    state: block
worlds:                        # per-world overrides on top of the above
  pvp_arena:
    modules: { Fullbright: { state: block } }
groups:                        # players with permission ladsapi.group.<name>
  staff:
    modules: { XaeroMinimap: { state: allow } }
```

Commands: `/ladsapi reload | status | inspect <player> | set <player> <Module.Option> <rule> | clear <player>`.
Permissions: `ladsapi.admin`, `ladsapi.group.<name>`, `ladsapi.bypass` (gets an empty policy).

### 6.3 Developer API (small on purpose)

`LadsClientAPI.get().player(uuid)` -> `isLads()`, `clientVersion()`, `override("Fullbright.Brightness", Rule.max(10))`,
`block("Minimap")`, `clear(...)`, `resend()`. Event: `LadsPlayerReadyEvent` (Bukkit + Velocity events; Fabric/Forge callback).

---

## 7. Badge

- Texture: `assets/theladscore/textures/gui/lads_badge.png`, 64x64, made from `window_256.png` (Lanczos). Drawn 8x8 in tab,
  ~9x9 in front of the nametag. Mipmapped/linear so it stays clean at that size.
- Who gets it = Lads players from the server's `presence` messages **plus** the cosmetics relay room (works on every server,
  talks only to `bot.arashyt.ca`, never to the Minecraft server: anti-ban safe). Client change: handle the relay's `join`.
- New client module "Lads Badge": Tab / Nametag / Show my own. Server can turn it off via policy like any module.
- Hooks: existing tab and nametag mixins on 1.8.9 / 1.20.1 / 26.3 (D2 table). Hardest: 26.3 nametags (world-space render-state
  submit, not a GUI blit). Needs its own lane + QA screenshots.
- Vanilla/Bedrock viewers: optional `%ladsapi_badge%` placeholder for TAB (our Velocity runs TAB 6.2.0). Text by default;
  a resource-pack font glyph is possible later, but 1.8.9 clients via Via can't see custom fonts.

---

## 8. Phases and lanes

| Phase | Work | Who | Parallel? |
|---|---|---|---|
| 0 | `PROTOCOL.md` (messages, limits, reset rules) + interfaces `LadsApiClient` / `ServerPolicy` frozen | Claude | first, ~1 h |
| 1 | Client core: `ServerPolicy`, option read hooks, merge with `HypixelSafetyManager`, never-list, validation, unit tests | AGY lane + Claude review | after 0 |
| 2 | Client transport 26.3 / 1.20.1 / 1.8.9 (3 lanes) | 3 AGY lanes | with 1 (interface frozen) |
| 3 | Client GUI: locks, tooltips, toast, "let servers change" setting | AGY lane | after 1 |
| 4 | Server jar: common + Paper + Fabric 26.3 (lane A), Forge 1.20.1 + Velocity (lane B), config generator + schema export (lane C) | 3 AGY lanes | with 1-3 |
| 5 | Badge: texture + module + relay `join` (lane A), tab/nametag 1.8.9 + 1.20.1 (lane B), 26.3 (lane C) | 3 AGY lanes | can start now |
| 6 | QA on Legion: local Paper 26.3 + Velocity + Fabric 26.3 + Forge 1.20.1 servers, 1.8.9/1.20.1/26.3 clients. Checks: Fullbright capped at 10%, minimap blocked, revert on leave/crash/switch, nothing sent on a plugin-less server (packet log), badge visible. QA boss bar on every run | Claude + Legion | last |
| 7 | Ship: client release + LadsClientAPI 1.0.0; install on lobby (Paper), Lads SMP (Fabric), Zero SMP (Forge); release notes + Discord | Claude | after QA + owner OK |

Every AGY lane: own worktree/branch, foreground commands, javap-verified mixin targets, unit tests, Claude reviews the diff
before merge (their "PASS" claims have been fabricated before).

---

## 9. Risks

| Risk | Fix |
|---|---|
| A value read without the getter, or read and written back, ignores the rule or leaks it to disk | D1 list (IntTextOption.save, settings screen edit/stepping); unit test: apply a policy, save config, check the file holds only the player's values |
| Server switch through Velocity leaves the old server's rules on | Reset on JoinGame + configuration start; new backend re-sends hello |
| Paper drops messages to clients that never registered the channel | Confirmed in the 26.3 jar; plugin adds `lads:api` to `CraftPlayer.channels()` before sending. Breaks only if Paper renames that internal method: unit-test it against each Paper version we support |
| Respawn-only proxy switch to a backend without the plugin keeps the old rules | Seed-change reset on the client + proxy `reset` on `ServerPostConnectEvent` |
| The client already announces `hypixel:ping`, AppleSkin and Jade channels through Fabric's global receivers (B2) | Out of scope here, but worth a separate anti-ban review: register those lazily too |
| Malicious server griefs a player | Never-list, validation, session-only, the "refuse changes" setting |
| Restrictions are honour-system | Same as Lunar/Badlion: a hacked client can ignore them. It stops honest Lads players from accidentally breaking rules, not cheaters |

---

## 10. Decisions for the owner (defaults in bold; build starts with these unless changed)

1. Players can refuse forced *changes* (blocks/limits always apply): **yes, setting ON by default**.
2. A server running LadsClientAPI can *unlock* modules our safety rules block (e.g. minimap on its own server): **yes**.
3. `require-lads-client` kick option: **included, off by default**.
4. Badge for vanilla viewers via TAB placeholder: **text placeholder in v1**, glyph later.
5. Legacy Spigot 1.8.8 on Java 8: **not supported**.
6. LadsConnect: **keeps running next to LadsClientAPI**; merge later if wanted.
