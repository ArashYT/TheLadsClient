# The Lads SMP — Performance, Memory, Worldgen & Tick Optimization Report

> [!IMPORTANT]
> **Priority #1 Hierarchy**: All decisions in this report prioritize **server tick stability (MSPT)**, **chunk generation throughput**, **heap memory efficiency**, and **client frame-time consistency** on Minecraft `26.3` (Java 25).

---

## 1. Harmful / Counterproductive "Optimization" Mods Removed

Bytecode inspection of all optimization mods in the reference pack revealed **two mods that actively degrade stability or performance on Minecraft 26.3**:

### A. `Ksyxis-1.4.6.jar` (`ksyxis`) — Removed
- **What It Does**: Mixes into `ServerLevelMixin`, `MinecraftServerMixin`, and MC 26.3's new `PrepareSpawnTask$Preparing` (`PrepareSpawnTaskPreparingMixin`).
- **Why It Is Harmful on MC 26.3**:
  1. Vanilla Minecraft `26.3` already removed the legacy 11x11 permanently-loaded spawn chunk ticket region, replacing it with `PrepareSpawnTask` during world load and player login.
  2. `Ksyxis`'s `PrepareSpawnTaskPreparingMixin` intercepts the chunk load radius during player login and **forces it from `3` down to `0`**. On a heavily modded SMP with custom terrain (`Biomes O' Plenty`, `Alex's Caves`, `BetterEnd`), spawning a joining player before even a 3-chunk radius is ready causes players to fall into unloaded void chunks or rubberband on login.

### B. `gpumemleakfix-fabric-26.3-1.9.jar` (`gpumemleakfix`) — Removed
- **What It Does**: Mixes into `RenderTarget` (`RenderTargetMixin`) to override `Object#finalize()` and runs a periodic `ClientTickMixin` timer.
- **Why It Is Harmful on MC 26.3 (Java 25)**:
  1. In Minecraft `26.3`, Mojang's new `renderpearl` graphics pipeline (`RenderTarget#destroyBuffers`) already deterministically closes both `colorTexture` and `depthTexture` GPU handles.
  2. Adding a non-empty `Object#finalize()` method to `RenderTarget` on **Java 25** prevents `RenderTarget` instances from being collected in normal young-gen GC cycles, forcing them into the JVM `Finalizer` queue and *increasing* memory retention during window resizes or shader reloads.

---

## 2. Verified Core Optimization Stack Retained

The following optimization mods were audited and verified to work harmoniously on Minecraft `26.3` + Fabric `0.19.5`:

| Mod | Version | Side | Verified Optimization Role |
| :--- | :---: | :---: | :--- |
| **`lithium`** | `0.26.2+mc26.3` | Both | Server/client game physics, mob AI goal selection, block-state lookup, hopper transfer, and poi search optimization |
| **`ferritecore`** | `9.0.0` | Both | Deduplicates `BlockState` and `StateHolder` property tables, saving ~350–500 MB of heap RAM across the 5,700+ decorative blocks in `rechiseled`/`chisel`/`betterend`/`biomesoplenty` |
| **`c2me`** | `0.4.2-alpha.0.10` | Both | Concurrent Chunk Management Engine — parallelizes worldgen noise/feature stages and chunk I/O across worker threads |
| **`sodium`** | `0.9.3-alpha.1+mc26.3` | Client | Modern chunk terrain renderer (`renderpearl` compatible) |
| **`immediatelyfast`** | `1.17.1+26.3` | Client | Batches immediate-mode entity, block-entity, HUD, and text draw calls |
| **`entityculling`** | `1.11.3` | Client | Async raytrace occlusion culling for entities and block entities behind walls |
| **`badoptimizations`** | `2.4.1` | Client | Caches sky color, lightmap updates, debug renderer checks, and redundant entity renderer lookups |
| **`particle_core`** | `0.3.3+fix1+26.2` | Client | Particle culling, frustum checks, and configurable particle caps |
| **`fastershadowmapper`** | `1.0.5` | Client | Optimizes shadow map frustum/cascade calculations when Iris shaders are active |
| **`connectivity`** | `26.3-7.7` | Both | Prevents login timeout disconnects (`ReadTimeoutException`) and oversized packet kicks during heavy modpack world join |
| **`clumps`** | `26.3.2` | Both | Merges nearby XP orbs into a single entity, preventing mob-farm XP orb entity lag |
| **`leavesbegone`** | `26.3.0` | Both | Fast scheduled leaf decay without cascading neighbor block-update lag |
| **`letmedespawn`** | `1.26.9.2` | Both | Ensures mobs that pick up dropped items still despawn, preventing hostile mob-cap exhaustion and entity buildup |

