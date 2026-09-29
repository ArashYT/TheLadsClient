# Module implementation audit

> **Historical baseline, superseded by release 1.1.0.** The table below was captured before native integrations and the mod pack were implemented. Current code connects 24 built-in and 22 external-backed entries, repairs HUD size/position/editor behavior, adds native input/lightmap/scoreboard hooks, and pins 35 upstream jars per exact target. See [REPAIR_STATUS.md](REPAIR_STATUS.md) for current coverage, remaining gaps and actual runtime evidence, and [CLIENT_MODS.md](CLIENT_MODS.md) for exact upstream versions. Old statements that these integrations or bundles do not exist are superseded.

Source audit, 2026-09-05, requested Minecraft **1.21.11 and 26.2**. This is evidence of code wiring, not an in-game acceptance test. Native/menu work is concurrent; the table records the inspected baseline, followed by the common corrections and native recheck below. No installed mods, credential stores, or user settings were inspected.

## Findings for integration owners

- `ModuleManager` makes **60 registrations / 59 unique names**. Its second `Scoreboard` registration replaces the first `HudModule`, losing its HUD category, color configuration, and original options.
- Both native bridges feed the same **16 data-backed common HUDs**. Four additional HUDs are placeholders. Outside those 20 HUD entries there are four unconnected behavior helpers, two ignored title controls, and **33 catalog-only entries**. A saved enabled flag is not an implementation.
- Native `GuiMixin` calls `HudManager.render` and polls attack/use states for CPS. Initializers install the bridge and load configuration. The inspected source has no gameplay consumers of `ModuleManager`, `ZoomModule`, `ToggleSprintModule`, `ToggleSneakModule`, or `ExordiumModule` outside common code. The title renderer works independently of the `TitleScreen` / `Title Scale` module controls.
- The bridge provides coordinates, biome path, cardinal direction, time/day, health/max health, food, XP, ping, horizontal speed, key state, potion strings and resource-pack IDs. It provides **no armor/durability, yaw, absorption, saturation/exhaustion, scoreboard entries, player model, or map/chunk data**. Those features require explicit native contracts/integration; they cannot be completed honestly inside the existing HUD classes alone.
- Core's Gradle files merge common code and include Discord RPC libraries. They do not bundle the named optimization/map/recipe/animation mods. External-mod compatibility and distribution must be checked separately for each exact Minecraft version by the packaging owner.

## Evidence index

Paths below are relative to the repository root. Java method/class names provide stable anchors while other work is ongoing.

| Key | Source |
| --- | --- |
| C | [`ModuleManager.java`](../TheLadsCore/common/src/main/java/com/thelads/core/config/ModuleManager.java), constructor and `register`; [`Module.java`](../TheLadsCore/common/src/main/java/com/thelads/core/config/Module.java), empty default enable/disable handlers |
| M | [`modules/`](../TheLadsCore/common/src/main/java/com/thelads/core/modules/), class named in each row |
| H | [`hud/`](../TheLadsCore/common/src/main/java/com/thelads/core/client/hud/), named element's `render`; [`HudManager.java`](../TheLadsCore/common/src/main/java/com/thelads/core/client/hud/HudManager.java), registration/render |
| B | [`LadsGameBridge.java`](../TheLadsCore/common/src/main/java/com/thelads/core/client/bridge/LadsGameBridge.java); [`VanillaGameBridge12111.java`](../TheLadsCore/v1_21_11/src/main/java/com/thelads/core/v1_21_11/adapter/VanillaGameBridge12111.java); [`VanillaGameBridge26.java`](../TheLadsCore/v26_2/src/main/java/com/thelads/core/v26_2/adapter/VanillaGameBridge26.java) |
| N | [`1.21.11 GuiMixin`](../TheLadsCore/v1_21_11/src/main/java/com/thelads/core/v1_21_11/mixin/GuiMixin.java); [`26.2 GuiMixin`](../TheLadsCore/v26_2/src/main/java/com/thelads/core/v26_2/mixin/GuiMixin.java); their adjacent mixins and `TheLadsCoreClient12111` / `TheLadsCoreClient26` initializers |
| T | [`1.21.11 TitleScreenMixin`](../TheLadsCore/v1_21_11/src/main/java/com/thelads/core/v1_21_11/mixin/TitleScreenMixin.java); [`26.2 TitleScreenMixin`](../TheLadsCore/v26_2/src/main/java/com/thelads/core/v26_2/mixin/TitleScreenMixin.java); [`TitleScreenTheme`](../TheLadsCore/common/src/main/java/com/thelads/core/client/title/TitleScreenTheme.java) |
| P | [`1.21.11 build.gradle`](../TheLadsCore/v1_21_11/build.gradle); [`26.2 build.gradle`](../TheLadsCore/v26_2/build.gradle); each project's `src/main/resources/fabric.mod.json` and mixin manifest |

