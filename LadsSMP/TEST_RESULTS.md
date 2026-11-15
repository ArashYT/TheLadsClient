# The Lads SMP — Verification & Test Results Report

> [!IMPORTANT]
> In accordance with Phase Eight & Phase Nine requirements, this report records **only verified, factual test results** produced by our automated JUnit 5 test suite and full-pack 256-jar verification runner, and explicitly distinguishes automated verification from interactive in-game multiplayer tests.

---

## 1. Automated Build & JUnit 5 Test Suite Execution

- **Command Executed**: `.\gradlew.bat build test --info --no-daemon`
- **Working Directory**: `<repo-root>\LadsSMP`
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

- **Reference Path**: `%USERPROFILE%\curseforge\minecraft\Instances\Test For Lads SMP 26.3`
- **Verification**: Confirmed **256 `.jar` files** in `mods/` and **709 files** in `config/` remain completely untouched (read-only reference preserved).
- **Development Instance Path**: `%APPDATA%\.theladsclient\instances\lads-smp-26.3-dev`
  - Active mods in `minecraft/mods/`: **`225` `.jar` files** (including `ladssmp-1.0.0.jar`).
  - Consolidated/removed mods archived in `mods-disabled-by-audit/`: **`32` `.jar` files** + `README_DISABLED_MODS.json`.

---

## 4. Pack 0.5.0 full-content sweep (2026-11-12, arash-legion)

Pack: Lads SMP Reborn 0.5.0 = 0.4.0 + Fabric Loader 0.20.1, 74 mod updates, 18 new mods (`MOD_SUGGESTIONS.md`), Lads SMP 0.3.1.
Tool: `LadsSMP/qa-smoke` (test-only mod, never shipped): places every block (use, random tick, drops), spawns every entity
(lives, is killed for loot), uses every item (air, on a block, inventory tick), rolls every loot table, generates terrain and
structures in every dimension; the client half draws every item icon and tooltip. A watchdog records the server-thread stack of any
step over 5 s.

| Run | Content | Result |
| :--- | :--- | :--- |
| Dedicated server, 275 server-side mods | 19,765 blocks, 569 entities, 21,137 items | 0 failures |
| Client + integrated server, 353 mods | same, plus item icons/tooltips and in-world rendering | 0 failures, clean exit |
| Dedicated server | every loot table | 0 real failures (test-side param gaps only) |
| Launcher code (`InstallExperienceAsync`, `ApplyPendingPatchesAsync`) | fresh 0.5.0 install; 0.4.0 + update-0.5.0.json | both 353/353 jars, keybinds fixed |

Bugs found and fixed:
- Dedicated server did not boot: `xaeroatlases` needs client-only `modmenu`; `drippyloadingscreen` mixes into client classes (server packs must leave both out).
- Server froze (watchdog crash) when a Smart Backpacks Backpacker Camp generated: its trader built vanilla trades on the worldgen thread (fixed in `MixinSmartBackpacksCampTrader`).
- Dungeons & Taverns chart maps held the server thread up to ~4 min: explorer-map searches now have a 3 s budget, radius cap 16, never run off-thread.
- World join crashed: Fabrishot's key saved as an SDL keycode overran `InputConstants.isKeyDown` (bounds guard); Lads SMP parkour keys were GLFW codes (now Left Alt / G / C).
- `disabled_items.json` / `config.json` failed to save on Windows every start; botany pots logged codec errors on every save.
- Update reverts: More Culling 1.9.2 (needs a Sodium beta), FancyMenu 3.10.0 (startup crash with Radial), Promenade 6.0.2 (Biolith beta breaks TerraBlender).

Known, not fixed: the first Eye of Ender per world takes ~13 s (stronghold rings); D&T Illager Manor / Toxic Lair charts can still take
25–33 s (one far chunk generation); 2 Better End music discs no longer exist after the update.


---

## 5. Pack 0.5.1 (Lads SMP 0.3.2): freezes when creating and joining a world, loading screen (2026-11-15, arash-legion)

Setup: fresh instance assembled from `lads-smp-reborn-26.3-0.5.0.zip` exactly as the launcher unpacks it, the Lads Core 26.3 jar built from the committed code (1.13.19), no old
save: the test mod `LadsSMP/qa-smoke` (`-Dladsqa.world=true`, test only, never shipped) waits for the title screen, opens Create New World, presses Create with the pack's default settings
(world type BetterX), flies the player, and records every presented frame with the render thread's stack (`ladsqa/frames.txt`), plus an external window-message sampler.