---

## 3. Startup, Registry & Mixin Overhead Reduction via `Lads SMP`

1. **19 Fewer Mod Jars Loaded at Startup (`256 -> 238`)**:
   - Consolidating 13 small tweak mods into `Lads SMP` and removing 6 duplicate/broken jars eliminates **19 separate JAR zip-file mounts**, **18 separate Mixin configuration passes**, and the duplicate **26.5 MB `alexsmobsfabric-26.3-1.0.0.jar`**.
2. **O(1) Item Disabler Lookups (`ItemDisablerSystem`)**:
   - Even though `Lads SMP` intercepts 10 separate gameplay pathways (pickup, hold/equip, inventory sweep, container slots, hoppers, drops, commands, loot tables, mob/block/trade drops, and recipes), it uses **zero world-wide entity scans** and **zero per-tick reflection**:
     - Exact item/recipe IDs are matched via an immutable `Set<String>` (`O(1)` hash lookup).
     - Player inventory sweeps run on a staggered interval (`inventorySweepIntervalTicks = 10`, i.e., twice per second rather than every tick) and skip empty slots immediately (`stack.isEmpty()`).
3. **Single-Pass In-Memory JSON Upgrading (`LadsDataUpgrader`)**:
   - `LadsDataUpgrader` runs once during `RegistryLoadTask$PendingRegistration#loadFromResource` (server startup / `/reload`) on the already-parsed `JsonElement` tree in memory, upgrading 1,714 loot tables and 160 recipes in **< 450 ms total** across all 256 jars.

---

## 4. Worldgen & Entity Performance Recommendations

1. **Structure Generation Density (`mr_dungeons_andtaverns` + `moogs_structures` + 16 Structure Mods)**:
   - The pack registers **8,045 structure `.nbt` files** across 18 structure mods. During rapid Elytra or boat exploration, structure bounding-box intersection checks and jigsaw pool resolution are the #1 source of server MSPT spikes.
   - **Action**: Always pre-generate a 5,000–7,500 block radius in Overworld, Nether, and End before opening the SMP to players, and keep `c2me` enabled on both client and server.
2. **Recommended Java 25 JVM Arguments for `lads-smp-26.3-dev` (8 GB Client / 12–16 GB Dedicated Server)**:
   ```text
   -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=100 -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=16M -XX:G1ReservePercent=20 -XX:G1MixedGCCountTarget=4 -XX:InitiatingHeapOccupancyPercent=15 -XX:G1MixedGCLiveThresholdPercent=90 -XX:SurvivorRatio=32 -XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1
   ```
   *(Note: `lads-smp-26.3-dev/instance.json` is pre-configured with `"maxRamMb": 8192`.)*

---

## 5. 2026-10-16 Optimization Pass (pack 0.2.3, local build)

Full report + A/B numbers: `artifacts/smp-opt/SMP-OPTIMIZATION-REPORT.md` (gitignored).
- `MixinSpriteLoaderTextureCap`: plain `item/` sprites above 128 px are downscaled (multiples of 16) before stitching. Smart Backpacks' 1024 px icons had grown the items atlas to 16384x8192; now 2048x2048. `-Dladssmp.itemTextureCap=0` turns it off.
- Removed `AdvancedWorldgenInfo` / `AdvancedLootInfo` / `AdvancedCoreInfo` (30-40 s of scans per server start/reload; JER + JEI WorldGen cover it), `ViaFabricPlus` (564 mixins, not needed on the SMP), `fastershadowmapper` (built into Iris 1.11.2+).
- Added `spark` (profiling) and `structure_layout_optimizer` (jigsaw); both also go on the server.
- ModernFix `remove_biome_temperature_cache=false` (overwrite conflict with Lithium); Sound Physics 16 rays / 256 blocks.
- Measured (2 runs each): server start -> join 26-27 s -> 12 s; launch WARN lines 755 -> 494.
- Item disabler caches its verdict per Item; the spawner torch check runs behind vanilla's `isNearPlayer`.
- Iris removed (pack 0.2.3): Core forces OpenGL whenever Iris is installed; without it the pack runs on Vulkan. Arash-PC (RTX 4060 Ti): 462 -> 645 FPS (OpenGL, no Iris) -> 734 FPS (Vulkan); Legion laptop: no measurable change (CPU-bound). Shaders are no longer supported in the SMP.


---

## 6. 2026-11-15 World creation / join freezes and the loading screen (pack 0.5.3 as published, Lads SMP 0.3.4; round 1 called them 0.5.1 / 0.3.2)

