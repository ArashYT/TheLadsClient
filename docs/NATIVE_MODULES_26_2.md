# Native module audit — Minecraft 26.2

Updated 10 September 2026. The development target is Minecraft **26.2 / Java 25**. Later Minecraft releases need their own port, build and runtime verification.

The subsequent [HUD/menu update](HUD_EDITOR_26_2.md) shows only native modules in Lads settings and changes the interface to dark red/red. External engines remain installed; hiding their cards does not port their code. The feature checkpoint below preserves the earlier implementation evidence, while the HUD update has its own build hash and screenshots.

## Latest preserved catalog and release boundary

The release artifact is Core `907DDE926E534C0144F4F7F4303BEF0EA5340CD61BC7F89DD4265F8FCF6351D8`. It repeats the full world results below and passes 250 retained-settings checks. Its only Java change after the 52D feature checkpoint corrects an opt-in reconnect test fixture for an empty world list. See [the release runtime report](NATIVE_RUNTIME_CHECKPOINT_26_2.md).

The runtime catalog contains **66 modules: 48 native, 17 backed by external engines, and 1 unavailable (Exordium)**. These are runtime registration counts, not counts of completed gameplay tests. The exact catalog is also preserved in the preceding [`native-final-attempt1/world.log`](../artifacts/verification/native-final-attempt1/world.log), line 412.

The production manifest and isolated QA pack now contain **27 top-level upstream JARs plus Core**. Screenshot Viewer and Clumps were retired from the production manifest after their native replacement probes passed. Dependencies and module rows are different inventories; one engine can supply multiple module rows, and library JARs do not each represent a module.

The final fully passing combined checkpoint used Core SHA-256 `52D16AD48F6776945F790288D79496EA2B05AFC66E4A78F7462A79D2C0395DB5`, preserved with its [complete log](../artifacts/verification/native-final-checkpoint/world-pass.log), [exact pack manifest](../artifacts/verification/native-final-checkpoint/client-mods.json), [world frame](../artifacts/verification/native-final-checkpoint/world-frame.png) and [actual mods-menu frame](../artifacts/verification/native-final-checkpoint/mods-menu.png). It passed all required probes, including **53 crosshair, 13 narrator, 38 screenshot-gallery and 21 Clumps checks**, the **226-check combined native-world probe** and **61 RenderScale GPU checks**. The real settings-menu framebuffer was also captured through the game's completed render path.

This final checkpoint includes the image-worker lifecycle fix, Clumps QA-world guard correction, spectator crosshair rejection of `MISS` hits, and drawing-editor short-window preview layout fix. Required builds passed, along with **252 common Java regression tests** and **105 launcher tests**. Publication/installation status is tracked separately from this source and runtime evidence.

## Features owned by Lads

The ordinary module page is the entry point for native options. Larger native editors open from their corresponding action row. Version-specific hooks, ownership gates, reference provenance and detailed verification boundaries belong in the linked feature documents.

