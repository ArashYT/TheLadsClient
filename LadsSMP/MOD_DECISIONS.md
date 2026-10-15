# The Lads SMP — Mod Decisions, Consolidation & Server Exclusion Guide

> [!NOTE]
> All **32 mod removals and consolidations** (`256 -> 225` active mod jars) in `%APPDATA%\.theladsclient\instances\lads-smp-26.3-dev` are **100% reversible**—every removed or consolidated `.jar` has been archived in `lads-smp-26.3-dev/mods-disabled-by-audit/` along with `README_DISABLED_MODS.json`, and the reference instance at `%USERPROFILE%\curseforge\minecraft\Instances\Test For Lads SMP 26.3` remains completely untouched.

---

## 1. Summary of Applied Mod Actions (`256 -> 225` Active Jars)

### A. 13 Small Mods Consolidated into `Lads SMP` (`ladssmp-1.0.0.jar`)
These 13 standalone mods were replaced by clean-room, configurable implementations inside `Lads SMP` (`config/ladssmp/config.json`):

| # | Original Jar Filename | Mod ID | License | Replacement Module in `Lads SMP` | Config Toggle (`config/ladssmp/config.json`) |
| :---: | :--- | :--- | :---: | :--- | :--- |
| 1 | `always_eat-3.1.0.jar` | `always_eat` | MIT | `MixinFoodProperties` | `features.alwaysEat = true` |
| 2 | `noanimaltemptdelay-26.3.0-1.3.jar` | `noanimaltemptdelay` | Custom (Collective) | `MixinTemptGoal` | `features.noAnimalTemptDelay = true` |
| 3 | `betterspawnercontrol-26.3.0-4.9.jar` | `betterspawnercontrol` | Custom (Collective) | `MixinBaseSpawner` + `ConsolidatedFeatures#isSpawnerDisabledByLights` | `features.spawnerControl5Torch = true` |
| 4 | `cryingportals-26.3.0-3.0.jar` | `cryingportals` | Custom (Collective) | `MixinPortalShape` + `data/minecraft/tags/block/nether_portal_frame.json` | `features.cryingObsidianPortals = true` |
| 5 | `despawningeggshatch-26.3.0-4.6.jar` | `despawningeggshatch` | Custom (Collective) | `MixinItemEntity` + `ConsolidatedFeatures#tryHatchDespawningEgg` | `features.despawningEggsHatch = true` |
| 6 | `doubledoors-26.3.0-7.3.jar` | `doubledoors` | Custom (Collective) | `MixinDoorBlock` + `MixinFenceGateBlock` + `ConsolidatedFeatures` | `features.doubleDoors = true` |
| 7 | `deathbackup-26.3.0-3.7.jar` | `deathbackup` | Custom (Collective) | `DeathBackupManager` + `/ladssmp deathbackup list|restore` | `features.deathBackup = true` |
| 8 | `villagernames-26.3.0-8.7.jar` | `villagernames` | Custom (Collective) | `ConsolidatedFeatures#assignVillagerNameIfNeeded` | `features.villagerNames = true` |
| 9 | `FriendlyFire-fabric-MC26.3-26.3.0.1.jar` | `friendlyfire` | LGPL-2.1 | `MixinLivingEntity` + `ConsolidatedFeatures#shouldPreventFriendlyFire` | `features.friendlyFireProtection = true` |
| 10 | `AttributeFix-fabric-MC26.3-26.3.0.1.jar` | `attributefix` | LGPL-2.1 | `MixinRangedAttribute` + `ConsolidatedFeatures#uncapVanillaAttributes` | `features.attributeFixUncap = true` |
| 11 | `flatbedrock-fabric-26.3.0.1.jar` | `flatbedrock` | MIT | `data/minecraft/worldgen/material_rule/bedrock_floor.json` & `bedrock_roof.json` | Built-in worldgen material rule |
| 12 | `skeletonaifix-v26.3.0-mc26.3.x+fabric.jar` | `skeletonaifix` | MPL-2.0 | `MixinRangedBowAttackGoal` | `features.skeletonAiFix = true` |
| 13 | `golem_spawn_fix-fabric-26.3-1.0.2.jar` | `golem_spawn_fix` | AFL-3.0 | `MixinCarvedPumpkinBlock` + `ConsolidatedFeatures#isPermittedGolemCornerBlock` | `features.golemSpawnFix = true` |