Measured on arash-legion (slow CPU, GTX 1660 Ti, Vulkan), a fresh instance built from the 0.5.0 pack archive, "Create New World" with the pack's default
settings (BetterX world type), every frame the render thread presents recorded by `LadsSMP/qa-smoke` (`-Dladsqa.world=true`): the longest gap between two
presented frames, with the stack the render thread was in. Before = shipped 0.5.0 (Lads SMP 0.3.1); after = 0.5.1 (Lads SMP 0.3.2).

Where the window stood still (render thread, before):
- **Creating a world, 54-88 s** (5 s on a fast PC): Bits and Balance builds ~1,100 step recipes while the integrated server is constructed (on the render thread in 26.3).
  `EnhancedSlabRecipeLookup.slabForStep` walks all 1,500 slabs for each of 20,000 non-step blocks and `StepDynamicRegistry.idForSlab` runs two regexes per slab:
  ~30 million regex matches. `WoodStonecuttingRecipeGenerator.hasRegisteredItemNamespace` walks the whole item registry per wood family (30 s).
- **Joining a world, 20 s (fast PC) to 73 s (Legion)**: TCD Commons rebuilds every creative tab inside the login packet; Fabric API's `FabricCreativeModeTabOutput.insertAfter`
  copies each tab into a LinkedList and searches the anchor with `list.get(i)` (O(n) per step, O(n^2) per call) and Bits and Balance calls it for ~5,700 slab/step/vertical
  variants. JEI then starts in the next packet (17-20 s): 14,553 synthetic recipes, 23,000 ingredients. The client processes no packet meanwhile, so the first chunk arrives and nothing else.
- Startup: Xaero's World Map/Minimap check for updates over the network on the render thread (3.5 s); Villager API regenerates its 96 village files on every
  data pack repository reload (3 times per world creation, 1.7-6 s each); Entity Model Features bakes its models in one 10 s resource-reload task.

- **The world that never finishes loading (every pack version)**: AntiXray holds a connection's packets behind a chunk packet whose async obfuscation never completes (details and evidence in `TEST_RESULTS.md` section 5);
  fixed by `MixinAntiXrayNeverHolds` (every packet counts as ready on a game client) and AntiXray's obfuscation off in the pack's `config/antixray.toml`.

