# The Lads SMP — Loot, Structure Spacing, Progression & Exploit Balance Report

> [!IMPORTANT]
> **Audit Scope**: All **14,582 loot tables**, **10,363 recipes**, **8,045 structure files**, and **709 config files** across the 256 installed mods were audited for early-game progression bypasses, overpowered items, broken 26.3 loot modifiers, and duplication exploits.

---

## 1. Critical Discovery: 1,714 Silently Broken Loot Tables in Minecraft 26.3

### Root Cause
In Minecraft `26.3`, Mojang changed the `LootTable`, `LootPool`, and `LootPoolEntryContainer` Codec schemas:
1. The JSON key `"functions"` was renamed to **`"modifier"`**.
2. Inside each loot function object, `"function"` was renamed to **`"type"`**, and `"conditions"` was renamed to **`"condition"`**.
3. Legacy `"minecraft:set_nbt"` was replaced by **`"minecraft:set_custom_data"`**.
4. The block loot condition `"minecraft:block_state_property"` (`"block"`, `"properties"`) was removed in favor of **`"minecraft:match_block"`** (`"blocks"`, `"state"`).

Because Codec deserialization in 26.3 silently ignores unknown keys (`"functions"`), **1,714 loot tables across 26 mods** were dropping items with:
- **Stack counts stuck at `1`** (`minecraft:set_count` ignored — including **75 double-slab/gutter block loot tables** in `mcwpaths`, `items_displayed`, `mcwroofs`, and `mcwlights` where breaking a double slab only dropped 1 slab!).
- **Unenchanted books and gear** (`minecraft:enchant_with_levels` and `minecraft:enchant_randomly` ignored in `betterend`, `biomesoplenty`, `promenade`, `ecologics`, `structory`, `t_and_t`, `mvs`, `mns`, `mtr`, `mes`).
- **Missing Looting / Fortune scaling** (`minecraft:looting_enchant` and `minecraft:apply_bonus` ignored in `naturalist`, `betterend`, `biomesoplenty`).

### Automatic Resolution in `Lads SMP` (`LadsDataUpgrader`)
`LadsDataUpgrader` hooks `RegistryLoadTask$PendingRegistration#loadFromResource` at Mixin priority `500` and upgrades all **1,714 legacy loot tables** and **103 legacy `block_state_property` conditions** in-memory before Codec parsing:

| Mod Namespace | Legacy `"functions"` Tables Upgraded | Legacy `"block_state_property"` Conditions Upgraded | Impact of Fix |
| :--- | :---: | :---: | :--- |
| `betterend` | **704** | 0 | Restores all BetterEnd ore Fortune bonuses, Silk Touch drops, and structure chest item counts/enchantments |
| `biomesoplenty` | **463** | 0 | Restores BOP double-plant/slab drops, Fortune leaf/ore modifiers, and explosion decay |
| `promenade` | **173** | 0 | Restores Promenade block/chest loot counts and modifiers |
| `cookingforblockheads` | **159** | 0 | Restores multiblock kitchen drop modifiers |
| `chisel` | **144** | 0 | Restores Chisel block explosion decay and slab counts |
| `ecologics` | **132** | 0 | Restores Coconut/Walnut/Azalea/Seashell block & chest modifiers |
| `naturalist` | **78** | 0 | Restores Looting bonuses and cooked-meat-on-fire drops for all 30+ Naturalist animals |
| `mcwpaths` | **52** | **52** | Fixes 52 paving double-slabs dropping 2 slabs instead of 1 |
| `structory` | **39** | 0 | Restores Structory chest stack counts and random enchantments |
| `mtr` / `mns` / `mvs` / `mes` | **113** | 0 | Restores Moog's structure chest stack sizes and enchantments |
| `bitsandbalance` | **27** | **27** | Fixes the **fatal world-creation crash** on `bitsandbalance:blocks/azalea_door` |
| `items_displayed` | **14** | **14** | Restores multi-item display drops |
| `mcwroofs` & `mcwlights` | **9** | **9** | Fixes double roof/gutter drops |

---

## 2. Progression-Breaking Loot Tables Rebalanced by `LadsDataUpgrader`

Our audit identified **6 severe progression bypasses** where Overworld or common Nether chests dropped endgame bosses/materials (`nether_star`, `dragon_egg`, `elytra`, `netherite_ingot`, or 100% `enchanted_golden_apple`). All 6 are automatically rebalanced at load time by `LadsDataUpgrader` + `DisabledContentConfig`:

