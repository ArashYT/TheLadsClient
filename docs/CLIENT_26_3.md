# Minecraft 26.3 client development build

## What changed

26.3 is a separate Java 25 adapter and a default launcher profile. Existing selected profiles and world paths are retained. 26.2 remains Java 25; 1.21.11 and 1.21.1 remain Java 21. All four packs use Fabric Loader 0.19.5 or newer.

The 26.3 adapter ports the native menu, configurable Right Shift binding, HUD editor, food/durability overlays, render scale, gallery, crosshair, reconnect, raised HUD, clumps and other existing native features. SDL key/button/modifier handling, text input focus, the render pipeline API and tooltip hooks were updated for 26.3. The version-specific source package retains the v26_2 name for the port; the jar's game dependency is strictly 26.3.

Threads priority controls are newly implemented in common Core for all four versions. Render, integrated-server and Worker threads have configurable priorities; disabling restores their original priorities. The module is opt-in and does not promise a performance gain or reproduce the original mod's arbitrary-thread/debug UI.

From the source instance, 26.2/26.3 use native replacements for AppleSkin, AutoReconnect, Chat Signing Hider, Clumps, Dynamic FPS and Threads. The older adapters use upstream versions except for native Threads. Sodium, Iris, Lithium and the other retained engines remain original upstream mods, with their original licenses and settings. They have not been relabeled as native.

## Modrinth installation

Each missing jar is resolved against Modrinth's version API. The installer verifies game version, Fabric loader type, project/version identity, file URL/name, size and SHA-512 before downloading. Verified cached jars work without another API request. API failures fail the installation rather than silently choosing another game version. Installs retain the existing staging, dependency checking, receipt and rollback behavior.

`tools/sync_instance_mods.py` inventories the source instance, identifies hashes through Modrinth, closes required dependencies, applies tested compatibility pins, and writes all four manifests and `docs/mod-coverage.json`. API cache entries expire after 24 hours. No source-instance jar is redistributed by this script. Mods in its `REMOVED` map (GoodMC since 1.2.3) are never surveyed or re-added, and stay listed under `retired` in the manifests; `python tools/test_sync_removed.py` checks this offline.

Run from the repository root:

```powershell
python tools/sync_instance_mods.py 'C:\Users\Arash\curseforge\minecraft\Instances\The Lads Client Dev 26.3' --write
.\Build-LadsClient.ps1 -Launcher
```

The generated pack must be launched after changing versions: Modrinth's game labels and dependency listings do not capture every Fabric mixin or version-range incompatibility. Iris pins an older Sodium than the newest add-ons on 1.21.11, so the compatible add-on releases are pinned. On 1.21.1, Iris 1.8.14 beta and its Sodium/add-on combination are required; FixBookGUI 2.0.0 avoids the unconditional missing-Amendments mixin in 2.2.0. These exceptions are recorded in the sync script.

## Coverage and limitations

See [MOD_COVERAGE.md](MOD_COVERAGE.md) for every source mod on every version. 26.3 covers 92 of the 97 remaining source mods: six native features and 86 original Modrinth jars. Five remain unavailable from verified Modrinth projects: Connectivity, Cupboard, Fast Async World Save, GPU Memory Leak Fix and Identify. GoodMC was removed from every version in 1.2.3 (it shipped on 26.2 and 26.3 before); LadsCore's LegacySwing module is unaffected. Official non-Modrinth fallback downloads have not been implemented while the user's source preference is pending.

The source CurseForge instance and its worlds were not modified. Isolated QA worlds, accounts, caches and captures are under `artifacts/verification`; upstream GUI additions remain visible where their mods provide them.

## Validation

- Java build and deploy: all four adapters, `build deploy -x test` (the repository guide requires skipping the known unrelated full-suite regressions).
- Targeted common tests: Threads priority application/restoration and ModsMenu tests passed.
- Launcher: 143 tests passed, including API metadata/hash rejection and literal-newline upstream metadata without rewriting jar bytes.
- 26.3: real isolated world run, required native probes, framebuffer menu/HUD captures and graceful shutdown passed. Combined native probe: 223 checks; render scale: 61; HUD editor: 47; screenshot gallery: 38. The captured menu and HUD frames were inspected.
- 26.2, 1.21.11 and 1.21.1: the new pinned packs downloaded/installed and reached Core initialization plus a game window. These are startup smoke checks, not full world/feature parity checks.
- QA uses offline identities. Microsoft sign-in and authenticated multiplayer were not exercised. OS screenshot open/copy and certain focus/network conditions in probes were mocked; physical keyboard focus was not a human gameplay test.

Evidence: `artifacts/mod-audit/` and `artifacts/verification/26.3-title/logs/latest.log`; captures are in that QA directory's `screenshots` folder.
