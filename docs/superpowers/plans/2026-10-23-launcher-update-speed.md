# Launcher Update Speed Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Updates download as signed Velopack deltas, install silently behind a seamless Lads-styled bridge window at High priority, and the installer gets a Lads splash.

**Architecture:** Signing moves from "hash of the zip bytes" to "hash of the zip's entries" so delta-rebuilt packages verify. The release script seeds the previous release so `vpk pack` emits a delta. On apply, the launcher snapshots its own "Installing" screen, hands it to a tiny .NET Framework WinForms exe (`LadsUpdateBridge.exe`) that holds the screen and raises `Update.exe` to High, then calls Velopack's silent apply; the relaunched launcher signals a named event and the bridge fades out.

**Tech Stack:** .NET 8 / Avalonia 12 launcher, Velopack 1.2.158 (`vpk` pinned in `.config/dotnet-tools.json`), xUnit 2.5.3, WinForms on .NET Framework 4.6.2 (bridge), PowerShell 7 release tooling.

**Spec:** `docs/superpowers/specs/2026-10-23-launcher-update-speed-design.md`

## Global Constraints

- Work in worktree `%TEMP%\w1918upd`, branch `work/1918-update-speed` (from `main` = v1.9.17 `d5b06042`). Local commits only.
- Velopack stays 1.2.158 (NuGet + vpk). No new NuGet packages (the bridge's .NET Framework reference assemblies come from the SDK automatically).
- Owner decision: smallest download wins. Deltas everywhere, startup path included; never switch to full based on speed.
- The first release carrying this code ships **no delta** (older launchers can't verify one). A delta is built only when the previous release's `lads-update.json` contains `contentSha256`.
- Signing is never weakened: a package installs only if the release key signed its file SHA-256 or its `contentSha256`.
- Priority: launcher `High` only during the startup-screen download/rebuild/verify; `BelowNormal` for the background timer path; `Update.exe` `High` (set by the bridge); never `High` while a game may run.
- The bridge never blocks an update: if it fails, the launcher applies silently anyway.
- No console windows from anything we start (memory rule); the bridge is a `WinExe`.
- QA never touches the owner's real install (`%LOCALAPPDATA%\TheLadsClient`), `.minecraft` or `.theladsclient`: portable layouts under `artifacts/`, `THELADS_DIR` + `LADS_GLOBAL_MINECRAFT_DIR` set.
- Names fixed here: events `Local\TheLadsClient.UpdateBridgeShown` and `Local\TheLadsClient.LauncherShown`; bridge exe `LadsUpdateBridge.exe`; bridge temp dir `%TEMP%\TheLadsClient-update\`; bridge log `%TEMP%\TheLadsClient-update\bridge.log`; restart arg `--restore-window x,y,w,h,state`.
- After code changes run `graphify update .` (project rule).

## Review Focus

1. **Decimal-comma cultures (de-DE, pl-PL):** window sizes like `1280.5` must round-trip through `--restore-window` and the bridge's `--client`/`--bar` args. → Task 7 test `RestoreWindowArgsRoundTripUnderGermanCulture`; Task 6 harness runs the bridge with `de-DE` culture args.
2. **Spaces / non-ASCII in paths (`C:\Users\Jöns Ö\AppData\Local\Temp`):** bridge copy, args and image path must work. → Task 6 harness uses a temp root named `bridge test Jöns`.
3. **Slow apply (antivirus, HDD) longer than the grace:** the bridge must not relaunch while `Update.exe` is still running. → Task 6 harness case "Update.exe alive past grace".
4. **Silent apply fails and nothing reopens:** player must not be left with nothing. → Task 6 harness case "no signal after Update.exe exits → stub relaunched".
5. **High-DPI / second monitor:** the bridge must sit exactly over the old client area at 125–175 % scaling. → Task 9 screenshot strip at 150 % scaling.

---

### Task 1: Content-hash signing

**Files:**
- Modify: `TheLadsLauncher/UpdateSignature.cs`
- Create: `tools/Get-PackageContentHash.ps1`
- Test: `TheLadsLauncher.Tests/UpdateSignatureTests.cs`

**Interfaces:**
- Produces: `UpdateSignature.ContentHash(string packagePath) -> string` (uppercase hex); `record Manifest(string version, string package, string sha256, string? contentSha256 = null)`; `CreateManifest` fills both hashes; `Verify(...)` unchanged signature, accepts file **or** content match. `tools/Get-PackageContentHash.ps1 -Path <nupkg>` writes the same hex string to the pipeline.

Content hash (both implementations must produce identical bytes):

```text
for every zip entry (System.IO.Compression.ZipArchive), sorted ordinal by FullName:
    line = FullName + "\t" + lowercase-hex(SHA256(entry bytes)) + "\n"
duplicate FullName -> InvalidDataException("Duplicate entry <name> in <package>.")
ContentHash = uppercase-hex(SHA256(UTF-8, no BOM, concatenation of all lines))
```

- [ ] **Step 1: Write the failing tests** (add a helper `Zip(TestDirectory dir, string file, CompressionLevel level, params (string name, string text)[] entries)`)

```csharp
[Fact] public void ContentHashIgnoresZipContainerBytes()
{   // same entries, different order + compression level
    var a = Zip(dir, "a.nupkg", CompressionLevel.Optimal, ("lib/app/x.dll", "X"), ("y.nuspec", "Y"));
    var b = Zip(dir, "b.nupkg", CompressionLevel.NoCompression, ("y.nuspec", "Y"), ("lib/app/x.dll", "X"));
    Assert.NotEqual(Sha256(a), Sha256(b));
    Assert.Equal(UpdateSignature.ContentHash(a), UpdateSignature.ContentHash(b)); }
[Fact] public void ARebuiltPackageVerifiesByContent()          // manifest from a; Verify(...) on b (renamed to a's file name) passes
[Fact] public void AChangedEntryFailsBothHashes()             // b has "X2" -> Verify throws InvalidDataException containing "differs from the signed one"
[Fact] public void DuplicateEntryNamesAreRefused()            // ContentHash throws InvalidDataException containing "Duplicate entry"
[Fact] public void AnOldManifestWithoutContentHashStillVerifiesByFileHash() // manifest JSON {version,package,sha256} only: exact file passes, rebuilt b throws
[Fact] public void PowerShellContentHashMatchesCSharp()       // runs: pwsh -NoProfile -NonInteractive -File <repo>/tools/Get-PackageContentHash.ps1 -Path a ; Assert.Equal(C#, stdout.Trim())
```

- [ ] **Step 2: Run** `dotnet test TheLadsLauncher.Tests/TheLadsLauncher.Tests.csproj -c Release --filter FullyQualifiedName~UpdateSignatureTests` → new tests FAIL (`ContentHash` missing).
- [ ] **Step 3: Implement** `ContentHash`, the extended `Manifest`, `CreateManifest`, and `Verify` (file hash first; if it differs and `contentSha256` is set, compare `ContentHash`; otherwise keep today's message). Implement `tools/Get-PackageContentHash.ps1` (`param([Parameter(Mandatory)][string]$Path)`) with the same algorithm. The pwsh test finds the repo root by walking up from `AppContext.BaseDirectory` until `tools/Get-PackageContentHash.ps1` exists; start pwsh with `CreateNoWindow = true`.
- [ ] **Step 4: Run** the same filter → all `UpdateSignatureTests` PASS.
- [ ] **Step 5: Real-package check:** `pwsh tools/Get-PackageContentHash.ps1 -Path artifacts/release-check-1.9.17/TheLadsClient-1.9.17-full.nupkg` equals the result for a package rebuilt with `dotnet vpk delta patch` from 1.9.15 + a generated delta (commands in the spec). Expected: identical hashes.
- [ ] **Step 6: Commit** `feat(updater): sign package content so delta-rebuilt packages verify`

### Task 2: Sign-Release writes the content hash and gates the delta

**Files:**
- Modify: `tools/Sign-Release.ps1`

**Interfaces:**
- Consumes: `tools/Get-PackageContentHash.ps1` (Task 1).
- Produces: `lads-update.json` = `{"version","package","sha256","contentSha256"}` (compressed JSON, this key order).

- [ ] **Step 1:** `Push-Location` to the repo root (needed for `dotnet vpk`). Compute `contentSha256` with the helper; add it to the `[ordered]` manifest after `sha256`.
- [ ] **Step 2: Delta gate.** `gh release download $tag --pattern "TheLadsClient-$Version-delta.nupkg"`; if the draft has one:
  - previous version = `gh release view --repo $Repo --json tagName --jq .tagName` (latest published; the draft is never "latest") without the `v`;
  - base = `artifacts/release-check-<prev>/TheLadsClient-<prev>-full.nupkg` if present, else `gh release download v<prev> --pattern TheLadsClient-<prev>-full.nupkg`;
  - `dotnet vpk delta patch --base <base> --patch <delta> --output <work>/rebuilt.nupkg`;
  - `vpk delta patch` exiting non-zero, or a rebuilt content hash different from the full package's, both `throw "The v$Version delta does not rebuild the signed package; not signed."`.
- [ ] **Step 3: Verify the gate by hand** in a scratch dir with the local packages: 1.9.15 base + a 1.9.15→1.9.17 delta rebuilds to 1.9.17's content hash (pass); 1.9.16 base + the same delta → patch failure or mismatch (the throw).
- [ ] **Step 4: Commit** `feat(release): sign content hash, refuse to sign a delta that does not rebuild`

### Task 3: Delta-aware backend with full-package fallback

**Files:**
- Modify: `TheLadsLauncher/AutoUpdater.cs` (`VelopackUpdateBackend`)
- Modify: `tools/UpdateProbe/Program.cs` (constructor call only)
- Test: covered end to end by Task 8 (Velopack's `UpdateManager` is not mockable here)

**Interfaces:**
- Produces: `VelopackUpdateBackend(UpdateManager manager, UpdateManager fullOnly, Func<string, string, Task<byte[]>> fetchSigned, string publicKey)`; default ctor builds `fullOnly` from the same `GithubSource` with `new UpdateOptions { MaximumDeltasBeforeFallback = -1 }` (negative = no deltas, per Velopack docs). `public bool LastDownloadWasDelta { get; private set; }`.

- [ ] **Step 1:** In `DownloadLatestAsync`: `LastDownloadWasDelta = update.DeltasToTarget.Length > 0`. If verification fails **and** it was a delta: delete the package, log via `report("Delta update did not verify; downloading the full package…")`, re-run check + download with `fullOnly`, set `LastDownloadWasDelta = false`, verify again (failure → delete + throw, as today). Non-delta failures behave exactly as today.
- [ ] **Step 2:** Update the probe's `Backend()` to pass `new UpdateManager(feed, new UpdateOptions { MaximumDeltasBeforeFallback = -1 })` as `fullOnly`.
- [ ] **Step 3: Run** `dotnet build TheLadsLauncher/TheLadsLauncher.csproj -c Release` and `dotnet build tools/UpdateProbe/UpdateProbe.csproj -c Release` → both succeed; `dotnet test TheLadsLauncher.Tests/TheLadsLauncher.Tests.csproj -c Release` → all PASS.
- [ ] **Step 4: Commit** `feat(updater): use deltas, fall back to the full package if a rebuild fails verification`

### Task 4: Priority scopes, silent apply and the restart hook

**Files:**
- Create: `TheLadsLauncher/ProcessPriority.cs`
- Modify: `TheLadsLauncher/AutoUpdater.cs` (interface, `AutoUpdater`, `VelopackUpdateBackend.ApplyAndRestart`)
- Modify: `tools/UpdateProbe/UpdateProbe.csproj` (link `ProcessPriority.cs`)
- Test: `TheLadsLauncher.Tests/AutoUpdaterTests.cs`

**Interfaces:**
- Produces: `static class ProcessPriority { static IDisposable Scope(ProcessPriorityClass priority); }` (sets the current process, restores the previous class on dispose, swallows `Win32Exception`/`InvalidOperationException`). `ILauncherUpdateBackend.ApplyAndRestart(string[] restartArgs)`. `AutoUpdater.PrepareRestart { get; set; } : Func<string[]>?` called immediately before every `ApplyAndRestart` (null → `Array.Empty<string>()`).
- `VelopackUpdateBackend.ApplyAndRestart(args)`: `_manager.WaitExitThenApplyUpdates(pending, silent: true, restart: true, args)` then `Environment.Exit(0)`.

- [ ] **Step 1: Write the failing tests** (fake `Backend` records `Process.GetCurrentProcess().PriorityClass` inside `DownloadLatestAsync` and the args given to `ApplyAndRestart`)

```csharp
[Fact] public async Task StartupDownloadRunsHighAndOpenLauncherDownloadRunsBelowNormal()
{   var before = Process.GetCurrentProcess().PriorityClass;
    await new AutoUpdater(b).PollAsync(() => true, _ => { });                       // AskBeforeInstall = false
    Assert.Equal(ProcessPriorityClass.High, b.PriorityDuringDownload);
    await new AutoUpdater(b2) { AskBeforeInstall = true }.PollAsync(() => true, _ => { });
    Assert.Equal(ProcessPriorityClass.BelowNormal, b2.PriorityDuringDownload);
    Assert.Equal(before, Process.GetCurrentProcess().PriorityClass); }
[Fact] public async Task PriorityIsRestoredWhenTheDownloadFails()   // FailDownload = true -> priority equals `before` afterwards
[Fact] public async Task PrepareRestartRunsBeforeApplyAndItsArgsReachTheBackend()
{   // PrepareRestart returns ["--restore-window","1,2,3,4,Normal"]; PollAsync(() => false, ...) with PendingVersion set
    Assert.Equal(new[] { "--restore-window", "1,2,3,4,Normal" }, b.AppliedArgs);
    Assert.True(b.PrepareCalledBeforeApply); }
```

- [ ] **Step 2: Run** `dotnet test ... --filter FullyQualifiedName~AutoUpdaterTests` → FAIL (no `PrepareRestart`, wrong `ApplyAndRestart` arity).
- [ ] **Step 3: Implement.** Wrap only the `DownloadLatestAsync` call in `PollAsync` with `ProcessPriority.Scope(AskBeforeInstall ? BelowNormal : High)`. Call `PrepareRestart` in both `InstallNow` and `PollAsync` right before `ApplyAndRestart`. Update the existing fake backend and the probe's linked files.
- [ ] **Step 4: Run** all launcher tests → PASS; `dotnet build tools/UpdateProbe/UpdateProbe.csproj -c Release` → succeeds.
- [ ] **Step 5: Commit** `feat(updater): high priority only while updating, silent apply with restart args`

### Task 5: Release script builds deltas and the branded installer

**Files:**
- Modify: `Publish-Release.ps1:66-80`
- Create: `TheLadsLauncher/Assets/installer-splash.png`

**Interfaces:**
- Consumes: previous release's `lads-update.json` (`contentSha256` present ⇒ its launcher verifies deltas).
- Produces: `$packages` contains only this version's files: `TheLadsClient-<v>-full.nupkg`, `TheLadsClient-<v>-delta.nupkg` (when allowed), Setup, Portable, `releases.win.json` / `RELEASES` / `assets.win.json` listing only `<v>` entries.

- [ ] **Step 1: Splash asset.** Render the startup overlay with `TheLadsLauncher.exe --preview-startup <out>` (existing preview mode, off-screen) and crop/resize to about 600×340 with the logo centred on `#17181B`; save as `installer-splash.png`. Progress colour = the launcher's `LadsAccent` resource value.
- [ ] **Step 2: Delta seeding.** Before `vpk pack`: latest published tag via `gh release view --repo $repo --json tagName --jq .tagName`; download its `lads-update.json`; `$deltaAllowed = $null -ne (ConvertFrom-Json $json).contentSha256`. If allowed: `dotnet vpk download github --repoUrl https://github.com/$repo --channel win -o $packages`.
- [ ] **Step 3: Pack** with `--delta $(if ($deltaAllowed) {'BestSpeed'} else {'None'}) --splashImage TheLadsLauncher/Assets/installer-splash.png --splashProgressColor <LadsAccent hex>`. Add `TheLadsClient-$version-delta.nupkg` to `$required` when allowed.
- [ ] **Step 4: Trim** after pack, before `SHA256SUMS.txt`: delete every `*-full.nupkg` / `*-delta.nupkg` not containing `-$version-`; rewrite `releases.win.json` keeping `Assets` whose `Version -eq $version`; keep only `RELEASES` lines naming `-$version-`; assert `assets.win.json` names no other version.
- [ ] **Step 5: Verify** `./Publish-Release.ps1 -PrepareOnly -SkipCoreBuild` → (a) today the previous manifest has no `contentSha256` → no delta, only `<v>` files, and the `vpk pack` log lists the splash image; (b) with `$deltaAllowed` forced `$true` locally → delta produced and the trimmed feed lists exactly full + delta. Visual splash check: run the Setup.exe only inside Windows Sandbox (never on the owner's PC, it installs into the real `%LOCALAPPDATA%\TheLadsClient`); if Sandbox isn't enabled, skip and note it in the task report.
- [ ] **Step 6: Commit** `feat(release): delta packages, trimmed per-release feed, Lads installer splash`

### Task 6: Update bridge exe

**Files:**
- Create: `TheLadsLauncher.UpdateBridge/TheLadsLauncher.UpdateBridge.csproj`, `Program.cs`, `BridgeForm.cs`, `app.manifest`
- Modify: `TheLadsLauncher/TheLadsLauncher.csproj` (bridge copied into build + publish output)
- Modify: `Publish-Release.ps1` (assert `LadsUpdateBridge.exe` in `$publish`)
- Create: `tools/Test-UpdateBridge.ps1`

**Interfaces:**
- Project: `net462`, `WinExe`, `UseWindowsForms`, `PlatformTarget x64` (reads x64 `Update.exe` paths), `AssemblyName LadsUpdateBridge`, icon `../TheLadsLauncher/Assets/icon.ico`, manifest `dpiAwareness PerMonitorV2`.
- Command line (all numbers invariant culture, pixels):
  `--image <png> --client x,y,w,h --bar x,y,w,h --update-exe <path> --relaunch <path> --grace <seconds> [--reduced-motion]`
- Creates `Local\TheLadsClient.LauncherShown` (manual reset, unsignalled) **before** showing; sets `Local\TheLadsClient.UpdateBridgeShown` after the first paint.
- Behaviour: borderless, `ShowInTaskbar`, title "The Lads Client", bounds = `--client`; draws the PNG 1:1; unless reduced motion, a 110×scale px shine sweeps across `--bar` every 1600 ms (matches `StartupProgressShine`). Every 250 ms: set every process whose image path equals `--update-exe` to `High`. Exit paths: LauncherShown signalled → fade out ~150 ms, exit; no `Update.exe` alive for `--grace` s (launcher passes 15) or 10 min total → if no `TheLadsLauncher` process under the root is running, start `--relaunch` (missing file → log and skip); exit. Logs each step to `%TEMP%\TheLadsClient-update\bridge.log`.

- [ ] **Step 1: Write `tools/Test-UpdateBridge.ps1`** (fails until the bridge exists). Temp root `artifacts/bridge-test/bridge test Jöns/`; fake `Update.exe` = copy of `$env:SystemRoot\System32\PING.EXE` started hidden with `-n 60 127.0.0.1`; relaunch target = another PING copy named `The Lads Client.exe`; 400×300 PNG via `System.Drawing`. Cases:
  1. shown event within 2 s; window "The Lads Client" at the `--client` rect (`GetWindowRect`); fake `Update.exe` `PriorityClass -eq 'High'` within 1 s; set LauncherShown → bridge exits within 1 s; log has no relaunch.
  2. Update.exe alive past grace (`--grace 2`, ping alive 6 s): bridge still running at 4 s; after ping ends it relaunches within ~3 s (log line `relaunch`).
  3. Args formatted under `de-DE` in the caller still parse (script builds them with `[cultureinfo]::InvariantCulture`, bridge parses invariant; case 1 repeated with `--client 10,20,400,300` and current culture `de-DE`).
- [ ] **Step 2: Run** `pwsh tools/Test-UpdateBridge.ps1` → FAIL (exe missing).
- [ ] **Step 3: Implement** the bridge per Interfaces; wire `TheLadsLauncher.csproj` so `LadsUpdateBridge.exe` lands in both `bin/.../` and `dotnet publish -r win-x64` output (e.g. `ProjectReference` with `ReferenceOutputAssembly="false"` + copy target; builder's choice); `Publish-Release.ps1` throws if `$publish/LadsUpdateBridge.exe` is missing.
- [ ] **Step 4: Run** the harness → PASS (all 3 cases); `dotnet publish TheLadsLauncher/TheLadsLauncher.csproj -c Release -r win-x64 --self-contained true -o artifacts/bridge-test/pub` contains `LadsUpdateBridge.exe` (< 100 KB).
- [ ] **Step 5: Commit** `feat(launcher): Lads update bridge window that holds the screen during installs`

### Task 7: Launcher integration (snapshot, bridge, restore, signal)

**Files:**
- Create: `TheLadsLauncher/UpdateBridge.cs`
- Modify: `TheLadsLauncher/MainWindow.axaml.cs` (ctor ~:126, `Loaded` ~:280, update section ~:497-572)
- Modify: `TheLadsLauncher/Program.cs:18-20`
- Test: `TheLadsLauncher.Tests/UpdateBridgeArgsTests.cs`

**Interfaces:**
- Consumes: `AutoUpdater.PrepareRestart` (Task 4); bridge command line + event names (Task 6).
- Produces: `static class UpdateBridge` with `const string ShownEvent`, `const string LauncherShownEvent`, `bool TryStart(string snapshotPng, PixelRect client, PixelRect bar, bool reducedMotion)` (copy exe to `%TEMP%\TheLadsClient-update\`, `ProcessStartInfo.ArgumentList`, `--update-exe VelopackLocator.Current.UpdateExePath`, `--relaunch <RootAppDir>\The Lads Client.exe`, `--grace 15`; wait ≤ 2000 ms on `ShownEvent`; any exception → log + `false`), `void SignalLauncherShown()` (`EventWaitHandle.TryOpenExisting` + `Set`), `string FormatRestoreWindow(PixelPoint position, double width, double height, WindowState state)`, `bool TryParseRestoreWindow(string[] args, out PixelPoint position, out double width, out double height, out WindowState state)` (invariant culture).

- [ ] **Step 1: Write the failing tests**

```csharp
[Fact] public void RestoreWindowArgsRoundTripUnderGermanCulture()
{   using var _ = new CultureScope("de-DE");
    var text = UpdateBridge.FormatRestoreWindow(new PixelPoint(-1920, 40), 1280.5, 720.25, WindowState.Maximized);
    Assert.True(UpdateBridge.TryParseRestoreWindow(new[] { "x", "--restore-window", text }, out var p, out var w, out var h, out var s));
    Assert.Equal((new PixelPoint(-1920, 40), 1280.5, 720.25, WindowState.Maximized), (p, w, h, s)); }
[Fact] public void MissingOrBrokenRestoreWindowIsIgnored()   // no arg, "1,2,3", "a,b,c,d,Normal" -> false
[Fact] public void SignalLauncherShownWithoutABridgeDoesNothing() // no event exists -> no throw
```

- [ ] **Step 2: Run** `dotnet test ... --filter FullyQualifiedName~UpdateBridgeArgsTests` → FAIL.
- [ ] **Step 3: Implement `UpdateBridge.cs`.**
- [ ] **Step 4: MainWindow.** In the ctor set `_autoUpdater.PrepareRestart = PrepareUpdateRestart`. `string[] PrepareUpdateRestart()`: show `LauncherStartupOverlay` in the installing state (`StartupLoadingSpinner.Text = $"Installing update v{version}"`, `StartupPercentText.Text = "100%"`, `StartupProgressFill.Width` = full), `UpdateLayout()`, render the window at `RenderScaling` (`RenderTargetBitmap(new PixelSize(ClientSize * s), new Vector(96 * s, 96 * s))`) to `%TEMP%\TheLadsClient-update\snapshot.png`; client rect = `PointToScreen(0,0)` + `ClientSize * s`; bar rect = `StartupProgressFill`'s parent track via `PointToScreen` + `Bounds.Size * s`; `UpdateBridge.TryStart(...)` with `settings.ReducedMotion`; return `["--restore-window", FormatRestoreWindow(Position, Width, Height, WindowState)]`. In the ctor (not in preview modes): `TryParseRestoreWindow(Environment.GetCommandLineArgs(), ...)` → `WindowStartupLocation = Manual`, `Position`, `Width`, `Height`, `WindowState`. On `Opened`: `Dispatcher.UIThread.Post(UpdateBridge.SignalLauncherShown, DispatcherPriority.Background)`.
- [ ] **Step 5: Program.Main** right after `VelopackApp...Run()`: if the current priority is `High` or `RealTime`, set `Normal` (keeps QA's Idle/BelowNormal runs untouched).
- [ ] **Step 6: Run** all launcher tests → PASS; `dotnet build -c Release` → no warnings added.
- [ ] **Step 7: Commit** `feat(launcher): seamless update handoff to the bridge and restore window placement`

### Task 8: Update harness covers deltas, tampering and old clients

**Files:**
- Modify: `tools/Test-AutomaticUpdate.ps1`
- Modify: `tools/UpdateProbe/Program.cs`

**Interfaces:**
- Consumes: `LastDownloadWasDelta` (Task 3), content-hash manifest (Task 1), silent `ApplyAndRestart(string[])` (Task 4).
- Probe stages via `LADS_UPDATE_PROBE_STAGE`: unset = today's flow; `delta` (0.0.2 → 0.0.3 through `feed3`, must use a delta, applies); `evil-delta` (download only; must end verified on the genuine 0.0.3 with `LastDownloadWasDelta == false`); `old-client` (0.0.1, download only from `feed3`; must not use a delta).

- [ ] **Step 1: Extend the script:** pack 0.0.3 (`payload.txt` = "third package") into `feed3` after seeding it with `feed`'s 0.0.2 full + `releases.win.json` (so `vpk` makes a delta; assert `*-0.0.3-delta.nupkg` exists); sign 0.0.3 with `contentSha256` from `tools/Get-PackageContentHash.ps1`. Build `feed3-evil`: pack an "evil" 0.0.3 (payload "evil") seeded the same way, copy its delta over `feed3`'s delta name in a copy of `feed3`, fix that entry's `SHA1`/`SHA256`/`Size` in `releases.win.json`; keep the genuine full + manifest.
- [ ] **Step 2: Extend the probe** with the three stages; each writes `<stage>-result.json` `{ passed, version, delta }`; 0.0.3 checks `payload.txt == "third package"`.
- [ ] **Step 3: Script order:** existing 0.0.1 → 0.0.2 run; copy `portable` to `portable-evil`, run stage `evil-delta` there; unzip a fresh 0.0.1 portable to `portable-old`, run `old-client`; run stage `delta` in `portable` and wait for 0.0.3's result.
- [ ] **Step 4: Run** `pwsh tools/Test-AutomaticUpdate.ps1` → `PASS:` line now also lists "delta update, tampered delta fell back to full, old client used full".
- [ ] **Step 5: Commit** `test(updater): harness covers delta, tampered delta and old-client fallback`

### Task 9: Real-package QA, timing and seamlessness proof

**Files:**
- Modify: `TheLadsLauncher/AutoUpdater.cs` (QA-only source override under `#if LADS_QA_FEED`)
- Create: `artifacts/update-qa/REPORT.md` (not committed), screenshots under `artifacts/update-qa/progress/<YYYY-MM-DD>/`

**Interfaces:**
- `#if LADS_QA_FEED`: default ctor builds both managers from `LADS_QA_UPDATE_FEED` (local folder) and trusts `LADS_QA_UPDATE_KEY` (public key). Only QA builds pass `-p:DefineConstants=LADS_QA_FEED`; release builds never do.

- [ ] **Step 1:** Publish QA builds A = `1.9.90` and B = `1.9.91` (`-p:Version=... -p:DefineConstants=LADS_QA_FEED`, real Core jars; B changes `TheLadsLauncher.dll` + one Core jar), pack A, pack B seeded with A (delta), sign B with a throwaway key; install A as a portable layout in `artifacts/update-qa/portable` with `THELADS_DIR` + `LADS_GLOBAL_MINECRAFT_DIR` pointed into `artifacts/update-qa/`.
- [ ] **Step 2: Startup path:** start A with the QA feed env; capture the screen region every 250 ms from start to B's main UI. Pass = every frame shows the Lads screen (launcher or bridge), no Velopack dialog, no empty frame, B opens at the same bounds, `bridge.log` shows Update.exe raised to High, B's process priority Normal. Save the contact strip as `HHMM-bridge-startup.jpg`.
- [ ] **Step 3: Update-button path:** with B open, move it to a 150 % scaled monitor (or set 150 % on the single monitor), resized, publish C = `1.9.92`, let the timer find it (BelowNormal during download — check in Task Manager/`Get-Process`), click Update; same frame checks + same bounds after restart. Save `HHMM-bridge-button.jpg`.
- [ ] **Step 4: Timing table** (Arash-PC and Legion): A→B delta vs full (remove the delta from the feed for the full run): download time, rebuild+verify time, apply time (bridge shown → LauncherShown). Record in `REPORT.md`.
- [ ] **Step 5:** Run `pwsh tools/Test-AutomaticUpdate.ps1`, `pwsh tools/Test-UpdateBridge.ps1`, `dotnet test TheLadsLauncher.Tests/TheLadsLauncher.Tests.csproj -c Release` → all PASS. Run `graphify update .`.
- [ ] **Step 6: Commit** `chore(updater): QA-only local feed switch` and send the owner the two screenshot strips + timing table.
