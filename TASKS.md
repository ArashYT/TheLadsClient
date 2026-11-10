# Lads Client 1.13.9 - live task list
[ ] todo, [~] in progress, [x] done and verified. Notes and designs: docs/NEXT_1.13.9_NOTES.md
Mod: `gradlew build deploy -x test` passes on arash-hp.
In-game checks run from arash-hp on arash-pc with tools/ingame-drive.ps1 (how: notes doc). arash-pc's launcher bundle has the jars of 11-10 12:19; its 26.3 jar is the build of 11-10 13:02 (panorama fix).
Each open item has its own hub chat ("1.13.9 #<n>: ..."), at most 3 working at once; item 14 starts when all the others are [x]. A conductor on arash-hp (C:UsersAdministratorDocumentsLads Client Extraselease-conductor-1.13.9, scheduled task Lads1139Conductor) sends "continue" to a chat until its line is [x]; status.txt there shows where each chat stands. Edit only your own line; the blank lines between items keep merges clean.

1. [x] Chat: Up/Down/Tab stay in the chat input (26.3, 1.20.1). Seen in game on 26.3 (11-10): Up recalls the sent line, Tab completes /gamem to /gamemode. The merged mixin crashed 26.3 at startup (@WrapOperation on a super call takes ChatScreen, not Screen): fixed. 1.20.1 had the same bug plus keepOpen turned into an invalid @WrapOperation: fixed, 1.20.1 not started in game

2. [x] Start Hosting button does nothing. Cause: lads-host.json had scope OFF (picked on the screen), and Start Hosting with OFF means "stop", without a word. The screen no longer offers OFF and reads a saved OFF as Friends. Seen in game on 26.3 (11-10): "hosting started ... scope=FRIENDS", world backup toast, Stop Hosting. Also fixed there: the options panel clipped its right side (scope, approval, guests and joinable controls were invisible on a 640-wide GUI)

3. [x] Game window icon: 7 sizes (16-256) from the temporary logo (draft E); launcher icon.ico too

4. [~] Block-hit / 1.7 animations with any item or block, any mod or pack. Rule on 1.20.1, 1.21.11, 26.2 and 26.3: any main-hand item blocks while the off-hand shield blocks; blocks and 3D models take the 1.7 block pose and blockhit; an attack click swings over every other use pose (spyglass, trident, shield, modded). 1.8.9 (real sword blocking, no shields): a block or a 3D model in use keeps 1.7's swing. Drawn only, no packets. Seen in game: 26.3 only (11-10, diamond sword and oak planks: block pose and a swing while blocking; Arash still judges the look). The ports to 1.8.9, 1.20.1, 1.21.11 and 26.2 are merged (11-10 13:42): they compile and OldAnimationsTest passes (23 tests), none was started in game. The Blockhitting and block pose tooltips now say any item. Left: start the four ported versions in game with a shield in the off hand. arash-pc and desktop-57kj0bp were offline at 13:50; cyrax-pc is online but has launcher 1.8.4 and only 26.3 downloaded

5. [x] Read leftover server-integration notes (digest in the notes doc)

6. [~] ModSync: on invite join, fetch host mods (Modrinth/CurseForge direct, else P2P). Built and merged, not joined together yet: the mod list, diff, hash check and source choice; jar transfer from host to guest on P2P stream 2 and the site download (14 unit tests pass: ModSyncCoreTest, ModSyncTransferTest); the launcher part (list of the player's own jars with a Modrinth/CurseForge lookup by hash, checked restart that adds the host's jars from their own folder; dotnet build and 4 tests pass). Left: the in-game flow (host serves, guest syncs, "trust this host's mods?" question, restart), the rejoin after the restart, starting the game, a real invite between two PCs. Nothing was run in game yet

7. [~] Title screen: Change panorama + Update (update launcher and game, relaunch) next to Legacy layout. Change panorama is on every version (PanoramaPicker.next: the next release family of the Panorama Version setting; unit test). Seen in game on 26.3 (11-10): two presses gave 26.2 (a dark cave, Mojang's own image) and 26.1.2 (cherry grove). The flat grey came from the Panorama Version setting itself: newer client jars (1.21.11, 26.2, 26.3 checked) hold 1x1 placeholder panoramas and the real images are in the release's asset index. PanoramaPicker now reads them there (sha1-checked), uses the jar for older releases and replaces a cached placeholder set. The button on 26.2, 1.21.11, 1.20.1 and 1.8.9 compiles; none of those was started in game. Left: the whole Update button

8. [x] Launcher: a non-zero exit with no crash evidence is a normal close. Cause of the non-zero exit found (26.3): a JVM crash in the NVIDIA driver while workers compile render pipelines during close (2 hs_err files on arash-pc). Mod now waits for the workers before close. Seen on 26.3 (11-10): two window closes from a singleplayer world, "Game exited normally", no new hs_err. Not tried: closing right after leaving the lobby (the case that crashed)

9. [ ] Lads API controls every client setting, session-only, per world/gamemode (lobby: Fullbright off). Done 11-10: plugin 1.1.0 (game-mode rules, named and timed scopes, unit tests) is live on the lobby with Fullbright blocked in the lobby world; lads:api transport on 26.2 and 1.21.11 (jars build, not started in game). Open: vanilla and HUD options, the in-game check (arash-pc was offline)

10. [ ] True spectator system in the client (PvPArena and others)

11. [ ] External mods: check for updates, update safely

12. [ ] Emote "Twerking" with jiggle physics

13. [~] Anti-spoof handshake: notes read, scheme proposed in the notes doc, not built

14. [ ] Bump to 1.13.9, release notes, publish
