# Built-in background profiles for 26.2

DynamicFPS now contains the full policy engine and editor from [Dynamic FPS 3.11.9](https://github.com/juliand665/Dynamic-FPS/tree/3.11.9), commit `2c9d75091f8cf41e73f2a65c09353fea145ce438`, under its MIT license. It is compiled into Lads in an isolated package and resource namespace. The original copyright and license ship in `licenses/DynamicFPS-LICENSE.txt`. The retired Dynamic FPS JAR is not required.

Enable **DynamicFPS** in the RSHIFT menu, then choose **Background Profiles → Edit all profiles**. The Lads toggle controls the engine. The quick unfocused/hidden sliders mirror the detailed profiles. Aggressive applies focus policies immediately; Balanced gives an unfocused window three seconds of grace; Off restores Minecraft's normal limits. Detailed settings are saved atomically to `config/thelads/dynamicfps.json`. Existing `config/dynamic_fps.json` is read once when the new file is missing and is left untouched.

| Policy | Implementation |
| --- | --- |
| Focused, hovered, unfocused, minimized, idle, unplugged | Separate frame limits and VSync; focused mode uses vanilla settings, with optional menu uncap. |
| 0–14 FPS | Rendering is skipped at the requested rate, including complete render suspension at 0. The event loop retains a 15 Hz wake-up floor; this is not 15 rendered FPS. |
| Sound | Per-category and master multipliers, separate fade-up/down speeds, updates to active channels, music pause/resume at zero, and suppression of newly muted sounds. User sound options stay intact. |
| Graphics | Default, reduced and minimal profiles alter actual cloud, particle, entity shadow/distance, weather, AO, leaves, transparency and biome-blend settings. Original settings are restored on focus/disable/close and during options saving. These profiles do not change chunk loading or server simulation distance. |
| Idle and controllers | Real input callbacks, screen changes, cursor movement, and actual player position/look changes refresh activity. Controller-driven movement/look therefore count without requiring a controller library. |
| Battery | Local OSHI/JNA already bundled with Minecraft supplies real battery readings. Optional unplugged profiles, critical-level notifications, debug visibility and four HUD corners are retained. No battery library/native downloads or production mock data. |
| Other policies | Per-state toasts and opt-in garbage collection, initial-focus-click suppression, force-low-FPS/toggle key bindings, FPS debug detail, loading-overlay optimization, task-wait efficiency fix, and FREX flawless-frame compatibility. |

The separately licensed upstream battery bitmaps are not included. Lads draws original battery icons and toast panels using GUI primitives. Cloth Config remains the existing shared editor library. Java source translations and policy code retain upstream MIT attribution.

Lads also repairs several lifecycle issues while adapting the code: changing an active profile reapplies it immediately; switching minimal → reduced restores settings that only minimal overrides; save failures still reapply the temporary profile; disabled mode bypasses upstream idle/menu/click overrides; initial idle activity starts at initialization; and minimized state outranks a stale hover flag. Battery polling runs on a daemon worker every 15 seconds and rejects stale results after disabling or closing.

## Verification

The full multi-version `build deploy -x test` passes. `DynamicBatteryChecks` passes 14 OSHI input-conversion assertions, and a direct read through the bundled OSHI/JNA succeeded on the development host. Evidence: `artifacts/verification/native-mods-26.2/dynamic-battery-checks.log`. This validates data handling and local access, not battery drain on every hardware model.

`-Dthelads.verifyBackgroundPolicies=true` runs an opt-in title-screen probe after the loading overlay closes. Expected marker: `Lads background policy probe END: 42 passed, 0 failed`. The combined native in-world probe invokes the same checks and adds two actual player movement/look observations (44 checks). Failure logs `Lads background policy probe FAILED`. The probe changes only synchronous synthetic window/battery state, exercises transformed frame and sound hooks, graphics restoration, input wake-up, and construction of the full editor, then restores preferences and recency metadata before logging success. It does not claim physical focus events, measured display FPS, audible playback, battery drain, or GUI visual QA.

The fresh combined Minecraft 26.2 run passed all 42 title and 44 world checks; see `artifacts/verification/native-mods-26.2/native-ports-world-second.log`. Physical focus/minimize behavior, real audio fade perception and the detailed editor's visual layout require runtime observation. The older frame-cap-only checkpoints remain historical and do not verify this full port.