## Exact catalog matrix

The status applies to **both requested versions** at the inspected baseline. **Data** = native data reaches a common renderer, with incomplete options called out; **Placeholder** = drawn substitute, not the advertised feature; **Logic only** = common calculations/state with no native caller; **Title controls** = actual title UI exists but does not consume these settings; **Catalog only** = constructor/options without behavior. “External” describes an integration route, not verified availability or permission to redistribute an upstream mod.

| Exact catalog name | Status | Existing behavior / missing work and option handling | Evidence |
| --- | --- | --- | --- |
| FPS | Data | Live FPS; update rate and smoothing consumed. Common size/render corrections below. | H: FPSHudElement; B/N |
| Coordinates | Data | Native block XYZ; formats, vertical and per-axis colors consumed. Long coordinate layout needs measured width. | H: CoordinatesHudElement; B/N |
| Biome | Data, partial | Native biome registry **path**; label works. Name/ID format ignored; full namespaced ID unavailable through current bridge. | H: BiomeHudElement; B |
| PingHUD | Data | Native latency; label works. Calculated latency color was discarded before common correction below. No connection and zero latency are indistinguishable. | H: PingHudElement; B |
| ArmorHUD | Placeholder | Always draws `Armor: 100%`. No armor items, durability or hotbar attachment; all feature options unused. Needs native equipment data/rendering. | H: ArmorHudElement; B |
| Memory | Data | Real JVM used/max MB; all display modes consumed. | H: MemoryHudElement; B defaults |
| Direction | Data, partial | Native cardinal string only. Format/degrees/long-name options unused; actual yaw needs native data. | H: DirectionHudElement; B |
| Speed | Data | Native horizontal velocity times 20; b/s and km/h work. Precision ignored before common correction. Assumes nominal 20 ticks/sec. | H: SpeedHudElement; B |
| Day | Data | Native clock day; label consumed. 26.2 uses overworld clock; 1.21.11 uses level day time. | H: DayHudElement; B |
| Time | Data | Native formatted HH:mm; label consumed. 12-hour ignored before common correction. | H: TimeHudElement; B |
| Health | Data, partial | HP/max HP and display modes work. Absorption option unused; requires bridge data. | H: HealthHudElement; B |
| Hunger | Data, partial | Food and label work. Saturation option unused; requires bridge data and multiplayer semantics verification. | H: HungerHudElement; B |
| XP | Data | Native level/progress; all format choices consumed. | H: XpHudElement; B |
| Keystrokes | Data, partial | Native movement/jump/attack/use states; CPS/space options consumed. State changes are immediate, not animated. Mouse state/CPS derives from key bindings and render polling, not physical click events. | H: KeystrokesHudElement; N; client/CpsTracker |
| CPS | Data, partial | Left/right/label choices consumed. Polling can miss a press/release between rendered frames; wire actual mouse events for accurate counts and avoid double counting. | H: CpsHudElement; N; client/CpsTracker |
| Scoreboard | Placeholder | Draws border and `SCOREBOARD`, including normal HUD rendering. No vanilla sidebar hook. All restyling options unused; duplicate registration discards HudModule settings. Server must supply objectives for a real sidebar. | C; H: ScoreboardHudElement; B |
| Title Scale | Title controls | `TitleScaleModule.getScale` exists but no caller; live title layout never reads it. Wire layout and native widget hitboxes together. | M: TitleScaleModule; T |
| Exordium | Logic only | FPS-limit helper has no caller; even its Global settings are bypassed by branch selection. Needs actual throttling/cached-GUI integration or external mod, not just storing limits. | M: ExordiumModule; N/P |
| TexturePacks | Data, partial | Selected pack IDs are real. Initially only first ID; Show All/Max Packs ignored before common correction. Hidden metadata and override controls need native repository integration. | H: TexturePackHudElement; B |
| Potion Effects | Data, partial | Native effect description IDs with seconds. Show-when-empty works; duration toggle ignored before common correction. Needs native localization/amplifier/infinite-duration handling. | H: PotionHudElement; B |
| Fullbright | Catalog only | Gamma getter/options, no lightmap/gamma consumer. Brightness Multiplier slider constructor in C also has reversed limits. Needs native lightmap integration. | M: FullbrightModule; C/N |
| ToggleSprint | Logic only | Toggle/Always and sneak inhibition evaluate locally; no key event or player-input hook. | M: ToggleSprintModule; N |
| ToggleSneak | Logic only | Toggle/Hold state exists; no input caller or lifecycle reset from native client. | M: ToggleSneakModule; N |
| Zoom | Logic only | Smooth/scroll FOV state exists; no key, wheel or camera/hand FOV hook. | M: ZoomModule; N |
| SmoothHotbar | Catalog only | Speed option only. Needs native selected-slot interpolation/render hook. | M: SmoothHotbarModule; N |
| DynamicLights | Catalog only | Radius/quality/entity/item/underwater options only. Needs lighting engine integration or compatible external implementation; no emitted-light calculation exists. | M: DynamicLightsModule; N/P |
| OldDamageTilt | Catalog only | Intensity option only. Needs camera damage-tilt hook. | C/N |
| VerticalBobbing | Catalog only | Intensity option only. Needs camera movement/bobbing hook. | C/N |
| DynamicFPS | Catalog only | Limits/mode unused. Needs focus/minimize/idle detection and frame limiter; coordinate with Exordium/Performance. | M: DynamicFPSModule; N |
| ToggleNametags | Catalog only | Own-tag/background options unused. Needs player nametag renderer hooks. | M: ToggleNametagsModule; N |
| TitleScreen | Title controls | Custom title is real, but module enabled flag and Account Card Scale are ignored. Account-card slider range is valid; UI integration is absent. | M: TitleScreenModule; T |
| Paperdoll | Placeholder | Filled rectangles resembling a person; no player skin/equipment/pose. All behavior options unused. Needs native entity render bridge. | H: PaperdollHudElement; M/B |
| KillBanner | Catalog only | Style/color and an empty test handler. Needs kill attribution event, animation renderer and expiry; arbitrary server kills cannot be inferred reliably from client health alone. | M: KillBannerModule; N |
| Crosshair Tweaks | Catalog only | Color/scale/thickness options unused. Needs native crosshair replacement with vanilla attack indicators retained. | M: CrosshairModule; N |
| HideChatIndicators | Catalog only | Enabled flag only; no chat indicator hook. Must preserve chat text/reporting semantics. | C/N |
| AutoReconnect | Catalog only | Delay/attempt settings only. Needs disconnect/reconnect state machine, remembered server, user cancellation and attempt limits. Requires reachable server and valid session. | C/N |
| DiscordRPC | Catalog only | Native library dependencies exist, but no initialization/callback/update/shutdown calls or application ID. Needs Discord IPC runtime integration; privacy/detail settings unused. | M: DiscordRpcModule; P |
| TabList | Catalog only | Size/offset/background/shadow options unused. Needs player-list render hook; server supplies listed players. | C/N |
| Capes | Catalog only | Provider/elytra flags only. No provider fetch/cache/selection or cape rendering hook. External services, consent/license and texture validation need separate work. | C/N/P |
| RenderScale | Catalog only | All preset/scale/algorithm/dynamic-resolution options unused. Needs version-specific render targets/upscaling and FPS control; cannot implement by scaling HUD graphics. | C/N |
| XaeroWorldmap | Placeholder | Draws `MINIMAP` in a border; no map data, fullscreen map, cave mode or minimap toggle behavior. Requires real map implementation or exact-version external integration. | H: XaeroMinimapHudElement; M: XaeroWorldMapModule; B/P |
| JEI (Just Enough Items) | Catalog only | No item catalog, search, recipes, screens or external JEI integration. Options unused. Cheat Mode requires legitimate server operator/creative permission. | M: JeiModule; N/P |
| ScalableLux | Catalog only | Enabled flag only; no lighting engine implementation/bundle. External compatibility/version audit required. | C/N/P |
| FarBlockEntities | Catalog only | Enabled flag only. Needs block-entity distance/culling hook or external implementation, with performance limits. | C/N |
| Raised | Catalog only | Distance option unused; constructor's min/max are reversed. Needs hotbar positioning hook tied to chat state. | C/N |
| AppleSkin | Catalog only | Saturation/food/exhaustion/health options unused. Needs native hunger/tooltip overlays or external mod and server data semantics; Hunger HUD does not implement AppleSkin. | M: AppleSkinModule; B/N/P |
| EnhancedTooltips | Catalog only | NBT flag unused. Needs item tooltip hook; sanitize/limit large component output. | M: EnhancedTooltipsModule; N |
| EnhancedToolbars | Catalog only | Despite name, description/options concern tooltip durability/attributes. No native tooltip consumer. | M: EnhancedToolbarsModule; N |
| NotEnoughAnimations | Catalog only | Empty subclass. Needs player pose/animation integration or compatible external mod. | M: NotEnoughAnimationsModule; N/P |
| BetterStats | Catalog only | Empty subclass; no statistics screen. Needs native statistics requests/rendering or external mod. Server supplies multiplayer statistics. | M: BetterStatsModule; N/P |
| DisableNarrator | Catalog only | Enabled flag only; no narrator interception/control. | C/N |
| SignalLoss | Catalog only | Enabled flag only; no connection watchdog or warning renderer. Needs explicit distinction between slow server, disconnected state and network loss. | C/N |
| BetterScreenshots | Catalog only | Enabled flag only; no capture/save UI integration. | C/N |
| Clumps | Catalog only | Empty subclass. XP-orb merging is authoritative world/server work; a client-only toggle cannot change remote-server orb behavior. Needs compatible integrated/dedicated server implementation. | M: ClumpsModule; N/P |
| SkinLayers | Catalog only | Enabled flag only; no outer-layer mesh/extrusion hook. Requires native model integration or external mod. | M: SkinLayersModule; N/P |
| ImmediatelyFast | Catalog only | Enabled flag only; no renderer optimization/bundle. Requires version-specific external/native implementation. | M: ImmediatelyFastModule; N/P |
| PingView | Catalog only | Number/color choices unused; PingHUD is separate. Needs numerical latency rendering in the native tab list. | M: PingViewModule; N |
| BetterF3 | Catalog only | Rainbow/hide choices unused; no debug-screen hook or external bundle. | M: BetterF3Module; N/P |
| Performance | Catalog only | Memory-cleaner/background-FPS flags unused. No runtime control; do not substitute forced GC for demonstrated performance improvement. | M: PerformanceManagerModule; N |

