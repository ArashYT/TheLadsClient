# Zero SMP mod table (128 jars of the baseline pack)

Columns: **rt** = runtime FPS/TPS cost, **mem** = RAM/startup cost (H high, M medium, L low, - none). **side**: C client-only, B both, S server-side content/worldgen. **action** is the static verdict; see ZERO_SMP_PERFORMANCE.md for the evidence. mixins/OW/RD = mixin classes, @Overwrite, @Redirect counted from the jar. Sorted by cost.

| mod jar | category | side | rt | mem | classes | mixins | OW | RD | update | action | note |
|---|---|---|---|---|---:|---:|---:|---:|---|---|---|
| DistantHorizons-3.3.3-1.20.1-fabric-forge.jar | Rendering (LOD) | B | H | H | 1764 | 37 | 0 | 4 |  | config | 37 mixins, 54 jar-in-jar libs. Server does NOT have DH (join log: 'Client has mods that are missing on server: ... distanthorizons'), so LODs only come from chunks the client has received. User already cut radius 256->64, threads 15->3. |
| grazziergrass-forge-1.4.5+mc1.20.1.jar | Visual (grass) | C | H | M | 48 | 4 | 0 | 0 |  | config | Procedural blades through custom shaders: bladesPerBlock=10, renderRadius=160, grassPlantsAsBlades, denseFlowers, bladeParticles. Biggest per-frame GPU/CPU knob in grassy biomes. |
| Quark-4.0-462.jar | Content/worldgen | B | M | H | 936 | 116 | 0 | 0 |  | config | 116 mixins, 164 modules ALL enabled, 14.6 MB. Largest single source of extra worldgen features and tick hooks in the pack (most modules are on by default). |
| supplementaries-1.20-3.1.43-forge.jar | Content | B | M | H | 1106 | 109 | 0 | 0 |  | keep | 109 mixins, 13 MB; many block-entity renderers + shader effects; Moonlight runtime assets each reload. |
| create-1.20.1-6.0.8.jar | Content/tech | B | M | H | 2687 | 57 | 0 | 1 |  | keep | 57 mixins, 18.7 MB; Flywheel instancing (backend DEFAULT, workerThreads=-1). Contraptions are the usual FPS/TPS sink. |
| alexscaves-2.0.2.jar | Content/worldgen/mobs | B | M | H | 1303 | 54 | 0 | 0 |  | config | 73 MB jar, 54 mixins, custom cave biomes + sky/fog overrides. DH log: 'partially incompatible'. pathfinding_threads=5, cave_map_search_attempts=128000 (map search spike on the server thread if not async). Needs Citadel. |
| open-parties-and-claims-forge-1.20.1-0.30.3.jar | Server/claims | B | M | M | 977 | 67 | 0 | 0 | forge-1.20.1-0.32.7 | update | 67 mixins; claim checks on block/entity interaction, explosions, fluid flow. 0.32.7 available (and Xaero 26.6.0 REQUIRES OPAC >= 0.32.4, see crash 2026-10-08 16:56). |
| entity_model_features-3.3.11-1.20.1-forge.jar | Rendering (CEM) | C | M | M | 282 | 64 | 0 | 0 |  | keep | 64 mixins into every entity renderer. Cost scales with CEM packs (Fresh Animations in resourcepacks/ is the heavy case). animationLODDistance=20. |
| entity_texture_features-7.2.5-1.20.1-forge.jar | Rendering | C | M | M | 268 | 60 | 0 | 0 |  | keep | 60 mixins; textureUpdateFrequency Fast. Pair with EMF. |
| moonlight-1.20-2.16.34-forge.jar | Library | B | M | M | 511 | 53 | 3 | 2 | 1.20-2.16.36-forge | update | 53 mixins; generates Supplementaries/Amendments dynamic packs every reload (545 ms). 2.16.36 available. |
| xaerominimap-forge-1.20.1-26.4.2.jar | Map | C | M | M | 689 | 22 | 0 | 0 | forge-1.20.1-26.6.0 | update | 26.6.0 available but needs OPAC >= 0.32.4. |
| xaeroworldmap-forge-1.20.1-1.45.0.jar | Map | C | M | M | 370 | 13 | 0 | 0 | forge-1.20.1-1.47.0 | update | max_loaded_regions=300; 1.47.0 available (matches minimap 26.6.0). |
| AmbientSounds_FORGE_v6.3.8_mc1.20.1.jar | Audio | C | M | M | 64 | 2 | 0 | 0 | 6.3.10 | update | 52 MB of sound; scans surroundings every few ticks (scanStepAmount=100). 6.3.10 released 2026-10-08. Needs CreativeCore. |
| alexsmobs-1.22.9.jar | Content/mobs | B | M | M | 1118 | 0 | 0 | 0 |  | config | ~90 spawn json configs; mob density is the TPS driver, not the mod itself. In Control spawn.json is empty so nothing caps it. |
| Presence Footsteps [FORGE] 1.0.0.jar | Audio | C | M | M | 94 | 0 | 0 | 0 |  | keep | Unofficial Forge port. Per-entity footstep simulation (maxSteppingEntities=50). |
| particular-1.20.1-Forge-1.5.7.jar | Visual (particles) | C | M | L | 87 | 27 | 0 | 0 |  | config | 27 mixins; 15 effects all enabled incl. rainRipples/waterDripRipples (duplicate of ParticleRain/Immersive Winds), fallingLeaves, fireflies, caveDust. |
| SnowRealMagic-1.20.1-Forge-10.7.0.jar | World/visual | B | M | L | 63 | 23 | 0 | 0 |  | keep | 23 mixins; snow layers accumulate through random ticks => server cost in winter biomes; weatherTickSlowness=16. |
| ParticleEffects-1.6.0+1.20.1+forge.jar | Visual (particles) | C | M | L | 54 | 19 | 0 | 0 |  | keep | 19 mixins (ParticleEngine x3, LevelRenderer x2, LivingEntity). Overlaps Particular for some effects. |
| watut-forge-1.20.1-1.2.3.jar | Network/animation | B | M | L | 71 | 18 | 0 | 1 |  | config | 18 mixins; sendMouseInfo/sendTypingSpeed/sendActiveGui packets continuously to all players in range (tick rate 10). |
| particlerain-4.0.1+1.20.1-forge.jar | Weather/visual | C | M | L | 116 | 16 | 0 | 0 |  | config | maxParticleAmount 1500, density 75/100, distance 16. Primary rain renderer (vanilla rain disabled). |
| tectonic-3.0.17-forge-1.20.1.jar | Worldgen | S | M | L | 56 | 16 | 0 | 0 |  | keep | Terrain density rewrite; only worldgen cost, but it is the reason chunk gen is slower than vanilla. |
| immersive_aircraft-1.4.1+1.20.1-forge.jar | Content | B | M | L | 199 | 15 | 0 | 0 | 1.5.0+1.20.1 | update | 15 mixins; renderDistance 192. 1.5.0 available. |
| continuity-3.0.0+1.20.1.forge.jar | Visual (CTM) | C | M | L | 177 | 13 | 0 | 0 |  | verify | Connected textures; only matters if a CTM resource pack is active. Fabric mod via Connector. use_manual_culling=true. |
| sound-physics-remastered-forge-1.20.1-1.5.1.jar | Audio | C | M | L | 91 | 11 | 0 | 1 |  | config | ray_count 32 x bounces 4 per sound source, 16 occlusion rays, max_sound_processing_distance 512, level clone range 4. |
| immersive-winds-1.20.1-0.9.jar | Weather/visual | B | M | L | 51 | 5 | 0 | 0 |  | config | Fabric mod through Connector. Its feature list includes particle rain/snow, rain splash particles, swaying grass/lanterns/fire and a wind system (rainParticleCount=200, rainBox 15x15): the same job as ParticleRain + Particular. 12 of its rain-impact sound files are missing in the jar (log WARN x12). |
| wakes-1.20.1-Forge-1.4.1.jar | Visual (water) | C | M | L | 63 | 4 | 0 | 0 |  | config | wakeResolution SIXTEEN; flood-fill every wake per fluid surface. |
| geckolib-forge-1.20.1-4.8.4 (1).jar | Library | B | M | L | 260 | 3 | 0 | 0 |  | keep | Animated-entity cost scales with entity count (Alex's, Mowzie's, Creeper/Enderman Overhaul). |
| Waves-1.20.1-1.6a.jar | Visual (water) | C | M | L | 42 | 3 | 0 | 0 |  | config | Entity-like wave spawner along every shoreline: waveSpawnFrequency=20, searchDistance=14, parallelProcessing=true. |
| mowziesmobs-1.8.2.jar | Content/mobs | B | L | H | 794 | 1 | 0 | 0 |  | keep | 35 MB. Two broken block models (black_pink_grottol, diamond_grottol) log ERROR each reload. |
| Connector-1.0.0-beta.49+1.20.1.jar | Loader | B | L | H | 1111 | 0 | 0 | 0 |  | keep | Sinytra Connector beta.49. Only 3 jars need Fabric->Forge transformation (continuity, immersive-winds, theladscore); cache is 24 MB. Fixed cost, not scaling with pack size. |
| oculus-mc1.20.1-1.8.0.jar | Rendering (shaders) | C | L | M | 1587 | 180 | 6 | 44 |  | keep | 180 mixins, 44 @Redirect, patches ~12 Embeddium classes => Embeddium prints 'instance is now tainted'. enableShaders=false at the moment; cost is mostly mixin overhead until a shader pack is on. DH only partially supports Oculus. |
| Kiwi-1.20.1-Forge-11.10.3.jar | Library | B | L | M | 808 | 44 | 0 | 0 |  | keep | 808 classes, 44 mixins (3 into Block, 2 into BlockItem). Needed by SnowRealMagic. |
| exposure-forge-1.20.1-1.9.20.jar | Content (camera) | B | L | M | 574 | 37 | 0 | 3 | 1.9.22 | update | 37 mixins incl. Entity, Player, LivingEntity, GameRenderer, Camera, MouseHandler, KeyboardHandler; photographs stay as GPU textures. 1.9.22 available. |
| backpacked-forge-1.20.1-3.0.9.jar | Content | B | L | M | 433 | 28 | 0 | 0 |  | keep | 28 mixins; backpack models per player; needs Framework. |
| citadel-2.6.3-1.20.1.jar | Library | B | L | M | 1419 | 16 | 0 | 3 |  | keep | 1419 classes; Alex's Mobs/Caves base. Does a blocking patreon HTTP check during resource reload ('Online mod data expired' in log). |
| Zeta-1.0-31.jar | Library | B | L | M | 696 | 16 | 0 | 0 |  | keep | Quark's engine; 696 classes. |
| PuzzlesLib-v8.0.15-1.20.1-Forge.jar | Library | B | L | M | 516 | 12 | 0 | 0 | 8.1.35 | update | 516 classes. 8.1.35 available (check EasyAnvils/EasyMagic compat before bumping). |
| CreativeCore_FORGE_v2.12.39_mc1.20.1.jar | Library | B | L | M | 642 | 11 | 0 | 0 | 2.12.40 | update | 642 classes, 11 mixins; only AmbientSounds needs it. 2.12.40 available. |
| konkrete_forge_1.8.0_MC_1.20-1.20.1.jar | Library | C | L | M | 399 | 5 | 0 | 0 |  | keep | Only JustZoom needs it. |
| mahoutsukai-1.20.1-v1.34.74.jar | Content | B | L | M | 713 | 2 | 0 | 0 |  | keep | 713 classes; log shows ~25 'Incorrect key projection.*' config keys on first world load (one-off). |
| Chimes-v2.1.1-1.20.1-Forge.jar | Audio | B | L | M | 41 | 0 | 0 | 0 |  | keep | 12 MB of sounds. |
| fabric-api-0.92.6+1.11.15+1.20.1.jar | Loader (Forgified FAPI) | B | L | M | 2 | 0 | 0 | 0 |  | keep | 45 jar-in-jar modules; required by Connector for the 3 Fabric mods. |
| fzzy_config-0.7.6+1.20.1+forge.jar | Library (Kotlin) | B | L | M | 754 | 0 | 0 | 0 | 0.7.7+1.20.1+forge | orphan | Only particle_core uses it; Lads Core replaces particle_core => removable together with Kotlin for Forge. |
| jei-1.20.1-forge-15.49.0.191.jar | UI | C | L | M | 988 | 0 | 0 | 0 | 15.62.0.219 | update | Startup 2.0 s on world join. 15.62.0.219 available (2026-10-04). |
| kotlinforforge-4.12.0-all.jar | Library | B | L | M | 4314 | 0 | 0 | 0 |  | orphan | 7 MB; only fzzy_config and particle_core need it. |
| voicechat-forge-1.20.1-2.6.22.jar | Audio/network | B | L | M | 674 | 0 | 0 | 0 | forge-1.20.1-2.6.24 | update | Client voice chat currently disabled=true in voicechat-client.properties. 2.6.24 available. |
| amendments-1.20-2.2.6.jar | Content (Supplementaries addon) | B | L | L | 254 | 52 | 2 | 2 |  | keep | 52 mixins, 2 @Overwrite. Moonlight dynamic assets add ~64 ms per resource reload. |
| rubidium-extra-0.5.4.4+mc1.20.1-build.131.jar | Rendering | C | L | L | 76 | 45 | 0 | 1 |  | keep | Embeddium Extra, 45 mixins. Sodium-Extra options file has everything on. |
| integrated_api-forge-1.20.1-1.8.0.jar | Library | B | L | L | 202 | 32 | 1 | 0 | 1.8.2 | update | 32 mixins (ChunkGenerator, LocateCommand). 1.8.2 available. |
| artifacts-forge-9.5.19.jar | Content | B | L | L | 178 | 24 | 0 | 0 |  | keep | 24 mixins, 6 into LivingEntity (equipment ticking via Curios). |
| theladscore.jar | Lads Core | C | L | L | 333 | 21 | 0 | 6 |  | keep | Fabric mod through Connector. |
| lithostitched-forge-1.20.1-1.4.11.jar | Worldgen API | S | L | L | 163 | 20 | 0 | 2 |  | keep | 20 mixins; needed by Tectonic. |
| architectury-9.2.14-forge.jar | Library | B | L | L | 382 | 19 | 0 | 0 |  | keep | 19 mixins; needed by Artifacts, FTB Library. |
| balm-forge-1.20.1-7.3.42.jar | Library | B | L | L | 403 | 16 | 0 | 0 | 7.3.44+forge-1.20.1 | update | Needed by Waystones. 7.3.44 available. |
| FarmersDelight-1.20.1-1.3.3.jar | Content | B | L | L | 289 | 14 | 0 | 0 | 1.20.1-1.3.4 | update | 1.3.4 available. |
| player-animation-lib-forge-1.0.2-rc1+1.20.jar | Library | B | L | L | 115 | 14 | 0 | 2 |  | orphan | 114 classes + 14 mixins (LivingEntityRenderer RD x2, PlayerModel); no jar references it. |
| skinlayers3d-forge-1.11.3-mc1.20.1.jar | Visual | C | L | L | 79 | 14 | 0 | 0 |  | lads | Lads Core replaces. |
| curios-forge-5.14.1+1.20.1.jar | Library/content | B | L | L | 143 | 13 | 0 | 0 |  | keep |  |
| notenoughanimations-forge-1.12.6-mc1.20.1.jar | Animation | C | L | L | 74 | 13 | 0 | 0 |  | lads | Lads Core replaces. |
| YungsApi-1.20-Forge-4.0.6.jar | Library | B | L | L | 167 | 12 | 0 | 0 |  | keep |  |
| GlitchCore-forge-1.20.1-0.0.1.1.jar | Library | B | L | L | 76 | 11 | 14 | 1 |  | keep | Used by SnowRealMagic (14 @Overwrite in its mixins!). |
| polymorph-forge-0.49.10+1.20.1.jar | QoL | B | L | L | 91 | 11 | 0 | 2 | 0.49.11+1.20.1 | update | Recipe-conflict resolver; mixes into recipe lookup. 0.49.11 available. |
| Data_Anchor-forge-1.20.1-1.0.0.22.jar | Library | B | L | L | 75 | 10 | 0 | 0 | 1.0.0.23-forge | orphan | Nothing references it, yet its 10 mixins hit Entity, ServerLevel, ServerPlayer, PlayerList. |
| immersive_armors-1.7.2+1.20.1-forge.jar | Content | B | L | L | 90 | 10 | 0 | 0 |  | keep |  |
| Corgilib-Forge-1.20.1-4.0.3.5.jar | Library | B | L | L | 151 | 8 | 2 | 0 |  | orphan | No jar references it (no mods.toml dependency, no class reference). |
| immersivehotbar-1.2.2-forge-1.20.1-all.jar | HUD | C | L | L | 49 | 8 | 0 | 1 |  | keep | 5 mixins in Gui. |
| lootr-forge-1.20-0.7.35.94.jar | Server/QoL | B | L | L | 100 | 8 | 0 | 2 |  | keep | Per-player loot: 8 mixins. |
| ecologics-forge-1.20.1-2.2.7.jar | Content | B | L | L | 109 | 7 | 0 | 0 |  | keep |  |
| another_furniture-forge-1.20.1-3.0.4.jar | Content | B | L | L | 85 | 6 | 0 | 1 |  | keep | Create addon, small. |
| endermanoverhaul-forge-1.20.1-1.0.4.jar | Mobs | B | L | L | 115 | 6 | 0 | 0 |  | keep | GeckoLib models. |
| yet_another_config_lib_v3-3.6.6+1.20.1-forge.jar | Library | C | L | L | 344 | 6 | 0 | 1 |  | keep | ParticleEffects and Immersive Hotbar use it. |
| YungsBetterNetherFortresses-1.20-Forge-2.0.6.jar | Worldgen | S | L | L | 31 | 6 | 0 | 0 |  | keep |  |
| carryon-forge-1.20.1-2.1.2.7.jar | QoL | B | L | L | 79 | 5 | 0 | 0 |  | keep | 5 mixins (Entity/Player/Block). |
| Controlling-forge-1.20.1-12.0.2.jar | UI | C | L | L | 39 | 5 | 0 | 0 |  | keep | Needs Searchables. |
| EasyAnvils-v8.0.2-1.20.1-Forge.jar | QoL | B | L | L | 52 | 5 | 0 | 0 |  | keep | Needs PuzzlesLib. |
| EasyMagic-v8.0.1-1.20.1-Forge.jar | QoL | B | L | L | 34 | 5 | 0 | 0 |  | keep | Needs PuzzlesLib. |
| ftb-library-forge-2001.2.13.jar | Library | B | L | L | 318 | 5 | 0 | 0 |  | keep |  |
| idas_forge-1.13.0+1.20.1.jar | Worldgen (structures) | B | L | L | 19 | 5 | 0 | 0 |  | keep | References Ice and Fire / Ars Nouveau / BYG / BoP that are not installed: ~20 datapack ERROR lines per reload (harmless, but repeated on every world load). |
| justzoom_forge_2.1.1_MC_1.20.1.jar | QoL | C | L | L | 24 | 5 | 0 | 0 | 3.0.1-1.20.1-forge | keep | Needs Konkrete (399 classes) for a zoom key. 3.0.1 available. |
| Paraglider-forge-20.1.3.jar | Content | B | L | L | 222 | 4 | 0 | 0 |  | keep | 222 classes, per-tick player state. |
| YungsBetterMineshafts-1.20-Forge-4.0.4.jar | Worldgen | S | L | L | 50 | 4 | 0 | 0 |  | keep |  |
| comforts-forge-6.4.0+1.20.1.jar | Content | B | L | L | 59 | 3 | 0 | 0 |  | keep |  |
| waystones-forge-1.20.1-14.1.20.jar | Content | B | L | L | 189 | 3 | 0 | 0 | 14.1.21+forge-1.20.1 | update | 14.1.21 available. |
| weaponmaster_ydm-forge-1.20.1-4.2.3.jar | Content | B | L | L | 72 | 3 | 0 | 0 |  | keep |  |
| bushierflowers-0.0.3-1.20.1.jar | Visual | B | L | L | 12 | 2 | 0 | 0 |  | keep | Tiny. Overlaps with the 'xali's Bushy Leaves' resource pack in the resourcepacks folder. |
| configured-forge-1.20.1-2.2.3.jar | UI | C | L | L | 120 | 2 | 0 | 0 |  | keep | Config UI; 'catalogue' bundled. |
| integrated_stronghold-1.1.1+1.20.1-forge.jar | Worldgen | B | L | L | 7 | 2 | 0 | 0 |  | keep |  |
| integrated_villages-1.3.2+1.20.1-forge.jar | Worldgen | B | L | L | 19 | 2 | 0 | 0 |  | keep | Tag minecraft:village refers to a missing swamp_village (4 ERROR lines per reload). |
| Jade-1.20.1-Forge-11.13.3.jar | HUD | C | L | L | 242 | 2 | 0 | 0 |  | lads | Lads Core replaces. |
| Necronomicon-Forge-1.6.0+1.20.1.jar | Library | B | L | L | 28 | 2 | 0 | 0 |  | keep | Needed by YouShallNotSpawn. |
| oceanicrealms-1.0.7-last-laster-ver.jar | Content | B | L | L | 166 | 2 | 0 | 0 |  | keep |  |
| coolrain-1.20.1-Forge-1.0.3.jar | Audio (rain) | C | L | L | 7 | 1 | 0 | 0 |  | verify | Sound-only mod (rain-on-block ambience). Third rain-sound system next to ParticleRain (rain/wind volumes) and Immersive Winds (rain_impact_* events, whose 12 .ogg files are missing: 6x foliage, 6x rain). Keep one. |
| createdeco-2.0.3-1.20.1-forge.jar | Content | B | L | L | 68 | 1 | 0 | 0 |  | keep |  |
| creeperoverhaul-3.0.2-forge.jar | Mobs | B | L | L | 49 | 1 | 0 | 0 |  | keep | GeckoLib models. |
| ftb-essentials-forge-2001.2.4.jar | Server utility | B | L | L | 64 | 1 | 0 | 0 |  | keep |  |
| ImmersiveThunder-forge-1.2.3.jar | Weather | C | L | L | 3 | 1 | 0 | 1 |  | keep | 3 classes; 6 MB of sounds. |
| midnightlib-forge-1.9.2+1.20.1.jar | Library | B | L | L | 24 | 1 | 0 | 0 |  | orphan | Only Cull Leaves needs it. |
| Searchables-forge-1.20.1-1.0.3.jar | Library | C | L | L | 41 | 1 | 0 | 0 |  | keep | Needed by Controlling. |
| YouShallNotSpawn-forge-1.20.x-2.0.2.jar | Server utility | S | L | L | 8 | 1 | 0 | 0 |  | config | disabled_entities.json5 still holds only the example entries. |
| BetterAdvancements-Forge-1.20.1-0.6.0.73.jar | UI | C | L | L | 37 | 0 | 0 | 0 |  | keep | 675 advancements loaded on each datapack reload (Quark + modded). |
| cloth-config-11.1.136-forge.jar | Library | B | L | L | 633 | 0 | 0 | 0 |  | keep | Needed by Artifacts, MoreCulling; optional for SPR/Voice Chat. |
| coroutil-forge-1.20.1-1.3.7.jar | Library | B | L | L | 44 | 0 | 0 | 0 |  | keep | Needed by WATUT. |
| cosmeticarmorreworked-1.20.1-v1a.jar | QoL | B | L | L | 40 | 0 | 0 | 0 |  | keep |  |
| framework-forge-1.20.1-0.8.0.jar | Library | B | L | L | 238 | 0 | 0 | 0 |  | keep | Needed by Backpacked. |
| immersive_paintings-0.6.13+1.20.1-forge.jar | Content | B | L | L | 93 | 0 | 0 | 0 |  | keep | Texture cache dir immersive_paintings_cache. |
| incontrol-1.20-9.4.7.jar | Server utility | S | L | L | 152 | 0 | 0 | 0 | 1.20-9.5.0 | config | spawn.json is empty ([]): no mob caps/rules are applied. 1.20-9.5.0 available. |
| oreexcavation-1.13.170.jar | QoL | B | L | L | 47 | 0 | 0 | 0 |  | keep | Large veins can spike a tick; limits are in oreexcavation-common.toml. |
| realmrpg_balloons-1.0.0-forge-1.20.1.jar | Content | B | L | L | 93 | 0 | 0 | 0 |  | keep |  |
| resourcefulconfig-forge-1.20.1-2.1.3.jar | Library | B | L | L | 80 | 0 | 0 | 0 |  | keep |  |
| resourcefullib-forge-1.20.1-2.1.29.jar | Library | B | L | L | 188 | 0 | 0 | 0 |  | keep |  |
| snowundertrees-1.20.1-1.4.12.jar | World | B | L | L | 12 | 0 | 0 | 0 |  | keep |  |
| chat_heads-0.15.7-forge-1.20.jar | HUD | C | L | - | 45 | 20 | 0 | 0 |  | lads | Lads Core replaces. |
| particle_core-0.3.3+1.20.1+forge.jar | Perf (particles) | C | L | - | 44 | 18 | 0 | 0 |  | lads | Lads Core replaces. Pulls Kotlin for Forge + fzzy_config. |
| dynamic-fps-3.11.4+minecraft-1.20.0-forge.jar | Perf (idle) | C | L | - | 71 | 13 | 1 | 0 |  | lads | Lads Core replaces (NativeDynamicFps1201). |
| entityculling-forge-1.11.3-mc1.20.1.jar | Perf (culling) | C | L | - | 34 | 10 | 0 | 0 |  | lads | Lads Core replaces. |
| modelfix-1.15.jar | Visual fix | C | L | - | 15 | 2 | 1 | 0 |  | keep | 2 mixins. |
| MouseTweaks-forge-mc1.20.1-2.25.1.jar | UI | C | L | - | 23 | 1 | 0 | 0 |  | lads | Lads Core replaces. |
| appleskin-forge-mc1.20.1-2.5.1.jar | HUD | C | L | - | 29 | 0 | 0 | 0 |  | lads | Lads Core replaces. |
| embeddium-0.3.31+mc1.20.1.jar | Rendering | C | - | M | 602 | 98 | 43 | 23 |  | config | Sodium port: the main FPS win. 98 mixins, 43 @Overwrite. chunk_builder_threads=1 in the instance (pack baseline 0=auto). |
| moreculling-0.24.6-all.jar | Perf (culling) | C | - | L | 122 | 44 | 0 | 0 |  | keep | 44 mixins; leaves culling DEPTH/2, itemframe LOD, sign/rain/cloud culling. Replaces Cull Leaves. |
| cullleaves-forge-4.1.1+1.20.1.jar | Perf (culling) | C | - | L | 10 | 5 | 0 | 0 |  | remove | cullleaves.json has enabled=false (user turned it off) and MoreCulling already does leaf culling (leavesCullingMode=DEPTH). Dead weight; also lets midnightlib go. |
| modernfix-forge-5.27.85+mc1.20.1.jar | Perf | B | - | - | 411 | 197 | 33 | 62 | 5.27.87+mc1.20.1 | config | 197 mixins. ALL options at defaults (log: '0 override(s)'): mixin.perf.dynamic_resources is off. 5.27.87 available. |
| ImmediatelyFast-Forge-1.5.5+1.20.4.jar | Perf (render) | C | - | - | 86 | 39 | 2 | 10 |  | lads | Lads Core replaces. |
| ferritecore-6.0.1-forge.jar | Perf (RAM) | B | - | - | 81 | 20 | 6 | 4 |  | keep | Keep. Saves ~hundreds of MB of blockstate/model memory. |
| noisiumed-forge-3.0.6+mc1.20-1.20.1.jar | Perf (worldgen) | S | - | - | 12 | 5 | 2 | 2 |  | keep | Fork of Noisium (original archived); faster noise-based terrain gen, claims DH + C2ME compat. Tectonic/Lithostitched not mentioned on its page. |
| Clumps-forge-1.20.1-12.0.0.4.jar | Perf (XP orbs) | B | - | - | 15 | 2 | 0 | 0 |  | keep | Reduces entity count; keep. |
| BorderlessWindow-1.20-1.6.0.jar | Window | C | - | - | 22 | 0 | 0 | 0 |  | lads | Lads Core replaces. |
| IMM v1.3.2-1.20.1.jar | Datapack glue | B | - | - | 0 | 0 | 0 | 0 |  | keep | 0 classes, data only. |
