# Lane QAPREP: LadsClientAPI QA Network Preparation & Checklist Report

- **Worktree**: `C:\Users\Arash\AppData\Local\Temp\wla-qaprep`
- **Branch**: `agy/ladsapi-qaprep`
- **Base Commit**: `e4a49715` (`ladsapi: phase 0 - frozen protocol v1 and client/server interfaces`)
- **Commit Hash**: `cd7592dd`
- **Diff Stat (`git diff --stat e4a49715..HEAD`)**:
```text
 LadsClientAPI/docs/lanes/QAPREP-REPORT.md          |  92 +++++
 LadsClientAPI/qa/CHECKLIST.md                      | 333 ++++++++++++++++++
 LadsClientAPI/qa/packet-logger/packet-logger.jar   | Bin 0 -> 2558 bytes
 .../src/com/thelads/qa/PacketLoggerPlugin.java     |  46 +++
 LadsClientAPI/qa/packet-logger/src/plugin.yml      |   5 +
 LadsClientAPI/qa/rules-qa.yml                      |  30 ++
 LadsClientAPI/qa/setup-network.ps1                 | 379 +++++++++++++++++++++
 LadsClientAPI/qa/start-network.ps1                 | 236 +++++++++++++
 LadsClientAPI/qa/stop-network.ps1                  | 141 ++++++++
 9 files changed, 1262 insertions(+)
```
- **Status**: COMPLETE

---

## 1. Deliverables Summary

All deliverables specified in `AGY-LADSAPI-B-QAPREP.md` and `AGY-LADSAPI-BUILD-RULES.md` have been implemented and verified in the worktree:

1. [`LadsClientAPI\qa\setup-network.ps1`](file:///C:/Users/Arash/AppData/Local/Temp/wla-qaprep/LadsClientAPI/qa/setup-network.ps1)
   - Idempotently builds the 5-node test network under `F:\LadsQA\ladsapi-net\`.
   - Node 1: Velocity 3.6.0 proxy on port **25700** (`player-info-forwarding-mode = "modern"`, `forwarding.secret`).
   - Node 2: Paper 26.3 backend "lobby" on port **25701** (ViaVersion, ViaBackwards, ViaRewind, ViaRewind-Legacy-Support, `LadsClientAPI.jar`, `rules-qa.yml`).
   - Node 3: Paper 26.3 backend "plain" on port **25702** (Via* plugins, `PacketLogger.jar`, **NO** `LadsClientAPI.jar` to prove zero-leak and proxy reset).
   - Node 4: Fabric 26.3 server on port **25703** (Fabric Loader 0.19.5, Fabric API 0.163.0+26.3, `LadsClientAPI.jar`, `rules-qa.yml`).
   - Node 5: Forge 1.20.1-47.4.10 server on port **25704** (`LadsClientAPI.jar`, `rules-qa.yml`).
   - Sourcing: inspects `\\Arash-HP\Servers` and `C:\LadsQA\shared\.qa-cache` first; falls back to pinned official URLs.
   - `-Jar <path>` parameter copies the jar into lobby, velocity, fabric, and forge, ensuring plain remains clean.

2. [`LadsClientAPI\qa\start-network.ps1`](file:///C:/Users/Arash/AppData/Local/Temp/wla-qaprep/LadsClientAPI/qa/start-network.ps1)
   - Discovers Java 25/21 for modern Paper/Fabric and Java 17 for Velocity/Forge from Legion QA cache or environment.
   - Launches all servers minimized with `PriorityClass = BelowNormal`.
   - Monitors server logs in real-time, waiting for `Done (` or `Listening on`.
   - Records PIDs to `.pid` files and outputs server status table with log file paths.

3. [`LadsClientAPI\qa\stop-network.ps1`](file:///C:/Users/Arash/AppData/Local/Temp/wla-qaprep/LadsClientAPI/qa/stop-network.ps1)
   - Gracefully shuts down active servers with timeout and fallback forced kill.
   - Verifies TCP ports 25700 through 25704 are completely released via `Get-NetTCPConnection`.

4. [`LadsClientAPI\qa\rules-qa.yml`](file:///C:/Users/Arash/AppData/Local/Temp/wla-qaprep/LadsClientAPI/qa/rules-qa.yml)
   - Fullbright: `allow` + `Brightness.max: 10`.
   - XaeroMinimap: `block` (plus Minimap alias).
   - Crosshair Tweaks: forced `Dot: { value: true, lock: true }`.
   - 1.7 Animations: forced `1.7 sword block pose: { value: false, lock: true }`.
   - `unlisted: inherit`.
   - Notice text: `"Lads QA Lobby: Fullbright capped at 10%"`.

5. [`LadsClientAPI\qa\CHECKLIST.md`](file:///C:/Users/Arash/AppData/Local/Temp/wla-qaprep/LadsClientAPI/qa/CHECKLIST.md)
   - 12 comprehensive acceptance checks with step-by-step instructions, expected results, and exact proof mechanisms.
   - Details the QA Boss-Bar rule (`QaBar.java` format `Lads QA · ladsapi · task i/N · <short-label> · OK x · SLOW y · FAIL z · CRASH 0 · ETA mm:ss`) and how each check reports to it.
   - Covers: handshake, single join toast, Fullbright clamping, minimap hard block, forced option greyed/lock tooltip, byte-for-byte SHA-256 config integrity on leave, crash resilience, proxy switch reset, direct join zero lads:api packets, player autonomy refusal, badge presence in tab/nametag, and 4-client multi-version matrix.

6. [`LadsClientAPI\qa\packet-logger`](file:///C:/Users/Arash/AppData/Local/Temp/wla-qaprep/LadsClientAPI/qa/packet-logger)
   - Standalone Bukkit verification plugin (`PacketLoggerPlugin.java`, `plugin.yml`, `packet-logger.jar`).
   - Listens on channel `lads:api` on the `plain` server to detect and log any unexpected packets to verify anti-ban compliance.

---

## 2. Exact Build & Validation Results (from this run)

### 2.1 PowerShell Script Syntax Validation
Command:
```powershell
pwsh -NoProfile -Command "[scriptblock]::Create((Get-Content '<file>' -Raw)) | Out-Null"
```

Execution Output:
```text
Validating: C:\Users\Arash\AppData\Local\Temp\wla-qaprep\LadsClientAPI\qa\setup-network.ps1
SYNTAX OK: C:\Users\Arash\AppData\Local\Temp\wla-qaprep\LadsClientAPI\qa\setup-network.ps1
Validating: C:\Users\Arash\AppData\Local\Temp\wla-qaprep\LadsClientAPI\qa\start-network.ps1
SYNTAX OK: C:\Users\Arash\AppData\Local\Temp\wla-qaprep\LadsClientAPI\qa\start-network.ps1
Validating: C:\Users\Arash\AppData\Local\Temp\wla-qaprep\LadsClientAPI\qa\stop-network.ps1
SYNTAX OK: C:\Users\Arash\AppData\Local\Temp\wla-qaprep\LadsClientAPI\qa\stop-network.ps1
```

### 2.2 PacketLogger Plugin Compilation
Command:
```powershell
javac -cp "$paperApi;$adventureApi;$adventureKey" -d $outDir $src
jar -cf $targetJar -C $outDir .
```
Result: `True` (Compiled with 0 warnings, 0 errors; archive verified with `jar -tf`).

---

## 3. Mixins

**None**. Lane QAPREP is strictly automation scripts, network layout preparation, configuration schemas, and QA verification documentation. No client or server Java mixins were modified in this lane.

---

## 4. What Was NOT Done / Could NOT Be Verified

Per binding rules (`AGY-LADSAPI-BUILD-RULES.md` and `AGY-LADSAPI-B-QAPREP.md`):
1. **Did not launch Minecraft games, servers, launcher, or benchmarks**: Servers and game clients were not launched on Arash-PC. Claude runs the automated verification pass on the Legion laptop (`ARASH-LEGION`) using the prepared scripts.
2. **Did not execute scripts on Legion directly**: Scripts and configurations were written and syntax-validated locally; physical directory creation on `F:\LadsQA\ladsapi-net\` will take place when Claude invokes `setup-network.ps1` on Legion.
3. **No git push or tag**: Changes are committed locally on branch `agy/ladsapi-qaprep`.