### B. 7 Broken, Duplicate, Invalid, or Harmful Jars Removed (Archived in `mods-disabled-by-audit/`)

| # | Jar Filename | Mod ID | Technical Reason for Removal |
| :---: | :--- | :--- | :--- |
| 1 | `alexsmobsfabric-26.3-1.0.0.jar` | `alexsmobs` | Duplicate Alex's Mobs port whose SemVer (`26.3.0`) shadowed CodxIO's `alexsmobs-2.2.2-fabric+26.3.jar` (`2.2.2`) and pulled in the broken `citadelfabric-26.3-1.0.0.jar`. |
| 2 | `citadelfabric-26.3-1.0.0.jar` | `citadel` | Incomplete port whose `ClientEvents.registerItemModels()` is a compiled `return;` stub, causing 6 missing item model errors on startup. Replaced by `citadel-2.7.2` embedded in `alexsmobs-2.2.2-fabric+26.3.jar`. |
| 3 | `EuphoriaPatcher-1.10.5-r5.9.3-fabric.jar` | `euphoria_patcher` | Throws `[EuphoriaPatcher] No Complementary file found in shaderpacks` error and overwrites the user's system clipboard on world join. |
| 4 | `Ksyxis-1.4.6.jar` | `ksyxis` | Injects into `PrepareSpawnTask` on MC 26.3 and forces the login spawn chunk radius from `3` to `0`, causing players to spawn into unloaded chunks / fall into void on login. |
| 5 | `gpumemleakfix-fabric-26.3-1.9.jar` | `gpumemleakfix` | Injects deprecated `Object.finalize()` onto `RenderTarget` on Java 25, adding GC finalizer queue pressure even though MC 26.3's `renderpearl` `RenderTarget.destroyBuffers()` already closes GPU textures deterministically. |
| 6 | `multi-key-bindings-1.3.5+26.3.jar` | `multi-key-bindings` | Contains a hardcoded `if (keyCode == 342)` (Left Alt) check in `KeyMappingMixin` that causes unintended multi-key activation whenever Left Alt is pressed. |
| 7 | `regs-more-foods-1.5.2+26.3+mod.jar` | `regs_more_foods` | Invalid MCreator NeoForge-only jar lacking `fabric.mod.json` (also contained a 30% bonus Dragon Egg loot table). |

### C. 12 Competing / Redundant Content Jars Removed (Archived in `mods-disabled-by-audit/`)

| # | Jar Filename | Mod ID | Gameplay-Design & Performance Rationale |
| :---: | :--- | :--- | :--- |
| 1 | `Nullscape_26.3_v2.0.1+26.3.jar` | `nullscape` | Removed in favor of **`betterend` + `mes`** (`Moog's End Structures`) so End terrain noise and biome generators do not conflict. |
| 2 | `chisel-fabric-2.2.1+mc26.3.jar` | `chisel` | Removed in favor of **`rechiseled` + `stoneworks` + `factory_blocks`**, saving **1,293 block registrations** and 8.1 MB of model/texture overhead. |
| 3 | `cryonicconfig-fabric-1.0.0+mc26.3.jar` | `cryonicconfig` | Unused library dependency after removing `chisel`. |
| 4 | `signpost-fabric-26.3-2.04.1.jar` | `signpost` | Removed in favor of **`waystones`** as the single standardized village/player teleportation network. |
| 5 | `animalgarden-commonraven-1.0.2-fabric-26.3-0.161.0.jar` | `animalgarden_commonraven` | MCreator single-animal mod removed in favor of **`alexsmobs` + `naturalist`**. |
| 6 | `animalgarden-owl-1.2.8-fabric-26.3-0.161.0.jar` | `animalgarden_owl` | MCreator single-animal mod removed in favor of **`alexsmobs` + `naturalist`**. |
| 7 | `animalgarden-prairiedog-1.0.3-fabric-26.3-0.161.0.jar` | `animalgarden_prairiedog` | MCreator single-animal mod removed in favor of **`alexsmobs` + `naturalist`**. |
| 8 | `animalgarden-redpanda-1.1.3-fabric-26.3-0.161.0.jar` | `animalgarden_redpanda` | MCreator single-animal mod removed in favor of **`alexsmobs` + `naturalist`**. |
| 9 | `animalgarden-seaotter-1.1.1-fabric-26.3-0.161.0.jar` | `animalgarden_seaotter` | MCreator single-animal mod removed in favor of **`alexsmobs` + `naturalist`**. |
| 10 | `animalgarden-westerngorilla-1.0.1-fabric-26.3-0.161.0.jar` | `animalgarden_westerngorilla` | MCreator single-animal mod removed in favor of **`alexsmobs` + `naturalist`**. |
| 11 | `aquariuslibs-1.2.0-fabric-26.3-0.160.5.jar` | `aquarius_libs` | Unused MCreator library dependency after removing the 6 `animalgarden_*` mods. |
| 12 | `voice-physics-2.11.1+mc1.16.5-26.x.jar` | `vc-audio-distance` | Removed in favor of **`sound_physics_remastered`** to eliminate duplicate OpenAL acoustic raycasting on Simple Voice Chat channels. |

