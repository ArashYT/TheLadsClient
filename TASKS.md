# Lads Client 1.13.9 - live task list
[ ] todo, [~] in progress, [x] done and verified. Notes and designs: docs/NEXT_1.13.9_NOTES.md
Mod: `gradlew build deploy -x test` passes on arash-hp.

1. [x] Chat: Up/Down/Tab stay in the chat input (26.3, 1.20.1). Compiles; needs an in-game check
2. [x] Start Hosting button does nothing. Cause: lads-host.json had scope OFF (picked on the screen), and Start Hosting with OFF means "stop", without a word. The screen no longer offers OFF and reads a saved OFF as Friends. Unit test; needs an in-game check
3. [x] Game window icon: 7 sizes (16-256) from the temporary logo (draft E); launcher icon.ico too
4. [~] Block-hit / 1.7 animations with any item or block, any mod or pack. 26.3 done: any main-hand item blocks while the off-hand shield blocks; blocks and 3D models take the 1.7 block pose and blockhit; an attack click swings over every other use pose (spyglass, trident, shield, modded). Maths unit-tested, not seen in game. Left: 1.8.9, 1.20.1, 1.21.11, 26.2; the Blockhitting tooltip still says "sword"
5. [x] Read leftover server-integration notes (digest in the notes doc)
6. [ ] ModSync: on invite join, fetch host mods (Modrinth/CurseForge direct, else P2P)
7. [ ] Title screen: Change panorama + Update (update launcher and game, relaunch) next to Legacy layout
8. [x] Launcher: a non-zero exit with no crash evidence is a normal close. Cause of the non-zero exit found (26.3): a JVM crash in the NVIDIA driver while workers compile render pipelines during close (2 hs_err files on arash-pc). Mod now waits for the workers before close; compiles, not tried in game
9. [ ] Lads API controls every client setting, session-only, per world/gamemode (lobby: Fullbright off)
10. [ ] True spectator system in the client (PvPArena and others)
11. [ ] External mods: check for updates, update safely
12. [ ] Emote "Twerking" with jiggle physics
13. [~] Anti-spoof handshake: notes read, scheme proposed in the notes doc, not built
14. [ ] Bump to 1.13.9, release notes, publish