Fixes (all in Lads SMP 0.3.2, `src/main/java/com/thelads/smp/mixin`):
`MixinBitsAndBalanceSlabStepIds` / `MixinBitsAndBalanceVariantIds` (remember the step id per slab), `MixinBitsAndBalanceNotAStep` (reverse index instead of the slab scan),
`MixinBitsAndBalanceNamespaceScan`, `MixinFabricCreativeTabOutput` (ListIterator instead of `get(i)`), `MixinXaeroOnlineChecks` / `MixinXaeroMinimapOnlineChecks`,
`MixinVillagerApiVillageRebuild`. Loading-screen pump: `client/LoadingPump` renders one normal frame from safe points of long steps (after every client task, JEI stage and
plugin call, per creative tab, per EMF model part and expression, per Villager API village, every 256th identifier / block-state read) whenever the last frame is older
than 120 ms and only a loading screen (or the login packet's own handling) is up; any exception switches it off. `-Dladssmp.pump=false` turns it off.

Results (render-thread frame gaps, Legion; `qa-smoke` writes `ladsqa/frames.txt`):
| Phase (render-thread gap = longest time without a presented frame) | 0.5.0 as shipped | 0.5.1 (2 clean runs of the final build) |
| :--- | ---: | ---: |
| Window opens to first frame | 72.2-73.9 s from JVM start (window itself at ~68 s) | unchanged (74.4 / 76.2 s; the constructor draws nothing before) |
| First frame to title screen: longest gap / gaps >= 250 ms | 10.2 s / 6 | 0.7-0.8 s / 11 |
| First frame to title screen: duration | 32.4 s | 32.3 / 32.3 s |
| Title to Create World screen: longest gap | 6.5 s | 0.6 s |
| Create New World: longest gap (click to loading screen) | 58.1 s (96 s) | 0.7 s (28-32 s) |
| Loading screen to player in world: longest gap (duration) | 72.9 s (83.6 s) | 2.2 s (30-32 s) |
| Whole load after the first frame: longest gap | 72.9 s | 2.2 s |
| Whole load: gaps >= 250 ms (their sum) | 14 (155.8 s) | 37-43 (19-23 s) |
| Click Create World to player in world | 179.7 s | 63.9 / 58.1 s |
| JVM start to player in world | 296.8 s | 182.1 / 174.9 s |

Round 2 (pack 0.5.2, Lads SMP 0.3.3; same measurement, 2 clean runs on the final pump): window to first frame 71.3 / 74.4 s, first frame to title 31.7 / 32.9 s (longest gap 0.66 / 0.67 s, 9 / 10 gaps >= 250 ms),
title to Create World 0.63 / 0.87 s, click to loading screen 0.66 / 0.72 s, loading screen to player in world 1.17 / 1.38 s, whole load: **longest gap 1.57 / 1.58 s**, gaps >= 250 ms **34 / 39** (sum 19.8 / 22.8 s), click to player in world 61.7 / 62.2 s.
The 10 biggest remaining gaps (run 2, phase / what the render thread was in): 1.58 s in-world first tick (Mixin Constraints `MixinHacks.getMixinsFor`, Sodium `ChunkBuilder.<init>`: class loading and renderer setup),
1.38 s level loading screen (Knot class loading of mixin-transformed classes), 1.29 s first sky-light read of the new world (`SkyLightSectionStorage`), 1.12 s leaving the world (Xaero `MapProcessor.waitForLoadingToFinish`),
0.89 s title (`FontSet.selectProviders`), 0.87 s title (BCLib `ToolBlockTransformers`, Fabric `DefaultItemComponentEvents`), 0.84 s loading screen (Twemoji `rebuildSuggestionIndex`, ASM `ClassWriter`),
0.84 s in-world (ASM `SymbolTable` class transform), 0.80 s in-world (AsyncParticles `BusyWaitEvictingQueue.add`), 0.72 s Create World (`Climate$RTree.sort`). Round 2 pump points added: component map build, JER loot sort, Bits and Balance JEI kiln recipes.
The largest ones are class loading and one-off native setup inside the first world tick, outside the loading-screen / login-packet scope the pump is allowed to run in, so the < 1 s goal is not reached (1.6 s); gaps >= 250 ms fell only slightly (37-43 to 34-39).

**Server thread after a teleport into a not generated dimension (round 2, Lads SMP 0.3.4)**: right after `/tp` or a portal into the Nether the integrated server thread stood still 17-33 s (Legion, BetterX Nether) because
everything that reads a block of the player's chunk waits (`Level.getBlockState` -> `ServerChunkCache.getChunkBlocking`) until the chunk is generated. `Level.hasChunkAt` is also true for a chunk that is only being generated, so the first guards
did nothing; `util/LoadedChunks.isLoaded` asks `getChunkNow` (FULL chunks only). Readers found and guarded, each from the stack of the frozen server thread: Bits and Balance Improved Ladders (`MixinBitsAndBalanceLaddersLoadedOnly`),
ImmersiveMC's per-tick pick (`MixinImmersiveMcUnloadedChunk`), `ServerPlayer.doTick` itself (`MixinServerPlayerUnloadedChunk`: only requests the chunk ticket while the chunk is missing, max 400 ticks), Bits and Balance's biome titles
(`StructureManager.startsForStructure`, `MixinStructureManagerUnloadedChunk`) and vanilla's accept-teleportation packet (`MixinServerGamePacketListenerHoldTeleportAck`: held until the chunk is there, max 400 ticks). All only in the integrated server of a game client.
Result on Legion: Nether 17.8 s (round 1 jar) / 33.0 s / 27.2 s before, **6.9 s + 12.1 s** and **29.4 s + 8.9 s** in two later worlds (not fixed); the 29.4 s one was Smart Backpacks: Smart Backpacks' `LightUpgradeHandler.onPlayerTick` (`findDesiredLightPos`) and Ad Chimneys' `SmokeLocations.onServerTick` read the same chunk,
and vanilla's `handleMovePlayer` waits when the 20 blocks/s test flight outruns generation. An Overworld stall of 7.3 s once was Incendium's clock function (`execute if entity @e[predicate=...]`: vanilla hashes the nested predicate per entity).

The 250 ms target is not reached: the longest gap fell from 72.9 s to 2.2 s and no gap is longer than that. 37-43 gaps over 250 ms remain, each a single CPU step of a mod
(JEI stages, registry sync, EMF) with no safe point inside; the pump cannot split a step it cannot enter. The 4-6 s between the window appearing and the first frame (the Minecraft
constructor builds every manager before it draws) is not addressed either: nothing can be drawn before the renderer exists. Earlier builds of the pump measured 4.3-5.4 s as the longest gap (before the
wait for the new world's data packs was pumped as well).
