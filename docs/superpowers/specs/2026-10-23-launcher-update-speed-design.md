# Launcher updates: small, fast, Lads-styled (design)

Date: 2026-10-23. Owner request: remove the Velopack "Installing Update" box (the OK button),
show a Lads-style screen instead, make updates compressed, run the installer at high priority
only while installing/updating, and make updating and installing faster.

## Why

Every update today downloads the full 240 MB package and then shows Velopack's stock Windows
dialog (title "The Lads Client Update", progress bar, OK button that does nothing). It looks
like a different program and the download is far larger than what actually changed.

## Measured facts (2026-10-23, real release packages in `artifacts/release-check-*`)

| | Today (full) | Velopack delta |
|---|---|---|
| 1.9.16 → 1.9.17 | 240 MB | 0.5 MB |
| 1.9.15 → 1.9.17 | 240 MB | 14.4 MB (.NET runtime patch changed) |

- `Publish-Release.ps1` packs into an empty `$packages` folder, so `vpk pack` never finds a
  previous release and never makes a delta. Deltas are on by default (`--delta BestSpeed`).
- Rebuilding a full package from base + delta (`vpk delta patch`) took 17 s of CPU on Arash-PC
  and the result is **not byte-identical** to the published full package (different SHA-256).
- `UpdateSignature` signs the SHA-256 of the full `.nupkg` file bytes, so a delta-rebuilt
  package fails our check today.
- The rebuilt package has the same 608 entry names and every entry's bytes are identical
  (including `[Content_Types].xml`, `_rels/.rels`, the nuspec, `setup.ico`); only zip
  container bytes differ. The content hash below matches on rebuilt vs real 1.9.17.
- Installed layout: `%LOCALAPPDATA%\TheLadsClient\{Update.exe, current\, packages\}`;
  `packages\` keeps the current full package, which Velopack uses as the delta base.
- Package makeup (1.9.17): 608 files, 374 MB raw. 5 Core jars ≈ 143 MB zipped (26.2 + 1.21.11
  ≈ 57 MB). `UseWindowsForms` ships the whole WinForms + WPF runtime (~60 MB raw).
- Velopack 1.2.158 has `UpdateManager.WaitExitThenApplyUpdates(asset, silent, restart, restartArgs)`;
  `silent: true` shows no Velopack dialog. Its OK button cannot be removed any other way.

## Goals

1. Updates download as deltas (a few MB) whenever the player is one or a few versions behind.
2. No Velopack dialog during updates. A Lads screen stays on screen the whole time, from
   "Installing" until the new launcher's startup screen appears: no gap, no jump.
3. High priority only while updating/installing, back to Normal automatically afterwards.
4. First install looks Lads-branded and is no slower than today.

## Non-goals / anti-goals

- Never raise priority while a game may be running (the background update timer runs while
  players are in-game). Stutter in-game would be a failure even if updates got faster.
- Never weaken update signing. A delta must not become a way to apply unsigned content.
- Never block or break an update because the bridge window failed. Worst case = today's gap.
- No fork of Velopack.
- Not in scope unless the owner says so: dropping the 26.2 / 1.21.11 Core jars, removing
  `UseWindowsForms`, splitting shared assets out of the Core jars.

## Design

### 1. Delta updates

- `Publish-Release.ps1`: before `vpk pack`, seed `$packages` with the previous release
  (`dotnet vpk download github --repoUrl https://github.com/ArashYT/TheLadsClient --channel win -o $packages`)
  so `vpk pack` writes `TheLadsClient-<ver>-delta.nupkg`. Upload the delta with the other
  assets; do **not** re-upload the previous full package. The uploaded `releases.win.json`
  must let clients find both the new full and the new delta (verify how `GithubSource`
  resolves entries; strip entries for files not in this release if needed).
- Keep `--delta BestSpeed` (BestSize gains little at 0.5–14 MB).
- **Owner decision (2026-10-23): smallest download wins.** Deltas everywhere, startup path
  included; a few extra seconds at startup on very fast internet are accepted. No
  speed-based switching to the full package. Keep Velopack's default
  `MaximumDeltasBeforeFallback` unless the timing table shows a long chain is absurd.

### 2. Signing that survives deltas

- `lads-update.json` gains `contentSha256`: SHA-256 over the full package's entries, one line
  `"<entry name>\t<sha256 hex of entry bytes>\n"` per entry, sorted ordinal by name. Duplicate
  entry names = invalid package.
- `UpdateSignature.Verify` accepts the package if the signed file `sha256` matches **or** the
  signed `contentSha256` matches. Manifests without `contentSha256` keep working (file hash only).
- `Sign-Release.ps1` writes both values. One test proves the C# and PowerShell content hashes
  agree, and that a delta-rebuilt package verifies by content while a tampered one fails.