| Module(s) | Native implementation and current evidence |
| --- | --- |
| Crosshair Tweaks | Nine shapes, original drawing editor, dimensions/rotation/offsets/colors, adaptive/rainbow blending, context/target/charge feedback, cooldown/tool/ammunition indicators, and all fourteen separate Crosshair Tweaks visibility/blend/opacity controls. **53 runtime checks passed**. An installed external crosshair renderer takes precedence. [Feature details](NATIVE_CROSSHAIR_26_2.md). |
| EnhancedToolbars | Native durability Numbers/Bar/Text, hint/colors, filters, full-item visibility and existing attribute/detail controls; preserves foreign tooltip components. **34 runtime checks passed**. [Details](DURABILITY_TOOLTIP_26_2.md). |
| EnhancedTooltips | Actual registry ID, food values, component count and bounded custom NBT display. Tooltip data is not modified. Covered by the combined native-world probe. |
| AppleSkin | Full native food HUD prediction, tooltip components, saturation/exhaustion/regeneration handling and server synchronization; no external AppleSkin engine required. **47 behavior, 14 rendering and 6 JEI checks passed**, with actual integrated-server payload receipt. [Details](NATIVE_FOOD_26_2.md). |
| Raised | Original Lads remake (1.7.0): hotbar group, chat-open and chat lifts on 1.8.9, 26.2 and 26.3; 1.6.0 layout carried over. [Details](NATIVE_RAISED_26_2.md). |
| DynamicFPS | Full native policy engine/editor, background and battery profiles, zero-FPS suspension, audio fades, temporary graphics/VSync, input/focus handling and lifecycle cleanup. **42 title and 44 world checks passed**. [Details](DYNAMIC_FPS_26_2.md). |
| DisableNarrator | Prevents native TTS backend creation while disabled, suppresses speech and shortcut/toast behavior, and initializes the real backend lazily when narration is restored. **13 constructor/mock-speech checks passed**; audible output is a separate check. [Details](NATIVE_NARRATOR_26_2.md). |
| SignalLoss | Native connection inactivity indicator, commands/settings and configuration lifecycle. **26 feature checks passed**, plus shared connection instrumentation checks. [Details](SIGNAL_LOSS_26_2.md). |
| AutoReconnect | Native timing, conditions, countdown/editor and reconnect lifecycle. **41 checks passed** using mock targets, without a remote reconnect or chat send. [Details](AUTO_RECONNECT_26_2.md). |
| Paperdoll | Original Lads remake (1.7.0): the actual player on the HUD with the module's triggers, timing, head and opacity options on 1.8.9, 26.2 and 26.3. [Details](NATIVE_PAPER_DOLL_26_2.md). |
| TabList / PingView | Native player-list arrangement and ping presentation. **42 runtime checks passed** against actual player-list extraction. [Details](NATIVE_TAB_TWEAKS_26_2.md). |
| RenderScale | Scales world color/depth targets while retaining native-resolution GUI, with linear/nearest upscaling, supersampling, dynamic bounds and cleanup. **61 GPU/world checks passed** across six stages. [Details](RENDER_SCALE_26_2.md). |
| ShulkerBoxUtils | World contents icon, counted inventory tooltip, badges, capacity/uniform-item indicators and bounded observed-data persistence. **28 base and 41 expanded parity checks passed**. Closed remote containers can only expose observed contents. [Details](SHULKER_BOX_UTILS_26_2.md). |
| BetterScreenshots | Gallery, viewer/editor and persistence engine are inside Core. **38 real gallery/GPU and isolated-file checks passed**, including the final image-worker build; OS open/copy actions were mocked. The external Screenshot Viewer has been retired from the production manifest. [Details](NATIVE_SCREENSHOTS_26_2.md). |
| Clumps | Native authoritative orb merging, pickup/Mending/event/save behavior for integrated worlds. **21 checks passed on the real integrated server**, and upstream Clumps has been retired from the production manifest. Remote servers own their own XP behavior. [Details](NATIVE_CLUMPS_26_2.md). |
| DiscordRPC | Native opt-in background Discord IPC service, retry and cleanup. Requires the project's own application ID; live presence is not established by registration or mock tests. [Setup and tests](DISCORD_RPC_SETUP.md). |
| SmoothHotbar | Elapsed-time hotbar selection movement with fractional GUI translation and interruption reset. This is not a measured display-FPS claim. |
| OldDamageTilt / VerticalBobbing | Classic hurt-angle control and bounded jump/fall camera motion, respecting vanilla timing and accessibility preferences. The camera/block-distance probe passed **15 checks**. |
| FarBlockEntities | Extends default rendering distance only for already-loaded block entities; existing frustum and specialized renderer rules remain. No chunk loading is forced. Included in the same 15-check probe. |
| ToggleNametags | Own third-person nametag/background options with vanilla visibility/team/distance rules. Nametag and connection instrumentation passed **18 shared checks**. |
| HideChatIndicators | Hides signing indicators while retaining message tags, signatures and reporting data; covered by the combined native-world probe. |
| KillBanner | Native artwork, animation, sound and settings, driven by authoritative server `PLAYER_KILLS` statistics. **18 runtime checks passed** using synthetic statistics packets. Initial history, duplicates, resets and reconnects do not invent kills; servers that omit these statistics cannot produce confirmed banners. No real PvP kill is claimed. |

The remaining native catalog entries are FPS, Coordinates, Biome, PingHUD, ArmorHUD, Memory, Direction, Speed, Day, Time, Health, Hunger, XP, Keystrokes, CPS, Title Scale, TexturePacks, Potion Effects, Fullbright, ToggleSprint, ToggleSneak, Zoom, TitleScreen and Scoreboard. Their registration remains connected; this inventory does not assert fresh physical interaction testing of every control.

## Retained external modules

The **17 external module rows** in this checkpoint are:

| Module rows | Retained engine boundary |
| --- | --- |
| Performance, Lithium, FerriteCore, ImmediatelyFast, EntityCulling, ScalableLux | Sodium and substantial simulation, memory, batching, visibility and lighting engines. |
| DynamicLights | LambDynamicLights renderer and compatibility system. |
| XaeroMinimap, XaeroWorldmap | Map rendering, world tiles, waypoints and persistent server/world data. |
| JEI (Just Enough Items) | Recipe/ingredient indexing and mod interoperability. |
| Capes, SkinLayers, NotEnoughAnimations | Cape-provider integration, specialized skin meshes and animation engine. |
| BetterF3, BetterStats | Modular debug layout and statistics browsing. |
| ModernAdvancements, Resourcify | Advancement/resource browsers and their complete upstream editors, with connected Lads settings. [Exact references and integration](REFERENCE_ENGINES_26_2.md). |

