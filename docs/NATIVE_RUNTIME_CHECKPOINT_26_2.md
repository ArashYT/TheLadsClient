# Native runtime release checkpoint — 10 September 2026

The final Minecraft 26.2 / Java 25 release Core is **907DDE926E534C0144F4F7F4303BEF0EA5340CD61BC7F89DD4265F8FCF6351D8** (SHA-256). Both its complete world run and separate retained-settings run passed. The exact JAR, production manifest, logs and actual world/menu frames are retained in `artifacts/verification/native-release-checkpoint`.

The actual catalog is **48 native modules, 17 backed by external engines, and one unavailable entry: Exordium**. The production pack has **27 upstream JARs plus Core**. Top-level dependencies and catalog modules are different counts.

| Actual game-runtime checks | Passed |
| --- | ---: |
| Combined native world features | 226 |
| Raised layout, editor resize and old-layout migration | 52 |
| AutoReconnect controls, lifecycle and mocked target dispatch | 41 |
| Dynamic FPS title policies and transformed hooks | 42 |
| SignalLoss geometry, options, commands and isolated config IO | 26 |
| Durability styles, filters, colors and text extraction | 34 |
| Paper Doll actual player, equipment, model state and opacity | 19 |
| Food sprites, tooltip factory and master switch | 14 |
| Pinned JEI food-component conversion | 6 |
| RenderScale world attachments and GPU sampling | 61 |
| Player-list and numeric ping rendering | 42 |
| Crosshair geometry, pipelines, contexts and drawing-editor handlers | 53 |
| Screenshot gallery, textures, file actions and settings | 38 |
| Actual integrated-server XP merging, pickup, Mending and persistence | 21 |
| Lazy narrator lifecycle and actual constructor integration | 13 |
| Retained-engine settings changed, read back and restored | 250 |

The combined 226 already includes 47 food behavior checks, 44 Dynamic FPS world checks and 41 shulker parity checks. Counts overlap and must not be summed into a unique-test total. The separate Java suite passed 252 targeted tests; the launcher suite passed 105 tests. Gallery file handling also passed 22 isolated IO checks. The five settings-probe skips are engines with no available mutable control: FerriteCore, ImmediatelyFast, JEI, Lithium and ScalableLux.

The integrated server delivered all three actual food payloads: saturation, exhaustion and natural regeneration. RenderScale completed six stages: half-resolution linear and nearest sampling, 150% supersampling, disabled scaling, native 100%, and bounded dynamic resolution. The GUI remained 1280×720 while world targets changed. GPU readbacks checked coverage and sampling; these are not comparative FPS benchmarks.

Minecraft opened the exact existing isolated save through its own API, rendered the real mods menu for 207 completed frames before its capture, then saved all dimensions on exit. Windows remained locked. The runner requested the menu only after every world/title probe passed. Physical input is separate; earlier synthetic Right Shift checks passed through the real keyboard handler. The gallery probe similarly used synthetic F10 through the handler. OS open/copy actions and reconnect/Discord transport targets were mocked; no authenticated remote session or Discord publication is claimed.

Runtime testing fixed two production integration defects: cancellable tooltip-return hooks could suppress the food callback, and inventory badges hooked an overload bypassed by normal item rendering. It also corrected isolated fixtures for 26.2 world-bound item components, creative food eligibility, text extraction, the exact QA save name, and empty-world-list navigation. Vanilla opens the create-world flow for an empty list; the reconnect probe now supplies an unresolved local summary future to test its actual intended parent without creating or joining a world. That final change affects only the opt-in probe.

Malformed-file exceptions in the logs are expected recovery fixtures; they precede explicit passing checks. Offline-account service errors are not authentication evidence. The gallery's bounded two-worker executor limits concurrent image decoding, and image/thumbnail jobs compose futures without waiting for another task on that pool.

Ten formerly managed feature JARs are retired in favor of native implementations: Custom Crosshair, Durability Tooltip, Raised, Dynamic FPS, AppleSkin, Paper Doll, AutoReconnect, TabTweaks, Screenshot Viewer and Clumps. Their external-presence gates still preserve ownership for manually installed upstream mods. The installer retires only its own hash-matching files. See [the module inventory](NATIVE_MODULES_26_2.md) and [reference parity audit](NATIVE_PARITY_AUDIT_26_2.md).

Exordium remains unsupported: the official Modrinth API returned no Fabric 26.2 release on this date. Its separate HUD-caching engine is not implemented by frame limiting or resolution scaling. Evidence is retained in `native-ports-checkpoint/exordium-26.2-query.json`; the [upstream releases](https://github.com/tr7zw/Exordium/releases) state compatibility limits. Microsoft registration/live login remains deferred; physical keyboard input, audible behavior and arbitrary third-party mod combinations remain unverified.

Earlier checkpoints remain intact: `native-ports-checkpoint` (22890C4E), `tab-tweaks-checkpoint` (3D898E55), `native-final-attempt1` (1866A5ED, failed QA name guard), `native-complete-checkpoint` (AA871278), and `native-final-checkpoint` (52D16AD4, complete world pass). The historical 24FAD43B installation's 108 native and 290 external-settings checks are preserved separately. They are not substituted for this release's results.
