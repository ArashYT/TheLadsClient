> Current development status (10 September 2026): see [DEVELOPMENT_BUILD_26_2.md](DEVELOPMENT_BUILD_26_2.md), [NATIVE_MODULES_26_2.md](NATIVE_MODULES_26_2.md) and [STANDALONE_ACCOUNTS.md](STANDALONE_ACCOUNTS.md). The 26.2 client now has additional native modules, a compact home screen and display-paced loading animations. The account manager runs inside Lads. The historical 1.1.0/1.2.0 findings below describe earlier builds; their catalog counts and screenshots are not the current development inventory. For the subsequent Lads application registration and approval email, see the Microsoft account section below; live authentication remains unverified.

# The Lads Client 1.1.0 repair status

Updated 5 September 2026. Targets are exactly **Minecraft 1.21.11 / Java 21** and **Minecraft 26.2 / Java 25**, with Fabric 0.19.3. Legacy 1.21.1 remains a separate build; it is not a substitute for 1.21.11.

## Try the release

The installed launcher is `%LOCALAPPDATA%\The Lads Client\TheLadsLauncher.exe`. Your existing shortcut points there. The complete portable release is `artifacts/client`; keep its DLLs, runtime files and `game-mods` folders together. Existing accounts, profiles, configuration and worlds are retained.

Use **Escape → Lads Client** in a world to open the new menu, or **Lads Settings** on the custom title screen. Search with Ctrl+F, filter by category, enabled status or favorites, open module settings, and use **Edit HUD** to drag enabled overlays. A shows optional disabled previews; G toggles snapping/grid. External mods have their own settings and require a game restart when enabled or disabled in the launcher.

## Microsoft account setup and remaining verification

Verified on 26 September 2026: Azure confirmed creation of **The Lads Client** in **Default Directory** for **personal Microsoft accounts only**, with the **Mobile and desktop applications** platform and `http://localhost`. **Allow public client flows** is enabled and persisted after a browser reload; no client secret was created. Application (client) ID `c8ca54dc-01e3-4bb3-824a-35e09bb3aa13` is now configured in the launcher's settings after an atomic update with a backup. The Azure signup challenge failure recorded on 6 September is historical and no longer the current blocker.

The official Minecraft application review request was **submitted on 27 September 2026 at approximately 04:40 UTC**, after the owner's review and explicit authorization. The form confirmed **"Thank you for contacting Mojang Studios."** No reference number was shown. See [submission evidence](../artifacts/login-setup/MINECRAFT_APPID_REVIEW_SUBMITTED_2026-09-27.json), which preserves the status at submission.

**Approval email received and verified on 28 September 2026:** **"AppID Review Complete (09.28.2026)"**, from **Minecraft Enforcement Notification <MCENotify@microsoft.com>**, timestamped **5:38 PM America/Toronto**, confirms approval for the allow list. The message does not name an application ID; association with `c8ca54dc-01e3-4bb3-824a-35e09bb3aa13` is based on the sole known new-application review submitted on 27 September. See [approval evidence](../artifacts/login-setup/MINECRAFT_APPID_APPROVAL_2026-09-28.json). Live launcher authentication remains unverified pending a runtime check.

In the previously installed launcher, an attempt at **23:52:34** returned a generic Minecraft account-verification failure with no known exact stage or cause; a second attempt at **23:53:02** was declined. Those results do not establish an application-approval failure. Live launcher authentication, session refresh, both game versions and authenticated multiplayer remain unverified. See [MICROSOFT_LOGIN_SETUP.md](MICROSOFT_LOGIN_SETUP.md) and [registration evidence](../artifacts/login-setup/MICROSOFT_REGISTRATION_2026-09-26.md).

At **00:06:33 on 27 September**, Refresh in the updated installed launcher reported **Minecraft Services authentication/profile failed** and listed Java Edition access, Xbox profile/family permissions and Minecraft API approval as checks. **No HTTP status was supplied**; HTTP 403 and application approval as the sole cause are not established. No device-code challenge was active at that handoff. The subsequent approval email does not establish successful live authentication; end-to-end verification remains pending. Do not submit a duplicate review request.

