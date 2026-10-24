# Lane BADGE-A Report: Badge Texture, Lads Badge Module, Relay Presence

- **Branch**: `agy/ladsapi-badgea`
- **Base Commit**: `e4a49715` (phase-0 base)
- **Commits**:
  - `e8c390fa` (`ladsapi: badge texture, Lads Badge module, relay presence (Lane BADGE-A)`)
  - `6130b2d0` (`ladsapi: lane BADGE-A completion report`)
- **Worktree**: `C:\Users\Arash\AppData\Local\Temp\wla-badgea`

---

## 1. Summary of Changes

1. **Badge Texture**:
   - Created script `tools/make-badge-texture.py` to deterministically downscale `TheLadsCore/common/src/main/resources/assets/theladscore/icons/window_256.png` with Lanczos (Pillow).
   - Generated two candidates:
     - **Candidate A** (full 256x256 downscaled to 64x64): `LadsClientAPI/docs/lanes/badge/lads_badge_candidate_full.png`
     - **Candidate B** (face/upper body 192x192 square crop centered at (128, 112) downscaled to 64x64): `LadsClientAPI/docs/lanes/badge/lads_badge_candidate_cropped.png`
   - **Claude's decision**: Claude picked Candidate B (cropped).
   - Committed active texture: `TheLadsCore/common/src/main/resources/assets/theladscore/textures/gui/lads_badge.png` (Candidate B, 64x64 RGBA).
   - All candidate files and 8px/9px comparison previews were moved out of `src/main/resources` to `LadsClientAPI/docs/lanes/badge/` so they do not ship in the mod jar.

2. **Module "Lads Badge"**:
   - Implemented in `TheLadsCore/common/src/main/java/com/thelads/core/modules/LadsBadgeModule.java`.
   - Category: `Category.HUD` (matching `TabListModule`).
   - Enabled by default (`setEnabled(true)`).
   - Options:
     - `Tab` (`BoolOption`, default `true`)
     - `Nametag` (`BoolOption`, default `true`)
     - `Show mine` (`BoolOption`, default `true`)
   - Registered in `ModuleManager.java`.
   - Added to `DEFAULT_ENABLED_VISIBLE` in `TheLadsCore/common/src/test/java/com/thelads/core/Lads197DefaultsTest.java`.

3. **`LadsBadge.showInTab` and `showOnNametag` Implementation**:
   - Implemented frozen signatures in `TheLadsCore/common/src/main/java/com/thelads/core/client/ladsapi/LadsBadge.java`.
   - Allocation-free: samples session UUID via `LadsGameBridge.getClientTicks()` at most once per 20 TPS client tick. On subsequent frames within the tick, queries return cached volatile references with 0 allocations.
   - Gating rules:
     - Player is null -> returns `false`.
     - Module disabled or blocked by server policy (`!module.isEnabled()`) -> returns `false`.
     - Matching option disabled (`!module.tab.get()` / `!module.nametag.get()`) -> returns `false`.
     - Player is local player ("me") -> returns `module.showMine.get()`.
     - Other players -> returns `LadsPresence.isLads(player)`.

4. **Cosmetics Relay Presence in `CosmeticsLink`**:
   - In `CosmeticsLink.java`:
     - `"snapshot"`: calls `LadsPresence.relayClear()`, then adds each player UUID from `players` via `LadsPresence.relayAdd(uuid)`.
     - `"join"`: adds joining player UUID via `LadsPresence.relayAdd(uuid)`.
     - `"leave"`: removes departing player UUID via `LadsPresence.relayRemove(uuid)`.
     - Room change & disconnect: calls `LadsPresence.relayClear()` on server address switch, room switch, reader thread exit, and `serve()` finally block.
     - Outbound messages to the relay remain untouched.

---

## 2. Mixin Targets

- **None**: Lane BADGE-A implements client common logic, module registration, texture assets, and protocol relay presence. Version-specific rendering mixins for tab and nametags are assigned to lanes BADGE-B (1.8.9 / 1.20.1) and BADGE-C (26.3).

---

## 3. Build and Test Verification

### BENCH-LOCK Check
- Checked `C:\Users\Arash\Desktop\The Lads Client Dev\.qa-bench\BENCH-LOCK` before running Gradle build.
- Waited while active, executed build once `BENCH-LOCK` was clear (`False`).

### Foreground Build Command
```powershell
.\gradlew.bat :common:test :v1_20_1:build :v26_3:build
```

