# Lads Client 1.2.0 — modules and performance

Right Shift opens the Lads modules menu during gameplay. Press it again to close the menu. The binding is listed under The Lads Client in Minecraft Controls and can be changed. Chat, text editing, inventories and other screens retain their own input. The title-screen and pause-menu Lads buttons remain available.

The Engines category brings bundled feature engines into the Lads catalog. Entity Culling, Lithium, FerriteCore and Xaero Minimap now have their own entries. This release integrates existing engines; it does not reimplement or relabel upstream code. Original jars, dependencies, authorship, licensing and integrity checks remain intact.

## Settings coverage

The following engines have actual controls in the Lads interface: AppleSkin, AutoReconnect, BetterF3, Better Statistics, Capes, Custom Crosshair, Dynamic FPS, Entity Culling, JEI, LambDynamicLights, Not Enough Animations, Raised, Screenshot Viewer, 3D Skin Layers, Sodium, Tab Tweaks, Xaero Minimap and Xaero World Map. JEI's configuration API becomes available after entering a world. The two Tab Tweaks catalog entries share the same native settings.

The fresh QA profiles exposed **339 controls on 1.21.11** and **341 on 26.2** before entering a world. These counts are profile-dependent: map redirects, server-enforced preferences and custom numeric values outside supported slider ranges are deliberately not edited here. This is a count of accessible controls, not a claim that every individual control has been exercised at runtime.

Changes are staged. **Apply** sends them to the engine's live configuration and save API. Leaving the page also applies pending changes; **Revert** restores the most recently loaded/applied values. These engine settings are not saved as a second set of Lads preferences. Reported write failures keep the menu open and restore the changed live fields where possible. Upstream save functions that swallow their own I/O errors cannot provide a general disk-transaction guarantee; Screenshot Viewer additionally receives independent typed file read-back verification.

**Advanced** opens the engine's complete editor for specialized options such as custom crosshair drawing/colors, recipe layout, map profiles/radar categories, Sodium video settings and entity exceptions. Paper Doll and Durability Tooltip currently retain their complete upstream editors; native Lads controls for those two engines are not implemented. Automatic optimizers—Lithium, FerriteCore, Clumps, ScalableLux and ImmediatelyFast—have status pages rather than fake runtime enable/disable toggles. Loading/unloading Fabric engines requires restarting Minecraft.

The 13 unavailable catalog features documented in REPAIR_STATUS.md are unchanged. This release does not claim that every historical placeholder is implemented.

## Verification and performance

Both production Fabric targets build and deploy through the Gradle release task. The isolated `TheLadsLauncher.Verification --settings` mode launches the pinned pack in `artifacts/verification/<version>-settings`, checks real configuration APIs, changes one supported value per available engine, reopens it, restores it and checks it again. It refuses to write outside those isolated directories. Automatic/no-control pages and JEI before world entry are explicitly reported as skipped.

The common UI tests cover staging, Apply/Revert, typed writes, conflict detection, failed setters/saves, external changes, profile switching, server enforcement, version-specific options, and screenshot persistence checks. This is separate from physical keyboard and visual testing.

HUD formatting and layout now reuse unchanged results. The measured armor/scoreboard CPU reduction is about **31–37%** in the common-code benchmark, with identical render-command checksums; this is **not an in-game FPS measurement**. See HUD_PERFORMANCE.md for absolute timings, allocations, methodology and preserved raw results.

During this work Windows automation could not focus the QA window (`foreground window did not report a process id`). One 1.21.11 interactive QA attempt also produced a native GLFW access violation. The later automated settings runs passed on both versions. Physical Right Shift and final native menu visual testing remain pending until desktop input is available; the existing user game was not closed or altered for testing.

Microsoft application registration and real online-session verification retain the separate requirements in MICROSOFT_LOGIN_SETUP.md; this release does not change authentication.