---

## 2. Small Mods Evaluated & Intentionally Retained as Standalone
- **`Clumps-fabric-26.3-26.3.2.jar`**: Uses a custom `IClumpedOrb` map merging XP orb values and firing `ClumpsEvents` for Mending calculations; negligible overhead and high stability.
- **`letmedespawn-fabric-26.3-1.26.9.2.jar`**: Tracks equipment tags (`pickedUpItems`) across `MobMixin`, `EnderManMixin`, and `EquipmentDispenseItemBehaviorMixin` so zombies/skeletons that pick up trash items still despawn without losing player gear.
- **`leavesbegone-v26.3.0-mc26.3.x+fabric.jar`**: Uses custom chunk-level `RandomBlockTickerChunk` scheduling for instant leaf decay without lag spikes.
- **`respawninganimals-v26.3.0-mc26.3.x+fabric.jar`**: Manages animal persistence vs natural despawn rules via `PuzzlesLib`.
- **`stackrefill-26.3.0-6.0.jar` & `stickyenchantinglapis-26.3.0-1.6.jar`**: Retained as standalone mods because `stickyenchantinglapis` uses client-server packet sync (`ToClientReceiveLapisCountPacket`) and custom `EnchantmentMenu` slot persistence.
- **`ctm-reborn-fabric-4.9.2.jar`**: Retained because `factory_blocks-fabric-1.4.2+mc26.3.jar` uses `.mcmeta` `"ctm"` definitions in `assets/factory_blocks/`.

---

## 3. Dedicated Server Exclusion List (Client-Only Mods)

When deploying **The Lads SMP** to a dedicated Linux/Windows server, **exclude the following 49 active client-only mod jars** (these remain active on the client in `lads-smp-26.3-dev` for FPS, HUD, and visuals):

| Category | Client-Only Mod IDs |
| :--- | :--- |
| **Rendering & FPS Optimization** | `sodium`, `iris`, `immediatelyfast`, `entityculling`, `badoptimizations`, `fastershadowmapper`, `particle_core`, `fadeless` |
| **Entity / Block / World Visuals** | `armor_3d`, `skinlayers3d`, `notenoughanimations`, `entity_model_features`, `entity_texture_features`, `entity_sound_features`, `continuity`, `fusion`, `ctm_reborn`, `better_end_sky`, `wakes`, `subtle_effects`, `effectual`, `fwa`, `elytra_physics`, `fishingrodfix`, `disablefog`, `mindfuldarkness`, `shadowdrop` |
| **HUD, Tooltips & Menus** | `modmenu`, `appleskin`, `animatedhearts`, `immersive-hotbar`, `chat_heads`, `fancytoasts`, `effectinsights`, `enchdesc`, `uei`, `jei_buttons`, `aci`, `ali`, `awi` |
| **Controls, Camera & QoL** | `controlling`, `searchables`, `mousetweaks`, `bridgingmod`, `cutthrough`, `ijmtweaks`, `worldeditcui` |
| **Audio & Client Maps** | `sounds`, `extremesoundmuffler`, `xaerominimap`, `xaeroworldmap`, `xmmp`, `noxesium` |
