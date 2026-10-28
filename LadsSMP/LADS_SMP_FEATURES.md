# The Lads SMP — Custom Mod (`ladssmp`) Architecture & Feature Reference

> [!IMPORTANT]
> **Project Location**: `<repo-root>\LadsSMP`  
> **Built Artifact**: `<repo-root>\LadsSMP\build\libs\ladssmp-1.0.0.jar`  
> **Installed In Dev Instance**: `%APPDATA%\.theladsclient\instances\lads-smp-26.3-dev\minecraft\mods\ladssmp-1.0.0.jar`  
> **Target Environment**: Minecraft `26.3` | Fabric Loader `0.19.5` | Fabric API `0.163.0+26.3` | Java `25`

---

## 1. Internal Modular Architecture

`Lads SMP` (`modid: ladssmp`) is structured into 5 cleanly separated packages:
- **`com.thelads.smp.config`** ([`LadsConfig.java`](LadsSMP/src/main/java/com/thelads/smp/config/LadsConfig.java)) — JSON configuration manager (`config/ladssmp/config.json`) with per-feature toggles and parameters.
- **`com.thelads.smp.disabler`** ([`DisabledContentConfig.java`](LadsSMP/src/main/java/com/thelads/smp/disabler/DisabledContentConfig.java), [`ItemDisablerSystem.java`](LadsSMP/src/main/java/com/thelads/smp/disabler/ItemDisablerSystem.java)) — The 10-pathway Comprehensive Item & Content Disabling Engine, quarantine storage, and audit logger.
- **`com.thelads.smp.compat`** ([`LadsDataUpgrader.java`](LadsSMP/src/main/java/com/thelads/smp/compat/LadsDataUpgrader.java)) — Automatic Minecraft 26.3 Loot Table, Condition, Recipe & Progression Balance Upgrader.
- **`com.thelads.smp.features`** ([`ConsolidatedFeatures.java`](LadsSMP/src/main/java/com/thelads/smp/features/ConsolidatedFeatures.java), [`DeathBackupManager.java`](LadsSMP/src/main/java/com/thelads/smp/features/DeathBackupManager.java)) — Clean-room implementations of 13 consolidated small QoL/tweak mods.
- **`com.thelads.smp.command`** ([`LadsCommands.java`](LadsSMP/src/main/java/com/thelads/smp/command/LadsCommands.java)) — `/ladssmp` admin command suite for live reload, item ban/unban, audit inspection, and death inventory restoration.

---

## 2. Comprehensive Item & Content Disabling System (All 10 Mandatory Pathways)

Configured via `config/ladssmp/disabled_items.json`, `ItemDisablerSystem` supports:
- **Exact Item IDs**: `"smartbackpacks:flight_upgrade"`
- **Namespace / Wildcard Globs**: `"femboymod:*_backpack"`, `"futuremod:*"`
- **Item Tags**: `"#ladssmp:disabled"`
- **Scoped Disabling**: `disabledItems` (global across all 10 pathways), `recipeOnlyDisabledItems` (blocks crafting/smelting while allowing mob drops), `lootOnlyDisabledItems` (blocks chest/loot-table generation while allowing boss drops), and `disabledRecipeIds`.

### How Each of the 10 Required Pathways Is Enforced