Latest source validation: the Release launcher build passed with zero warnings/errors and all **125 launcher tests passed**, with zero failures/skips. The registered Lads application is the default for fresh and previously unset settings. New coverage includes nine configuration cases and eleven authentication status/redaction cases; diagnostics report only broad stages and validated HTTP/Xbox codes. The self-contained Windows x64 release at `artifacts/login-setup/published-20260926` was **installed and verified on 27 September**. Installed EXE/DLL hashes match the publication; only those two files changed. All four installed game-mod files and the live settings file retained their exact hashes. Source game-mod files were excluded to avoid rolling back installed versions. The old launcher closed normally and the updated launcher restarted successfully. See [installation evidence](../artifacts/login-setup/installed-20260927.json). These checks do not prove live account authentication.

Historical verification on 6 September: the refresh fix made manual refresh and early renewal obtain new Xbox/XSTS and Minecraft tokens. Previously, CmlLib's internal cache could reuse the old Minecraft token even after the launcher requested refresh. Two regression cases reproduced that behavior before the fix and passed afterward using the real library with synthetic service responses. At that time, the Release build passed with zero warnings/errors and all 102 launcher tests passed. The corrected launcher was published to `artifacts/client` and installed under `%LOCALAPPDATA%\The Lads Client`; installed EXE/DLL hashes matched that publication then. Those checks did not prove live account authentication.

The launcher implements Microsoft device-code authentication, Xbox/XSTS and Minecraft session acquisition, Java-license/profile validation, serialized refresh/cancellation, Windows-protected account storage, and atomic account persistence. The borrowed Prism application ID has been removed. Current source uses the registered Lads application by default while preserving custom IDs; an invalid custom ID stops before launch preparation and offers setup or a separately chosen offline account. Authentication failure never silently becomes an offline identity. In-game account selection records the next-launch identity; it does not fabricate a new live session.

## Implemented client work

- Custom animated alpine-night title screen on both versions, with responsive layouts, mint actions, real native buttons, keyboard focus/narration, and late-added mod buttons. Title enable/branding-scale/account-card settings are connected. Rendering is bounded; geometry is not rebuilt every frame.
- Pause-menu Lads Client button added through the native layout, retaining vanilla and installed mod controls.
- New responsive mods screen with searchable cards, categories, favorites, real availability, detail controls, draggable numeric sliders, text/color editing, clipboard paste, scrolling and keyboard navigation. Resource packs, video settings and external configuration screens are connected to the game.
- Correct HUD scale, measured geometry, persisted positions, drag bounds, enabled-only editor and real data. Armor reports equipped items and durability; health/absorption, biome IDs, direction/yaw and singleplayer saturation come from native state. Multiplayer saturation is not invented when authoritative data is unavailable.
- Real sidebar objective/team/player scores replace the placeholder only when enabled and populated. Original vanilla sidebar behavior remains available when the Lads overlay is disabled or absent.
- Native Zoom (default Z, rebindable in Controls), scroll/smooth/hand zoom, sprint/sneak toggles, physical mouse CPS, and Fullbright. Toggle state resets on focus/world/pause transitions and retains vanilla movement eligibility.
- Pinned, exact-version mod packs: **35 original upstream jars per target**, including performance, lighting, maps, recipes, animations, crosshairs, screenshots, reconnecting, capes and tooltips. Original SHA-512 hashes, sizes, Fabric IDs and per-project attribution are recorded. See [CLIENT_MODS.md](CLIENT_MODS.md).
- Launcher preparation downloads at bounded concurrency and reuses verified cached jars. User jars and disabled choices are preserved; conflicts and missing hard dependencies are actionable. Downloads and preflight finish before active changes, with rollback for failed in-process commits; power-loss recovery is not a durable transaction, and externally modified files are preserved with recoverable backups. Runtime version selection uses real Java installations. Log/progress work is throttled, hidden-window timers pause, and forced RealTime priority/GC/JVM tuning are removed.

