# 26.2 native parity audit — 10 September 2026

This audit records the completed Minecraft 26.2 world run preserved in
[`native-final-checkpoint/world-pass.log`](../artifacts/verification/native-final-checkpoint/world-pass.log).
Its Core JAR SHA-256 is `52D16AD48F6776945F790288D79496EA2B05AFC66E4A78F7462A79D2C0395DB5`.
The actual runtime catalog reports **48 native modules, 17 external modules and
one unavailable module, Exordium**. After the verified native replacements, the
production manifest contains **27 upstream JARs plus Lads Core**; library and
catalog counts differ because multiple modules can share one engine and engines
can require libraries. Later binary changes require their own verification.

Inventory anchors: [`ModuleManager`](../TheLadsCore/common/src/main/java/com/thelads/core/config/ModuleManager.java),
[`TheLadsCoreClient26.onInitializeClient`](../TheLadsCore/v26_2/src/main/java/com/thelads/core/v26_2/TheLadsCoreClient26.java),
[`NativeQualityOfLife.register`](../TheLadsCore/v26_2/src/main/java/com/thelads/core/v26_2/feature/NativeQualityOfLife.java),
[`ExternalModSettings.register`](../TheLadsCore/v26_2/src/main/java/com/thelads/core/v26_2/gui/ExternalModSettings.java),
and the [production pins](../TheLadsLauncher/game-mods/26.2/client-mods.json).
A registration proves routing. The feature-specific checks and boundaries below
establish what was exercised; they do not promise every arbitrary mod combination,
physical input, remote server, or external service was tested.

## Completed reference replacements and boundaries

| Catalog / exact reference | Current native behavior | Verified evidence / boundary |
|---|---|---|
| **Crosshair Tweaks**, historically mapped to **Custom Crosshair v1.6.7-fabric-mc26.2** (`Cecs5C4L`) | Original [`NativeCrosshair`](../TheLadsCore/v26_2/src/main/java/com/thelads/core/v26_2/feature/crosshair/NativeCrosshair.java) and drawing editor implement nine shapes, independent geometry/colors, inverse/rainbow blending, attack/bow/crossbow/trident dynamics, target/context visibility, cooldown rings, tool/projectile indicators and bounded saved drawings. | **53 transformed world checks passed**, alongside ten focused tests. [Feature details and probe limits](NATIVE_CROSSHAIR_26_2.md). Editor handlers and rendering were exercised; physical mouse/RSHIFT input is a separate check. Correction: exact JAR metadata declares **CC0-1.0**, not ARR. Implementation remains original code with no copied upstream sources/assets. |
| **Crosshair Tweaks 1.5.3+26.2**, a **different** Microcontrollers mod | All 14 declared visibility/blend/opacity/attack/debug controls are now consumed by the native renderer. | The native renderer yields if either external crosshair mod ID is loaded. Unmanaged `C:/The Lads Client/mods/crosshairtweaks-1.5.3+26.2-fabric.jar` and its configuration are preserved; using native controls requires that instance's external renderer to be absent. LGPL-3.0 reference, original implementation. |
| **EnhancedToolbars** vs **Durability Tooltip 1.1.6-fabric-mc26.2** (`cvbPblt6`) | Original native Numbers/Bar/Text display, hint, varying/base/gold colors, base color, vanilla namespace/full durability/blacklist filters; old four controls remain active | Full declared behavior recreated in original code; no ARR source/assets copied. Ten presentation tests and **34 actual world tooltip/text-extraction checks passed**. [Details](DURABILITY_TOOLTIP_26_2.md). |
| **EnhancedTooltips** | Item ID, food values, component count, bounded custom-data NBT text, honoring hidden tooltip data | No separately pinned EnhancedTooltips reference was found. The four declared options have consumers. This is a native utility, not evidence of every tooltip feature from an unnamed upstream project. Shulker grid belongs to ShulkerBoxUtils; food prediction belongs to AppleSkin. |
| **SignalLoss 1.2.1+26.2**, supplied reference | Full native policy with actual inbound timestamps, two-second default threshold, join grace, minimum warning duration, recovery linger, singleplayer/background switches, three placements, colors, commands and safe config import | **26 checks passed**, covering actual toast geometry, options, command tree and isolated persistence. [Apache-2.0 source, complete controls and limits](SIGNAL_LOSS_26_2.md). No controlled outage of a real remote server is claimed. |
| **BetterScreenshots** → **ScreenshotViewer 1.3.6-fabric-mc26.2** (manifest display version `26.2-fabric-1`) | Complete native MIT gallery: all 20 pinned configuration controls, lazy thumbnails/cache, folder/compression settings, ordering/scroll/zoom, button placement/colors, chat links, full viewer, copy/open/rename and confirmed deletion | **38 checks passed**, including real gallery/viewer extraction and GPU image upload; OS open/clipboard actions were mocked. [Exact source/license, file tests and final-binary status](NATIVE_SCREENSHOTS_26_2.md). Production upstream pin retired; CatConfig and platform libraries remain. **A bitmap paint editor is not claimed.** |
| Supplied **DecentScreenshot 1.0+26.2** | Native BetterScreenshots provides its F10/title/gallery entry convention, full viewer, rename, thumbnail cache and screenshot discovery through the complete ScreenshotViewer engine | [Native feature mapping](NATIVE_SCREENSHOTS_26_2.md). The **38-check gallery probe** exercises synthetic F10 through the real keyboard handler and restores screen/state. This reproduces the supplied entry convention; it is not a source port of DecentScreenshot. Its advertised GitHub URL returned 404 during audit; the supplied artifact declares CC0-1.0. |
| **TabList / PingView** → **TabTweaks 1.5.11** | Full native source port; 42 actual PlayerTabOverlay checks passed and production upstream pin removed | [Native port and verification](NATIVE_TAB_TWEAKS_26_2.md): 21 layout controls and 13 ping controls feed one renderer, including all upstream scalar/color options. Direct vanilla rendering probe covers layout, visibility, objectives, shadows, scale, numerical latency bands, hats and boss state with local profiles. Upstream source revision `faa19c704c3c967e1cf0f0355791f9d90d47a1c5`, LGPL-3.0-only; corresponding modified source and licenses are bundled. |
| **DisableNarrator 1.0.0**, supplied reference | Original native lazy speech-engine wrapper suppresses construction, speech, Ctrl+B/toasts and unwanted initialization warnings while enabled; re-enable restores normal narration access, disable clears queues, shutdown releases an initialized engine | **13 checks passed**, using mock speech plus the actual transformed GameNarrator constructor. [Lifecycle and accessibility boundary](NATIVE_NARRATOR_26_2.md). Vanilla narration preferences remain intact; audible platform speech was not exercised. |

