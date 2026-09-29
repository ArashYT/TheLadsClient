# LadsCore HUD and module update — 2026-09-28

Primary target: Minecraft 26.3. Matching adapter changes are included for 26.2; all four supported Core artifacts compile.

## Agreed scope decisions

- Discord RPC is visibly marked **Coming soon**, with its toggle unavailable and runtime IPC disabled.
- Retain Xaero's engine for its terrain, maps, waypoints and server rules. The Lads Minimap module owns the toggle, editor placement and settings entry point; this is an integration, not a native reimplementation.
- Keep shader support. Iris crashed during Vulkan initialization in the full-pack trial (`artifacts/hud-changes-runtime-6.log`). First launch uses OpenGL with Iris; without Iris it prefers Vulkan. Existing explicit renderer preferences are retained, with a labeled OpenGL compatibility fallback while Iris is loaded.
- LegacySwing matches the supplied [showcase](https://www.youtube.com/watch?v=kTVpIkeOod4), which is the [Legacy Console Edition Swing Animation](https://modrinth.com/mod/legacy-console-edition-swing-animation) showcase. Its packaged CC0 license and attribution are included in Core resources. No separate animation mod is required.

## Implemented

| Area | Result |
| --- | --- |
| HUD editor | Settings gear for every available HUD widget, outside its text with collision-aware placement; hover-only disabled/locked labels; grouping and locking preserved. |
| Editor controls | Top/bottom/left/right docking, vertical side controls, collapse to a central reopen button, and Reset layout. Right-click offers settings, lock/unlock and horizontal/vertical/both centering. Group centers move the group; locked selections stay fixed. |
| Defaults | Reference-inspired left stats, keystrokes/CPS/ping cluster, top-center direction, right scoreboard, left effects and bottom status HUDs. Minimap starts below the toast area; paper doll sits beside it. Saved layouts remain intact; use Reset layout to adopt new defaults. |
| BossBar | Native boss-bar HUD with position/scale, bar/name visibility, maximum count, sky darkening, fog and music controls. |
| Coordinates | Centered text; labeled X/Y/Z and one axis per line by default, with horizontal layout available. Existing format/color choices remain supported. |
| Armor | Actual equipped item textures and native inventory durability decorations beside names/durability. Editor sample items also have visible durability. |
| Keystrokes | LMB/RMB CPS appear beneath their labels. |
| VerticalBobbing | Legacy camera-pitch formula and 0.8 tick smoothing, updated per completed game tick and interpolated during rendering. Existing accessibility and activity guards remain. |
| LegacySwing | Toggleable console-style swing using the supplied reference's fourth-power timing and transforms; left hand mirrors the right. Server combat mechanics are unchanged. |
| Dynamic FPS | New defaults: 60 unfocused, 30 hidden. Mode changes apply their presets: Aggressive 15/5, Balanced 60/30, Off disables throttling. Saved/custom background profiles remain authoritative on startup. |
| Vanilla FPS reduction | Vanilla inactivity limiter bypassed even when Dynamic FPS is off. Its option is removed from vanilla and the retained Sodium options path. |
| Borderless | 26.3 uses SDL fullscreen handling instead of the Windows oversized-window path. 26.2 uses exact desktop bounds for undecorated borderless windows and removes the extra framebuffer pixel adjustment. |
| Zoom and controls | Default native zoom binding is C; user bindings are preserved. Native controls search filters names and assigned keys. Controlling's richer screen remains compatible in the bundled pack. |
| Privacy | Minecraft telemetry event senders return DISABLED and telemetry log creation is suppressed. This does not claim to change telemetry inside every third-party mod. |
| Pause menu | Feedback, bug-report and player-report buttons removed. |
| Screenshots | Standard Minecraft captures and the gallery's default folder use global `.minecraft/screenshots` (`%APPDATA%/.minecraft/screenshots` on Windows). Existing screenshots are not moved. |
| Skin customization | Custom Lads layout/buttons, rotating skin preview, model-layer toggles reflected in the preview, and handedness control. |
| Resource packs | Existing search plus compatibility/version filtering and alphabetical sorting of available packs. Selected-pack precedence is preserved. |
| Mod maintenance | Updated compatible 26.3 pack versions; maintained compatibility exclusions and the 26.2 pack. Added explicit `--update` and `--refresh` maintenance flags to the sync tool. |

## Verification

- `TheLadsCore/gradlew.bat build deploy -x test` passes and stages jars under `TheLadsLauncher/game-mods/<version>/theladscore.jar`.
- Targeted common HUD suites: **108 passed**, zero failures/errors (`artifacts/hud-changes-final-tests-build.log`).
- Verification project/.NET build: zero warnings/errors (`artifacts/hud-changes-verification-build.log`).
- Final 26.3 isolated world run passed on the staged JAR: 248 native feature checks (including 25 checks specific to this request), 50 HUD-editor checks, background-policy checks, 61 render-scale GPU checks, 38 gallery checks, actual menu/HUD/skin/controls/resource-pack captures, and graceful shutdown (`artifacts/hud-changes-runtime-final-263.log`).
- Final 26.2 focused requested-feature run passed on the staged JAR: 25 requested-feature checks, 50 HUD-editor checks, completed screen captures and graceful shutdown (`artifacts/hud-changes-runtime-final-262.log`). This run explicitly excludes the failing broader checks listed below.
- Final HUD capture: `artifacts/verification/26.3-title/screenshots/native-hud-1790648612197.png`.

## Remaining verification limits

- The broader 26.2 run failed nametag visibility, crosshair attack-indicator and render-scale checks (`artifacts/hud-changes-runtime-262.log`). These failures remain unresolved; the focused pass does not certify that broader suite.
- Physical Alt-Tab/maximized-window edge behavior has not been verified. The borderless geometry changes compile and load, but framebuffer captures do not prove OS window-frame behavior.
- QA used isolated offline worlds. Live Xaero server restrictions and online multiplayer were not exercised; their implementation remains owned by the retained Xaero engine.
- New defaults do not overwrite user-customized layouts, key bindings or FPS profiles.

## Deployment

Built artifacts are staged in the launcher's versioned packages. No public release, Git commit, push or source CurseForge-profile replacement was performed.

Verified staged JAR SHA256 hashes (matching the final isolated runtime copies):

| Version | SHA256 |
| --- | --- |
| 26.3 | `58129DA4D1B4412F15137F32B811D8B33EC4C02B56EBB839CF13B1A0A48FBB73` |
| 26.2 | `B8BA288E52BCBC3BF18EE7B744234FBFB5DA65F42DF9A19A6E2F7D73B745C9FE` |
