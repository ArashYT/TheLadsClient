# Client improvements researched 2026-09-29

## Selection
Compared the current Lads source and pinned mod inventories with official Lunar, Badlion, LabyMod and Prism documentation. These are established comparison clients; this is not a measured market-share ranking. Existing zoom, keystrokes, maps, armor HUD, scoreboard caching, Dynamic FPS, Sodium/Lithium/FerriteCore integrations, render scaling and in-game module favorites are not counted as new work.

The selection favors frequently useful controls, native implementation, offline operation, reversible settings and measurable reductions in repeated work. New game adapters target Minecraft 26.2 and 26.3. Older adapters must continue building; unsupported new game hooks must remain unavailable there.

## Research sources
- [Lunar features](https://www.lunarclient.com/features): integrated configurable mods and performance settings. Marketing FPS claims are not benchmarks for Lads.
- [Lunar performance guide](https://support.lunarclient.com/solutions/how-to-fix-fps-issues-in-lunar-client): HUD caching, particles, menu/unfocused frame limits.
- [Lunar launcher design](https://www.lunarclient.com/news/how-to-use-the-new-lunar-client-launcher): navigation, consistent controls and integrated launcher functions.
- [Lunar HUD module list](https://www.lunarclient.com/news/patch-notes-7): Clock, Stopwatch, Reach Display, Server Address and Item Tracker.
- [Lunar Stopwatch](https://www.lunarclient.com/news/5-essential-lunar-client-mods-to-speedrun-minecraft-like-a-pro): configurable timer and key bindings.
- [Lunar Reach Display](https://www.lunarclient.com/news/lunar-client-mods-for-the-best-pvp-experience): display hit distance; never change attack reach.
- [Lunar chat settings](https://lunarclient.dev/apollo/developers/mods/chat): timestamps and local chat conveniences.
- [Badlion Item Counter](https://www.badlion.net/wiki/item-counter-mod?lang=pl): inventory item totals in a configurable HUD.
- [Prism instance copy](https://www.prismlauncher.org/wiki/help-pages/instance-copy/): explicitly selected game settings and instance organization.
- [LabyMod changes](https://www.labymod.net/api/download): searchable settings, instance management, clearer startup errors and UI improvements.

## 25 distinct changes
| ID | Category | Addition | Acceptance |
|---|---|---|---|
| L1 | Launcher | Favorite profiles | Persisted stars, favorites first in both selectors and profile cards |
| L2 | Launcher | Profile search and version filter | Combined case-insensitive name/version search, clear empty state |
| L3 | Launcher | Duplicate profile settings | New isolated profile, copies selected local settings, leaves worlds/accounts and source intact |
| L4 | Launcher | Settings preset export/import | Portable bounded JSON format, excludes launcher account files and machine-specific launcher paths, imports into a new profile |
| L5 | Launcher | Profile preflight | Readable version/Java/memory/disk/running-state diagnostics without launching a game |
| O1 | Optimization | Ungrouped HUD layout fast path | Avoid per-frame maps, sets and stream work while retaining clamping/draw order |
| O2 | Optimization | Text width memoization | Reuse unchanged text measurements, invalidate font/resource changes |
| O3 | Optimization | Potion HUD snapshot cache | One native effect-list snapshot per player tick, reset on player/world change |
| O4 | Optimization | Resource-pack HUD snapshot cache | Reuse unchanged listings, refresh on selection changes |
| O5 | Optimization | Optional particle budget | Bound decorative particle spawns and distance, preserve important/gameplay particles |
| Q1 | In-game QoL | Copy coordinates | Rebindable action copies locally; never sends chat |
| Q2 | In-game QoL | Portal coordinate conversion | Overworld/Nether conversion with correct negative-coordinate floor |
| Q3 | In-game QoL | Low durability warning | Configurable threshold, cooldown and recovery reset, checks held item and armor |
| Q4 | In-game QoL | Inventory-full notification | Transition-based alert, no repeated per-tick spam |
| Q5 | In-game QoL | Chat timestamps | Optional local display timestamp, retains original chat styles/signature metadata |
| M1 | Native module | Clock | Local time, 12/24-hour format, movable/scalable HUD |
| M2 | Native module | Stopwatch | Start/pause/reset keys and menu actions, monotonic elapsed time |
| M3 | Native module | Item Counter | Held-item / arrows / totems / rockets inventory count |
| M4 | Native module | Reach Display | Recent client-observed hit distance, expires, never changes interaction range |
| M5 | Native module | Server Address | Current connection label, optional address masking |
| U1 | UI | Page fades | Short cancellable navigation transitions |
| U2 | UI | Animated button feedback | Hover/pressed visual feedback with keyboard focus |
| U3 | UI | Command palette | Ctrl+K, searchable pages/actions, keyboard selection, Esc close |
| U4 | UI | Launcher reduced motion | Persisted setting disables page/button/toast motion and particles |
| U5 | UI | Action notifications | Non-modal feedback, readable error messages, timed success dismissal |

## Verification standard
Build/deploy all Core adapters and build/test the launcher. Exercise profile round trips and malicious/oversized input rejection, cache invalidation and data parity, particle limits, timer state, warnings and coordinate math. Verify transformed 26.2/26.3 game hooks and actual rendered frames. Report CPU/allocation/call-count results only for what was measured; do not claim a general FPS increase from microbenchmarks. Preserve the Remastered launcher icon, complete wordmark, configurable Right Shift and existing shared-world behavior.

## Implemented and verified — September 29, 2026

All 25 rows are implemented. New game hooks are registered as native only in the 26.2 and 26.3 adapters. They are opt-in; existing controls and saved module choices remain intact.

- **Build:** `gradlew.bat build deploy -x test --console=plain` passes for 1.21.1, 1.21.11, 26.2 and 26.3. The launcher Release build has no warnings or errors.
- **Launcher:** 358 tests pass. The sandbox `--preview-productivity` run passes profile search, combined version filtering, favorite actions, settings duplication, keyboard command selection, reduced motion, persistent errors and profile diagnostics. Its actual window captures are in `artifacts/productivity-preview/captures/`, including the 960×600 minimum-size layout.
- **Core:** 90 targeted tests pass, including warning cooldowns, negative portal coordinates, timer transitions, particle quotas, text-metric invalidation and identical fast/general HUD placement at viewport edges. The unchanging-text test makes one width measurement over 1,000 frames; a changed text or font epoch triggers another measurement.
- **26.2 / 26.3:** Each final native run passes 39 new game-API checks plus one real resource-reload check. It verifies the actual controls registry, coordinate clipboard action (restoring the previous clipboard), action-bar warnings, item/offhand counts, chat styles/signatures/tags, effect and pack cache refresh, transformed particle limits, and the transformed attack-distance hook. Both runs capture completed game frames and shut down cleanly. The trip-wire confirms the user's global `.minecraft` is unchanged.
- **Evidence:** `artifacts/verification-1.2.3/improvements-final-1.3.0/20260929-183652-game-26.2-title` and `20260929-183819-game-26.3-title`. The older parent-directory name is the existing harness convention, not the tested release version.
- **Limits:** The reach probe uses an isolated synthetic target through the real attack method. These runs do not verify live-server hit acceptance, online authentication or physical input. No broad FPS gain is claimed. The general historical Core test suite is not used as a release gate; selected HUD and new-feature tests are run explicitly in addition to the mandated build/deploy command.

The native tests caught and fixed the SDL unbound-key difference in 26.3 before release. Use Minecraft's own unknown-key value for all three new bindings.