| # | Required Pathway | Mixin / Event Hook in `Lads SMP` | Runtime Behavior |
| :---: | :--- | :--- | :--- |
| **1** | **Prevent Pickup & World Item Entities** | [`MixinItemEntity.java`](LadsSMP/src/main/java/com/thelads/smp/mixin/MixinItemEntity.java) (`tick` & `playerTouch`) | Immediately discards any `ItemEntity` carrying a disabled item on spawn/tick and cancels `playerTouch` pickup before it can enter an inventory. |
| **2** | **Prevent Holding, Equipping & Using** | `UseItemCallback`, `UseBlockCallback`, `AttackBlockCallback`, `AttackEntityCallback`, [`MixinLivingEntity.java`](LadsSMP/src/main/java/com/thelads/smp/mixin/MixinLivingEntity.java) (`onEquipItem`) | Cancels right-click use, block placement, block/entity attacks, and armor/offhand/Trinket equipping; immediately strips & quarantines the item. |
| **3** | **Sanitize Existing Player Inventories** | `ServerTickEvents.END_SERVER_TICK` + `ServerPlayConnectionEvents.JOIN` (`ItemDisablerSystem#sanitizePlayer`) | Sweeps main inventory, hotbar, armor, and offhand on player login and every `10` ticks (`0.5s`). Applies configurable `existingItemPolicy`: **`QUARANTINE`** (saves full JSON snapshot to `config/ladssmp/quarantine/<uuid>.json`), **`DELETE`**, or **`REPLACE`** (replaces with `replacementItemId`), and writes to `config/ladssmp/audit.log`. |
| **4** | **Block Container, Slot & Hopper Transfers** | [`MixinSlot.java`](LadsSMP/src/main/java/com/thelads/smp/mixin/MixinSlot.java) (`mayPlace`), [`MixinAbstractContainerMenu.java`](LadsSMP/src/main/java/com/thelads/smp/mixin/MixinAbstractContainerMenu.java) (`clicked`), [`MixinHopperBlockEntity.java`](LadsSMP/src/main/java/com/thelads/smp/mixin/MixinHopperBlockEntity.java) (`addItem`) | Blocks inserting disabled items into any container slot, sanitizes clicked stacks in any open menu, and prevents Hoppers/droppers from pulling or pushing disabled items. |
| **5** | **Prevent Dropping or Player-to-Player Transfer** | [`MixinServerPlayer.java`](LadsSMP/src/main/java/com/thelads/smp/mixin/MixinServerPlayer.java) (`drop`) | Intercepts `ServerPlayer#drop(ItemStack, boolean, boolean)` and `drop(boolean)`, cancelling the drop and quarantining the disabled item so it can never be tossed to another player. |
| **6** | **Prevent Spawning via Commands (`/give`, `/item`)** | [`MixinItemInput.java`](LadsSMP/src/main/java/com/thelads/smp/mixin/MixinItemInput.java) (`createItemStack`) | Hooks `net.minecraft.commands.arguments.item.ItemInput#createItemStack` and throws a clean `CommandSyntaxException` (`"Item '<id>' is disabled on The Lads SMP"`) whenever `/give` or `/item` attempts to instantiate a disabled item. |
| **7** | **Strip from Vanilla & Modded Loot Tables** | [`MixinRegistryLoadTask.java`](LadsSMP/src/main/java/com/thelads/smp/mixin/MixinRegistryLoadTask.java) + [`MixinLootTable.java`](LadsSMP/src/main/java/com/thelads/smp/mixin/MixinLootTable.java) | Dual-layer defense: (1) strips disabled & `lootOnlyDisabledItems` entries from `LootTable` JSONs at registry load time, and (2) filters `LootTable#getRandomItemsRaw` at runtime so programmatic/Lootr/Fishing/Archeology rolls never emit a disabled item. |
| **8** | **Block Mob Drops, Block Drops & Villager Trades** | [`MixinEntityDrops.java`](LadsSMP/src/main/java/com/thelads/smp/mixin/MixinEntityDrops.java), [`MixinBlockDrops.java`](LadsSMP/src/main/java/com/thelads/smp/mixin/MixinBlockDrops.java), [`MixinMerchantOffer.java`](LadsSMP/src/main/java/com/thelads/smp/mixin/MixinMerchantOffer.java) | Cancels `Entity#spawnAtLocation`, `Block#popResource`, and marks any `MerchantOffer` selling or buying a disabled item as out-of-stock (`setToOutOfStock()`) while returning `ItemStack.EMPTY` from `assemble()`. |
| **9** | **Block Crafting, Smelting & Modded Recipes** | [`MixinRegistryLoadTask.java`](LadsSMP/src/main/java/com/thelads/smp/mixin/MixinRegistryLoadTask.java), [`MixinRecipeMap.java`](LadsSMP/src/main/java/com/thelads/smp/mixin/MixinRecipeMap.java), [`MixinCraftingMenu.java`](LadsSMP/src/main/java/com/thelads/smp/mixin/MixinCraftingMenu.java) | Triple-layer defense: (1) neutralizes disabled recipes during JSON loading, (2) strips recipes whose ID or output matches `disabledItems`/`recipeOnlyDisabledItems`/`disabledRecipeIds` inside `RecipeMap.create()`, and (3) clears `ResultContainer` in `CraftingMenu#slotChangedCraftingGrid`. |
| **10** | **Future-Proof Configuration & Live Reload** | [`DisabledContentConfig.java`](LadsSMP/src/main/java/com/thelads/smp/disabler/DisabledContentConfig.java) + [`LadsCommands.java`](LadsSMP/src/main/java/com/thelads/smp/command/LadsCommands.java) | Supports any future mod item via exact ID, wildcard glob (`namespace:*`, `*:*_pattern`), or `#tag`, and reloads live via `/ladssmp reload` or `/ladssmp disable <id> [reason]`. |