## Common fixes and verification

Completed inside `common/client/hud/`, without changing bridge contracts, configuration, native code, or menus:

- `HudElement.getScale` consumes the registered percentage slider (50–200%, including 125/175%), bounds the transform and retains the legacy dropdown path. Ping's latency color now reaches the text renderer through a color-aware centered-text helper.
- Dynamic text HUDs compute their width **before** drawing the background, eliminating the prior frame's background dimensions. Coordinates measure all visible fragments and font line height, including world-border values and per-axis formatting.
- Speed consumes the precision choice; time consumes the 12-hour choice, handles midnight/noon and preserves unknown time formats. Decimal formatting is locale-stable.
- TexturePacks consumes Show All and Max Packs using real selected IDs in the native order; empty lists still show Default. It does not invent hidden-pack metadata or alter the resource repository.
- Potion Effects consumes Show duration for the native ` (Ns)` suffix, preserves unfamiliar effect strings, honors global text shadow, and resets its dimensions when effects expire.
- Keystrokes reports bounds containing its mouse/space rows for every visibility combination. The space marker is drawn geometrically, avoiding an overheight font glyph. `HudManager` restores its pushed transform even if rendering throws.

Validation: **52 tests passed, zero failures/errors/skips**: 51 cases in [`HudBehaviorTest.java`](../TheLadsCore/common/src/test/java/com/thelads/core/HudBehaviorTest.java), plus the existing `LadsGraphicsTest`. This includes actual draw geometry/colors/text, all speed units/precision combinations, midnight/noon, first-frame backgrounds, key bounds, pack limits, potion expiry and transform cleanup. Fixtures use an in-memory bridge and options; they do not read or write user configuration. The Gradle invocation also compiled common main/test Java successfully:

