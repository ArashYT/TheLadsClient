# GUI Lane Report: Locks, Tooltips, Write-Back Fixes, and Join Toast

- **Lane:** GUI (`AGY-LADSAPI-B-GUI.md`)
- **Worktree:** `C:\Users\Arash\AppData\Local\Temp\wla-gui`
- **Branch:** `agy/ladsapi-gui`
- **Base Commit:** `e4a49715` (`ladsapi: phase 0 - frozen protocol v1 and client/server interfaces`)
- **Commit:** `6eee7ab8` (`ladsapi: GUI lock icons, tooltips, write-back fixes and join toast`)
- **`git diff --stat e4a49715..HEAD`:**
  ```text
   LadsClientAPI/docs/lanes/GUI-REPORT.md             | 134 ++++++++++++
   .../core/client/gui/LadsSettingsScreen.java        | 241 ++++++++++++++++-----
   .../thelads/core/client/hud/ServerRulesNotice.java |  50 ++++-
   .../com/thelads/core/client/ladsapi/LadsApiUi.java | 118 +++++++++-
   .../thelads/core/client/ladsapi/LadsApiUiTest.java | 121 +++++++++++
   5 files changed, 603 insertions(+), 61 deletions(-)
  ```

---

## 1. Summary of Changes

### 1.1 `LadsSettingsScreen` (Locks, Tooltips, Forced Options, Module Rows)
1. **Lock Icon & Tooltip:**
   - In `optionRow`, checks `ServerPolicy.active().option(detail.getName(), option.getName())`.
   - If a rule exists, draws a 6x8 px lock icon (`drawLockIcon`) in accent red (`0xFFFF3B3B`) if forced/locked, or muted grey (`0xFF888888`) if constrained.
   - Hovering any row with an active rule sets `tipOption` and `tipOptionRule`, rendering tooltips formatted via `LadsApiUi.formatOptionTooltip`:
     - Slider max: `"Limited by <server>: max 10%"`
     - Slider min: `"Limited by <server>: min 2"`
     - Slider min & max: `"Limited by <server>: min 2, max 10"`
     - Choices: `"Limited by <server>: Only: A, B"`
     - Forced value: `"Set by <server>"`
2. **Forced Options Greyed & Effective Values Displayed:**
   - Forced options (`rule.forcesValue() && !policy.refusesChanges()`, or explicit `rule.lock`) set `serverLocked = true`.
   - All controls (buttons, slider tracks, editable text fields, color pickers, dropdown cycles) have `enabled = !serverLocked` and are greyed out.
   - Sliders display effective values via `displayValue(s)` which calls `s.getValue()`.
3. **Module Rows:**
   - In `renderCatalog`:
     - If `module(name) == BLOCK`, displays subtitle `"Blocked by <server>"` and disables toggle.
     - If `module(name) == ON`, displays subtitle `"Turned on by <server>"` (unless refused) and disables toggle.
   - In `renderDetails`:
     - If `module(name) == BLOCK`, disables toggle, shows `"Blocked by <server>"`, and strictly suppresses the `"Bypass..."` button (server authority cannot be bypassed).
     - If `module(name) == ON`, disables toggle and displays `"ON"`.

---

## 2. Write-Back Audit & Fixes (D1 Report 5.3 & 5.4)

Every path that previously read an effective getter (`getValue()`, `getIndex()`, `getColor()`) and wrote back into the option was audited and fixed:

| Location | Path | Bug in Base | Fix Applied |
|---|---|---|---|
| `LadsSettingsScreen:1980` | `startEdit(Option o)` | Read `s.getValue()` and `c.getColor()` into `editBuffer`. Committing wrote server clamps to user config. | Now reads `s.getOwnValue()` and `c.getOwnColor()`. Returns immediately if `isServerLocked(o)`. |
| `LadsSettingsScreen:1995` | `commitEdit()` | Committed parsed text directly to option and called `ConfigManager.save()`. | Aborts immediately if `isServerLocked(editingOption)`. |
| `LadsSettingsScreen:1924` | `keyPressed` (arrow keys) | Called `s.setValue(s.getValue() + direction * step)` and `d.setIndex(d.getIndex() + direction)`. | Now calls `s.setValue(s.getOwnValue() + ...)` and `d.setIndex(d.getOwnIndex() + ...)`. Aborts if `isServerLocked(o)`. Handles `:field` suffix on `focusId`. |
| `LadsSettingsScreen:1779` | `mouseClicked` | Allowed dragging or cycling options. | Added `if (isServerLocked(o)) return true;`. Excluded `:field` button IDs from slider dragging so text field button action runs cleanly. |
| `LadsSettingsScreen:1836` | `updateDrag(double x)` | Slider mouse dragging wrote to option without checking server locks. | Added `if (isServerLocked(dragging)) return;`. |
| `LadsSettingsScreen:1419` | `ColorOption` picker | Opened color picker with `c.getColor()`, exposing server forced color to picker. | Now passes `c.getOwnColor()`. Action guarded with `!serverLocked`. |
| `LadsSettingsScreen:1400` | `BoolOption` toggle & `DropdownOption` cycle | Actions could run if invoked programmatically. | Guarded actions with `if (!serverLocked)`. |
| `LadsSettingsScreen:585-695` | `killBannerPicker` | Tiles and buttons for `bannerStyle`, `customVisual`, `customSound`, and `randomize` lacked lock checks. | Added `isServerLocked` checks on all four options, disabling tiles and guarding actions. |