Primary author pages: [Custom Crosshair](https://modrinth.com/mod/custom-crosshair-mod),
[Crosshair Tweaks](https://modrinth.com/mod/crosshairtweaks),
[Durability Tooltip](https://modrinth.com/mod/durability-tooltip),
[ScreenshotViewer source](https://github.com/LGatodu47/ScreenshotViewer),
[TabTweaks author feature list](https://modrinth.com/mod/tabtweaks),
[SignalLoss source and config](https://github.com/hexandcube/SignalLoss).
Exact artifact declarations, not generic descriptions, determine the versioned
findings above. Exact installed bytecode was inspected for both crosshair references
and TabTweaks; the complete pinned TabTweaks source revision was also retrieved.

## Existing native catalog and new ports

| Catalog entries | Verified source anchor / boundary |
|---|---|
| FPS, Coordinates, Biome, PingHUD, ArmorHUD, Memory, Direction, Speed, Day, Time, Health, Hunger, XP, Keystrokes, CPS, Potion Effects, TexturePacks, Scoreboard | [`HudManager`](../TheLadsCore/common/src/main/java/com/thelads/core/client/hud/HudManager.java) and each `*HudElement` consume declared controls through `NativeHudMixin`/26.2 bridge. These are Lads utilities, not one-to-one ports of an identified external reference. Gameplay data, scaling and keyboard probes have historical evidence; no blanket new visual parity claim. |
| Zoom, ToggleSprint, ToggleSneak, Fullbright | Dedicated 26.2 input/render mixins and [`NativeFeatures`](../TheLadsCore/v26_2/src/main/java/com/thelads/core/v26_2/feature/NativeFeatures.java). No matching pinned upstream reference was identified for exhaustive parity comparison. |
| TitleScreen, Title Scale | `TitleScreenMixin`, `TitleWidgetRegistry`, common title themes and native title extras. Native UI; account authentication is a separate launcher/runtime boundary. |
| SmoothHotbar, OldDamageTilt, VerticalBobbing | `HotbarAnimation`, `DamageTiltMixin`, `VerticalBob*` state/extraction/transform hooks. Native declared behavior; full Animatium is not reproduced by these three features. |
| ToggleNametags | `OwnNametagMixin` / `NametagBackgroundMixin`: own third-person label and text background, preserving vanilla team/visibility rules. No pinned full nametag editor reference identified; do not claim arbitrary label formatting/distance customization. |
| FarBlockEntities | `FarBlockDistance` / `FarBlockEntitiesMixin`: configurable default distance, only loaded block entities, specialized overrides preserved. Supplied 2.1 reference is small; loaded-chunk/frustum boundaries are intentional. |
| KillBanner | `NativeKillBanner`, server-stat hook, HUD hook and packaged textures/sounds. Authoritative player-kill statistic changes; no inferred victim/round metadata. Server must report statistics. No separate matching reference mod artifact identified. |
| ShulkerBoxUtils | Expanded native world icon, actual-component inventory/tooltip features and bounded observed cache; [exact 1.3.0 parity matrix](SHULKER_BOX_UTILS_26_2.md). Persistent observations are last-known data, not omniscience about closed multiplayer containers. |
| AppleSkin | Full native food/tooltip/HUD engine with packet and lifecycle handling; **47 behavior, 14 rendering and 6 pinned JEI checks passed**, with actual integrated-server delivery of all three payloads. [Feature matrix and server boundary](NATIVE_FOOD_26_2.md). Live remote synchronization still requires a reporting server. Production AppleSkin pin retired; manually installed upstream keeps ownership. |
| Raised | Full native **6.0.0 source revision** with group/anchor/layer editor, SmoothHotbar composition and safe **5.1.2 saved-layout migration**; **52 checks passed**, including actual widgets, compact resize and exact prior QA config. [Version distinction, migration and overlay limits](NATIVE_RAISED_26_2.md). Production upstream pin retired; no claim that every arbitrary third-party overlay is movable. |
| DynamicFPS | Full native policy/editor engine from 3.11.9; **42 title and 44 world checks passed** through transformed frame/sound/graphics hooks with synthetic focus/battery inputs. [Policies and evidence limits](DYNAMIC_FPS_26_2.md). Production upstream pin retired. These counts do not measure physical focus/minimize events, battery drain or audible fade perception. |
| Paperdoll | Native actual-player renderer and complete settings; **19 checks passed**, covering avatar/skin/equipment/item extraction and rendering-state isolation, with the model visible in a completed world frame. [Reference mapping and limits](NATIVE_PAPER_DOLL_26_2.md). Production upstream pin retired; existing saved HUD coordinates remain in use and separately installed upstream keeps ownership. |
| AutoReconnect | Native lifecycle, three strategies and configuration/actions editors; **41 checks passed** with mock targets and real screen handlers. [Complete matrix and exclusions](AUTO_RECONNECT_26_2.md). Production upstream pin retired. No real network/world reconnect or chat transmission was exercised. |
| Clumps 26.2.1 | Full native integrated-server engine; **21 real ServerLevel checks passed** for mixed-value merging, award/pickup, Mending, persistence and cleanup. [Exact save guard, source and server boundary](NATIVE_CLUMPS_26_2.md). Production upstream pin retired. Remote servers still control their own XP entities. |
| RenderScale | Native world render targets/upscale/dynamic policy; **61 actual GPU/lifecycle checks passed**, and the runtime catalog confirms its native route. [GPU and lifecycle evidence](RENDER_SCALE_26_2.md). Performance claims still require measurements on the target workload/hardware. |
| DiscordRPC | Native IPC and privacy-aware service with mock transport coverage; needs an explicit application ID and running Discord for actual presence. [Setup/evidence](DISCORD_RPC_SETUP.md). Not equivalent to proven live Discord publication. |
| Exordium | Unavailable. Full GUI/HUD render caching is not implemented by DynamicFPS or RenderScale. Its independent caching engine has compatibility risk with current rendering hooks; do not describe frame limiting as its native replacement. |

## External engines retained deliberately

| Catalog entries / pins | Rationale and true behavior owner |
|---|---|
| Performance → Sodium 0.9.1; Lithium 0.25.3; FerriteCore 9.0.0; ImmediatelyFast 1.16.4; EntityCulling 1.10.5; ScalableLux 0.2.1 | Large rendering, memory, simulation, culling or light engines; remain upstream. Performance's legacy “memory cleaner” declaration does not prove a native collector. Current Lads page routes to Sodium settings. |
| DynamicLights → LambDynamicLights 4.12.4 | Dynamic lighting plus item/entity/world compatibility engine; current implementation is external. |
| XaeroMinimap 26.4.2; XaeroWorldmap 1.45.0 | Persistent map tiles, waypoint metadata, server/world separation and map renderers; substantial and proprietary. |
| JEI (Just Enough Items) 30.29.0.201 | Recipe/ingredient index and mod extension ecosystem; substantial external engine. |
| SkinLayers → 3D Skin Layers 1.11.2; NotEnoughAnimations 1.12.4; Capes 1.5.11 | Model/mesh, animation and external texture-provider compatibility. Source and licenses differ; retained implementations own these behaviors. |
| BetterF3 19.0.0; BetterStats 5.5.6 | Full debug-layout and statistics browsing systems. BetterF3 can be ported later (MIT); BetterStats/TCDCommons pinned metadata is ARR. Their size/license justifies retention for this pass, not claiming impossibility. |
| ModernAdvancements 1.10.2; Resourcify 1.8.5 | Advancement search/tracking and resource browsing/download/update engines. See [pins and rationale](REFERENCE_ENGINES_26_2.md). |

Fabric API, Mod Menu, config/language libraries and engine dependencies remain
pack infrastructure. The gallery's nested CatConfig and platform bridge libraries
are retained libraries, not separately claimed native gameplay features.

## Deferred reference engines

| Reference | Current boundary |
|---|---|
| Exordium | Catalog entry remains unavailable. Full GUI/HUD render caching has not been implemented or validated with current renderer hooks; DynamicFPS and RenderScale do not replace that cache engine. |
| Optimized Block Entities | A Fabric 26.2 candidate exists, but its alternate shulker renderer can bypass native ShulkerBoxUtils hooks. Deferred until actual coexistence is verified. |
| Animatium / safemod2 | The supplied jar targets 26.1–26.1.2. An official 26.2 candidate exists, but overlaps OldDamageTilt, VerticalBobbing, SmoothHotbar and retained animation engines. Full Animatium is not claimed by those native modules. |
| Kerria | Supplied artifact requires 1.21.1; no released Fabric 26.2 artifact was verified in the reference audit. |
| Retromod | Supplied artifact is NeoForge; a Fabric 26.2 beta candidate exists, but bytecode rewriting does not establish compatibility with custom GPU/rendering engines. Excluded from the production pack. |

See [exact candidate versions, primary sources and installation boundaries](REFERENCE_ENGINES_26_2.md).
ModernAdvancements remains an install-time official download under its license;
its availability does not authorize embedding its upstream JAR in the launcher.

## Reproducible version evidence

Exact declarations saved from `javap -p` (plus bytecode for tooltip enums/config):

- [`parity-custom-crosshair-1.6.7.txt`](../artifacts/verification/native-mods-26.2/parity-custom-crosshair-1.6.7.txt)
- [`parity-crosshair-tweaks-1.5.3.txt`](../artifacts/verification/native-mods-26.2/parity-crosshair-tweaks-1.5.3.txt)
- [`parity-durability-tooltip-1.1.6.txt`](../artifacts/verification/native-mods-26.2/parity-durability-tooltip-1.1.6.txt)
- [`parity-signal-loss-1.2.1.txt`](../artifacts/verification/native-mods-26.2/parity-signal-loss-1.2.1.txt)
- [`parity-screenshot-viewer-1.3.6.txt`](../artifacts/verification/native-mods-26.2/parity-screenshot-viewer-1.3.6.txt)
- [`parity-tabtweaks-1.5.11.txt`](../artifacts/verification/native-mods-26.2/parity-tabtweaks-1.5.11.txt)

The preserved checkpoint includes **226 native feature checks**, the feature-specific
results above, an actual completed world frame and an explicitly requested real
Lads menu capture. Some probes run more than once or form part of the 226-check
suite; their counts must not be summed as independent coverage. The framebuffer
captures prove rendering through the game API, not physical input or online login.
Microsoft authentication and live Discord publication remain separate service
verification boundaries. The [runtime report](NATIVE_RUNTIME_CHECKPOINT_26_2.md)
records later release checks without changing this frozen JAR's evidence.


## Additive gallery checkpoint, 2026-09-10 19:25

The original 19:25 gallery checkpoint remains preserved as historical evidence.
The complete pinned Screenshot Viewer 1.3.6 engine was ported into Lads,
with F10 and the supplied DecentScreenshot gallery entry convention. All 20 pinned
configuration options, file actions, viewer, cache and placement are retained;
CatConfig libraries remain explicit retained configuration dependencies.
The isolated native gallery runtime probe passed 38 checks, including real render
extraction and GPU image upload. OS clipboard/open were mocked. See
[NATIVE_SCREENSHOTS_26_2.md](NATIVE_SCREENSHOTS_26_2.md) for source/license anchors,
file tests and source/runtime detail. Its subsequent executor and closed-gallery
control fixes are included in the **52D16AD4** final checkpoint recorded above,
which also passed all 38 gallery checks. The production external jar has now been
retired; separately installed copies continue to keep ownership.

The final two-thread image-executor binary (`52D16AD4...`) passed the same 38 native
gallery checks again at 19:27:38; see the frozen final log linked in the feature
document. No worker waits on another screenshot job in the shared executor.

Final pack decision: after complete combined QA of core 52D16AD4..., the production
26.2 manifest retired Screenshot Viewer and Clumps (27 retained external jars).
The consolidated rows above now reflect this verified native-gallery result.
