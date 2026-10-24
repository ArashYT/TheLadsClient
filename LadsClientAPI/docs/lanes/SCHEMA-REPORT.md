# Lane SCHEMA Report: Export every Lads module + option for LadsClientAPI server jar

## 1. Overview
- **Branch**: `agy/ladsapi-schema`
- **Base Commit**: `e4a49715` (ladsapi: phase 0 - frozen protocol v1 and client/server interfaces)
- **Worktree**: `C:\Users\Arash\AppData\Local\Temp\wla-schema`
- **Scope**: Implemented `SchemaExport` in `TheLadsCore` common, created unit test `SchemaExportTest`, registered Gradle task `:common:exportLadsApiSchema`, generated `LadsClientAPI/common/src/main/resources/ladsapi-schema.json`, and compared counts against `D1-settings-inventory.csv`.

---

## 2. Implementation Details

### 2.1 Exporter (`com.thelads.core.client.ladsapi.SchemaExport`)
- Located at [`SchemaExport.java`](file:///C:/Users/Arash/AppData/Local/Temp/wla-schema/TheLadsCore/common/src/main/java/com/thelads/core/client/ladsapi/SchemaExport.java).
- Instantiates `new ModuleManager()` to obtain all registered modules.
- Emits JSON in the frozen wire schema:
  - `"schema": 1`
  - `"lads": "<LadsVersion>"` (resolves from `thelads.version` system property or `LadsVersion.VERSION`, yielding `"1.9.12"`).
  - `"modules"`: list of modules with `name`, `category`, `neverForced`, `automation`, and `options`.
- Option types handled:
  - `slider` (`SliderOption`): `name`, `type`, `default`, `min`, `max`, `step`, `control`. Whole numbers are formatted without `.0` decimals.
  - `bool` (`BoolOption` and `PlayerActionOption`): `name`, `type`, `default`, `control`.
  - `dropdown` (`DropdownOption`): `name`, `type`, `default` (choice string from `getValue()`), `choices` (string array), `control`.
  - `color` (`ColorOption`): `name`, `type`, `default` (hex formatted `#AARRGGBB` e.g. `#FFFFFFFF`), `control`.
  - `text` (`TextOption` and `IntTextOption`): `name`, `type`, `control`.
  - `other` (other `Option` subclasses such as `ActionOption`, `DoubleOption`, `SoundOption`, `SoundListOption`, `ParticleReductionOption`): `name`, `type`, `control`.
- Option defaults: calls `option.reset()` on each option before reading defaults.
- Options server control: reads `option.serverControl().name()` (`FULL` for slider/bool/dropdown/color, `NEVER` for text/other).
- Flags: `neverForced` and `automation` evaluated via `LadsApiRules.isNeverForced(module.getName())` and `LadsApiRules.isAutomation(module.getName())`.
- Added pseudo-module `"UseWhileMining"` with category `"MECHANIC"`, `neverForced: true`, `automation: true`, and empty options array (`[]`).

### 2.2 Hidden Modules Analysis & Decision
- Inspected all usages of `ModuleManager.isHiddenModule`:
  - `LadsSettingsScreen.java:1985`: Filters out hidden modules so they do not show in the player's in-game GUI settings menu.
  - `NativeHudEditorProbe.java:79`: Filters out hidden modules from HUD editor element probing.
- Server-side policy resolution:
  - `ServerPolicy.module(name)` and `HypixelSafetyManager.isDisallowed(name)` match modules strictly by registered name, without checking whether the module is marked hidden in the GUI.
  - In `PROTOCOL.md` (and `PLAN-LadsClientAPI.md`), the protocol's canonical example policy explicitly blocks `"XaeroMinimap"`, which is registered via `registerHidden(...)`.
  - Other hidden modules like `Jade` have configurable options, and performance modules like `Lithium` or `EntityCulling` can be targeted by servers.
- **Decision**: Hidden modules **are addressable** by the server jar via name rules and are therefore **retained** in the schema export rather than skipped.

### 2.3 Gradle Task
- Registered `tasks.register('exportLadsApiSchema', JavaExec)` in [`common/build.gradle`](file:///C:/Users/Arash/AppData/Local/Temp/wla-schema/TheLadsCore/common/build.gradle).
- Depends on `compileJava` and `processResources`.
- Generates file at `../LadsClientAPI/common/src/main/resources/ladsapi-schema.json` relative to `TheLadsCore` (resolves to [`ladsapi-schema.json`](file:///C:/Users/Arash/AppData/Local/Temp/wla-schema/LadsClientAPI/common/src/main/resources/ladsapi-schema.json)).

### 2.4 Unit Test (`com.thelads.core.client.ladsapi.SchemaExportTest`)
- Located at [`SchemaExportTest.java`](file:///C:/Users/Arash/AppData/Local/Temp/wla-schema/TheLadsCore/common/src/test/java/com/thelads/core/client/ladsapi/SchemaExportTest.java).
- Verifies:
  - Exporter output parses into valid JSON object with `schema == 1` and non-empty `lads` version.
  - Fullbright module exists, category is `MECHANIC`, `neverForced == false`, `automation == false`.
  - `Fullbright.Brightness` exists as `type == "slider"`, `default == 100`, `min == 0`, `max == 100`, `step == 5`, `control == "FULL"`.
  - Every single option across every module defines a valid `control` (`FULL`, `LIMIT_ONLY`, or `NEVER`).
  - Pseudo-module `UseWhileMining` exists with `neverForced == true`, `automation == true`, and 0 options.

---

## 3. Inventory Comparison: Schema vs `D1-settings-inventory.csv`

### 3.1 Totals
| Metric | Schema (`ladsapi-schema.json`) | D1 Inventory CSV (`versions == 'all'`) | Difference |
|---|---|---|---|
| **Total Modules** | **100** | **77** | +23 |
| **Total Options** | **718** | **437** | +281 |

### 3.2 Module Count Differences (+23 in Schema)
1. **Pseudo-module (+1)**:
   - `UseWhileMining` added per requirement (not a standalone `Module` class in `ModuleManager`, but an automation rule).
2. **Zero-option Modules (+19)**:
   - In `D1-settings-inventory.csv`, each row represents an option (`module,option,type,...`). Modules with zero options had no rows in the CSV.
   - The schema exports all registered modules: `EntityCulling`, `Jasione`, `Async`, `RawInput`, `LegacySwing`, `Clumps`, `AppleSkin`, `IgnorePacketErrors`, `ScalableLux`, `Lithium`, `FerriteCore`, `XaeroWorldmap`, `JEI (Just Enough Items)`, `NotEnoughAnimations`, `BetterStats`, `ModernAdvancements`, `Resourcify`, `DisableNarrator`, `XaeroMinimap`.
3. **Hidden / Complex Modules with Options (+3)**:
   - `TitleScreen` (1 option: `Account Card Scale`)
   - `Exordium` (options in `ExordiumModule`)
   - `TNT Timer` (5 options)
4. **Naming Normalization**:
   - In CSV: `ToggleSprint`, `CustomFov`, `TitleScale`.
   - In `ModuleManager`: `Toggle Sprint & Sneak`, `Custom FOV`, `Title Scale`.

### 3.3 Option Count Differences (+281 in Schema)
The D1 audit was a preliminary inventory that sampled top-level settings. The live `ModuleManager` registers all nested and module-specific options:
- **`BetterF3`**: 56 options in live schema vs 5 in CSV (+51; individual line toggles and section colors).
- **`Crosshair Tweaks`**: 62 options in live schema vs 14 in CSV (+48; style-specific offsets, dots, outlines, and dynamic crosshair parameters).
- **`KillBanner`**: 27 options in live schema vs 7 in CSV (+20; message formats, banner timings, text/color options).
- **`Combo Sounds`**: 19 options in live schema vs 5 in CSV (+14; volume, pitch, streak thresholds, sound choices).
- **`HYPIXEL Mods`**: 24 options in live schema vs 7 in CSV (+17; bedwars, skyblock, and auto-tip toggles).
- **`PingView`**: 13 options in live schema vs 3 in CSV (+10).
- **`ShulkerBoxUtils`**: 12 options in live schema vs 1 in CSV (+11).
- **`SurvivalTweaks`**: 13 options in live schema vs 3 in CSV (+10).
- **`EnhancedToolbars`**: 11 options in live schema vs 1 in CSV (+10).
- **`AppleSkin`**: 10 options in live schema vs 0 in CSV (+10).
- **`SignalLoss`**: 8 options in live schema vs 1 in CSV (+7).
- **`AutoReconnect`**: 8 options in live schema vs 2 in CSV (+6).
- **`Item Physics`**: 8 options in live schema vs 2 in CSV (+6).
- **`DynamicLights`**: 7 options in live schema vs 1 in CSV (+6).
- **`BetterResolution`**: 6 options in live schema vs 1 in CSV (+5).
- **`Fullbright`**: 1 option in live schema vs 2 in CSV (-1; in 1.9.10+, Brightness Multiplier was removed leaving only Brightness).
- **`Performance`**: 2 options in live schema vs 10 in CSV (-8; dynamic FPS and thread options are grouped in their own modules).

---

## 4. Exact Build and Test Output (Verbatim)

```text
> Task :common:compileJava
Note: C:\Users\Arash\AppData\Local\Temp\wla-schema\TheLadsCore\common\src\main\java\com\thelads\core\config\ShareCode.java uses or overrides a deprecated API.
Note: Recompile with -Xlint:deprecation for details.

> Task :common:processResources
> Task :common:classes

> Task :common:compileTestJava
Note: C:\Users\Arash\AppData\Local\Temp\wla-schema\TheLadsCore\common\src\test\java\com\thelads\core\client\qa\QaProgressTest.java uses or overrides a deprecated API.
Note: Recompile with -Xlint:deprecation for details.

> Task :common:processTestResources
> Task :common:testClasses
> Task :common:test

> Task :common:exportLadsApiSchema
SLF4J(W): No SLF4J providers were found.
SLF4J(W): Defaulting to no-operation (NOP) logger implementation
SLF4J(W): See https://www.slf4j.org/codes.html#noProviders for further details.
Exported ladsapi-schema.json to: C:\Users\Arash\AppData\Local\Temp\wla-schema\LadsClientAPI\common\src\main\resources\ladsapi-schema.json

BUILD SUCCESSFUL in 1m 22s
6 actionable tasks: 6 executed
```

---

## 5. Mixin Verification
- **None**: No mixins were added, modified, or targeted in this lane. Only `TheLadsCore` common Java sources, common test sources, common Gradle build script, and `LadsClientAPI` resources were touched.

---

## 6. What Was NOT Done or Verified
- **Minecraft Client / Server Execution**: In accordance with the binding build rules ("Do NOT launch Minecraft, servers, the launcher, QA or benchmarks. Claude runs QA on the Legion laptop later."), Minecraft was not launched. Verification was performed via unit tests in `:common:test` and standalone execution in `:common:exportLadsApiSchema`.
- **Game Version Adapters**: Modern version adapters (`v1_20_1`, `v26_3`) and 1.8.9 Forge were untouched per instructions ("Touch only common + that one resource"). Dynamic client-side-only options (such as version-specific poses or reflection-discovered player actions) are not part of common schema.

---

## 7. Commit & Git Stat
- Final Commit Hash: `9d4e3c4cb941c99b5902ab979aabcc23d105010d`
- `git diff --stat e4a49715..HEAD`:
```text
 .../common/src/main/resources/ladsapi-schema.json  | 6186 ++++++++++++++++++++
 LadsClientAPI/docs/lanes/SCHEMA-REPORT.md          |  153 +
 TheLadsCore/common/build.gradle                    |   11 +
 .../thelads/core/client/ladsapi/SchemaExport.java  |  160 +
 .../core/client/ladsapi/SchemaExportTest.java      |  137 +
 5 files changed, 6647 insertions(+)
```
