# The Lads SMP — Minecraft 26.3 Compatibility, Mixin & Log Error Report

> [!IMPORTANT]
> Every error, exception, and warning recorded in `C:\Users\Arash\curseforge\minecraft\Instances\Test For Lads SMP 26.3\logs\latest.log` and discovered during bytecode/data-pack inspection of the 256 mods is analyzed below with its exact root cause and verified fix.

---

## 1. Fatal World-Creation & Server-Startup Data Errors (Fixed by `LadsDataUpgrader`)

### Issue 1A: `bitsandbalance:blocks/azalea_door` Unknown Loot Condition Crash
- **Symptom in `latest.log`**:
  ```text
  Failed to parse bitsandbalance:loot_table/blocks/azalea_door.json from pack file/bitsandbalance-fabric-26.3-2.4.1.jar
  com.google.gson.JsonParseException: Unknown registry key in ResourceKey[minecraft:root / minecraft:loot_condition_type]: minecraft:block_state_property
  ```
- **Root Cause**: Minecraft `26.3` removed `minecraft:block_state_property` (`"block"`, `"properties"`) and replaced it with `minecraft:match_block` (`"blocks"`, `"state"`). In `26.3`, any unparseable `loot_table` in `RegistryDataLoader` aborts world creation (`Failed to load registries due to above errors`) and kicks the player back to the title screen.
- **Scope Across All 256 Mods**: A full-pack scan found **103 occurrences** of `"minecraft:block_state_property"` across `bitsandbalance` (27), `mcwpaths` (52), `items_displayed` (14), `mcwroofs` (5), `mcwlights` (4), and `citadel` (1).
- **Verified Fix**: `MixinRegistryLoadTask` + `LadsDataUpgrader#upgradeConditionObject` intercepts `RegistryLoadTask$PendingRegistration#loadFromResource` at priority `500` and rewrites every `"minecraft:block_state_property"` condition into valid 26.3 `"minecraft:match_block"` JSON before Codec validation.

### Issue 1B: 160 `justenoughbreeding` Legacy 1.20 Recipe Parse Errors
- **Symptom in `latest.log`**:
  ```text
  Couldn't parse data file 'justenoughbreeding:taming/friendsandfoes/glare' from 'justenoughbreeding:recipe/taming/friendsandfoes/glare.json'
  Not a string: [{"item":"minecraft:glow_berries"}]
  ```
- **Root Cause**: `justenoughbreeding-fabric-26.2-3.4.1.jar` contains 346 recipe files under `data/justenoughbreeding/recipe/`, of which **160 files** (for `alexscaves`, `alexsmobs`, `biomesoplenty`, `ecologics`, `friendsandfoes`, `naturalist`, `quark`, `snowpig`, `snuffles`, `toms_mobs`, `wan_ancient_beasts`, etc.) still use 1.20 JSON array syntax (`[{"item": "..."}]` or `[{"tag": "..."}]`) instead of 26.3 ingredient string syntax (`"namespace:id"` or `"#namespace:tag"`), or reference entities/items from uninstalled mods.
- **Verified Fix**: `LadsDataUpgrader#processJustEnoughBreedingRecipe` flattens all legacy ingredient arrays into 26.3 comma-separated ingredient strings, strips uninstalled item/entity references, and replaces recipes for uninstalled mods with an empty no-op `minecraft:crafting_special_bookcloning` entry so `RegistryDataLoader` never fails.

### Issue 1C: 1,714 Silently Ignored Pre-26.3 Loot Table `"functions"` Arrays
- **Symptom**: Modded blocks (such as `mcwpaths` double slabs, `betterend` ores, `biomesoplenty` plants) and modded structure chests (`structory`, `mvs`, `mns`, `mtr`, `mes`, `promenade`, `ecologics`) dropped unenchanted items or single stacks (`count = 1`) instead of their configured stack sizes and enchantments.
- **Root Cause**: Minecraft `26.3` renamed `"functions"` to `"modifier"`, `"function"` to `"type"`, and `"conditions"` to `"condition"` in `LootTable`, `LootPool`, and `LootPoolEntryContainer`. Note that `moogs_structures` (`LegacyDataUpgrader`) attempted to fix this at Mixin priority `1000`, **but** its `upgradeFunction()` helper forgot to recurse into nested `"conditions"` arrays inside each modifier!
- **Verified Fix**: `LadsDataUpgrader` runs at Mixin priority `500` (before `moogs_structures`) and recursively upgrades `"functions" -> "modifier"`, `"function" -> "type"`, `"conditions" -> "condition"`, `"minecraft:set_nbt" -> "minecraft:set_custom_data"`, and all nested conditions across all **1,714 affected loot tables**.