---

## 3. Feature Migration Table (22 Consolidated Mods)

| # | Original Mod (`modid`) | Original Jar | License | Clean-Room Implementation in `Lads SMP` | Config Key (`config/ladssmp/config.json`) | Validation Status |
| :---: | :--- | :--- | :---: | :--- | :--- | :---: |
| 1 | Always Eat (`always_eat`) | `always_eat-3.1.0.jar` | MIT | `MixinFoodProperties#ladssmp$canAlwaysEat` allows eating food at full hunger except chorus fruit/ominous bottles | `features.alwaysEat` | Verified |
| 2 | No Animal Tempt Delay (`noanimaltemptdelay`) | `noanimaltemptdelay-26.3.0-1.3.jar` | Collective | `MixinTemptGoal#ladssmp$removeTemptCooldown` zeroes `calmDown` timer when stopping `TemptGoal` | `features.noAnimalTemptDelay` | Verified |
| 3 | Better Spawner Control (`betterspawnercontrol`) | `betterspawnercontrol-26.3.0-4.9.jar` | Collective | `MixinBaseSpawner` + `ConsolidatedFeatures#isSpawnerDisabledByLights` disables spawner when >= 5 faces have torches/lanterns | `features.spawnerControl5Torch` | Verified |
| 4 | Crying Portals (`cryingportals`) | `cryingportals-26.3.0-3.0.jar` | Collective | `MixinPortalShape` + `data/minecraft/tags/block/nether_portal_frame.json` allows `minecraft:crying_obsidian` in portal frames | `features.cryingObsidianPortals` | Verified |
| 5 | Despawning Eggs Hatch (`despawningeggshatch`) | `despawningeggshatch-26.3.0-4.6.jar` | Collective | `MixinItemEntity` + `ConsolidatedFeatures#tryHatchDespawningEgg` hatches despawning `minecraft:egg` items (higher chance on Hay Block, capped by nearby chickens) | `features.despawningEggsHatch` | Verified |
| 6 | Double Doors (`doubledoors`) | `doubledoors-26.3.0-7.3.jar` | Collective | `MixinDoorBlock` + `MixinFenceGateBlock` + `ConsolidatedFeatures` synchronously toggles paired wooden doors and adjacent fence gates | `features.doubleDoors` | Verified |
| 7 | Death Backup (`deathbackup`) | `deathbackup-26.3.0-3.7.jar` | Collective | `DeathBackupManager` records player inventory snapshots on death to `config/ladssmp/death_backups/<uuid>.json`; `/ladssmp deathbackup list|restore` | `features.deathBackup` | Verified |
| 8 | Villager Names (`villagernames`) | `villagernames-26.3.0-8.7.jar` | Collective | `ConsolidatedFeatures#assignVillagerNameIfNeeded` assigns deterministic curated names on `ServerEntityEvents.ENTITY_LOAD` | `features.villagerNames` | Verified |
| 9 | Friendly Fire (`friendlyfire`) | `FriendlyFire-fabric-MC26.3-26.3.0.1.jar` | LGPL-2.1 | `MixinLivingEntity` + `ConsolidatedFeatures#shouldPreventFriendlyFire` blocks accidental direct hits on own/allied tamed pets unless sneaking | `features.friendlyFireProtection` | Verified |
| 10 | AttributeFix (`attributefix`) | `AttributeFix-fabric-MC26.3-26.3.0.1.jar` | LGPL-2.1 | `MixinRangedAttribute` + `ConsolidatedFeatures#uncapVanillaAttributes` raises `MAX_HEALTH`, `ARMOR`, `ARMOR_TOUGHNESS`, and `ATTACK_DAMAGE` caps to `1,000,000` | `features.attributeFixUncap` | Verified |
| 11 | Flat Bedrock (`flatbedrock`) | `flatbedrock-fabric-26.3.0.1.jar` | MIT | Built-in `data/minecraft/worldgen/material_rule/bedrock_floor.json` and `bedrock_roof.json` overrides | Built-in datapack | Verified |
| 12 | Skeleton AI Fix (`skeletonaifix`) | `skeletonaifix-v26.3.0-mc26.3.x+fabric.jar` | MPL-2.0 | `MixinRangedBowAttackGoal` prevents skeletons from strafing when outside vertical range (`|dy| > 4`) | `features.skeletonAiFix` | Verified |
| 13 | Golem Spawn Fix (`golem_spawn_fix`) | `golem_spawn_fix-fabric-26.3-1.0.2.jar` | AFL-3.0 | `MixinCarvedPumpkinBlock` + `ConsolidatedFeatures#isPermittedGolemCornerBlock` allows Iron Golems to form even when grass/flowers/snow/carpets sit under their arms | `features.golemSpawnFix` | Verified |
| 14 | Name Tag Tweaks (`nametagtweaks`) | `nametagtweaks-26.3.0-4.2.jar` | ARR | `NameTagTweaksManager` + `MixinLivingEntity#dropAllDeathLoot` + shaped recipe `data/ladssmp/recipe/name_tag.json` | `features.nameTagTweaks` | Verified |
| 15 | Naturally Charged Creepers (`naturallychargedcreepers`) | `naturallychargedcreepers-26.3.0-3.7.jar` | ARR | `MixinCreeper#finalizeSpawn` naturally spawns charged creepers with 0.1 (10%) default chance | `features.naturallyChargedCreepers` | Verified |
| 16 | No Feather Trample (`nofeathertrample`) | `nofeathertrample-26.3.0-1.4.jar` | ARR | `MixinFarmlandBlock#turnToBaseBlock` prevents trampling when wearing Feather Falling boots or with Slow Falling | `features.noFeatherTrample` | Verified |
| 17 | Enchanting Commands (`enchantingcommands`) | `enchantingcommands-26.3.0-3.8.jar` | ARR | `EnchantingCommandsManager` implements `/ec list` and `/ec enchant <enchantment> <level (0-255)>` | `features.enchantingCommands` | Verified |
| 18 | Stonecutter Damage (`mr_stonecutter_damage`) | `stonecutter-damage-mcmeta-95-107_26.1-26.2.jar` | MIT | `MixinStonecutterBlock#stepOn` damages entities standing on stonecutters with custom damage type and blacklist tag | `features.stonecutterDamage` | Verified |
| 19 | I'm Fast (`imfast`) | `imfast-FABRIC-26.3-1.0.6.jar` | MIT | `MixinVelocityNoSpeedLimit` relaxes the 'moved too quickly' thresholds (100.0f, 300.0f, vehicle 100.0d) 4x; 'moved wrongly' (0.0625) stays vanilla | `features.noSpeedLimit` (default off) | Verified |
| 20 | Login Protection (`logprot`) | `logprot-fabric-26.2-3.5.jar` | ARR | `LoginProtectionManager` + `MixinLoginProtection` + `MixinServerGamePacketListenerImpl` protects players for 50s on login/respawn/dimension travel until move/act | `features.loginProtection` | Verified |
| 21 | Default Server Properties (`dsp`) | `default-server-properties-fabric-26.3.0.1.jar` | MPL-2.0 | `MixinDedicatedServerProperties#fromFile` seeds default server.properties on fresh dedicated server deployments | `features.defaultServerProperties` | Verified |

---

## 4. `/ladssmp` Admin Command Reference

All commands require operator permission level `2` (`Commands.LEVEL_GAMEMASTERS`):
- `/ladssmp status` — Displays active disabler rule counts, `LadsDataUpgrader` statistics (`UpgradedTables`, `UpgradedConditions`, `UpgradedRecipes`, `FilteredLootEntries`, `FilteredRecipes`), and feature status.
- `/ladssmp reload` — Hot-reloads both `config/ladssmp/config.json` and `config/ladssmp/disabled_items.json` and immediately sweeps all online players.
- `/ladssmp disable <itemId> [reason]` — Adds `<itemId>` (or glob pattern) to `disabled_items.json`, saves to disk, and immediately sweeps all online players.
- `/ladssmp enable <itemId>` — Removes `<itemId>` from `disabled_items.json` and saves to disk.
- `/ladssmp list` — Lists all currently disabled item IDs/globs, recipe-only bans, and loot-only bans with their reasons.
- `/ladssmp deathbackup list <player>` — Lists recorded death inventory snapshots for `<player>`.
- `/ladssmp deathbackup restore <player> [indexFromLatest]` — Restores a player's death backup snapshot directly into their inventory.