### Exact Build Output (copied from run)
```text
> Configure project :v1_20_1
Fabric Loom: 1.17.21

> Configure project :v1_21_11
Fabric Loom: 1.17.21

> Configure project :v26_2
Fabric Loom: 1.17.21

> Configure project :v26_3
Fabric Loom: 1.17.21

> Task :common:compileJava UP-TO-DATE
> Task :common:processResources
> Task :common:classes
> Task :common:jar
> Task :common:compileTestJava
> Task :common:processTestResources
> Task :common:testClasses
> Task :common:test

> Task :v1_20_1:processResources
> Task :v1_20_1:classes
> Task :v1_20_1:compileTestJava NO-SOURCE
> Task :v1_20_1:jar
> Task :v1_20_1:processIncludeJars
> Task :v1_20_1:remapSourcesJar SKIPPED
> Task :v1_20_1:processTestResources NO-SOURCE
> Task :v1_20_1:testClasses UP-TO-DATE
> Task :v1_20_1:test NO-SOURCE
> Task :v1_20_1:validateAccessWidener
> Task :v1_20_1:check
> Task :v1_20_1:remapJar

> Task :v26_3:compileJava
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

BUILD SUCCESSFUL in 1m 55s
21 actionable tasks: 17 executed, 4 up-to-date
```

### Unit Tests
- `com.thelads.core.client.ladsapi.LadsBadgeTest`: 8 tests, 0 failures, 0 errors.
  - `moduleDisabledHidesBadgeForAll()`: PASSED
  - `nametagOptionDisabledHidesOnlyOnNametag()`: PASSED
  - `showMineOptionControlsLocalPlayerBadge()`: PASSED
  - `tabOptionDisabledHidesOnlyInTab()`: PASSED
  - `otherLadsAndNonLadsPresenceRules()`: PASSED
  - `nullPlayerNeverShowsBadge()`: PASSED
  - `allocationFreeHighFrequencySimulation()`: PASSED
  - `badgeTextureResourceIsValid64x64Png()`: PASSED
- `com.thelads.core.client.cosmetics.net.CosmeticsLinkTest`: 11 tests, 0 failures, 0 errors.
  - `messagesUpdateRelayPresence()`: PASSED
  - (All 10 existing WebSocket/framing/emotes tests): PASSED
- `com.thelads.core.Lads197DefaultsTest`: PASSED

---

## 4. Git Diff Stat

```text
git diff --stat e4a49715..HEAD
 LadsClientAPI/docs/lanes/BADGE-A-REPORT.md         | 175 +++++++++++++++++++
 .../lanes/badge/lads_badge_cand_a_8px_preview.png  | Bin 0 -> 516 bytes
 .../lanes/badge/lads_badge_cand_a_9px_preview.png  | Bin 0 -> 636 bytes
 .../lanes/badge/lads_badge_cand_b_8px_preview.png  | Bin 0 -> 521 bytes
 .../lanes/badge/lads_badge_cand_b_9px_preview.png  | Bin 0 -> 651 bytes
 .../lanes/badge/lads_badge_candidate_cropped.png   | Bin 0 -> 10679 bytes
 .../docs/lanes/badge/lads_badge_candidate_full.png | Bin 0 -> 10535 bytes
 .../core/client/cosmetics/net/CosmeticsLink.java   |  36 +++-
 .../com/thelads/core/client/ladsapi/LadsBadge.java |  59 ++++++-
 .../com/thelads/core/config/ModuleManager.java     |   1 +
 .../com/thelads/core/modules/LadsBadgeModule.java  |  25 +++
 .../assets/theladscore/textures/gui/lads_badge.png | Bin 0 -> 10679 bytes
 .../java/com/thelads/core/Lads197DefaultsTest.java |   1 +
 .../client/cosmetics/net/CosmeticsLinkTest.java    |  44 +++++
 .../thelads/core/client/ladsapi/LadsBadgeTest.java | 186 +++++++++++++++++++++
 tools/make-badge-texture.py                        |  62 +++++++
 16 files changed, 581 insertions(+), 8 deletions(-)
```

---

## 5. What Could NOT Be Done or Verified

1. In-game visual badge rendering on Minecraft clients (1.8.9, 1.20.1, 26.3): Per Rule 33, game clients and servers were not launched during worker agent execution. Tab list and nametag rendering mixins belong to lanes BADGE-B and BADGE-C, and full game QA is run by Claude on the Legion laptop.
2. Live WebSocket connection to `wss://bot.arashyt.ca/cosmetics/ws`: Out of scope; network protocols were verified using offline unit tests in `CosmeticsLinkTest` via simulated WebSocket frames and parsed JSON messages.