Root causes of "the world freezes after the first chunk loads" (stacks in `PERFORMANCE.md` section 6): the client render thread stands still for 20-73 s right when it joins, and 54-88 s when it creates the world,
because Bits and Balance's step recipes (regex per slab per block), Fabric API's creative tab `insertAfter` on a LinkedList (O(n^2), 5,700 variants), JEI starting inside the login packet and
Villager API / Xaero work all run on the render thread; the window shows the first chunk and nothing else and the client handles no packet meanwhile.

| Run | Result |
| :--- | :--- |
| Create new world, 0.5.0 | window frozen 58 s while creating (Bits and Balance), 73 s after joining (creative tabs), click to player in world 179.7 s |
| Create new world, 0.5.1, 2 clean runs | longest frozen gap 2.2 s in the whole load, creating 0.7 s, title to create screen 0.6 s, click to player in world 58-64 s |
| Fresh world 2 (fixed pack: Lads SMP 0.3.2, AntiXray config off), 17 min | joined at +168 s, flew 4 Overworld legs of 998-999 blocks east, west (a 1,996-block pass), south and north at 20 blocks/s: every leg finished (49-99 s), the client kept 386-850 chunks loaded around the player, a chunk was loaded under the player at each leg end, 0 packets held (`antiXrayHeld=0` in every report); End: 4 legs of 331 blocks done; Nether: first leg (498 blocks) done, the other three stop at terrain walls at the test's fixed height y=100 (chunks still loaded: client 52 to 504); mobs (zombie, cow) tick (age 20 to 180 in 8 s) |
| Server thread in that run | longest stall 25.2 s right after the teleport into the not yet generated Nether (Bits and Balance's per-tick ladder check reads the player's block and waits for that chunk; BetterX Nether generation on this CPU) and 4.0 s once during the Overworld flight; no other stall over 3 s |
| Fresh worlds with the final build (AntiXray on or off in its config) | 6 of 6 healthy (4 short worlds with block break/place and mob checks, 2 long flights); before the fix 9 of 19 fresh worlds stopped receiving chunks. Block break (`startDestroyBlock`, client and server air), place (`useItemOn`, client and server cobblestone) and mobs ticking passed in 3 of 3 worlds where the item reached the hand in time (2 runs had the test's `item replace` arrive late: not a game fault) |
| Dedicated server boot (274 server mods, final jar sha1 c63823cb...) | `Done (16.294s)!`, clean `stop`, exit 0 (also 16.860 s on the previous build) |

**The "no more chunks load" freeze itself** (seen in 9 of 19 fresh worlds, with and without the other fixes): the integrated server stops delivering packets to the client after the second chunk batch.
Evidence at the stuck moment (test mod `qa-smoke` reads both ends): the server's `PlayerChunkSender` holds 5/5 unacknowledged batches and 366-470 chunks pending, the client's channel is empty and
connected (`inboundBuf=0`, no open bundle, 0 queued packets, renders at 60 fps and moves locally), and the server's `Connection` has **8,122 packets waiting in AntiXray's `antiXray$isActionReady` / `pendingActions` queue**.
AntiXray 1.4.17 (`me.drex.antixray.common.mixin.ConnectionMixin.flushQueue`) sends a connection's packets strictly in order and only when the head packet reports ready (`Util.isReady`); a
chunk packet that never becomes ready stops the connection for good (no thread busy, no log line). It happens with AntiXray switched off in its config too (stuck run with `enabled = false`: 20,722 packets held),
so the config alone does not help. Connectivity and Packet Fixer remove the keep-alive timeouts, so the client is not even kicked: the player sees the first chunks, can still walk, and nothing else ever
arrives (no chunks, no teleports, no game mode). Fix in Lads SMP 0.3.2: `MixinAntiXrayNeverHolds` makes `Util.isReady` true on a game client (its own server has nothing to hide from its only player; a dedicated
server is untouched), and the pack's `config/antixray.toml` (config patch in `update-0.5.1.json` for installed packs) leaves the obfuscation itself off in the pack's own worlds so no half-processed chunk is ever sent.