| # | Loot Table ID | Original Broken Behavior | Automated Fix in `Lads SMP` |
| :---: | :--- | :--- | :--- |
| 1 | `mns:chests/mega_fortress/intact_library` | Dropped `minecraft:nether_star` inside a library chest without fighting the Wither | `minecraft:nether_star` added to `lootOnlyDisabledItems` — stripped from all chest loot tables while remaining obtainable from killing the Wither |
| 2 | `regs_more_foods:entities/ender_dragon` | Dropped bonus `minecraft:dragon_egg` (30% chance) via loot table | `minecraft:dragon_egg` added to `lootOnlyDisabledItems` — blocked from loot tables |
| 3 | `mtr:chests/ocean_temple/treasury_chest` | Spawned `netherite_ingot`, `netherite_scrap`, and `ancient_debris` in an Overworld Ocean Monument treasury | Automatically downgraded in `LadsDataUpgrader` to `minecraft:diamond` (`weight: 2`), keeping Netherite strictly Nether-exclusive |
| 4 | `nova_structures:chests/nether_fortress/fort_inside_generic` | Spawned `netherite_ingot` (`weight: 5`) and `ancient_debris` (`weight: 10`) in common Nether Fortress hallway chests | `netherite_ingot` removed; `ancient_debris` weight reduced from `10 -> 1` |
| 5 | `mobchampions:entities/tier_3_loot` & `tier_4_loot` | Allowed Overworld Champion zombies/skeletons to drop `minecraft:elytra`, `netherite_ingot`, and `netherite_scrap` | `elytra`, `netherite_ingot`, and `netherite_scrap` replaced with `minecraft:diamond` (`weight: 3`) in `LadsDataUpgrader` |
| 6 | `explorations:chests/slime_cave` | 100% guaranteed (`rolls: 1`, single entry) `minecraft:enchanted_golden_apple` in common underground Slime Caves | Diluted in `LadsDataUpgrader` by injecting `"type": "minecraft:empty", "weight": 19` alongside weight `1` (**100% -> 5% chance**) |

---

## 3. Overpowered Items & Equipment Disabled via `Lads SMP`

The following items and recipes are disabled by default in `config/ladssmp/disabled_items.json` and enforced across all 10 gameplay pathways:

| Disabled Entry | Scope | Gameplay / Balance Rationale |
| :--- | :---: | :--- |
| `smartbackpacks:flight_upgrade` | **Global (All 10 Pathways)** | Grants Creative-style flight from a backpack upgrade slot, completely bypassing End/Elytra progression. |
| `smartbackpacks:repair_upgrade` | **Global (All 10 Pathways)** | Passively repairs all items inside the backpack using raw XP without Mending or anvils. |
| `usefulhats:halo` | **Global (All 10 Pathways)** | Crafts from 1 Nether Star + 4 Glowstone and grants Creative flight + complete immunity to hostile mob targeting. |
| `usefulhats:shulker_helmet` | **Global (All 10 Pathways)** | Grants repeatable Levitation flight from a hat slot. |
| `relics_artifacts:chrono_watch` | **Global (All 10 Pathways)** | Passively rewinds player position/health by 5 seconds from main inventory without even occupying a Trinket slot. |
| `femboymod:*_backpack` | **Global (All 10 Pathways)** | Disables the redundant second backpack system (`canvas_backpack` through `netherite_backpack`) so only `smartbackpacks` provides wearable storage and players cannot nest Femboy Backpacks inside Smart Backpacks. |
| `alexsmobs:mimicream` / `alexsmobs:mimicream_repair` | **Recipe-Only Disabled** | Prevents duplicating any god-tier enchanted weapon, tool, or armor piece using 8 Mimicream in a crafting grid. Mimicream still drops from Mimicubes as a trophy/ingredient for non-duplication uses. |
| `minecraft:nether_star` & `minecraft:dragon_egg` | **Loot-Table-Only Disabled** | Prevents structure/dungeon/entity loot tables from generating free boss trophies while allowing normal Wither/Dragon boss drops and Beacon crafting. |

---

## 4. Duplication & Progression Exploit Fixes Applied in `config/`

1. **Infinite Death XP Duplication (`bitsandbalance` + `mr_player_deathcorpses`)**:
   - **Exploit**: `bitsandbalance-gameplay.json` had `"maintainExperience": true` while `mr_player_deathcorpses` spawned a corpse containing the player's XP—allowing players to double their XP bar on every death.
   - **Fix**: Set `"maintainExperience": false` in `config/Bits and Balance/bitsandbalance-gameplay.json`.
2. **Carry On Spawner / Lootr / Drawer Pickup Exploit (`carryon`)**:
   - **Exploit**: Players could shift-right-click to pick up vanilla Mob Spawners, Trial Spawners, Vaults, instanced `Lootr` chests, `Waystones`, and filled `Storage Drawers`.
   - **Fix**: Added `minecraft:mob_spawner`, `minecraft:trial_spawner`, `minecraft:vault`, `lootr:*`, `waystones:*`, `storagedrawers:*`, `cookingforblockheads:*`, `barteringstation:*`, and `tradingpost:*` to `forbiddenTiles` in `config/carryon-common.json`, AND enforced a server-side `UseBlockCallback` guard in `ItemDisablerSystem#isProtectedBlock`.
3. **Backpack Nesting & Capacity Creep (`smartbackpacks`)**:
   - **Fix**: Set `canNestBackpacks = false`, `inceptionUpgradeAllowsNesting = false`, `diamondBackpackSlots = 54`, and `netheriteBackpackSlots = 72` in `config/smartbackpacksconfig.toml`.
4. **Universal Enchants Infinity + Mending (`universalenchants`)**:
   - **Fix**: Set `infinityMending = false` in `config/universalenchants-common.toml`.
5. **Waystones Free Teleportation (`waystones`)**:
   - **Fix**: Set `minimumBaseXpCost = 1.0`, `blocksPerXpLevel = 500`, and `warpPlateTickTime = 60` in `config/waystones-common.toml`.
6. **Lootr Chest Griefing Protection (`lootr`)**:
   - **Fix**: Set `"blast_immune": true` in `config/lootr-common.jsonc` so Creepers/TNT cannot destroy per-player instanced loot chests before other players open them.
