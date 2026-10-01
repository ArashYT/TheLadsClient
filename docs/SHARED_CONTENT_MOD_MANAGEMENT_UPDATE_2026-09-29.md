# Shared content, GoodMC removal and mod management — implementation record (2026-09-29)

Branch `feat/1.2.3-shared-content-mod-management`, based on the release commit `8015c19` (v1.2.2). All changes are
uncommitted in the working tree; nothing was pushed or published. Launcher version is now 1.2.3; Core's own
`mod_version` is unchanged (1.2.0). User-facing behaviour is documented in `docs/SHARED_CONTENT_AND_MODS.md`; release notes
are in `docs/releases/1.2.3.md`.

## What changed

| Area | Result |
| --- | --- |
| A. Shared content | `saves`, `resourcepacks`, `shaderpacks` of every profile are NTFS junctions to `%APPDATA%\.minecraft\…` (no admin/Developer Mode). `servers.dat` is read and written in place by LadsCore on all four versions (ServerList `gameDirectory` redirect + per-entry merge under a shared lock), with a three-way launcher fallback when LadsCore is disabled. Migration is inventory-first, collision-safe, resumable and reported. The isolation option now only separates `options.txt`/keybinds. |
| B. GoodMC | Removed from the 26.2/26.3 manifests, the generator (`REMOVED` + `retired` array + self-test), coverage JSON and docs. The installer retires launcher-owned copies (enabled, disabled, legacy or original name) to `.lads-mod-cache\retired\goodmc\`; user copies stay. LegacySwing untouched. |
| C. Inventory | One reconciled inventory (files, disabled files, selected manifest, other versions' manifests, receipts, choices, LadsCore modules, embedded jars, platform) on the launcher Mods page and a new in-game Installed mods view, with the six filters, search incl. children, Reset, and counts computed from files. |
| D. File names | Pack jars use the manifest `fileName`; legacy `lads-<id>.jar[.disabled]` renamed with identical bytes and suffix inside the installer transaction; old-pin disabled copies resolved via Modrinth's hash lookup (offline: keep name). Ownership is by receipt hash, not name. |
| E. Toggles | Per-profile `lads-mod-state.json` (stable Fabric id + project id fallback; absent key = keep disk state). Launcher and in-game toggles with dependency plans, Restart required while running, application before the next start in every launch path, LadsCore disable + launcher recovery, embedded libraries → parent action, platform rows locked. |

Also fixed along the way: hidden launcher list entries (version filter + strict JSON parse), the always-empty server
picker (GZip on uncompressed NBT), Update buttons re-enabling or replacing pack mods, a 26.2/26.3 crash with Tooltips TXF on
hidden tooltips (masked by GoodMC), and the 26.3 title label showing 26.2.

## Files changed (139 files, +15,611 / −2,067, excluding `artifacts/` and `graphify-out/`)

- Launcher services (new): `SharedContentService`, `SafeFileOps`, `Nbt`, `GameSession`, `RunningGameMarker`, `LockFiles`,
  `FabricModMetadata`, `ModPreferences`, `ModInventoryService`, `ModInventoryView`, `ModStateService`, `UserModFiles`,
  `PackwizIndex`.
- Launcher (modified): `ClientModInstaller`, `BundledModInstaller`, `ProfileService`/`IProfileService`, `PathService`/
  `IPathService`, `LaunchService`, `ServiceTestSuite`, `MinecraftServerListReader`, `MainWindow.axaml(.cs)`, `Program.cs`,
  `LauncherSettings.cs`, `Views/GalleryModsView.axaml(.cs)`, `ViewModels/ProfilesViewModel.cs`, `TheLadsLauncher.csproj`
  (version), `game-mods/26.2|26.3/client-mods.json`.
- Launcher tests: 13 new test files plus updates to three existing ones (341 tests, baseline 143).
- LadsCore common (new): `shared/{SharedContentPaths, ServerListMerge, FileLocks, SharedContentQa}`,
  `mods/{ModStateStore, LoadedMod, InventorySnapshot, ModInventoryModel, ModDependencyPlanner, CoreCatalogExporter}` + tests;
  modified `LadsSettingsScreen` (Installed mods view), `LadsGameBridge`, `ModuleSupport`, `ModsMenuTest`.
- LadsCore per version: `ServerListSharedMixin`, `ServerListAccessor`, `ServerDataAccessor` (1.21.11, 26.x), shared-content
  probes, bridges (`loadedMods`), title/tick hooks, `LadsSettingsScreen26/12111` settings links, 26.x `fabric.mod.json`
  (`fabric-api` dependency), `ItemTooltipMixin` fix, `GlobalScreenshots` root, QA probe adjustments, v1_21_1 `build.gradle`
  (MixinExtras compile-only) and `fabric.mod.json` (loader ≥ 0.16.9).
- Verification harness `TheLadsLauncher.Verification/Program.cs`: sandbox global root, prepare/installers/inventory like the
  launcher, `--set-mods`, `--show-mods`, `--expect-core-disabled`, Fabric loaded-list parsing, trip-wire on the real
  `.minecraft`, per-run evidence.
- Data/tools/docs: `tools/sync_instance_mods.py`, `tools/test_sync_removed.py`, `docs/mod-coverage.json`,
  `docs/MOD_COVERAGE.md`, `docs/CLIENT_26_3.md`, `docs/SHARED_CONTENT_AND_MODS.md`, `docs/releases/1.2.3.md`, this file.

## Evidence

All QA used `artifacts\verification\*` game folders and the sandbox root `artifacts\verification\global-sandbox`; a harness
trip-wire compared the real `%APPDATA%\.minecraft` before and after every run and never changed. Full logs, JSON, listings and
screenshots: `artifacts\verification-1.2.3\` (`RESULTS.md`, `AUDIT.md`, `final\`). Before-state: `artifacts\inventory-before-1.2.2\`.

| Check | Result |
| --- | --- |
| Launcher tests (`dotnet test … -c Release`) | 341 passed, 0 failed (baseline 143) |
| Core common tests (`:common:test`) | 433 run, 2 failed — the same 2 pre-existing failures as the untouched baseline (HudHotPathTest ArmorHUD, M3ChallengerTest defaultRequire) |
| Core `build deploy -x test`, launcher and Verification builds | succeeded; launcher 0 warnings |
| 26.3 full in-game (final code) | native feature 248/248, HUD editor 50/50, shared content 18/18 incl. stale-writer, menu + Installed mods + HUD captures; Fabric list 239 ids, `goodmc` absent |
| 26.2 requested-features run (final code) | 25/25 requested features, HUD editor 50/50, shared content 18/18; listed the 26.3 world without opening it (downgrade flagged) |
| 1.21.11 / 1.21.1 title runs (final code) | shared content 18/18 each; saw the 26.3-created world (not opened), pack, shader pack and server |
| Toggle cycle (26.3) | disabled ids absent from Fabric's loaded list; in-game request applied before next start; cascade `cloth-config` → `effectual, gammautils, moreculling`; re-enable restored all 87 jars byte-identical |
| LadsCore off/on | no Core init while off, fallback server copy, recovery from the launcher Mods page, files byte-identical afterwards |
| Launcher previews | Mods counts equal the files (`87 enabled · 0 disabled · 0 pending · 21 unavailable`), identical to the in-game counts line; folder buttons point at the shared root |
| Packaging (`Publish-Release.ps1 -PrepareOnly`) | local 1.2.3 Setup/Portable/nupkg built (unsigned, not uploaded); packaged Core jars equal the verified jars |

## Before/after inventory

- **File names (26.3)**: 88 files before — 87 `lads-<id>.jar` + `theladscore.jar`. After — 86 original upstream names + Core,
  and `lads-goodmc.jar` retired. Full mapping: `artifacts\verification-1.2.3\before-after-26.3-filenames.csv`. Examples:
  `lads-sodium.jar → sodium-fabric-0.9.2+mc26.3.jar`, `lads-iris.jar → iris-fabric-1.11.6+mc26.3.jar`,
  `lads-resourcify.jar → Resourcify (26.3-fabric)-1.8.7.jar`, `lads-framepacer.jar → framepacer-1.2.1-[26.1-26.4]-multiloader.jar`.
  26.2 QA: 91 renamed + GoodMC retired; 1.21.11 QA: 27 renamed + 74 installed.
- **Launcher list (26.3)**: before, 70 of 88 jars visible — 18 hidden (asynclogger, clientsort, entity_model_features,
  entity_texture_features, essential-container, euphoria_patcher, fabric-language-kotlin, fastershadowmapper, ferritecore,
  framepacer, goodmc, jasione, ksyxis, netprodis, no-resource-pack-warnings, nopackcompatcheck, quick-pack,
  serverpingerfixer); no pending, unavailable, embedded, LadsCore-module or platform rows. After, 164 rows: all 87 jars,
  21 unavailable with reasons, 53 Lads modules, platform rows, and 207 embedded libraries under their parents
  (filters All 164 · Lads 54 · Third-party 107 · Enabled 97 · Disabled 42 · Libraries 43).
- **In-game menu**: before, only the 52 built-in Lads modules; no third-party mod, library or LadsCore row. After, the native
  catalog is unchanged and the new Installed mods view lists 376 rows on 26.3 (counts line `87 enabled · 0 disabled ·
  0 pending · 21 unavailable · 53 Lads modules · 207 embedded libraries`).
- **GoodMC**: before, shipped on 26.2/26.3 and loaded (`goodmc 6.0.1`); after, absent from both manifests and from Fabric's
  loaded list in every run, launcher-owned copies in `.lads-mod-cache\retired\goodmc\`.

## What migration did (QA sandbox)

26.3/26.2/1.21.11 QA worlds (`Client QA 26_3`, `Client QA 26_2`, `New World`) moved into the shared saves; profile folders
became junctions; identical items went to `.lads-shared-duplicates`, differing names were kept side by side, Euphoria
Patcher output was kept aside, empty profile server lists were moved to the migration backup and the shared list kept all
entries. A second run changed nothing. The user's real profiles have **not** been migrated yet — that happens on the first
start of the 1.2.3 launcher (real-data facts to expect: one world name exists in three different copies and will be kept as
three worlds; 2 × 145 resource packs are identical copies and become backups; the empty 19-byte profile/shared server lists
add nothing to the 30-server global list).

## Limitations and follow-ups

- Not verified at runtime: two separate game processes saving the server list at the same instant (verified with separate
  ServerList objects in one game), the real Recycle Bin warning dialog, GUI clicks (automated equivalents used), migration of
  the real `%APPDATA%\.minecraft`, and the broader 26.2 suites (the 1.2.2 nametag/crosshair/render-scale failures remain).
- Fixed: a fresh 1.21.1 profile crashed once in sodium-extra 0.9.0 (pinned since 1.2.2; `Map.replaceAll` on a fastutil
  8.5.12 map in `FogSettings.sanitize`). The pin is now 0.9.1 (`taFAlSOP`), the newest 1.21.1 build that accepts Sodium
  0.8.12-beta.1 (0.9.2+ need Sodium >=0.8.12). Fresh-folder title run passes:
  `artifacts/verification-1.2.3/sodiumfix-1.21.1/20260929-121756-game-1.21.1-sodiumfix`.
- GoodMC stored `attack_speed` base 32767 in the player data of worlds it ran in. On 26.2/26.3, when the integrated server
  (singleplayer or LAN host) loads a player whose base is exactly 32767 and GoodMC is not installed, LadsCore resets that one
  attribute to its default (4.0), the same as `/attribute … minecraft:attack_speed base reset`, and logs it
  (`GoodMcAttackSpeedReset`, `GoodMcResidue`). Remote servers are not touched. Verified in game:
  `artifacts/verification-1.2.3/goodmc-attack-speed/20260929-124233-game-26.3-title` ("reset LadsQA's attack_speed base
  from 32767.0 to 4.0"). GoodMC config files are left in place.
- `tools/resolve_client_mods.py` was a stale second generator with no callers; it is deleted (commit `dacac66`, merged).
- Process notes: one UI-integration agent ran a read-only Mods preview against the real 26.3 profile with an outdated build
  (5 lines appended to `.theladsclient\logs\launcher_debug.txt`; accounts, settings and mods unchanged; the screenshots were
  deleted); the preview guard now refuses real folders. A verification agent ran `graphify update .`, which created
  `graphify-out/` (it did not exist before); it has been refreshed for the final code and can be deleted if unwanted.
