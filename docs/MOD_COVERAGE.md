# Instance mod coverage

Source: The Lads Client Dev 26.3, audited 2026-09-28. 99 jars contain 98 unique Fabric mod IDs. The duplicate Faster Shadow Mapper jar is collapsed to one compatible release. GoodMC was removed from the pack in 1.2.3, so the tables cover the other 97.

Native means implemented inside Core. Modrinth means an unchanged upstream jar downloaded through its API. Original means an older original project supplies the feature instead of the source instance's newer fork; this is not a claim of identical fork behavior. Missing means it is not included. Removed means the pack stopped shipping it because nothing needs it any more.

| Version | Native source features | Upstream source features | Missing | Total upstream pack jars (including dependencies and existing client extras) |
|---|---:|---:|---:|---:|
| 1.21.1 | 19 | 67 | 5 | 66 |
| 26.3 | 25 | 60 | 5 | 59 |

Connectivity, Cupboard, Fast Async World Save, GPU Memory Leak Fix, and Identify have no verified Modrinth project. These are explicit gaps, not silently substituted jars.

GoodMC: Old Combat & Blockhitting (`goodmc`) was removed from every version in 1.2.3; it previously shipped on 26.3. `tools/sync_instance_mods.py` excludes it through `REMOVED`, so refreshing the pack cannot add it back, and the 26.3 manifest lists it under `retired` with its published hashes. The launcher moves copies it installed to `.lads-mod-cache\retired\goodmc\` (recoverable) and leaves copies you added or modified in place. LadsCore's separate LegacySwing module is unchanged.

1.4.6: Lads Core embeds Capes, Controlling with Searchables, Entity Culling, Entity Texture Features, Entity Model Features, Fix Book GUI, Hovering Hotbar, Ksyxis, Lazy AI, NBT Autocomplete, Optimized Cushions, Quick-Pack, Server Pinger Fixer, Fast IP Ping, Tooltips TXF, World Play Time (Reborn) and ImmediatelyFast (`NATIVE` in `tools/sync_instance_mods.py`), and the manifests retire their jars. Gamma Utils, MotionBlur Plus (with Satin), Sound Physics Remastered and ClientSort left the pack (`REMOVED`); Puzzles Lib and Forge Config API Port only stay on 1.21.11, where Paper Doll needs them.

1.7.0: NoPackCompatCheck left the 26.3 pack (`REMOVED`). No Resource Pack Warnings, still shipped, already hides the outdated-pack warning and confirmation on the resource and data pack screens and lets such packs load. 3D Skin Layers is shipped again on 26.3 and (Forge 1.2.0) 1.8.9: the SkinLayers module switches its player layers.

| Source mod | 1.21.1 | 26.3 |
|---|---|---|
| 3d-Skin-Layers | Modrinth | Modrinth |
| AppleSkin | Modrinth | Native |
| Architectury | Modrinth | Modrinth |
| Async Logger | Modrinth | Modrinth |
| AutoHideHUD | Native | Modrinth |
| AutoReconnect | Modrinth | Native |
| BadOptimizations | Modrinth | Modrinth |
| Balm | Modrinth | Modrinth |
| Better Statistics Screen | Modrinth | Modrinth |
| BetterRenderDistance | Modrinth | Modrinth |
| Capes | Native | Native |
| Chat Heads | Modrinth | Modrinth |
| Chat Signing Hider | Modrinth | Native |
| Classic Minecraft Icon | Native | Native |
| Cloth Config v26.3 | Modrinth | Modrinth |
| Clumps | Modrinth | Native |
| Collective | Modrinth | Modrinth |
| Concurrent Chunk Management Engine | Modrinth | Modrinth |
| Configured Defaults | Modrinth | Modrinth |
| Connectivity Mod | Missing | Missing |
| Continuity | Modrinth | Modrinth |
| Controlling | Native | Native |
| cupboard | Missing | Missing |
| Custom FOV | Modrinth | Modrinth |
| Drippy Loading Screen | Modrinth | Modrinth |
| Dynamic FPS | Modrinth | Native |
| Effectual | Modrinth | Modrinth |
| Entity Model Features | Native | Native |
| Entity Texture Features | Native | Native |
| EntityCulling | Native | Native |
| essential-container | Modrinth | Modrinth |
| Euphoria Patcher | Modrinth | Modrinth |
| Fabric API | Modrinth | Modrinth |
| Fabric Language Kotlin | Modrinth | Modrinth |
| FancyMenu | Modrinth | Modrinth |
| Fast IP Ping | Native | Native |
| fastasyncworldsave Mod | Missing | Missing |
| Faster Shadow Mapper | Modrinth | Modrinth |
| FerriteCore | Modrinth | Modrinth |
| FixBookGUI | Native | Native |
| Forge Config API Port | Removed | Removed |
| FramePacer | Modrinth | Modrinth |
| Fzzy Config | Modrinth | Modrinth |
| Gpu memory leak fix mod | Missing | Missing |
| Hovering Hotbar | Native | Native |
| Identify | Missing | Missing |
| ImmediatelyFast | Native | Native |
| Iris | Modrinth | Modrinth |
| Ixeris | Modrinth | Modrinth |
| Jade | Modrinth | Modrinth |
| Jasione | Modrinth | Modrinth |
| Just Enough Items | Modrinth | Modrinth |
| Konkrete | Modrinth | Modrinth |
| Ksyxis | Native | Native |
| LambDynamicLights | Modrinth | Modrinth |
| Lazy AI | Native | Native |
| LibJF | Modrinth | Modrinth |
| Lithium | Modrinth | Modrinth |
| Melody | Modrinth | Modrinth |
| MezzConfig | Modrinth | Modrinth |
| Mod Menu | Modrinth | Modrinth |
| ModernFix | Modrinth | Modrinth |
| Modpack Core Essentials | Modrinth | Modrinth |
| More Culling | Modrinth | Modrinth |
| Mouse Tweaks | Modrinth | Modrinth |
| MRU | Modrinth | Modrinth |
| NBT Autocomplete | Native | Native |
| Network Protocol Disconnect | Modrinth | Modrinth |
| No Resource Pack Warnings | Modrinth | Modrinth |
| NoPackCompatCheck | Modrinth | Removed |
| NotEnoughAnimations | Modrinth | Modrinth |
| Optimized block entities | Modrinth | Modrinth |
| Optimized Cushions | Modrinth | Native |
| Placeholder API | Modrinth | Modrinth |
| Puzzles Lib | Removed | Removed |
| quick-pack | Native | Native |
| Reese's Sodium Options | Modrinth | Modrinth |
| Resourcify | Modrinth | Modrinth |
| Searchables | Native | Native |
| Server Pinger Fixer | Native | Native |
| Simple Voice Chat | Modrinth | Modrinth |
| Sodium | Modrinth | Modrinth |
| Sodium Extra | Modrinth | Modrinth |
| TCDCommons API | Modrinth | Modrinth |
| Threads | Native | Native |
| TLib | Modrinth | Modrinth |
| Tooltips TXF | Native | Native |
| ViaFabricPlus | Modrinth | Modrinth |
| WaveyCapes | Modrinth | Modrinth |
| World Play Time Reborn | Modrinth | Native |
| Xaero's Minimap | Modrinth | Modrinth |
| Xaero's World Map | Modrinth | Modrinth |
| YetAnotherConfigLib | Modrinth | Modrinth |

Exact project IDs, implementation names and release IDs are in `mod-coverage.json`; hashes, licenses and source links are in each `game-mods/<version>/client-mods.json`.