```powershell
# From TheLadsCore
.\gradlew.bat :common:test --tests com.thelads.core.HudBehaviorTest --tests com.thelads.core.LadsGraphicsTest --offline --console=plain
```

`git diff --check` passed for the changed tracked HUD sources. No full native build, deployment or in-game verification was performed by this bounded sidecar; release packaging remains with the parent.

## Remaining cross-cutting integration defects

- **HUD editor scale:** [`DraggableHudScreen.render`](../TheLadsCore/common/src/main/java/com/thelads/core/client/gui/DraggableHudScreen.java) calls `element.render(g)` directly but draws borders and tests hits using `getRenderWidth/Height`. The GUI owner must apply the same origin/scale transform used by `HudManager` around content rendering. With functional sliders this existing mismatch is visible for non-100% sizes.
- **Position restoration:** [`ConfigManager.applyJson`](../TheLadsCore/common/src/main/java/com/thelads/core/config/ConfigManager.java) restores positions into `HudSettings`; no element/manager/editor consumer applies those stored coordinates. Coordinate application needs an initialization/profile-switch path that does not overwrite an in-progress drag. Saved positions were not migrated or removed here.
- **First-frame edge clamping:** `HudManager` clamps before elements calculate their current dynamic width/height. A newly widened label near the screen edge can overrun for one frame; an element larger than the viewport has no fitting/wrapping policy. A future explicit layout/measurement phase should be shared with the editor rather than rendering twice to measure.
- **Native recheck:** concurrent work added `PauseScreenMixin` in both requested projects. 1.21.11's HUD injection now uses an exact descriptor and `require = 1`; 26.2's `GuiMixin` still injects `extractRenderState` with `require = 0`. Confirm the 26.2 descriptor and make the required HUD hook fail visibly instead of silently omitting every overlay. Title/pause hooks do not fill the catalog's gameplay gaps. No new native module consumers were found on the final source recheck.
- **Packaging claims:** no availability/licensing claims about upstream external mods are made here. Each version needs an explicit dependency manifest, actual artifacts, startup validation, settings ownership and feature-level acceptance tests. The 59-row catalog is not that manifest.

Native, bridge, configuration, menu and external-mod implementation remain assigned to their respective owners. This audit does not authorize hiding entries or claiming catalog-only entries as implemented.

