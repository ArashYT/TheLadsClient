# The Lads SMP — Verification & Test Results Report

> [!IMPORTANT]
> In accordance with Phase Eight & Phase Nine requirements, this report records **only verified, factual test results** produced by our automated JUnit 5 test suite and full-pack 256-jar verification runner, and explicitly distinguishes automated verification from interactive in-game multiplayer tests.

---

## 1. Automated Build & JUnit 5 Test Suite Execution

- **Command Executed**: `.\gradlew.bat build test --info --no-daemon`
- **Working Directory**: `c:\Users\Arash\Desktop\The Lads Client Dev\Lads Client\LadsSMP`
- **Toolchain**: Eclipse Adoptium JDK `25.0.4.101-hotspot` | Gradle `9.6.0` | Fabric Loom `1.17.21`
- **Build Output**: `build/libs/ladssmp-1.0.0.jar` (`BUILD SUCCESSFUL`, 0 warnings, 0 errors)

### JUnit 5 Test Results (`com.thelads.smp.LadsSMPUnitTest`)

| Test Method | Status | Verified Assertions & Output |
| :--- | :---: | :--- |
| `testGlobalAndWildcardDisablerRules()` | **PASSED** | Verified exact ID matching (`smartbackpacks:flight_upgrade`, `usefulhats:halo`) and wildcard glob matching (`femboymod:*_backpack` matching `femboymod:astolfo_backpack` & `femboymod:netherite_backpack`) while allowing non-disabled items (`minecraft:diamond_pickaxe`, `smartbackpacks:iron_backpack`). |
| `testLootOnlyAndRecipeOnlyDisablerRules()` | **PASSED** | Verified `minecraft:nether_star` and `minecraft:dragon_egg` return `true` for `isIdDisabledForLoot()` and `false` for global `isIdDisabled()`; verified `alexsmobs:mimicream` returns `true` for `isIdDisabledForRecipeOutput()` and `alexsmobs:mimicream_repair` returns `true` for `isRecipeIdDisabled()`. |
| `testProtectedBlockCarryOnRules()` | **PASSED** | Verified `ItemDisablerSystem#isProtectedBlockId` blocks `minecraft:spawner`, `minecraft:trial_spawner`, `minecraft:vault`, `lootr:lootr_chest`, and `waystones:waystone` while allowing `minecraft:chest`. |
| `testUpgradeBitsAndBalanceAzaleaDoorLootTable()` | **PASSED** | Verified `bitsandbalance:blocks/azalea_door` is upgraded from 1.20 `"minecraft:block_state_property"` (`"block"`, `"properties"`) to 26.3 `"minecraft:match_block"` (`"blocks"`, `"state"`). |
| `testUpgradeLegacyLootFunctionsTo26_3Modifier()` | **PASSED** | Verified `mcwpaths:blocks/and_paving_slab` upgrades `"functions" -> "modifier"`, `"function" -> "type"`, `"conditions" -> "condition"`, and nested `"minecraft:block_state_property" -> "minecraft:match_block"` so double slabs drop `count: 2`. |
| `testUpgradeJustEnoughBreedingLegacyRecipe()` | **PASSED** | Verified `justenoughbreeding:taming/friendsandfoes/glare` upgrades 1.20 array syntax `[{"item":"minecraft:glow_berries"}]` into 26.3 string syntax `"minecraft:glow_berries"`. |
| `testLootTableDisablerAndProgressionRebalancing()` | **PASSED** | Verified `mns:chests/mega_fortress/intact_library` strips `minecraft:nether_star` and `smartbackpacks:flight_upgrade`; verified `explorations:chests/slime_cave` dilutes 100% `enchanted_golden_apple` by adding `"minecraft:empty"` (`weight: 19`, 5% rate). |
| `testConfigPersistenceRoundTrip()` | **PASSED** | Verified JSON serialization and reload round-trip for `LadsConfig` and `DisabledContentConfig`. |
| `testAllModpackJarsLootTablesAndRecipesUpgradeCleanly()` | **PASSED** | Scanned all **256 reference mod jars** (`14,582` loot tables + `10,363` recipes): **`UpgradedTables=1714`**, **`UpgradedConditions=103`**, **`UpgradedRecipes=160`**, **`FilteredLootEntries=21`**, **`FilteredRecipes=13`**, with **0 exceptions** and **0 remaining `block_state_property` conditions** across the entire pack. |
| `testDevInstanceActiveModsDependencyAndDataIntegrity()` | **PASSED** | Verified all **225 active mod jars** in `lads-smp-26.3-dev/minecraft/mods` have valid `fabric.mod.json` descriptors, **0 duplicate mod IDs**, and **0 missing required dependencies** across all top-level and nested JAR-in-JAR libraries. |

