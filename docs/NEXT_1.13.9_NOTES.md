# 1.13.9 - notes digest and open designs (2026-11-10)

What earlier sessions left about server integration, plus where each open 1.13.9 feature stands.
Gathered by a research pass: paths are pointers, not checked line by line.
S = C:\Servers on arash-hp (PvPArena, TheLadsLobby, LadsConnect sources and the live servers).
API = LadsClientAPI/common/src/main/java/com/thelads/ladsapi. Core = TheLadsCore/common/src/main/java/com/thelads/core.

## Building on arash-hp
- JAVA_HOME = C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot (the paths in gradle.properties are another PC's).
- There is no C:\The Lads Client on arash-hp, so nothing can be tested in game here.
- arash-pc has the game: C:\Users\Arash\AppData\Roaming\.theladsclient\profiles\<version> (logs\, mods\theladscore.jar). Its saves folder is a junction to .minecraft\saves, shared by every profile. Old logs are .log.gz: read them with GZipStream.

## In-game checks on arash-pc, driven from arash-hp (worked 11-10)
- The remote shell (ai-hub pc.ts) runs as Arash in his desktop session, so it can start the game, press keys and take screenshots. Check first that he is away (GetLastInputInfo) and that no java.exe runs.
- The launcher is C:\Users\Arash\AppData\Local\TheLadsClient\current\TheLadsLauncher.exe (start it with explorer.exe and the desktop shortcut). On Play it copies current\game-mods\<version>\theladscore.jar over the profile's jar, so new jars go into game-mods, not into the profile. The jars of the 1.13.8 install are kept in ...\TheLadsClient\game-mods-backup-1108.
- tools/ingame-drive.ps1 (copy it to the PC) sends keys and clicks and saves screenshots; positions are fractions of the window. At 1920x1080: launcher Play `click 0.89 0.575` (-Proc TheLadsLauncher), title Singleplayer `click 0.19 0.527`, first world `click 0.15 0.21`, Play Selected World `click 0.418 0.921`, pause menu Multiplayer Options `click 0.584 0.523`, Start/Stop Hosting `click 0.291 0.966`. `close` is the window X.
- After a game crash the launcher shows a "Game Crashed" window over Play: Relaunch is `click 0.358 0.625`.
- A mixin that compiles can still fail when the game starts (MixinExtras checks handler signatures at apply time): a @WrapOperation on a `super.method()` call takes the mixin's target class as the receiver. Start the game after every mixin change.

## Start Hosting (task 2, fixed 11-10)
- Evidence: arash-pc's New World\lads-host.json had "scope": "OFF"; the 11-08 15:22 log shows 46 s in the Host screen with no backup, "Started serving" or HostSession line, and no lads-backups folder exists.
- Cause: the scope row offered OFF. Start Hosting then called HostSession.start with OFF, which means stop and shows no toast.

## Non-zero exit on window close (task 8, 26.3)
- Evidence: profiles\26.3\hs_err_pid28228.log (11-10 10:13:06) and hs_err_pid34580.log (11-08 23:23:22), each 1-2 s after "Stopping!" in the game log. Both: EXCEPTION_ACCESS_VIOLATION in nvoglv64.dll, thread Worker-Main-N, under VK10.vkCreateGraphicsPipelines from renderpearl PipelineBuilder.compilePipeline.
- Cause: the game closes while resources reload (11-10: "Reloading ResourceManager" right after "Stopping!", the reload that drops the server pack on disconnect; 11-08: closed during the first load). close() destroys render objects while workers still compile pipelines.
- Fix (ClientTickMixin, 26.3): Minecraft.close() first waits up to 10 s for the worker pool to go quiet. To check in game: join the lobby, close with X, no new hs_err file.

## Server integration leftovers
- Live LadsClientAPI jars (Lobby, Velocity) are the 1.0.0 build of 10-23: deploy a newer one, set `game-name` and `hub-command`.
- Live rules.yml sets nothing (S\The Lads Network Lobby - Paper 1.21.11\plugins\LadsClientAPI\rules.yml; worlds and groups empty).
- Badge for all viewers: `%ladsapi_badge%` plus a new glyph (S\staging\lads-perks-2026-11-10\PLAN.md:19-25). The live placeholder is empty.
- Lads-only suits, pets, kill effects, finishers, early access (PLAN.md:26-33). Pass bugs (S\TASKS.md:10).
- Waiting on client releases: duel overlay, pop-up invites, 60 fps held items, menu screens (PLAN.md:36; S\TASKS.md:9,11).
- ladsapi-schema.json says 1.13.4, the client is 1.13.8.
- 1.21.11 banner reset on a proxy switch.

## Anti-spoof handshake (task 13)
- The notes say only "a signed hello the server verifies"; perks then check that flag (S\TASKS.md:8). No key, nonce or format was written down.
- Today: hello, hello, policy, ack (LadsClientAPI/docs/PROTOCOL.md:19-28,86-98). The server hello has no nonce and any valid reply is accepted (API\session\SessionManager.java:238-253). `require-lads-client` and perks trust it. LadsConnect reads the spoofable minecraft:brand.
- Reusable: the relays already prove the account (nonce, Mojang join of sha1("lads-cosmetics:"+nonce), hasJoined: cosmetics-server/cosmetics.py:164,1068-1108).
- Limits to keep: the protocol is frozen (PROTOCOL.md:3), the client never sends first, no telemetry.
- Proposed, not built: the server hello carries a random nonce; the client asks the Lads relay (where Mojang already proved its account) for a short-lived Ed25519 token over uuid + nonce + server id + expiry; the client puts it in its hello; the plugin checks it against the relay's public key shipped in the plugin and only then marks the session verified. Old clients stay unverified, not kicked.
- Honest limit: nothing can prove the binary on a PC the player owns. This stops brand and channel spoofing and ties the claim to a Mojang-proven relay session.

## Server-controlled settings (task 9)
- Already there: module allow/block/on and option value/min/max/choices/lock for Bool, Slider, Dropdown, Color (PROTOCOL.md:47-59); never Text/Action/Sound/Profiles or automation/privacy modules (Core\client\ladsapi\LadsApiRules.java:13-23).
- Scope today: rules.yml, then worlds.<world>, then groups.<perm>, then per-player API overrides (API\policy\PolicyBuilder.java:49-82). Lobby Fullbright-off fits `worlds` once a newer plugin is live.
- Restore: the policy lives in memory only and resets on disconnect, JoinGame, a seed-changing respawn or the config phase (Core\client\ladsapi\LadsApiClient.java:265-310), so saved settings are never overwritten.
- Missing: game-mode and timed scopes, vanilla and HUD options, transports for 1.21.11 and 26.2, any live rule.

## Spectator (task 10)
- PvPArena: S\PvPArena\src\main\java\ca\arashyt\pvparena\commands\SpectateCommand.java, listeners\CombatMechanics.java (startSpectatingPOV 5226, exitSpectatingPOV 5337, sendJavaPovCameraPacket 4935). Also TheLadsLobby MenuManager.java:2545, Infinite Parkour, UHC.
- Spectators are SURVIVAL, flying and invisible, moved into the target's eyes; the hotbar is mirrored by packets. No lads:api message says "you are spectating".
- Client: 26.3 only (feature\PovCamera.java, mixin\PovHudMixin.java, commit 9cacfc419).
- Needed: a lads:api spectate message (target, mode) so the client can run a real camera, HUD and target switcher, with the packet tricks kept as the vanilla fallback.

## ModSync (task 6)
- Invites and joins: cosmetics-server/social.py; Core\client\social (SocialState, HostSession, GuestSession, p2p\Mux).
- No mod list is exchanged: game.modsHash is always empty; the join gate checks only mc, loader and pack id/version (SocialState.java:644-680).
- Mux uses streams 0-1; the spec keeps 2+ for pack transfer (docs/superpowers/specs/2026-10-23-friends-p2p-design.md:54,64).
- Reuse: loadedMods() (v26_3 adapter\VanillaGameBridge26.java:588) and the launcher's ModpackPatchEngine.cs (allow-listed hosts, mandatory hashes).
- Mods cannot load into a running game: the guest must fetch (Modrinth/CurseForge by hash, else P2P on stream 2), then relaunch through the launcher and rejoin. Only hash-checked files, only after the guest accepts.
- Design (11-10, built in steps; code: Core\client\social\modsync):
  - Which mods: the profile's own jars (launcher inventory ownership "user", installed and enabled). The launcher writes them before every launch to `<gameDir>\.lads-mod-cache\modsync.json` ({v, mods: [id, name, version, file, size, sha1, sha512, source, url]}). It looks each jar up by hash (Modrinth sha1, CurseForge fingerprint: ContentCatalog, the CurseForge key lives in the launcher only) and caches the answer. Pack profiles and 1.8.9 (Forge) do not sync.
  - Host: presence `game.modsHash` = ModSyncList.hash (32 hex; "" = nothing to sync, or an old client). It serves its list and its listed jars on Mux stream 2, only when asked there.
  - Guest: the host's modsHash is empty or equal to its own: joins as before. Else, once the link is up and before the game connects: asks for the list on stream 2 (never to a host whose modsHash is empty: an old Mux ends the session on a stream-2 frame), then ModSyncList.missing and ModSyncSource.plan. SITE jars: https from cdn.modrinth.com or *.forgecdn.net only, no question. P2P jars: one "trust this host's mods?" question per host, then stream 2. Every file: size and sha512 (ModSyncHash); a site download that fails falls back to P2P.
  - Files go to `<gameDir>\lads-modsync\<hostUuid>\<ModSyncEntry.localName()>`. The guest's mods folder and settings are never touched.
  - Relaunch: the game writes `<gameDir>\lads-modsync.json` (host, files with size and sha512) and quits. The launcher checks every file again and starts the same profile with `-Dfabric.addMods=<that folder>` and `-Dlads.modsync.host=<uuid>`; the game then rejoins that host. A normal launch has neither argument, so the guest's own setup is back. Fabric loads the higher version when the guest has another version of a synced mod.
  - Steps: [x] list, diff, hash check, source choice + tests (ModSyncCoreTest). [ ] Mux stream 2 + transfer. [ ] launcher: list writer, relaunch. [ ] game flow + Friends screen question and progress. [ ] rejoin after relaunch. [ ] in-game start, two-PC invite.