Fabric API, Mod Menu and required configuration/language libraries remain dependencies. Exact pinned versions, attribution and integrity hashes belong to the pack manifest. Retaining an engine means its complete implementation remains upstream; a configuration adapter alone does not recreate it. Installers retire only owned, matching-hash files and preserve user-added or modified JARs.

## Unavailable engine

**Exordium** is the sole unavailable catalog entry. Its GUI caching renderer has not been ported into this 26.2 build and remains excluded for compatibility. Dynamic FPS policies and RenderScale do not replace GUI caching. See the dated release/compatibility evidence in [the runtime checkpoint](NATIVE_RUNTIME_CHECKPOINT_26_2.md).

The sibling reference-mod folder is an input to compatibility research, not an installation manifest. Version/loader mismatches and specialized rendering engines remain tracked in [the detailed parity audit](NATIVE_PARITY_AUDIT_26_2.md).

## Runtime evidence and its limits

The passing `52D16AD…` checkpoint includes **226 combined native-world checks**, alongside the feature markers above. Several counts overlap: for example the combined probe includes food, background-policy and shulker checks, and JEI conversion is invoked twice. They must not be summed into a unique-test total.

Minecraft loaded the existing isolated QA world through its own world-opening API, completed more than 200 real world frames per RenderScale stage, read back GPU pixels, captured the completed frame and saved the world on graceful exit. The GUI stayed 1280×720 while world targets exercised 640×360, 1920×1080, 1280×720 and 960×540. This verifies target allocation/composition/lifecycle and sampling; it is not an FPS comparison or a physical-display measurement. [Harness details](AUTO_WORLD_QA_26_2.md).

Crosshair/editor and other probes invoke transformed Minecraft code, with deliberately synthetic state where identified. They restore preferences and temporary state. Physical RSHIFT/mouse input, Microsoft-authenticated multiplayer, audible audio/narration, live Discord presence, a real remote outage and real PvP kills remain separate evidence requirements. Windows being locked did not prevent native background world/GPU verification, but no lock-screen input was synthesized.

### Historical checkpoints — not current catalog or release claims

- [`108-checks.log`](../artifacts/verification/native-mods-26.2/108-checks.log) records the earlier **33 external JARs + Core** run at 17:39:05 on 10 September: 108 native checks, including ten synthetic Right Shift pipeline checks and the then-current simpler background/shulker implementations. Core SHA-256: `24FAD43B579667E47D7FBADDBF80E97E17ED51FC47BF5FFD4F53CD381FE4D09F`. Log SHA-256: `A6EB1F80A6FC708DCE3FAFE1E3E522BA2558A7D28E99AE7E7486DA5BB9A2AFD1`. It does not cover the subsequently expanded native engines.
- [`290-settings-checks.log`](../artifacts/verification/native-mods-26.2/290-settings-checks.log) records the earlier all-controls integration write/restore run: **290 controls, seven skipped modules, zero failures**, including 28 Modern Advancements/Resourcify controls. Log SHA-256: `F7928940E16B28040468F70C45BB5A332EFA5E760FA5F3941E02F8BE908A06B4`. This proves the adapters tested at that checkpoint, not current module counts or full engine gameplay.
- [`native-ports-checkpoint`](../artifacts/verification/native-ports-checkpoint) and [`tab-tweaks-checkpoint`](../artifacts/verification/tab-tweaks-checkpoint) preserve subsequent passing world/GPU and TabTweaks checkpoints, described in [NATIVE_RUNTIME_CHECKPOINT_26_2.md](NATIVE_RUNTIME_CHECKPOINT_26_2.md). They predate the now-passing native Clumps/gallery checkpoint.
- [`native-final-attempt1`](../artifacts/verification/native-final-attempt1) preserves Core `1866A5EDDEF308AA43DC712F395AE2C2941D4A5160E2731C3661ABD9A221A421`, its log and world frame. Crosshair53 and narrator13 passed there, but Clumps failed its world-name guard before any assertions and the gallery probe had not run. The complete log SHA-256 is `2DB5387537BBFB6B2284F02BE4A45B9C149BEC2FB764C4CBD9BE05AC8044DC0D`. The later AA871278 checkpoint supersedes these incomplete results.
- [`native-complete-checkpoint`](../artifacts/verification/native-complete-checkpoint) preserves the first fully passing combined run, Core `AA871278A3A592275604ACB8CF26C889CD0E63DF33624C865BDE5548541966B0`, including its 207-frame menu capture. The final 52D16AD checkpoint repeats the required checks after the image-worker lifecycle fix.
