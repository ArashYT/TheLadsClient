# LadsClientAPI Acceptance Checklist & QA Runbook

This document defines the formal acceptance checks for the `LadsClientAPI` server jar and client integration.
All tests run on **ARASH-LEGION** within the automated verification harness and local test network.

---

## 1. Test Network Topology & Ports

All servers run locally on ARASH-LEGION under `F:\LadsQA\ladsapi-net\`:

| Identifier | Engine / Platform | Port | Role / Installed Components |
|---|---|---|---|
| `velocity` | Velocity 3.6.0 | **25700** | Modern forwarding (`forwarding.secret`), routes to lobby/plain/fabric/forge. Runs LadsClientAPI proxy reset listener. |
| `lobby` | Paper 26.3 | **25701** | Backend lobby. ViaVersion + ViaBackwards + ViaRewind. `LadsClientAPI.jar` + `rules-qa.yml`. |
| `plain` | Paper 26.3 | **25702** | Clean backend. Via* plugins + `PacketLogger.jar`. **NO LadsClientAPI** (anti-ban & zero-leak proof). |
| `fabric` | Fabric 26.3 | **25703** | Fabric Loader 0.19.5 + Fabric API 0.163.0+26.3 + `LadsClientAPI.jar` + `rules-qa.yml`. |
| `forge` | Forge 1.20.1-47.4.10 | **25704** | Minecraft Forge 1.20.1 + `LadsClientAPI.jar` + `rules-qa.yml`. |

Client Test Matrix:
1. **Minecraft 1.8.9 Forge** (`TheLadsCore-1.8.9.jar`)
2. **Minecraft 1.20.1 Fabric** (`TheLadsCore-1.20.1.jar`)
3. **Minecraft 1.20.1 Forge** (`TheLadsCore-1.20.1.jar` via Sinytra Connector)
4. **Minecraft 26.3 Fabric** (`TheLadsCore-26.3.jar`)

---

## 2. QA Boss-Bar Integration Rule

Per the binding owner rule (`CLAUDE-1911-LANE-RULES.md`, `QaBar.java`):
> **Every QA run must show the live boss-bar progress bar:**
> `Lads QA · <run> · task i/N · <current> · OK <ok> · SLOW <slow> · FAIL <fail> · CRASH <crash> · ETA <mm:ss>`

### Driver & Bar Specifications:
- **Bar Entity**: `ServerBossEvent` (`UUID 1ad5a0a0-0000-4000-8000-00000000b055`), overlay `PROGRESS`.
- **Bar Colors**:
  - `GREEN`: 0 FAIL and 0 CRASH.
  - `YELLOW`: SLOW > 0, 0 FAIL.
  - `RED`: FAIL + CRASH > 0.
- **Log Replay & Reporting**:
  - Each completed check outputs a single line to `results.log`:
    - Success: `HH:MM:SS OK <idx> <task-name> <duration>ms`
    - Failure: `HH:MM:SS FAIL <idx> <task-name> <reason>`
    - Timeout: `HH:MM:SS SLOW <idx> <task-name> <duration>ms`
  - The driver updates the boss bar via `QaBar.update(server, tasks, done, current)`.

---

## 3. Acceptance Checks

### CHK-01: Protocol Handshake (`hello` -> `hello` -> `policy` -> `ack`)
- **Boss-Bar Task**: `task 1/12 · chk01-handshake`
- **Applicable Environments**: Paper lobby (:25701), Fabric (:25703), Forge (:25704).
- **Setup**: Clean client connects to server.
- **Steps**:
  1. Client connects to server.
  2. Server schedules `hello` at +1 tick.
  3. Client receives server `hello` on channel `lads:api` and responds with client `hello`.
  4. Server sends initial `policy` snapshot (`rev: 1`).
  5. Client validates JSON, stores `ServerPolicy` in memory, and replies with `ack`.
- **Expected Result**:
  - Server debug log shows:
    - `[lads:api] Sent hello to <player> (v=1, server=Lads QA Lobby)`
    - `[lads:api] Received hello from <player>: lads=1.9.13, mc=<version>`
    - `[lads:api] Sent policy rev=1 to <player> (modules=2, options=3)`
    - `[lads:api] Received ack rev=1 from <player> (ignored=0, refused=0)`
  - Client log shows:
    - `[LadsAPI] Received server hello from 'Lads QA Lobby'`
    - `[LadsAPI] Replied with client hello`
    - `[LadsAPI] Received policy rev 1: 2 modules, 3 options`
    - `[LadsAPI] Sent ack rev 1`
- **Proof**:
  - Exact regex match in server log: `lads:api.*Sent policy rev=1` followed by `Received ack rev=1`.
  - Client log line: `[LadsAPI] Sent ack rev 1`.

---

### CHK-02: Join Notification Toast (Single toast, never per-module)
- **Boss-Bar Task**: `task 2/12 · chk02-toast`
- **Setup**: Connect to Paper lobby (:25701) with `rules-qa.yml` active.
- **Steps**:
  1. Client finishes handshake and applies policy.
  2. Client UI manager triggers join notification.
- **Expected Result**:
  - Exactly **one** toast notification appears in the top-right corner:
    `"Lads QA Lobby"` / `"This server limits Lads settings (view)"`
  - Notice text appears in tooltip or popup: `"Lads QA Lobby: Fullbright capped at 10%"`.
  - NO spam of individual toasts per restricted module.
- **Proof**:
  - Screenshot capture: `artifacts/verification/ladsapi/shots/chk02_toast.png`.
  - Verification harness log: `ToastManager queued exactly 1 server policy notification`.

---

### CHK-03: Fullbright Clamping (In-game 10% limit + Gamma Clamp)
- **Boss-Bar Task**: `task 3/12 · chk03-fullbright`
- **Setup**: Player's local config has `Fullbright = ON`, `Brightness = 100%`. Server policy sets `Fullbright = allow`, `Brightness.max = 10`.
- **Steps**:
  1. Enter a dark area (cave or nighttime with block light = 0).
  2. Open Lads Client Mod Settings -> Fullbright.
  3. Inspect Brightness slider.
  4. Attempt to drag slider above 10%.
- **Expected Result**:
  - In-game rendered world brightness is visibly dark (capped at 10% light level).
  - In settings menu, slider value displays `10%` and padlock icon is rendered.
  - Hovering over slider displays tooltip: `"Limited by Lads QA Lobby: max 10%"`.
  - Dragging the slider clamps maximum value to 10%.
  - Vanilla gamma setting (`options.txt`) input is capped at 1.0 (no bypass via options.txt).
- **Proof**:
  - Screenshot of menu: `artifacts/verification/ladsapi/shots/chk03_fullbright_slider.png`.
  - Screenshot of world view: `artifacts/verification/ladsapi/shots/chk03_fullbright_world.png` (verified mean pixel luminance < 35/255).
  - Client log: `SliderOption[Fullbright.Brightness] clamped from 100 to 10 by ServerPolicy`.

---

### CHK-04: Minimap Hard Block ("Blocked by this server")
- **Boss-Bar Task**: `task 4/12 · chk04-minimap-block`
- **Setup**: Player's local config has `XaeroMinimap = ON`. Server policy sets `XaeroMinimap: block`.
- **Steps**:
  1. Inspect HUD on server join.
  2. Open Lads Client Mod Settings -> Minimap.
  3. Attempt to toggle Minimap ON.
- **Expected Result**:
  - Minimap HUD element is completely absent from the screen.
  - In settings menu, Minimap toggle is disabled (OFF) and greyed out.
  - Red badge or subtitle reads: `"Blocked by this server"`.
  - "At own risk" bypass button is NOT available (server policy takes absolute precedence over client bypass list).
- **Proof**:
  - Screenshot of HUD: `artifacts/verification/ladsapi/shots/chk04_hud_nominimap.png`.
  - Screenshot of menu toggle: `artifacts/verification/ladsapi/shots/chk04_minimap_blocked.png`.
  - Client assertion: `Module.getByName("XaeroMinimap").isEnabled()` returns `false`.

---

### CHK-05: Forced Option & GUI Lock (Greyed out with lock tooltip)
- **Boss-Bar Task**: `task 5/12 · chk05-forced-option`
- **Setup**: Server policy forces `Crosshair Tweaks.Dot = true` (lock=true) and `1.7 Animations.1.7 sword block pose = false` (lock=true).
- **Steps**:
  1. Join server.
  2. Inspect crosshair in game.
  3. Open Settings -> Crosshair Tweaks.
  4. Inspect the `Dot` boolean toggle.
  5. Attempt to click or change it.
- **Expected Result**:
  - Crosshair renders a center dot on screen.
  - In GUI, the `Dot` checkbox/toggle is rendered in disabled/locked state (dimmed/greyed).
  - Padlock icon appears next to the label.
  - Mouse hover shows tooltip: `"Locked by server"`.
  - Clicking does not toggle the value.
- **Proof**:
  - Screenshot of Crosshair menu: `artifacts/verification/ladsapi/shots/chk05_crosshair_locked.png`.
  - Harness assertion: `BoolOption[Crosshair Tweaks.Dot].get()` returns `true`, `BoolOption[Crosshair Tweaks.Dot].getOwn()` returns player's original choice.

---

### CHK-06: Clean Disconnect Reversion (Byte-for-byte config integrity)
- **Boss-Bar Task**: `task 6/12 · chk06-leave-revert`
- **Setup**: Record SHA-256 hashes of all `.json` files in client config directory prior to connection.
- **Steps**:
  1. Record pre-join config hashes: `Get-FileHash -Algorithm SHA256 <client>/config/*.json`.
  2. Connect to lobby server (policy applied: Fullbright 10%, Dot forced, Minimap blocked).
  3. Disconnect to title screen / main menu.
  4. Inspect client memory and options in singleplayer or menu.
  5. Compute post-disconnect config hashes: `Get-FileHash -Algorithm SHA256 <client>/config/*.json`.
- **Expected Result**:
  - Immediately upon disconnect, `ServerPolicy` is set to `null`.
  - In singleplayer / title menu:
    - Fullbright returns to player's own value (100%).
    - Crosshair Dot returns to player's own setting.
    - Minimap toggle is restored to player's own enabled state.
  - Every config file on disk has an **identical SHA-256 hash** before and after the session.
  - Nothing from the server policy was ever flushed to disk.
- **Proof**:
  - SHA-256 comparison table in log: `Pre-hash == Post-hash` for all files.
  - Client log: `[LadsAPI] Session reset on disconnect. ServerPolicy cleared.`

---

### CHK-07: Crash / Hard Kill Resilience (No leaked settings on next launch)
- **Boss-Bar Task**: `task 7/12 · chk07-crash-resilience`
- **Setup**: Client connected to lobby server with active server overrides.
- **Steps**:
  1. Confirm server policy active.
  2. Abruptly kill the game process: `Stop-Process -Id <ClientPID> -Force` (simulating crash / power cut).
  3. Relaunch client into title screen.
  4. Inspect settings values on startup.
- **Expected Result**:
  - Client boots cleanly with zero crash logs or corrupt configuration files.
  - Fullbright is 100%, Dot is at player's original setting, Minimap is enabled.
  - No server policy data persisted across process boundary.
- **Proof**:
  - Client boot log: `Loaded module configs successfully`.
  - Harness assertion verifying option values match player's base profile.

---

### CHK-08: Proxy Switch Lobby -> Plain (Velocity reset packet)
- **Boss-Bar Task**: `task 8/12 · chk08-proxy-switch`
- **Setup**: Client connects through Velocity proxy (`127.0.0.1:25700`) to `lobby` (:25701).
- **Steps**:
  1. Join proxy (lands in `lobby`). Verify policy active (Fullbright capped at 10%).
  2. Player executes `/server plain` (switching to backend `plain` :25702).
  3. Velocity intercepts `ServerPostConnectEvent` and sends `{"t":"reset"}` on channel `lads:api`.
  4. Client receives `reset`.
- **Expected Result**:
  - Client immediately discards `ServerPolicy`.
  - Since `plain` has no LadsClientAPI, no new `hello` or `policy` is sent.
  - Player's own settings (Fullbright 100%, Minimap enabled) are active on `plain`.
- **Proof**:
  - Velocity log: `[LadsClientAPI-Velocity] Sent reset packet to <player> on switch to plain`.
  - Client log: `[LadsAPI] Received server reset packet. Dropping active policy.`
  - In-game screenshot on plain server: `artifacts/verification/ladsapi/shots/chk08_plain_restored.png`.

---

### CHK-09: Direct Join to Plain Server (Anti-Ban Rule: ZERO lads:api packets)
- **Boss-Bar Task**: `task 9/12 · chk09-antiban-zero-leak`
- **Setup**: `plain` Paper server (:25702) running with `PacketLogger.jar` installed.
- **Steps**:
  1. Client connects directly to `127.0.0.1:25702` (bypassing proxy and lobby).
  2. Complete login, spawn into world, move around for 10 seconds, open chat, disconnect.
  3. Inspect `F:\LadsQA\ladsapi-net\plain\plugins\PacketLogger\packets.log` and `latest.log`.
- **Expected Result**:
  - Client connects as a completely standard, clean Minecraft client.
  - Client does NOT send any packet on `lads:api`.
  - Client does NOT register `lads:api` in `minecraft:register` / `REGISTER`.
  - `packets.log` remains empty (0 bytes) or non-existent.
  - Server log shows `0` unexpected payloads received.
- **Proof**:
  - `packets.log` file size = `0` bytes (or file absent).
  - Regex search on `plain\logs\latest.log` for `UNEXPECTED_PACKET` returns 0 matches.
  - Verification harness assertion: `AntiBanZeroPayloadsVerified: PASS`.

---

### CHK-10: Player Override ("Let servers change my Lads settings" = OFF)
- **Boss-Bar Task**: `task 10/12 · chk10-player-override`
- **Setup**: In client General Settings, toggle `"Let servers change my Lads settings"` to `OFF`.
- **Steps**:
  1. Set toggle to OFF.
  2. Connect to lobby (:25701).
  3. Server sends policy with forced `Crosshair Tweaks.Dot = true` and restriction `Fullbright.Brightness.max = 10`.
  4. Inspect client response and active settings.
- **Expected Result**:
  - Forced option changes are **refused**:
    - Crosshair Tweaks Dot remains at player's own setting (NOT forced).
  - Restrictions / Limits **still apply**:
    - Fullbright Brightness is STILL capped at 10% (limits cannot be refused).
    - XaeroMinimap is STILL blocked (blocks cannot be refused).
  - Client sends `ack` with `refused: ["Crosshair Tweaks.Dot", "1.7 Animations.1.7 sword block pose"]`.
- **Proof**:
  - Server log: `Received ack rev=1 from <player> with refused=[Crosshair Tweaks.Dot, 1.7 Animations.1.7 sword block pose]`.
  - Client log: `[LadsAPI] Refused 2 forced options due to user preference`.

---

### CHK-11: Lads Badge in Tab & Nametag (Dual Client Presence Test)
- **Boss-Bar Task**: `task 11/12 · chk11-badge-render`
- **Setup**:
  - Client A (`LadsPlayer1`): Lads Client.
  - Client B (`LadsPlayer2`): Lads Client.
  - Client C (`VanillaPlayer`): Vanilla / non-Lads client.
- **Steps**:
  1. All three clients join lobby (:25701).
  2. Server sends `presence` message: `{"t":"presence","full":true,"add":["<UUID-A>", "<UUID-B>"]}`.
  3. Client A presses Tab to view player list overlay.
  4. Client A looks directly at Client B and Client C in the world.
- **Expected Result**:
  - In Tab list:
    - Client B has the 64x64 anime girl logo badge rendered cleanly next to their name.
    - Client C has **NO** badge rendered.
  - Overhead nametag:
    - Client B has the anime girl badge rendered to the left of their nametag above their head.
    - Client C has a standard vanilla nametag without badge.
- **Proof**:
  - Screenshot of Tab overlay: `artifacts/verification/ladsapi/shots/chk11_tab_badge.png`.
  - Screenshot of nametags: `artifacts/verification/ladsapi/shots/chk11_nametag_badge.png`.
  - Harness image diff confirming badge texture pixels present on Client B and absent on Client C.

---

### CHK-12: Multi-Version Acceptance Matrix
- **Boss-Bar Task**: `task 12/12 · chk12-matrix-run`
- **Setup**: Run verification matrix across all 4 client versions against all server targets.
- **Steps**:
  1. Execute automated verification runner across the matrix:
     - 1.8.9 Client -> Velocity (:25700) -> Lobby (:25701)
     - 1.20.1 Fabric Client -> Velocity (:25700) -> Lobby (:25701)
     - 1.20.1 Forge Client -> Velocity (:25700) -> Forge (:25704)
     - 26.3 Fabric Client -> Velocity (:25700) -> Fabric (:25703)
- **Expected Result**:
  - All clients connect successfully without kick, disconnect, or handshake exception.
  - All 11 preceding checks pass on every client version.
  - Zero packet format errors, zero ClassCastExceptions, zero memory leaks.
- **Proof**:
  - Summary matrix report in `artifacts/verification/ladsapi/matrix-results.log`:
    ```text
    Matrix Run Summary:
    1.8.9  Forge   | Velocity -> Paper Lobby  | PASS
    1.20.1 Fabric  | Velocity -> Paper Lobby  | PASS
    1.20.1 Forge   | Direct   -> Forge 1.20.1 | PASS
    26.3   Fabric  | Direct   -> Fabric 26.3  | PASS
    Total: 12/12 checks OK across 4 client versions.
    ```

---

## 4. Operator Runbook (Running QA on ARASH-LEGION)

To execute this QA suite on ARASH-LEGION:

1. **Setup Network**:
   ```powershell
   # In PowerShell on Legion (or via legion-qa runner)
   .\LadsClientAPI\qa\setup-network.ps1 -Root "F:\LadsQA\ladsapi-net" -Jar "path\to\LadsClientAPI.jar"
   ```

2. **Start Network**:
   ```powershell
   .\LadsClientAPI\qa\start-network.ps1 -Root "F:\LadsQA\ladsapi-net"
   ```

3. **Run Verification Harness**:
   ```powershell
   # Harness runs with QaBar enabled, outputting live progress and results.log
   .\legion-qa.ps1 -Name ladsapi-acceptance -Source $env:TEMP\wla-qaprep `
       -HarnessArgs '--scenario ladsapi --network "127.0.0.1:25700"' -Wait
   ```

4. **Stop Network**:
   ```powershell
   .\LadsClientAPI\qa\stop-network.ps1 -Root "F:\LadsQA\ladsapi-net"
   ```
