# Zero SMP (Minecraft 1.20.1, Forge 47.4.10 + Sinytra Connector) - performance research

Lads Client 1.8.5, lane "research". Report only: the real instance (`%APPDATA%\.theladsclient\instances\zero-smp-1.20.1`) and the pack were never changed.
Status of this file: **STATIC + REAL-PROFILE EDITION** (section 6 "A/B measurements" is filled in when the sandbox runs finish).

Evidence tags used below: `[PROFILE]` = the owner's own F3+L profile of the live pack (20:24 on 2026-10-08), `[LOG]` = real game logs, `[CFG]` = config files in the instance (diffed against the shipped baseline `tools/modpack-manager/baseline-1.0.0`), `[JAR]` = read from the jars (mixins, classes, defaults), `[WEB]` = Modrinth API / project pages (checked 2026-10-08), `[AB]` = sandbox A/B run of this lane, `[EST]` = estimate, not measured.

## 0. Summary

The pack is CPU-bound on the render thread, not GPU-bound. In the owner's profile the GPU averaged 48 % while the render thread spent 8.3 ms per frame (120 fps cap): `[PROFILE]`

| share of frame time | what | owner of the cost |
|---:|---|---|
| **18.6 %** | `minecraft:after_cutout_blocks` render stage | **Grassier Grass** (the only continuous listener in that stage, see 2.2) |
| **14.5 %** (+3.4 % in the client tick) | particle rendering | ParticleRain, Particular, Immersive Winds, Wakes, Waves, Create, Alex's Caves ... (57 + 17 + 17 custom particle classes) |
| **11.5 %** | `gui` (almost all "unspecified") | HUD stack: Lads HUD modules, Xaero minimap/radar, Immersive Hotbar, Create/Paraglider overlays |
| **10.8 %** | Distant Horizons | LOD pass + its generic-object renderer (364 "render prep" calls per frame) |
| 7.0 % (+2.5 % tick) | entity rendering | mobs in view (Alex's Mobs, Creeper Overhaul, ...) |
| 4.8 % | `animateTick` | random display ticks of modded blocks |

Ranked recommendations (details and exact keys in section 5; every item states client/server and risk):

1. **Grassier Grass: `renderRadius` 160 -> 64..96** (config, client). It is the largest single line in the profile. Risk: low (grass pops in closer).
2. **De-duplicate the rain/particle stack** (config, client): turn off the rain-splash/ripple particles of Particular and Immersive Winds, which ParticleRain already provides; cap ParticleRain at 1000. Risk: low.
3. **ModernFix `mixin.perf.dynamic_resources=true`** (config, client; ModernFix is shipped with all 110 options at default, so every opt-in is still off). ModernFix's own wiki reports 1.7 GB -> 0.95 GB heap on its BlanketCon '23 pack. Needs a compat test with Embeddium/Oculus/EMF/Connector (section 6).
4. **JVM/RAM**: the live process runs `-Xms512m -Xmx6144m` with `MaxGCPauseMillis=50` (launcher defaults). The measured live set is only 2.0-3.3 GB, so 6 GB is correct; set `-Xms` = 3 GB or more to stop heap-resize churn, and consider Shenandoah (Distant Horizons itself recommends it for Java 17). Details in 5.5.
5. **Find out what the 11.5 % GUI cost is** and fix the cheapest part (A/B: minimap/radar off, HUD hidden) - section 6.
6. **Remove dead weight**: Cull Leaves (config says `enabled=false`, MoreCulling already culls leaves), three orphan libraries (CorgiLib, Data Anchor, Player Animation Lib), and - once Lads Core replaces particle_core - Kotlin for Forge + fzzy_config (and MidnightLib once Cull Leaves is gone).
7. **Embeddium `chunk_builder_threads`: instance has 1, the shipped baseline has 0 (auto)**. Put 0 back unless the owner set 1 deliberately for Distant Horizons.
8. **Server (PebbleHost, I cannot see it)**: add Radium (Lithium port, server), ServerCore, Chunky pregeneration, spark, FastFurnace/FastWorkbench; give In Control actual mob caps (`spawn.json` is `[]` today); mod updates that fix real leaks (Balm 7.3.44).
9. **Keep installing**: ModernFix, FerriteCore, Embeddium(+Extra), MoreCulling, Noisiumed, Clumps. They are doing their job.

Not recommended (verified not available/unsuitable for 1.20.1 Forge): Lithium, ScalableLux, Krypton (Fabric only), Starlight-Forge (2023, abandoned), Canary together with Radium (declared incompatible).

---

## 1. Environment and method

* Machine: i7-13700KF (8P+8E, 24 threads), 32 GB DDR5, RTX 4060 Ti, Windows 11. Other lanes compile and QA-launch constantly, so free RAM was 4-12 GB during this work and CPU was shared. All wall-clock numbers below carry that noise.
* Pack: instance `zero-smp-1.20.1`, pack 1.0.0, Forge 47.4.10, Connector 1.0.0-beta.49, **128 jars / 436 MB / 39 480 classes / 2 137 mixin classes** (116 `@Overwrite`, 182 `@Redirect`). Three jars (c2meF alpha 12, lazyyyyy, crawl-on-demand) were added to the live folder by another lane at 21:34; they are *not* part of this analysis' baseline.
* Real JVM (read from the owner's profile, never from the process command line because that holds the session token): Temurin 17.0.20.1, `-XX:+UnlockExperimentalVMOptions -XX:+UseG1GC -XX:G1NewSizePercent=20 -XX:G1ReservePercent=20 -XX:MaxGCPauseMillis=50 -XX:G1HeapRegionSize=16M -Xmx6144m -Xms512m`. The launcher setting is 8192 MB, the pack overrides it with `instance.json maxRamMb 6144`.
* Evidence read: 7 game logs of 2026-10-08 (`latest.log` + 6 rotated), 1 crash report, the owner's F3+L profile zip (`debug/profiling`), every file in `config/`, every jar (metadata, mixins, dependencies, class counts), Modrinth version/hash data for all 128 jars, Modrinth/CurseForge/wiki pages for the candidates.
* Tools written for this lane (all in this folder, no dependencies beyond Python and a JDK): `build_tables.py` + `mod_notes.py` (per-mod table), `mixin_hotspots.py` (static Mixin target scan), `jfr_attrib.py` / `jfr_gc.py` (JFR -> per-mod CPU shares / GC pauses), `variants.py` + `make_source.py` (config variants and variant source instances for the QA harness), `owner_playing.ps1` (so a QA run never starts while the owner plays).

## 2. What the real profile says `[PROFILE]`

Source: `debug/profiling/2026-10-08_20_24_09-Zero SMP!-1_20_1.zip` (client profiler, 337 frames in 2 810 ms, Zero SMP server, render distance 12 / simulation 8, Distant Horizons on, no shaders, 146 entities around).

* Frame time: mean **8.29 ms**, p50 6.75, p90 14.6, p99 18.9, max 29.2 ms. GPU utilisation mean 48 % (16.9-100). Mean CPU over all 24 threads 20 %; the busiest cores sat at ~60 %. Heap in use 2.0-3.3 GB of 4.2 GB committed (max 6 GB).
* No hitch above 30 ms inside the 2.8 s window (the two "deviation" files are ordinary 22 ms ticks).

### 2.1 Frame budget (percent of total profiled time)

| rank | section | % total | notes |
|---:|---|---:|---|
| 1 | `level/terrain/minecraft:after_cutout_blocks` | **18.60** | Forge `RenderLevelStageEvent` listeners of stage AFTER_CUTOUT_BLOCKS, once per frame |
| 2 | `level/particles` (render) | **14.52** | 13.76 is the engine's own render loop ("unspecified") |
| 3 | `gui` | **11.51** | 10.97 "unspecified" (Forge overlays are not individually profiled), chat 0.42 |
| 4 | `level/terrain/DH-RenderLevel` | **10.78** | LOD Opaque 1.83, Transparent 1.63, Custom Objects/render prep 1.41, Custom Objects/Clouds 1.63 (34 generic objects per frame), SSAO 0.59, build render list 0.58, fog 0.49, GL setup 0.38, anti-aliasing 0.33 |
| 5 | `tick` | 16.36 | `animateTick` 4.84, particles 3.36, entities 2.49, block entities 0.69 |
| 6 | `level/entities` (render) | 6.95 | Zombie 4/frame, Creeper Overhaul, Spider, Tasmanian devil, pig, raccoon |
| 7 | `updateDisplay` | 3.55 | swap/driver |
| 8 | `level/opaque_particles` | 2.37 | |
| 9 | `level/hand` | 1.49 | seven mods mix into `ItemInHandRenderer` |
| 10 | `level/entity_draws` | 1.01 | |
| - | `level/terrain` self (vanilla/Embeddium chunk layers) | 1.16 | the Embeddium terrain passes are cheap on the CPU |

Two consequences: (a) the most valuable optimisations are *not* in the terrain renderer that every "FPS mod" targets, they are in mod render hooks (grass, particles, HUD); (b) at the 120 fps cap the CPU has 35 % headroom on the owner's machine, which is why the pack "feels fine" there. On a 6-core/12-thread laptop the same sections would take 2-3x longer.

### 2.2 Who owns `after_cutout_blocks`? `[JAR]`

Scanned every jar for `RenderLevelStageEvent` stage references (Fabric-side hooks of Immersive Winds/Continuity go through the Forgified Fabric API and could not be resolved statically; the A/B in section 6 confirms by switching grass off). AFTER_CUTOUT_BLOCKS is referenced by exactly: **Grassier Grass** (`ShortGrassClient`), Alex's Caves (`ClientEvents`, cave effects, conditional) and PuzzlesLib (generic invoker, no renderer behind it in this pack). Grassier Grass 1.4.5 builds a mesh per chunk section on worker threads (`GrassSectionBuilder/Cache`), animates blades with a compute shader (`GrassComputeAnimator`, 34 KB class, 22 KB vertex shader) and dispatches draw calls per section (`GrassDrawDispatcher`). Its config schema (read from the class): `bladesPerBlock` range 1-64 **default 64**, `renderRadius` range 16-160 **default and maximum 160**. The pack already lowered `bladesPerBlock` to 10 but left `renderRadius` at the maximum, `grassPlantsAsBlades`, `denseFlowers` and `bladeParticles` on. The mod's own comment claims "distance is cheap" because meshes are cached per chunk; the profile says the draw dispatch is not.

### 2.3 Who owns particles?

57 custom particle classes in Alex's Caves, 17 Alex's Mobs, 17 Supplementaries, 11 Particular, 7 Create, 7 Mowzie's, 5 Amendments, 5 Mahou Tsukai, 3 Farmer's Delight, 2 ParticleRain, 2 Wakes. The mixins touching `ParticleEngine`: Particle Core (7), Particle Effects (3), Oculus (2), ParticleRain (2), WATUT (2), Embeddium Extra (1). Weather alone is implemented four times: ParticleRain (vanilla rain disabled via `renderDefaultWeather=false`, up to 1500 particles), Immersive Winds (particle rain/snow, splash particles, `rainParticleCount=200`), Particular (`rainRipples`, `waterDripRipples`), and sounds three times (Cool Rain = rain-on-block sounds only, ParticleRain's rain/wind volumes, Immersive Winds' `rain_impact_*` events whose 12 `.ogg` files do not exist in its jar). The JFR attribution in section 6 splits the 14.5 % per mod.

### 2.4 The GUI cost

`gui` is 11.5 % of the frame although nothing complicated is on screen. Forge overlays are not profiled separately. Candidates: Lads Core HUD modules (the shared `thelads_config.json` has 61 of 90 modules enabled, the 1.20.1 core implements a subset: FPS, Coordinates, ArmorHUD, Keystrokes, Paperdoll, PingHUD, Potion Effects, Nametags, TabList ...), Xaero's minimap with entity radar (`display_radar=true`, `radar_smooth_dots=true`, biome blending, terrain slopes), Immersive Hotbar (animated XP bar, glow, particles), Create goggles/Paraglider overlays. The A/B in section 6 isolates it with F1 (hide GUI), minimap off and radar off.

---

## 3. Startup and memory `[LOG]`

| session (2026-10-08) | `Game took` | ModernFix bootstrap stage | mod construction starts | resource reload starts | world open total |
|---|---:|---:|---:|---:|---:|
| 02:23 | 33.8 s | 11.0 s | | | 41.8 s |
| 17:15 | 39.6 s | 9.2 s | 13 s | 24 s | |
| 19:37 | 37.7 s | 11.9 s | 20 s | 26 s | |
| 20:03 | 51.1 s | 15.1 s | 25 s | 32 s | 59.9 s |
| 20:07 | **33.3 s** | 9.96 s | 15 s | 21 s | **41.9 s** |
| 20:43 | 51.9 s | 15.7 s | 24 s | 33 s | 60.4 s |
| 21:34 | 57.2 s | | | | 67.7 s |

The spread (33-57 s) is machine load, not the pack: the same jar set was 33 s when the PC was idle and 52-57 s while other lanes compiled. Order of the best run: 0-9 s JVM + Forge discovery + Connector + Mixin bootstrap, 9-15 s vanilla bootstrap and registries, 15-21 s mod construction (Quark alone constructs 164 modules), 21-32 s resource reload (atlas 4096x2048, Moonlight/Supplementaries runtime packs 622 ms, ETF/EMF scanning), then 8.5 s from the main menu to the world, of which JEI 2.0 s and two datapack reloads of 2.1 s / 0.9 s.

Why it is long, from the jars: **2 137 mixin classes** over 1 099 distinct target classes (ModernFix 197, Oculus 180, Quark 116, Supplementaries 109, Embeddium 98, OPAC 67, EMF 64, ETF 60, Create 57, Alex's Caves 54), 39 480 classes to scan, ModernFix running with *every* opt-in off. The ~11 mods Lads Core replaces account for 8 MB and 130 mixins; the three orphan libs add 32 mixins that hook `Entity`, `ServerLevel`, `ServerPlayer` and `PlayerList` for nothing.

Memory: 6 GB heap is generous. Live set (heap in use at the profile) 2.0-3.3 GB after ~15 minutes with Distant Horizons at radius 64. Known leak signs in the logs: `One or more BufferBuilders have been leaked, ModernFix will attempt to correct this.` on every disconnect from the server (2 of 2 server sessions, immediately after Distant Horizons closes its database), and Balm 7.3.42 (needed by Waystones) has a documented memory leak fixed in 7.3.44.

---

## 4. Static findings

### 4.1 Pack anatomy (what is in the 128 jars)

Full per-mod table with category, side, cost rating, update, action and note: **[MOD_TABLE.md](MOD_TABLE.md)** (also `.csv`/`.json`). Highlights:

* Heavy by design: Distant Horizons (rt H, mem H), Grassier Grass (rt H), Quark/Supplementaries/Create/Alex's Caves (rt M, mem H).
* Library layer: 25 libraries; 6 have no consumer or become unused (4.5).
* 21 of 128 jars have newer builds on Modrinth, listed in the table (`update` column) and `mods_modrinth.json`.
* 10 jars are not on Modrinth under the same hash (BorderlessWindow, Backpacked, Configured, Cosmetic Armor Reworked, Framework, FTB Essentials, FTB Library, MoreCulling "-all" build, Oceanic Realms, theladscore).

### 4.2 Mixin hot spots `[JAR]`

Full list: **[mixin_hotspots.md](mixin_hotspots.md)**. The classes everyone patches (candidates for conflict hunting when a crash mentions them):

| # mods | class | notable |
|---:|---|---|
| 28 | `Minecraft` | ModernFix 9 |
| 22 | `Entity` | Kiwi 2, Artifacts 2, Backpacked 2, Create 2, Exposure 2, ModernFix 2 + 3 orphan-lib hooks |
| 21 | `Player` | ETF 2, Supplementaries 2 ... |
| 20 | `GameRenderer` | Oculus 5 (4 `@Redirect`), EMF 2 |
| 20 | `LevelRenderer` | Oculus 11, Embeddium Extra 6, Embeddium 5 (10 `@Overwrite`), MoreCulling 3, Particle Effects 2, Create 2 |
| 20 | `LivingEntity` | Artifacts 6, Create 3, Supplementaries 3 |
| 15 | `ClientLevel` | Embeddium 4, ParticleRain 2 |
| 15 | `ServerLevel` | ModernFix 3, Citadel, Corgilib, Data Anchor, GlitchCore, SnowRealMagic, YSNS ... |
| 11 | `Gui` | Immersive Hotbar 5, Dynamic FPS 2 |
| 6 | `ParticleEngine` | Particle Core 7, Particle Effects 3, Oculus 2, ParticleRain 2, WATUT 2 |
| 6 | `LocateCommand` | Yung's x2, IDAS, Integrated Villages/Stronghold/API (relevant if Async Locator is added) |

Embeddium logs `Mod mixin into Embeddium internals detected. This instance is now tainted` (Oculus, ETF, Embeddium Extra patch 12 Embeddium classes): expected for this combination, but it means upstream Sodium/Embeddium bug reports will be refused.

### 4.3 Overlapping functionality

| job | mods doing it | verdict |
|---|---|---|
| leaf/face culling | Embeddium (block-face, fog occlusion, entity culling), MoreCulling (leaves DEPTH, item frames, signs, rain, clouds), Cull Leaves (config `enabled=false`), EntityCulling (Lads replaces) | remove Cull Leaves; keep MoreCulling |
| rain / snow visuals | ParticleRain, Immersive Winds (particle rain + snow + splash), Particular (`rainRipples`, `waterDripRipples`), Waves/Wakes (water) | keep ParticleRain; switch off the duplicates in the other two |
| rain sounds | Cool Rain (sound only), ParticleRain sound volumes, Immersive Winds `rain_impact_*`, Ambient Sounds | pick one; Immersive Winds' are silently missing anyway |
| particles budget | Particle Core (Lads replaces), Particle Effects, Particular | Lads Core budget will own this |
| config UIs | Cloth, YACL, Resourceful Config, fzzy_config, MidnightLib, Configured/Catalogue, Searchables+Controlling | 5 of them exist for 1-2 consumers each (fzzy_config: Particle Core only; MidnightLib: Cull Leaves only) |
| backpacks / ropes | Quark Backpack + Backpacked mod; Quark Rope + Supplementaries rope | gameplay duplicates, ~zero perf; Quark has all 164 modules on |
| HUD info | Jade, AppleSkin, chat_heads, Mouse Tweaks (all Lads-replaced) | handled by Lads Core |
| camera | Quark Camera + Exposure | gameplay duplicate |

### 4.4 Config values that hurt (exact file / key / current -> proposed) `[CFG]`

| file (under `minecraft/config/`) | key | now | proposed | why / evidence | side | risk |
|---|---|---|---|---|---|---|
| `grassiergrass-client.toml` | `[rendering] renderRadius` | 160 (cap) | **64-96** | 18.6 % of frame `[PROFILE]` | client | low |
| `grassiergrass-client.toml` | `bladeParticles` | true | false | extra particles from wind (particle stack is already 14.5 %) | client | low |
| `grassiergrass-client.toml` | `denseFlowers` | true | false | spawns extra flower companions | client | low |
| `particular-common.toml` | `rainRipples`, `waterDripRipples` | true, true | false, false | ParticleRain already draws ripples (`rippleRes`, `doDefaultSplashing=false`) | client | low |
| `immersivewind.json` | `enableRainHitParticles`, `enableSplashParticles` | true, true | false, false | duplicate of ParticleRain; `rainParticleCount=200` | client | low |
| `particlerain/config.json` | `perf.maxParticleAmount` | 1500 | 1000 | particle stack 14.5 % | client | low |
| `sound_physics_remastered/soundphysics.properties` | `environment_evaluation_ray_count`, `_ray_bounces`, `max_occlusion_rays` | 32, 4, 16 | 16, 2, 8 | rays per sound source | client | low |
| `modernfix-mixins.properties` | `mixin.perf.dynamic_resources` | off | **true** | RAM + startup (ModernFix wiki) | client | medium (compat test) |
| `modernfix-mixins.properties` | `mixin.perf.dynamic_entity_renderers`, `mixin.perf.clear_mixin_classinfo`, `mixin.perf.faster_item_rendering` | off | try true | startup RAM / GUI item rendering; ModernFix warns some mods crash with entity-renderer laziness | client | medium |
| `embeddium-options.json` | `performance.chunk_builder_threads` | **1** | 0 (shipped baseline) | 1 builder thread on a 24-thread CPU slows chunk updates | client | low |
| `DistantHorizons.toml` | `maxHorizontalResolution` | "BLOCK" (finest) | "TWO_BLOCKS" or "FOUR_BLOCKS" | LOD vertex/GPU cost; DH section 10.8 % | client | low |
| `DistantHorizons.toml` | `enableAntiAliasing` | true | false (low-end) | DH's MSAA pass 0.33 % on this GPU, more on weaker | client | low |
| `options.txt` | `biomeBlendRadius` | 7 | 3 | chunk rebuild colour sampling (15x15 = 225 vs 7x7 = 49 samples per block) | client | low |
| `alexsmobs.toml`, `alexscaves-general.toml` | `pathfindingThreads` / `pathfinding_threads` | 5 and 5 | 2 and 2 | two 5-thread pools on top of DH 3 + chunk builders + C2ME (6-core players) | both | low |
| `incontrol/spawn.json` | whole file | `[]` | real mob caps / per-mod spawn rules | nothing limits Alex's Mobs/Caves, Creeper/Enderman Overhaul, Oceanic Realms | server | gameplay |
| `ysns/disabled_entities.json5` | `disabled` | only example entries | list of entities not wanted | same | server | gameplay |

Already good (do not touch): Embeddium `always_defer_chunk_updates_v2`, `use_compact_vertex_format`, `use_entity_culling`, `use_fog_occlusion`, `animate_only_visible_textures`; DH `numberOfThreads=3`, `threadRunTimeRatio=0.5`, `lodChunkRenderDistanceRadius=64`, `transparency=DISABLED`, `enableCloudRendering=false`, `enableSsao=false`, `horizontalQuality/verticalQuality=LOW`; ImmediatelyFast all regular options on (Lads replaces it); EMF `animationLODDistance=20`; Create `fanParticleDensity=0.5`.

### 4.5 Library orphans and removal candidates `[JAR]`

* **No consumer anywhere in the pack** (no `mods.toml` dependency, no class reference): CorgiLib 4.0.3.5, Data Anchor 1.0.0.22 (10 mixins into Entity/ServerLevel/ServerPlayer/PlayerList; its newer build "fixes memory leak"), Player Animation Lib 1.0.2-rc1 (14 mixins). Verify once by booting without them.
* **Become orphans when Lads Core replaces particle_core**: Kotlin for Forge 4.12.0 (7 MB, 4 314 classes) and fzzy_config 0.7.6 (754 classes). Neither is needed by anything else.
* **Become an orphan when Cull Leaves is removed**: MidnightLib.
* Cull Leaves itself (`cullleaves.json: enabled=false`, `cullRoots=false`).
* Keep: CreativeCore (needed by Ambient Sounds), Konkrete (Just Zoom), CoroUtil (WATUT), GlitchCore (SnowRealMagic references it; 14 `@Overwrite` in its own mixins), Searchables (Controlling), PuzzlesLib (Easy Anvils/Magic).

### 4.6 Log findings `[LOG]`

* Startup warnings that are noise: 8 "Error loading class ... ClassNotFoundException" from optional mod compat mixins (Mekanism, Serene Seasons, TFC, Weather2, Aleki Ships ...), 150+ "Unknown sound in sound rate config" lines (SPR's `sound_rates.properties` is 107 KB from a larger pack), Reference-map warnings.
* Errors repeated on every datapack reload (3 times per session): IDAS refers to Ice and Fire / Ars Nouveau / BYG / Biomes O' Plenty items and entities that are not installed (12 loot tables, 4 biome tags, 2 spawner lists), Integrated Villages `minecraft:village` tag references a missing `swamp_village`, Supplementaries `way_sign_destinations`/`adventure_map_destinations` fail because of that. Harmless but ~20 ERROR lines + stack traces each reload.
* Two Mowzie's models fail to load (`black_pink_grottol`, `diamond_grottol`: "LLibrary bug not putting particle into textures"), one Mahou Tsukai model (`proximity_projection_keys2`).
* Citadel does a blocking patreon HTTP check during resource reload ("Online mod data expired"), and a `GET request failed. Response Code: 301` from a worker.
* Distant Horizons: `G1 Garbage collector detected ... recommended Shenandoah (Java 8 through 17)`, `Partial Oculus support enabled`, `Alex's Caves ... partially incompatible` (chat message; the two Alex's Caves ambient-light options are already false).
* Embeddium `NVIDIA_THREADED_OPTIMIZATIONS` workaround applied (normal on NVIDIA).
* Crash 16:56 (fixed since): Xaero's Minimap 26.6.0 needs Open Parties and Claims >= 0.32.4 and the pack had 0.30.3. Any update of Xaero must be coupled with OPAC (client **and** server).

---

## 5. Recommendations (ranked)

Legend: **C** client only, **S** server needed, **B** both; risk = chance of a visible behaviour change or crash.

### 5.1 Config (exact values in 4.4)

Top five by expected value: (1) Grassier Grass radius, (2) particle de-duplication, (3) ModernFix dynamic resources, (4) Embeddium builder threads back to auto, (5) SPR ray counts. Ready-to-apply form for the pack patch engine: `proposed_pack_patch.json` in this folder (config-only, no downloads).

### 5.2 Remove / replace

| action | what | side | gain | risk |
|---|---|---|---|---|
| remove | Cull Leaves (+ MidnightLib) | C | 1 jar + 5 mixins, no behaviour change (already off) | none |
| remove | CorgiLib, Data Anchor, Player Animation Lib | B | -32 mixins incl. hooks into `Entity`, `ServerLevel`, `ServerPlayer`, `PlayerList`; less startup work | low (boot-test) |
| remove with Lads Core | Kotlin for Forge, fzzy_config | B | -9.6 MB, 5 000 classes, Kotlin init | low |
| remove (optional) | Cool Rain **or** keep it and drop Immersive Winds' rain sounds; Immersive Winds' particle rain | C | audio + particle duplication | low |
| decide | Oculus: 180 mixins and shaders off by default, but shader packs ship in `shaderpacks/`; keep | C | - | - |
| replace | AmbientSounds 6.3.8 -> 6.3.10 (adds biome-regex cache and bounded terrain-height scan), Balm 7.3.42 -> 7.3.44 (memory leak fix), Immersive Aircraft 1.4.1 -> 1.5.0 ("improved render performance"), JEI 15.49 -> 15.62 (cached tooltips, lazy preparation), ModernFix 5.27.85 -> .87 | B/C | small each | low |

### 5.3 Add (verified on Modrinth/CurseForge for 1.20.1 Forge, 2026-10-08)

Nothing below was downloaded or run by this lane except spark (authorised); candidates need the owner's OK before an A/B in the sandbox (section 7).

| rank | mod | side | what it does here | maintained | compat notes | risk |
|---:|---|---|---|---|---|---|
| 1 | **spark** 1.10.53 (Forge, 2023-09) | B | `/spark profiler`, `tps`, `health`, `gc`: the tool to find the next problem | upstream active (newer builds target newer MC) | none | none |
| 2 | **AllTheLeaks** 1.1.3 (CurseForge only, uncandango) | B | generic leak fixes for Minecraft/Forge/mods; has a memory monitor, fixed an Oculus+GeckoLib crash in 1.0.8 | yes | do not enable "Ingredient Dedupe" on 1.20.1 per its own notes | low |
| 3 | **Radium** 0.12.4 (Lithium port, Forge/NeoForge 1.20.1, 2024-09) | **S** (client unsupported) | entity/AI/pathfinding/chunk-tick optimisations on the dedicated server and the integrated server | 2024-09 | **declares Canary incompatible**; do not install both; verify against Create contraption collisions and Alex's Mobs AI in a test world | medium |
| 4 | **ServerCore** 1.5.2 (Forge/Fabric 1.20.1) | **S** | entity activation range, dynamic tick distance/mobcaps, chunk tick distance, breeding caps | 2024-11 | config-driven, most features off by default | low |
| 5 | **Chunky** 1.3.146 | S | pregenerate the world once so Tectonic/Quark/Alex's worldgen cost is paid offline | yes | none | none |
| 6 | **FastFurnace** 8.0.2 + **FastWorkbench** 8.0.4 (+ **Placebo**) | B | recipe lookup caches for furnaces/crafting (matters with Create automation, Quark crafter, Polymorph) | 2026-06 | needs Placebo; test against Polymorph | low |
| 7 | **Saturn** 0.1.3 | B | memory/allocation reductions | 2024-02 (old but small, 90 KB) | optional deps Canary/Pluto only | low |
| 8 | **Packet Fixer** 3.3.2 | B | avoids oversized-packet disconnects in big modpacks | 2026-04 | none | low |
| 9 | **BadOptimizations** 2.4.1 (client) | C | skips redundant lightmap uploads, debug renderers, sky colour sampling | 2025-12 | none declared; DH/Oculus both touch the lightmap -> test | low |
| 10 | **Async Locator** 1.20-1.3.0 | S | async `/locate`, villager explorer maps, dolphins, chest maps (vanilla only) | 2023-06 | **6 mods in this pack mixin into `LocateCommand`**; modded maps (Alex's cave maps with `cave_map_search_attempts=128000`) stay synchronous | medium, low value |
| 11 | **AI Improvements** 0.5.2 | S | vanilla AI tuning | 2023-06 | Alex's Mobs use their own AI | low value |
| - | Memory Leak Fix 1.1.5 | B | superseded by AllTheLeaks | 2024-01 | - | - |
| - | Smooth Boot (Reloaded) 0.0.4 | C | thread priorities; ModernFix already does `thread_priorities` | 2024-01 | - | not worth it |
| - | Better Biome Blend 1.4.0 | C | alternative to lowering `biomeBlendRadius` | 2026-04 | Embeddium has its own blending | skip, use the option |

Explicitly unavailable for 1.20.1 Forge: Lithium (Fabric/Quilt only), ScalableLux (no 1.20.1 build), Krypton (Fabric), Starlight (2023 Forge build, known light bugs), Canary+Radium together.

### 5.4 Updates worth taking

AmbientSounds 6.3.10, Balm 7.3.44, Immersive Aircraft 1.5.0, JEI 15.62.0.219, ModernFix 5.27.87, Moonlight 2.16.36, Exposure 1.9.22, Voice Chat 2.6.24, Waystones 14.1.21 (feature-cycle crash fix), In Control 9.5.0, Integrated API 1.8.2, Create-side nothing. **Xaero's Minimap/World Map 26.6.0/1.47.0 only together with Open Parties and Claims 0.32.7 on client and server.** PuzzlesLib 8.0.15 -> 8.1.35 changes a lot (34 builds) - bump with Easy Anvils/Magic and test.

### 5.5 JVM arguments and RAM (Java 17)

Current (launcher defaults): `-Xms512m -Xmx6144m -XX:+UseG1GC -XX:G1NewSizePercent=20 -XX:G1ReservePercent=20 -XX:MaxGCPauseMillis=50 -XX:G1HeapRegionSize=16M`.

* **RAM**: measured live set 2.0-3.3 GB -> keep **6 GB** for players with >= 16 GB RAM; with `dynamic_resources` 4 GB should be enough; do not go above 8 GB (G1 pause growth, and Distant Horizons/LWJGL/Embeddium use 1.5-2.5 GB of native memory on top of the heap).
* **`-Xms`**: raise to at least 3072m (or `= -Xmx`) so G1 stops resizing the heap during world load.
* **Collector**: Distant Horizons prints the recommendation itself (`ZGC (Java 21+) or Shenandoah (Java 8 through 17)`). Temurin 17 contains Shenandoah (`-XX:+UseShenandoahGC`). Do not add `-XX:+DisableExplicitGC` (DH reports "Explicit Garbage Collection: Enabled" and releases native buffers through it).
* Proposed set to A/B (section 6): `-XX:+UnlockExperimentalVMOptions -XX:+UseG1GC -XX:MaxGCPauseMillis=100 -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=16M -XX:G1ReservePercent=20 -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 -XX:InitiatingHeapOccupancyPercent=15 -XX:+PerfDisableSharedMem -Xms4G -Xmx6G`, versus `-XX:+UseShenandoahGC -Xms4G -Xmx6G`.
* Windows tip (untested): add the instance folder and the launcher's `runtime\java-17` to the antivirus exclusions (464 MB of jars are re-read on every launch).

### 5.6 Server operator notes (PebbleHost, cannot be measured from the client)

* The join log shows the **server runs the same content mods but not**: Lads Core, MoreCulling, Particle Core, Grassier Grass, ParticleRain, Snow Under Trees, Distant Horizons, Continuity, Particle Effects, AppleSkin, Just Zoom, Presence Footsteps. So **Distant Horizons only ever sees chunks the client received**; there is no server-side LOD generation load, and no server-fed distant terrain either.
* Worldgen is the TPS killer: Tectonic (density rewrite) + Lithostitched + Quark's world modules + Alex's Caves cave biomes + IDAS/Integrated structures + two Yung's mods. Noisiumed already helps. **Pregenerate with Chunky** inside a world border and add Radium + ServerCore.
* No mob rules exist (In Control `spawn.json` is `[]`), `watut` sends mouse/typing/GUI packets continuously (`dynamicGuiTickSendRateOfGUIUpdates=10`), Lootr and OPAC check every interaction, SnowRealMagic accumulates snow via random ticks (`weatherTickSlowness=16`).
* Standard server flags (Aikar) on Java 17 and `spark` for `/spark profiler start --timeout 120` during lag reports.
* Keep ModernFix on the server too (`perf.remove_spawn_chunks`, `bugfix.paper_chunk_patches` already default on; `remove_spawn_chunks` is opt-in and worth testing there).

---

## 6. A/B measurements (sandbox)

PENDING - waiting for the shared harness (`work/185-harness`) to reach READY. Planned scenarios: cold startup with JFR (per-mod CPU attribution), steady-state world run (idle / fly / rain / mobs), GC/heap per JVM option set, config variants from `variants.py`, and mod ablations with the harness `--remove-mods` option.

## 7. Needs the owner's decision

* OK to download and A/B-test the candidate jars of 5.3 in the sandbox (sizes: Saturn 90 KB, Radium 696 KB, ServerCore 1.4 MB, FastFurnace 6 KB + FastWorkbench 27 KB + Placebo, BadOptimizations 449 KB, Packet Fixer 184 KB, Chunky 333 KB, AllTheLeaks from CurseForge)? Only spark was authorised.
* Was `chunk_builder_threads = 1` deliberate (to leave CPU for Distant Horizons)?
* Do shaders matter to the pack (Oculus + 8 shader packs shipped)? If not, Oculus (180 mixins) could become optional.

## 8. Files

`MOD_TABLE.md/.csv/.json`, `mixin_hotspots.md/.json`, `mixins_by_mod.json`, `mods_meta.json`, `mods_deps.json`, `mods_modrinth.json`, `candidate_mods_modrinth.json`, `profile_sections.json`, scripts listed in section 1.