---

## 3. Policy Toast (`LadsApiUi` & `ServerRulesNotice`)

1. **`LadsApiUi.policyApplied(serverName, changed, notice)`**:
   - Exactly one toast per policy: title formatted via `formatPolicyToastTitle(serverName, changed)` (`"<server> limits N Lads settings"`).
   - Tolerates missing `"Server Control"` module; checks `"Show server notice"` setting (`isShowServerNoticeEnabled()`) and hides toast if off.
   - Clears toast if `changed <= 0` and `notice` is empty.
2. **`LadsApiUi.policyCleared()`**:
   - Resets state and dismisses the toast.
3. **`ServerRulesNotice`**:
   - Reused existing HUD drawing overlay (`ServerRulesNotice.render`).
   - Added `showPolicyToast(title, notice)` and `clearPolicyToast()`.
   - Card drawn with client notification palette: background `0xE0130B10`, top accent bar `0xFFFF3B3B`, title `0xFFFFFFFF`, notice `0xFFCCCCCC`, 8-second timeout.

---

## 4. Unit Tests (`LadsApiUiTest.java`)

Unit test suite added at `TheLadsCore/common/src/test/java/com/thelads/core/client/ladsapi/LadsApiUiTest.java`:
- `formatPolicyToastTitleSingularAndPlural`: verifies singular ("1 Lads setting"), plural ("3 Lads settings"), 0 changes, and fallback server name.
- `formatOptionTooltipForcedValues`: verifies forced slider, bool, choice, and color produce `"Set by <server>"`.
- `formatOptionTooltipSliderLimits`: verifies `"Limited by <server>: max 10%"`, `"min 2"`, and `"min 2, max 8"`.
- `formatOptionTooltipDropdownChoices`: verifies `"Limited by <server>: Only: A, B"`.
- `formatOptionTooltipNullOrLockOnly`: verifies null rule returns `""` and lock-only rule produces `"Set by <server>"`.
- `formatModuleStatusRules`: verifies `BLOCK` ("Blocked by <server>"), `ON` without refusal ("Turned on by <server>"), and `ON` with refusal (`null`).
- `policyAppliedAndClearedLifecycle`: verifies toast activation, title/notice caching, single-line toasts, zero-change suppression, and clearance.

---

## 5. Verification & Test Output

### 5.1 BENCH-LOCK Verification
Checked `C:\Users\Arash\Desktop\The Lads Client Dev\.qa-bench\BENCH-LOCK`:
- Checked at 15:23:09: `rx pc-rx11-smoke (1.8.9 render extras) until 15:28` - waited.
- Checked at 15:23:58: `none` (cleared).
- Checked at 15:28:42: `none` (cleared).

### 5.2 Build & Test Results
Command executed:
```pwsh
.\gradlew.bat :common:test :v1_20_1:build :v26_3:build
```
Exact output:
```text
BUILD SUCCESSFUL in 51s
21 actionable tasks: 16 executed, 5 up-to-date
```
Common test suite results:
```text
1194 tests completed, 0 failures, 1 skipped
```
`LadsApiUiTest` result (`TheLadsCore/common/build/test-results/test/TEST-com.thelads.core.client.ladsapi.LadsApiUiTest.xml`):
```xml
<testsuite name="com.thelads.core.client.ladsapi.LadsApiUiTest" tests="7" skipped="0" failures="0" errors="0" timestamp="2026-10-18T19:28:07.649Z" hostname="ARASH-PC" time="0.006">
  <testcase name="formatOptionTooltipForcedValues()" classname="com.thelads.core.client.ladsapi.LadsApiUiTest" time="0.002"/>
  <testcase name="formatPolicyToastTitleSingularAndPlural()" classname="com.thelads.core.client.ladsapi.LadsApiUiTest" time="0.001"/>
  <testcase name="formatOptionTooltipSliderLimits()" classname="com.thelads.core.client.ladsapi.LadsApiUiTest" time="0.001"/>
  <testcase name="formatOptionTooltipNullOrLockOnly()" classname="com.thelads.core.client.ladsapi.LadsApiUiTest" time="0.0"/>
  <testcase name="formatOptionTooltipDropdownChoices()" classname="com.thelads.core.client.ladsapi.LadsApiUiTest" time="0.0"/>
  <testcase name="policyAppliedAndClearedLifecycle()" classname="com.thelads.core.client.ladsapi.LadsApiUiTest" time="0.0"/>
  <testcase name="formatModuleStatusRules()" classname="com.thelads.core.client.ladsapi.LadsApiUiTest" time="0.0"/>
</testsuite>
```

---

## 6. Mixin Targets & Boundaries

- **Mixin Targets:** None. Lane GUI did not add or modify any mixins. All UI changes are within common client classes (`LadsSettingsScreen`, `ServerRulesNotice`, `LadsApiUi`).
- **Clean Scope Enforced:**
  - Did NOT modify `ServerPolicy.java`, `LadsApiClient.java`, option classes (`Option.java`, `BoolOption.java`, `SliderOption.java`, etc.), or `HypixelSafetyManager.java` (lane CORE).
  - Did NOT modify frozen versions `v26_2` or `v1_21_11`.
  - All commands executed strictly in foreground.