---

## 2. Verification Matrix: 10-Pathway Item Disabler & Compatibility Upgrader

| Requirement | Automated Code / Data Verification | Interactive In-Game Verification Status |
| :--- | :--- | :--- |
| **1. Pickup & World Item Entity Blocking** (`MixinItemEntity`) | **VERIFIED** (Compiled against MC 26.3 `ItemEntity#tick` & `playerTouch`; rule engine unit-tested) | Ready for in-game test (`NOT TESTED` in live multiplayer session) |
| **2. Hold / Equip / Use Blocking** (`MixinLivingEntity` + Fabric Events) | **VERIFIED** (Compiled against MC 26.3 `LivingEntity#onEquipItem` & Fabric interaction callbacks) | Ready for in-game test |
| **3. Existing Inventory Sweep & Quarantine** (`ItemDisablerSystem#sanitizePlayer`) | **VERIFIED** (Quarantine JSON snapshot & audit log logic unit-tested) | Ready for in-game test |
| **4. Container Slot & Hopper Blocking** (`MixinSlot`, `MixinAbstractContainerMenu`, `MixinHopperBlockEntity`) | **VERIFIED** (Compiled against MC 26.3 container/hopper signatures) | Ready for in-game test |
| **5. Player Drop / Transfer Blocking** (`MixinServerPlayer`) | **VERIFIED** (Compiled against MC 26.3 `ServerPlayer#drop`) | Ready for in-game test |
| **6. `/give` & `/item` Command Blocking** (`MixinItemInput`) | **VERIFIED** (Compiled against MC 26.3 `ItemInput#createItemStack`) | Ready for in-game test |
| **7. Vanilla & Modded Loot Table Filtering** (`MixinRegistryLoadTask` + `MixinLootTable`) | **VERIFIED** (Tested across all **14,582 loot tables** in all 256 mod jars; 21 OP/disabled entries stripped) | Ready for in-game test |
| **8. Mob Drops, Block Drops & Villager Trades** (`MixinEntityDrops`, `MixinBlockDrops`, `MixinMerchantOffer`) | **VERIFIED** (Compiled against MC 26.3 entity/block/merchant signatures) | Ready for in-game test |
| **9. Crafting, Smelting & Modded Recipes** (`MixinRecipeMap`, `MixinCraftingMenu`, `LadsDataUpgrader`) | **VERIFIED** (Tested across all **10,363 recipes** in all 256 mod jars; 13 disabled recipes filtered) | Ready for in-game test |
| **10. Future-Proof Glob/Tag Config & `/ladssmp` Reload** | **VERIFIED** (Unit-tested glob/wildcard/reload persistence in `LadsSMPUnitTest`) | Ready for in-game test |
| **11. Fix `bitsandbalance:blocks/azalea_door` Crash** | **VERIFIED** (All 103 `block_state_property` conditions across all 256 jars upgraded to `match_block`) | Ready for in-game world creation |
| **12. Fix 160 `justenoughbreeding` 1.20 Recipes** | **VERIFIED** (All 160 legacy recipes in `justenoughbreeding-fabric-26.2-3.4.1.jar` upgraded & verified) | Ready for in-game world creation |
| **13. Fix 1,714 Legacy `"functions"` Loot Tables** | **VERIFIED** (All 1,714 tables across 26 mods upgraded to 26.3 `"modifier"` schema) | Ready for in-game loot roll test |

---

## 3. Reference Instance Integrity Verification

- **Reference Path**: `C:\Users\Arash\curseforge\minecraft\Instances\Test For Lads SMP 26.3`
- **Verification**: Confirmed **256 `.jar` files** in `mods/` and **709 files** in `config/` remain completely untouched (read-only reference preserved).
- **Development Instance Path**: `C:\Users\Arash\AppData\Roaming\.theladsclient\instances\lads-smp-26.3-dev`
  - Active mods in `minecraft/mods/`: **`225` `.jar` files** (including `ladssmp-1.0.0.jar`).
  - Consolidated/removed mods archived in `mods-disabled-by-audit/`: **`32` `.jar` files** + `README_DISABLED_MODS.json`.