## Catalog coverage and limits

**46 of 59 legacy catalog entries have actual implementations connected: 24 built-in and 22 through installed mods.** Entries marked INFO/Unavailable are not working toggles. The remaining 13 are SmoothHotbar, OldDamageTilt, VerticalBobbing, ToggleNametags, KillBanner, HideChatIndicators, DiscordRPC, RenderScale, FarBlockEntities, EnhancedTooltips, DisableNarrator, SignalLoss and Exordium. This release is a substantial working client update, not evidence that every old feature is complete.

The external mod's own options are authoritative; old Lads declarations do not override unrelated upstream settings. Clumps requires the authoritative world/server to run it, capes do not grant ownership, and JEI does not grant server privileges. Exordium is excluded following its maintainer's in-game rendering-conflict warning. Tab Tweaks is pinned but archived upstream and needs reassessment for future versions.

## Verification

- **151 targeted Java checks passed**, zero failures/errors/skips: HUD behavior/native-data/scoreboard/editor geometry, menus, title, account handoff and paths.
- **100 launcher regression checks passed**, zero failures/skips, including 45 pinned-pack installer cases. The tests cover authentication, protected account persistence, version selection, Core installation, disabled/dependency behavior, interrupted downloads, staged commit/rollback and link-safe write paths.
- Required Gradle **build deploy -x test** passed for legacy 1.21.1 plus both requested production targets. Exact deployed Core hashes were checked. The repository's pre-existing broken cosmetics suite remains excluded by its build instructions.
- **1.21.11, full 35-jar pack:** actual title, pause-menu Lads button, searchable modules, world load/save/quit, saved FPS at 125% scale, enabled-only HUD editor, real Xaero minimap, JEI iron search/helmet recipe and durability tooltip were observed.
- **26.2, full 35-jar pack:** actual title with Friends/credits/mod screenshot controls, world load/save/quit, pause-menu Lads button, clipboard search, actual scoreboard value 12, midnight Fullbright off/on comparison, and equipped Iron Helmet 165/165 HUD were observed.
- Crosshair editor navigation was verified on both final builds; the 26.2 ScalableLux fallback correctly selects its own Mod Menu entry. Launching 26.2 from the actual installed shortcut with the selected offline TestPlayer account reached the custom title and new mods menu. The installed executable reports 1.1.0.0 and matches the published hash; both actual user profiles have the Core plus 35 verified mod jars. Native game screenshots are in `artifacts/client-preview`. QA worlds and offline LadsQA accounts are isolated under `artifacts/verification`.

These are startup and selected feature checks, not exhaustive testing of every upstream option or a controlled FPS benchmark. Online servers, Realms, automatic reconnect with a real session, and live token renewal still need approved Microsoft login. Zoom/toggle/CPS have native hooks and build validation; hold-key behavior has not been fully exercised by automated UI input.

Nonfatal upstream observations: optional compatibility class probes, resource-pack metadata warnings in some bundled mods, Custom Crosshair's missing optional drawn-image file log, and 26.2 OSHI/JNA Windows performance-counter warnings. Offline Realms/profile authorization errors are expected and do not count as online verification.

## Reproduce

From the repository root:

```powershell
.\Build-LadsClient.ps1 -Launcher
# Add -Install to update the existing local launcher after closing it.
dotnet test TheLadsLauncher.Tests/TheLadsLauncher.Tests.csproj -c Release
dotnet run --project TheLadsLauncher.Verification -- 1.21.11 '<repository root>' --title
dotnet run --project TheLadsLauncher.Verification -- 26.2 '<repository root>' --title
```

The verification harness uses isolated worlds, bounded RAM/distance and a 30 FPS cap, then closes its own test process. It never proves a real Microsoft account works.



