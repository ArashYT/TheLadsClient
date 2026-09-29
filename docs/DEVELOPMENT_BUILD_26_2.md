# Lads development build — 10 September 2026

Minecraft **26.2 / Java 25** is the main development target. The separate 1.21.11 / Java 21 build is retained. Future Minecraft releases need a verified compatibility port and dependency set before they become supported.

## Standalone accounts

Accounts are managed inside Lads, following Prism's familiar add/default/refresh/sign-in-again workflow. Lads launches Minecraft directly. It does not require Prism, use Prism's application identity, or import Prism's token cache. A launch can temporarily select another account without replacing the saved default. [Account behavior](STANDALONE_ACCOUNTS.md).

Microsoft registration and live login remain deferred as requested. This machine has no configured Lads Microsoft application ID. Local development identities can run singleplayer; live authentication, token renewal and authenticated multiplayer remain unverified. Authentication failures never silently become offline identities.

## Interface and native features

- Right Shift opens/closes the Lads mods menu in gameplay and opens it from title/pause. Closing restores the parent; chat and other active screens keep their keyboard input. The binding is configurable in Minecraft Controls.
- The dark red mods menu includes search, categories, favorites, saved options and a HUD editor. Its palette follows the launcher. The six-action home screen keeps secondary native/mod actions under More.
- The early launcher loading window uses display frame callbacks and elapsed-time animation, live preparation status, a version badge and elapsed clock. The old 33 ms timer is gone. The measured callback rate on this display was approximately 60/second; this is not a claim of presented FPS or game performance.
- The runtime catalog has **48 native modules, 17 backed by retained engines, and one unavailable legacy entry: Exordium**. Lads settings now shows **only native Lads modules**; search, favorites and categories cannot expose external entries. These counts do not mean every external mod was recreated. [Complete inventory and boundaries](NATIVE_MODULES_26_2.md).
- Full native additions include food overlays, Raised layout editing, Dynamic FPS profiles, Paper Doll, reconnect controls, player-list/ping rendering, the crosshair drawing editor, screenshot gallery, XP-orb merging, narrator control and resolution scaling. Feature documents contain the exact reference versions, options, source licenses and runtime evidence.
- Sodium and the other large rendering, mapping, recipe, animation and resource engines remain upstream. Their settings remain accessible through Minecraft's ordinary Mod Menu; external-engine pages have been removed from Lads settings. The pack has **27 top-level upstream JARs plus Core**, including support libraries. Ten formerly managed feature JARs are replaced by native code. Original upstream source/licenses are included where required.
- HUD editing now measures text before placement, shares GUI coordinates with gameplay and keeps group offsets fixed at screen edges. Visible selection, Group/Ungroup and Lock position/Unlock controls are available. [HUD editor behavior and verification](HUD_EDITOR_26_2.md).

Exordium has no verified Fabric 26.2 release and its HUD caching engine remains unavailable. OBE, Animatium and incompatible supplied Kerria/Retromod artifacts remain outside the verified pack for the reasons in [the parity audit](NATIVE_PARITY_AUDIT_26_2.md). Discord presence is implemented but requires an application ID, running Discord and explicit opt-in; live publication is unverified. Remote-server behavior remains server-owned, including XP-orb merging and exact food synchronization.

## Verification and delivery

The final feature build passed **252 targeted Java regression tests** and **105 launcher tests**. Actual Minecraft runs passed the 226-check combined native-world probe, 61 GPU scaling checks, 53 crosshair checks, 38 gallery checks, 21 real integrated-server Clumps checks and the dedicated food, layout, narrator, reconnect, Paper Doll, tab-list and Dynamic FPS probes. Counts overlap and are not summed. Retained engine settings were changed, read back and restored for **250 controls**, with five engines having no mutable control for that probe. [Exact artifacts and runtime report](NATIVE_RUNTIME_CHECKPOINT_26_2.md).

Actual world and mods-menu frames were captured from Minecraft's completed framebuffer. Tests used isolated local development accounts and saves under `artifacts/verification`. Synthetic Right Shift previously passed through the real keyboard handler; physical input remains unobserved while Windows is locked. GPU readback, settings round trips and mocked reconnect/Discord transports are distinct from physical input, audible behavior and authenticated multiplayer.

The portable package is `artifacts/client-development`; keep its runtime files and `game-mods` directories together. The installed launcher path is `%LOCALAPPDATA%\The Lads Client\TheLadsLauncher.exe`. Rebuild with `Build-LadsClient.ps1 -Launcher -OutputDirectory artifacts/client-development`, then use `tools/Install-LadsRelease.ps1 -SourceDirectory artifacts/client-development`. Installation verifies hashes and preserves replaced files in `.update-backups`; account/profile settings and worlds are retained. The managed mod installer retires only its own hash-matching superseded JARs.