- (Claude's call) `Sign-Release.ps1` refuses to sign until it has rebuilt the full package
  from the draft's delta + the previous full (local `artifacts/release-check-<prev>` copy,
  download only if missing) and the `contentSha256` matches. A broken delta never ships.
- (Claude's call) Client fallback: if a delta-rebuilt package fails the content check, delete
  it and download the full package once (`MaximumDeltasBeforeFallback = 0` for that retry)
  instead of retrying the delta forever.

**Rollout constraint:** 1.9.17 and older launchers only know the file hash; if they are offered
a delta they rebuild, fail verification, delete it and retry forever. So the **first release
carrying this code ships no delta**. Deltas start with the release after it. Old clients jumping
several versions have no delta chain from their version and fall back to the full package
(confirm in the harness).

### 3. Seamless Lads update screen ("bridge")

The launcher must exit before Velopack swaps `current\`, so a separate tiny process holds the
screen.

- New project `TheLadsLauncher.UpdateBridge`: WinForms on .NET Framework 4.x (built into every
  Windows version the launcher supports, so no runtime ships with it; target tens of KB).
  PerMonitorV2 manifest so it positions in physical pixels. Published next to the launcher
  as `LadsUpdateBridge.exe`.
- Apply sequence (same for the startup auto-update and the title-bar Update button):
  1. Launcher switches to its startup overlay in the "Installing v<x>" state (100%).
  2. After that frame renders, it saves the window to a PNG and notes window bounds (px),
     window state and the progress bar's rect.
  3. Copies the bridge exe to `%TEMP%\TheLadsClient-update\` (files in `current\` get replaced)
     and starts it with: png path, bounds, progress rect, root dir, reduced-motion flag.
  4. Waits up to 2 s for the bridge's "shown" named event, then calls
     `WaitExitThenApplyUpdates(asset, silent: true, restart: true, restartArgs)` and exits.
     `restartArgs` carry the bounds/state so the new launcher opens exactly where the old one
     was (matters when the user moved/resized before clicking Update).
- Bridge window: borderless, same bounds, draws the PNG 1:1 plus a shine sweeping across the
  progress bar (same style as the Update button shine; off when reduced motion). Shows in the
  taskbar with the Lads icon so the taskbar button doesn't blink out.
- New launcher: on its first rendered startup frame sets named event
  `Local\TheLadsClient.LauncherShown`; the bridge fades out (~150 ms) and exits.
- Failure handling (refined while planning): the bridge waits while any `Update.exe` under the
  app root is alive, then gives the new launcher 15 s to signal; hard cap 10 min. If nothing
  signalled and no launcher is running, it starts the root stub `The Lads Client.exe` (after a
  failed silent apply that reopens the old version, which retries later). If the bridge can't
  start or doesn't signal within 2 s, the launcher applies anyway.
- The first update *into* the release carrying this code still shows Velopack's box once
  (the old launcher runs that apply).

### 4. Priority only while updating

- Bridge: while alive, raises every `Update.exe` process whose path is under the app root to
  `High` (poll ~250 ms). Ends with the apply; nothing to restore.
- Launcher: raises itself to `High` only for the startup-screen download → delta rebuild →
  verify (CPU-heavy, nothing else running yet), restores `Normal` in `finally`.
- (Claude's call) The background timer path (launcher open, a game may be running) runs the
  download → delta rebuild → verify at `BelowNormal`, restored in `finally`. The ~17 s rebuild
  must never cost game frames.
- New launcher sets its own priority to `Normal` at startup (guard: Windows does not pass
  `High` to child processes, but don't rely on how Velopack starts it).
- First install (`Setup.exe`) runs before any of our code, so its priority can't be changed
  without forking Velopack. Not done.

### 5. Branded installer

- `vpk pack --splashImage <Lads splash> --splashProgressColor <Lads red>`: splash in launcher
  style (dark `#17181B`, Lads logo). The builder picks the exact art from the launcher assets.

## Verification

- Extend `tools/Test-AutomaticUpdate.ps1` (portable layout, never the real install in
  `%LOCALAPPDATA%\TheLadsClient`): pack v1 → v2 (no delta) → v3 (delta) and prove:
  delta downloaded (not full), content signature passes, tampered delta rejected, no Velopack
  dialog, bridge window present continuously until the new launcher's window (screenshots
  every 250 ms), Update.exe at High during apply, relaunched launcher at Normal, old-client
  path falls back to full.
- Timing table on Arash-PC and Legion with the real packages (1.9.17 → candidate): full vs
  delta end to end (download + rebuild + apply).
- QA sandbox rule applies (THELADS_DIR / LADS_GLOBAL_MINECRAFT_DIR set; never the owner's
  real install or `.minecraft`).

## Left to the builder

Bridge drawing details and fade timing, exact splash art, `MaximumDeltasBeforeFallback` value,
how `releases.win.json` is trimmed, test layout.
