# Lane CORE Report: Client Protocol, ServerPolicy, Option Hooks, Safety Merge

## 1. Environment & Branch
- **Worktree**: `C:\Users\Arash\AppData\Local\Temp\wla-core`
- **Branch**: `agy/ladsapi-core`
- **Base commit**: `e4a49715`

## 2. Summary of Implementation
Implemented PROTOCOL.md v1 sections 3-4 on the client behind the frozen interfaces:

1. **`LadsApiClient` (`TheLadsCore/common/src/main/java/com/thelads/core/client/ladsapi/LadsApiClient.java`)**:
   - Payload size enforcement: incoming payloads > `LadsApi.MAX_IN` (524,288 bytes) are dropped immediately.
   - Rate limiting: rolling 1-second window capped at 8 messages/second. Excess messages are silently dropped.
   - Clean-room JSON parser using Gson (compatible with Minecraft 1.8.9's Gson 2.2.4 runtime without relying on newer Java streams or lambdas in dispatch loops).
   - `hello`: Resets previous server policy and presence; stores sanitized `serverName` (<= 32 chars); marks `sessionOk = true`; responds with client hello `{"t":"hello","v":1,"lads":LadsVersion.VERSION,"mc":...}` via the registered `Transport`. Capped at a strict maximum of 2 hello replies per connection session.
   - `policy`: Accepted only when `sessionOk = true`. Validates the incoming rules against registered modules and options; compiles an immutable `ServerPolicy` snapshot and atomically activates it; transmits an `ack` containing `ignored` and `refused` lists (max 64 entries each); triggers UI notification via `LadsApiUi.policyApplied(serverName, changedCount, notice)`.
   - `presence`: Handles `full: true` via `LadsPresence.serverReplace`, plus incremental `add` and `remove` operations (bounded to at most 1,000 UUIDs per payload). Accepts both 36-char dashed UUIDs and 32-char hex UUIDs without dashes.
   - `reset`: Drops policy and presence, clears session status via `ServerPolicy.set(null)`, `LadsPresence.serverClear()`, `LadsApiUi.policyCleared()`, `sessionOk = false`.
   - Connection lifecycle: `reset()`, `joinGame(hashedSeed)`, and `respawn(hashedSeed)`. In `respawn(hashedSeed)`, session state is preserved if the hashed seed matches the current connection seed, preventing spurious resets across dimensions or player respawns.

2. **Validation Rules**:
   - Matching: exact name match first, followed by case-insensitive fallback against `ModuleManager` modules and options via `Module.getOption(name)`. Bidirectional mapping preserves exact server wire names in the `ack` message.
   - Pseudo-module `UseWhileMining` (`HypixelSafetyManager.USE_WHILE_MINING`) accepted for `allow` and `block`.
   - Modules in `LadsApiRules.isNeverForced` (automation, privacy, input, threads) reject `"on"`.
   - `unlisted: allow` never lifts `LadsApiRules.isAutomation` modules (`StackRefill`, `UseWhileMining`, `AutoReconnect`, `MouseTweaks`, `KeepUiOpen`).
   - Controllability: options with `Option.serverControl() == NEVER` are rejected and appended to `ignored`.
   - Slider validation: rejects NaN, Infinity, out-of-range bounds (`min > max`). Forced values are clamped into option `[min, max]` and snapped to the option's step.
   - Dropdown validation: rejects choice indexes out of bounds and choices not present in `DropdownOption.getChoices()`.
   - Color validation: parses `#RRGGBB` and `#AARRGGBB` hex strings, enforcing minimum alpha `>= 0x40`.
   - Immunity: "Server Control" module and all its options are strictly immune to server manipulation; any server rules targeting it are ignored and reported in the ack.

3. **`ServerPolicy` & Allocation-Free Static Helpers (`TheLadsCore/common/src/main/java/com/thelads/core/client/ladsapi/ServerPolicy.java`)**:
   - Fast path: single volatile read (`ServerPolicy.active()`), immediately returning `own` when null.
   - Zero-allocation hot-path lookups: module rules and option rules indexed in arrays / maps without string formatting or heap allocations per frame.
   - Refusal mode ("Let servers change my Lads settings" = false): ignores forced `on` and forced `value` rules only; safety restrictions (`block`, `min`, `max`, `choices`) continue to apply.
   - `applySlider`: returns forced value if not refused, otherwise clamps `own` into `[rule.min, rule.max] ∩ [min, max]` and snaps to step.
   - `applyDropdown`: returns forced choice index if not refused; if a `choices` subset is configured and player's choice is not in the set, selects the first permitted choice.
   - `applyColor`: returns forced ARGB color if not refused; forces `ColorOption.isUseGlobal()` to `false` while an override is active.

4. **Option Getter Hooks & Disk-Leak Guard**:
   - Read hooks in `BoolOption.get()` (covering `PlayerActionOption`), `SliderOption.getValue()` / `getIntValue()`, `DropdownOption.getIndex()` / `getValue()`, `ColorOption.getColor()` / `isUseGlobal()` query `ServerPolicy.apply*`.
   - Serialization integrity: `ConfigManager.toJson()` / `save()` and raw getters (`getOwn()`, `getOwnValue()`, `getOwnIndex()`, `getOwnColor()`) access raw fields only. Server settings are never written to disk.
   - Fixed `IntTextOption.save()` to write raw `Integer.parseInt(super.getValue())` instead of calling `get()`.

5. **`HypixelSafetyManager` & `Module`**:
   - Added `Block.SERVER` to `HypixelSafetyManager.Block` enum.
   - Evaluated first in `blockOf(moduleName)`: policy `BLOCK` returns `Block.SERVER`; policy `ALLOW` returns `Block.NONE`.
   - `Block.SERVER` is strictly non-bypassable in `isDisallowed`.
   - `getBlockedNotice` for `Block.SERVER`: `"<module> is blocked by <server>."`
   - `Module.isEnabled()` returns `false` when blocked, `true` when policy says `ON` and not refused, else player's own `enabled` state.
   - Registered always-on `Server Control` module (`Category.SERVER`) with `BoolOption` `"Let servers change my Lads settings"` (default true) and `BoolOption` `"Show server notice"` (default true).

6. **Fullbright Gamma Cap**:
   - Added `FullbrightModule.serverLimited()` returning `true` when a server policy blocks Fullbright or specifies any rule for Fullbright.
   - Capped vanilla gamma at `1.0` when `FullbrightModule.serverLimited()` is `true`:
     - v26_3: `NativeFeatures.gamma` (~line 143)
     - v1_20_1: `NativeFeatures1201.gamma` (~line 234)
     - v1_8_9: `Fullbright189.tick` (~line 52)

7. **Unit Test Suite (`TheLadsCore/common/src/test/java/com/thelads/core/client/ladsapi/LadsApiClientTest.java`)**:
   - 19 test cases covering: PROTOCOL example policy, malformed/oversized JSON drops, NaN/Inf rejection, bad dropdown choices/indices, unknown names, NEVER options, never-forced ON rejection, automation vs unlisted allow, Server Control immunity, player refusal of forced values, apply* math (clamping, snapping, choices fallback), reset and seed-sensitive respawn, rate limiting (8 msgs/s), max 2 hello replies, no send before server hello, presence parsing (dashed + 32-hex), Fullbright serverLimited toggle, HypixelSafetyManager integration, and disk-leak guard (verifying `ConfigManager.toJson()` serializes only player's own settings).

## 3. Mixin Targets & Verification
No mixin files were touched in this lane. The three version-specific gamma cap edits modified non-mixin native feature classes:
1. `TheLadsCore/v26_3/src/main/java/com/thelads/core/v26_2/feature/NativeFeatures.java`: `public static float gamma(float g)` - verified gamma capped at `Math.min(g, 1.0f)` when `FullbrightModule.serverLimited()`.
2. `TheLadsCore/v1_20_1/src/main/java/com/thelads/core/v1_20_1/feature/NativeFeatures1201.java`: `public static float gamma(float g)` - verified gamma capped at `Math.min(g, 1.0f)` when `FullbrightModule.serverLimited()`.
3. `TheLadsCore/v1_8_9/src/main/java/com/thelads/core/v1_8_9/feature/Fullbright189.java`: `public static void tick(Minecraft mc)` - verified gamma capped at `Math.min(mc.gameSettings.gammaSetting, 1.0f)` when `FullbrightModule.serverLimited()`.

## 4. Build & Test Results from THIS Run

### A. Common Unit Tests (`cd TheLadsCore; .\gradlew.bat :common:test`)
```
> Task :common:compileJava UP-TO-DATE
> Task :common:processResources UP-TO-DATE
> Task :common:classes UP-TO-DATE
> Task :common:compileTestJava
> Task :common:processTestResources UP-TO-DATE
> Task :common:testClasses
> Task :common:test

BUILD SUCCESSFUL in 1m 2s
5 actionable tasks: 2 executed, 3 up-to-date
```
- Total test suites: 112 suites
- Total tests executed: 1,206 tests, 0 failures, 0 errors, 0 skipped
- `LadsApiClientTest`: 19 tests, 0 failures, 0 errors, 0 skipped (time: 0.071s)

### B. Modern Core Builds (`cd TheLadsCore; .\gradlew.bat :v1_20_1:build :v26_3:build`)
```
> Task :v1_20_1:assemble
> Task :v1_20_1:build
> Task :v26_3:jadeCompatibilityIdentity
> Task :v26_3:processResources
> Task :v26_3:classes
> Task :v26_3:processIncludeJars
> Task :v26_3:compileTestJava
> Task :v26_3:processTestResources NO-SOURCE
> Task :v26_3:testClasses
> Task :v26_3:test
> Task :v26_3:validateAccessWidener
> Task :v26_3:jar
> Task :v26_3:assemble
> Task :v26_3:verifyJadePackaging
> Task :v26_3:check
> Task :v26_3:build

BUILD SUCCESSFUL in 1m 14s
18 actionable tasks: 16 executed, 2 up-to-date
```

### C. 1.8.9 Core Build (`cd TheLadsCore\v1_8_9; .\gradlew.bat build`)
```
> Task :downgradeCommon
> Task :compileJava
> Task :processResources
> Task :classes
> Task :jar
> Task :shadowJar
> Task :shadeJvmdgApi
> Task :remapJar
> Task :assemble
> Task :compileTestJava NO-SOURCE
> Task :processTestResources NO-SOURCE
> Task :testClasses UP-TO-DATE
> Task :test NO-SOURCE
> Task :validateAccessWidener NO-SOURCE
> Task :check UP-TO-DATE
> Task :build

BUILD SUCCESSFUL in 22s
7 actionable tasks: 7 executed
```

## 5. What Could NOT Be Done or Verified
- Game client runtime / in-game rendering checks: Not performed because Rule 33 explicitly prohibits launching Minecraft, servers, launcher, QA, or benchmarks in build lanes.
- Live network packet exchange with actual server instances: Network protocols were tested end-to-end via unit tests with mock byte transports and JSON payloads rather than live server connections.

## 6. Git Diff Stat & Commit
- **Commit**: `843d7f79` (`843d7f7923fced82f7395087dc0c9db813b0419e`)
- **`git diff --stat e4a49715..HEAD`**:
```text
 LadsClientAPI/docs/lanes/CORE-REPORT.md            | 139 +++++
 .../thelads/core/client/HypixelSafetyManager.java  |  21 +-
 .../thelads/core/client/ladsapi/LadsApiClient.java | 662 ++++++++++++++++++++-
 .../thelads/core/client/ladsapi/ServerPolicy.java  | 137 ++++-
 .../java/com/thelads/core/config/BoolOption.java   |   2 +-
 .../java/com/thelads/core/config/ColorOption.java  |   7 +-
 .../com/thelads/core/config/DropdownOption.java    |   5 +-
 .../com/thelads/core/config/IntTextOption.java     |   2 +-
 .../main/java/com/thelads/core/config/Module.java  |   7 +
 .../com/thelads/core/config/ModuleManager.java     |   8 +
 .../java/com/thelads/core/config/SliderOption.java |   4 +-
 .../com/thelads/core/modules/FullbrightModule.java |   8 +
 .../core/client/ladsapi/LadsApiClientTest.java     | 453 ++++++++++++++
 .../core/v1_20_1/feature/NativeFeatures1201.java   |   1 +
 .../thelads/core/v1_8_9/feature/Fullbright189.java |   5 +
 .../thelads/core/v26_2/feature/NativeFeatures.java |   1 +
 16 files changed, 1439 insertions(+), 23 deletions(-)
```
