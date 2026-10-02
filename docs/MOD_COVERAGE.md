# Instance mod coverage

Source: The Lads Client Dev 26.3, audited 2026-09-28. 99 jars contain 98 unique Fabric mod IDs. The duplicate Faster Shadow Mapper jar is collapsed to one compatible release. GoodMC was removed from the pack in 1.2.3, so the tables cover the other 97.

Native means implemented inside Core. Modrinth means an unchanged upstream jar downloaded through its API. Original means an older original project supplies the feature instead of the source instance's newer fork; this is not a claim of identical fork behavior. Missing means it is not included. Removed means the pack stopped shipping it because nothing needs it any more.

| Version | Native source features | Upstream source features | Missing | Total upstream pack jars (including dependencies and existing client extras) |
|---|---:|---:|---:|---:|
| 1.21.1 | 19 | 67 | 5 | 66 |
| 1.21.11 | 20 | 68 | 5 | 77 |
| 26.2 | 24 | 62 | 5 | 64 |
| 26.3 | 25 | 61 | 5 | 59 |

Connectivity, Cupboard, Fast Async World Save, GPU Memory Leak Fix, and Identify have no verified Modrinth project. These are explicit gaps, not silently substituted jars.

GoodMC: Old Combat & Blockhitting (`goodmc`) was removed from every version in 1.2.3; it previously shipped on 26.2 and 26.3. `tools/sync_instance_mods.py` excludes it through `REMOVED`, so refreshing the pack cannot add it back, and the 26.2/26.3 manifests list it under `retired` with its published hashes. The launcher moves copies it installed to `.lads-mod-cache\retired\goodmc\` (recoverable) and leaves copies you added or modified in place. LadsCore's separate LegacySwing module is unchanged.

1.4.6: Lads Core embeds Capes, Controlling with Searchables, Entity Culling, Entity Texture Features, Entity Model Features, Fix Book GUI, Hovering Hotbar, Ksyxis, Lazy AI, NBT Autocomplete, Optimized Cushions, Quick-Pack, Server Pinger Fixer, Fast IP Ping, Tooltips TXF, World Play Time (Reborn) and ImmediatelyFast (`NATIVE` in `tools/sync_instance_mods.py`), and the manifests retire their jars. Gamma Utils, MotionBlur Plus (with Satin), Sound Physics Remastered and ClientSort left the pack (`REMOVED`); Puzzles Lib and Forge Config API Port only stay on 1.21.11, where Paper Doll needs them.

| Source mod | 1.21.1 | 1.21.11 | 26.2 | 26.3 |
|---|---|---|---|---|
| 3d-Skin-Layers | Modrinth | Modrinth | Modrinth | Modrinth |
| AppleSkin | Modrinth | Modrinth | Native | Native |
| Architectury | Modrinth | Modrinth | Modrinth | Modrinth |
| Async Logger | Modrinth | Modrinth | Modrinth | Modrinth |
| AutoHideHUD | Native | Native | Modrinth | Modrinth |
| AutoReconnect | Modrinth | Modrinth | Native | Native |
| BadOptimizations | Modrinth | Modrinth | Modrinth | Modrinth |
| Balm | Modrinth | Modrinth | Modrinth | Modrinth |
| Better Statistics Screen | Modrinth | Modrinth | Modrinth | Modrinth |
| BetterRenderDistance | Modrinth | Modrinth | Modrinth | Modrinth |
| Capes | Native | Native | Native | Native |
| Chat Heads | Modrinth | Modrinth | Modrinth | Modrinth |
| Chat Signing Hider | Modrinth | Modrinth | Native | Native |
| Classic Minecraft Icon | Native | Native | Native | Native |
| Cloth Config v26.3 | Modrinth | Modrinth | Modrinth | Modrinth |
| Clumps | Modrinth | Modrinth | Native | Native |
| Collective | Modrinth | Modrinth | Modrinth | Modrinth |
| Concurrent Chunk Management Engine | Modrinth | Modrinth | Modrinth | Modrinth |
| Configured Defaults | Modrinth | Modrinth | Modrinth | Modrinth |
| Connectivity Mod | Missing | Missing | Missing | Missing |
| Continuity | Modrinth | Modrinth | Modrinth | Modrinth |
| Controlling | Native | Native | Native | Native |
| cupboard | Missing | Missing | Missing | Missing |
| Custom FOV | Modrinth | Modrinth | Modrinth | Modrinth |
| Drippy Loading Screen | Modrinth | Modrinth | Modrinth | Modrinth |
| Dynamic FPS | Modrinth | Modrinth | Native | Native |
| Effectual | Modrinth | Modrinth | Modrinth | Modrinth |
| Entity Model Features | Native | Native | Native | Native |
| Entity Texture Features | Native | Native | Native | Native |
| EntityCulling | Native | Native | Native | Native |
| essential-container | Modrinth | Modrinth | Modrinth | Modrinth |
| Euphoria Patcher | Modrinth | Modrinth | Modrinth | Modrinth |
| Fabric API | Modrinth | Modrinth | Modrinth | Modrinth |
| Fabric Language Kotlin | Modrinth | Modrinth | Modrinth | Modrinth |
| FancyMenu | Modrinth | Modrinth | Modrinth | Modrinth |
| Fast IP Ping | Native | Native | Native | Native |
| fastasyncworldsave Mod | Missing | Missing | Missing | Missing |
| Faster Shadow Mapper | Modrinth | Modrinth | Modrinth | Modrinth |
| FerriteCore | Modrinth | Modrinth | Modrinth | Modrinth |
| FixBookGUI | Native | Native | Native | Native |
| Forge Config API Port | Removed | Modrinth | Removed | Removed |
| FramePacer | Modrinth | Modrinth | Modrinth | Modrinth |
| Fzzy Config | Modrinth | Modrinth | Modrinth | Modrinth |
| Gpu memory leak fix mod | Missing | Missing | Missing | Missing |
| Hovering Hotbar | Native | Native | Native | Native |
| Identify | Missing | Missing | Missing | Missing |
| ImmediatelyFast | Native | Native | Native | Native |
| Iris | Modrinth | Modrinth | Modrinth | Modrinth |
| Ixeris | Modrinth | Modrinth | Modrinth | Modrinth |
| Jade | Modrinth | Modrinth | Modrinth | Modrinth |
| Jasione | Modrinth | Modrinth | Modrinth | Modrinth |
| Just Enough Items | Modrinth | Modrinth | Modrinth | Modrinth |
| Konkrete | Modrinth | Modrinth | Modrinth | Modrinth |
| Ksyxis | Native | Native | Native | Native |
| LambDynamicLights | Modrinth | Modrinth | Modrinth | Modrinth |
| Lazy AI | Native | Native | Native | Native |
| LibJF | Modrinth | Modrinth | Modrinth | Modrinth |
| Lithium | Modrinth | Modrinth | Modrinth | Modrinth |
| Melody | Modrinth | Modrinth | Modrinth | Modrinth |
| MezzConfig | Modrinth | Modrinth | Modrinth | Modrinth |
| Mod Menu | Modrinth | Modrinth | Modrinth | Modrinth |
| ModernFix | Modrinth | Modrinth | Modrinth | Modrinth |
| Modpack Core Essentials | Modrinth | Modrinth | Modrinth | Modrinth |
| More Culling | Modrinth | Modrinth | Modrinth | Modrinth |
| Mouse Tweaks | Modrinth | Modrinth | Modrinth | Modrinth |
| MRU | Modrinth | Modrinth | Modrinth | Modrinth |
| NBT Autocomplete | Native | Native | Native | Native |
| Network Protocol Disconnect | Modrinth | Modrinth | Modrinth | Modrinth |
| No Resource Pack Warnings | Modrinth | Modrinth | Modrinth | Modrinth |
| NoPackCompatCheck | Modrinth | Modrinth | Modrinth | Modrinth |
| NotEnoughAnimations | Modrinth | Modrinth | Modrinth | Modrinth |
| Optimized block entities | Modrinth | Modrinth | Modrinth | Modrinth |
| Optimized Cushions | Modrinth | Modrinth | Modrinth | Native |
| Placeholder API | Modrinth | Modrinth | Modrinth | Modrinth |
| Puzzles Lib | Removed | Modrinth | Removed | Removed |
| quick-pack | Native | Native | Native | Native |
| Reese's Sodium Options | Modrinth | Modrinth | Modrinth | Modrinth |
| Resourcify | Modrinth | Modrinth | Modrinth | Modrinth |
| Searchables | Native | Native | Native | Native |
| Server Pinger Fixer | Native | Native | Native | Native |
| Simple Voice Chat | Modrinth | Modrinth | Modrinth | Modrinth |
| Sodium | Modrinth | Modrinth | Modrinth | Modrinth |
| Sodium Extra | Modrinth | Modrinth | Modrinth | Modrinth |
| TCDCommons API | Modrinth | Modrinth | Modrinth | Modrinth |
| Threads | Native | Native | Native | Native |
| TLib | Modrinth | Modrinth | Modrinth | Modrinth |
| Tooltips TXF | Native | Native | Native | Native |
| ViaFabricPlus | Modrinth | Modrinth | Modrinth | Modrinth |
| WaveyCapes | Modrinth | Modrinth | Modrinth | Modrinth |
| World Play Time Reborn | Modrinth | Native | Native | Native |
| Xaero's Minimap | Modrinth | Modrinth | Modrinth | Modrinth |
| Xaero's World Map | Modrinth | Modrinth | Modrinth | Modrinth |
| YetAnotherConfigLib | Modrinth | Modrinth | Modrinth | Modrinth |

Exact project IDs, implementation names and release IDs are in `mod-coverage.json`; hashes, licenses and source links are in each `game-mods/<version>/client-mods.json`.