---

## 2. Mod Loader, Rendering & Mixin Conflicts Resolved

### Issue 2A: `citadel:effect_item`, `fancy_item`, `icon_item` Missing Item Model Errors
- **Symptom in `latest.log`**:
  ```text
  [Render thread/ERROR]: No item model definitions for: [citadel:effect_item, citadel:fancy_item, citadel:icon_item]
  ```
- **Root Cause**: Having both `alexsmobsfabric-26.3-1.0.0.jar` (SemVer `26.3.0`) and `alexsmobs-2.2.2-fabric+26.3.jar` (SemVer `2.2.2`) in `mods/` caused Fabric Loader to pick `alexsmobsfabric-26.3-1.0.0.jar` and standalone `citadelfabric-26.3-1.0.0.jar`, whose `ClientEvents#registerItemModels` is a compiled no-op stub (`return;`).
- **Verified Fix**: Archived `alexsmobsfabric-26.3-1.0.0.jar` and `citadelfabric-26.3-1.0.0.jar` into `mods-disabled-by-audit/`, activating CodxIO's `alexsmobs-2.2.2-fabric+26.3.jar` and its embedded `citadel-2.7.2-fabric+26.3.jar`.

### Issue 2B: `EuphoriaPatcher` Missing Shader Error & Clipboard Hijack
- **Symptom in `latest.log`**:
  ```text
  [EuphoriaPatcher] ERROR: No Complementary file found in shaderpacks
  ```
- **Root Cause**: `EuphoriaPatcher-1.10.5-r5.9.3-fabric.jar` requires a base Complementary Shaders zip in `shaderpacks/` to patch at runtime, and hijacks the user's system clipboard on world join when misconfigured.
- **Verified Fix**: Archived `EuphoriaPatcher-1.10.5-r5.9.3-fabric.jar` into `mods-disabled-by-audit/`.

### Issue 2C: `multi-key-bindings` Hardcoded Left-Alt (`342`) Trigger Bug
- **Root Cause**: Decompilation of `KeyMappingMixin` inside `multi-key-bindings-1.3.5+26.3.jar` revealed a leftover debug check (`if (keyCode == 342)`) that fires multi-key bindings whenever Left Alt is pressed.
- **Verified Fix**: Archived `multi-key-bindings-1.3.5+26.3.jar` into `mods-disabled-by-audit/`.

### Issue 2D: Benign Startup Warnings Verified Safe
1. **`kuma_api` vs `controlling` Mixin `@Accessor` Warning**:
   - `Method overwrite conflict for getSelectedKey in kuma_api.mixins.json:KeyBindsScreenAccessor from mod kuma_api, previously written by com.blamejared.controlling.mixin.AccessKeyBindsScreen`.
   - **Verification**: Both interfaces define an identical `@Accessor("selectedKey") KeyMapping getSelectedKey()` getter on `KeyBindsScreen`. Identical `@Accessor` getters are 100% binary compatible.
2. **`biolith` Optional `TerraBlender` Check**:
   - `ClassNotFoundException: terrablender.api.SurfaceRuleManager` logged at `INFO`/`WARN` by `biolith` (embedded in `promenade`).
   - **Verification**: `TerraBlender 26.3.0.0.9` relocated `SurfaceRuleManager` to `terrablender.PreInit`; `biolith` catches the `ClassNotFoundException` and falls back to its native `BiolithSurfaceRules` pipeline cleanly.
3. **`diagonalfences` / `diagonalwindows` Missing Property Info on Curved `mcwfences`**:
   - `mcwfences` curved double gates and wired fences use non-standard cardinal properties and are intentionally skipped by `diagonalfences` (`Block is missing required properties, skipping diagonal expansion`). Standard fences/walls connect diagonally as expected.
