# Bundled client mod pack

Current 26.3 development build: see [CLIENT_26_3.md](CLIENT_26_3.md) and [MOD_COVERAGE.md](MOD_COVERAGE.md) for all four version packs, native replacements, and missing upstream releases. The version table below is a historical attribution snapshot, not the current lockfile.

The 10 September 2026 development build pins 33 upstream Fabric jars for Minecraft 26.2 and 35 for 1.21.11. Dynamic FPS, Raised, Custom Crosshair and Durability Tooltip have been replaced by native Lads features on 26.2; Modern Advancements and Resourcify are added as retained engines. See [NATIVE_MODULES_26_2.md](NATIVE_MODULES_26_2.md) and [REFERENCE_ENGINES_26_2.md](REFERENCE_ENGINES_26_2.md). The version table below retains the original upstream attribution for both packs. Nested library mods may make the in-game Mod Menu count higher. The launcher downloads original artifacts from Modrinth and verifies their SHA-512, size, and Fabric mod ID; it does not repackage them. Each upstream project retains its own license and authorship. The JSON manifests include exact version IDs, original filenames, source repositories, sizes and hashes.

| Project | Minecraft 1.21.11 | Minecraft 26.2 | License |
|---|---|---|---|
| [Fabric API](https://modrinth.com/mod/fabric-api) | 0.141.6+1.21.11 | 0.159.0+26.2 | Apache-2.0 |
| [Cloth Config API](https://modrinth.com/mod/cloth-config) | 21.11.153+fabric | 26.2.155+fabric | LGPL-3.0-only |
| [Sodium](https://modrinth.com/mod/sodium) | mc1.21.11-0.8.14-fabric | mc26.2-0.9.1-fabric | LicenseRef-Polyform-Shield-1.0.0 |
| [Lithium](https://modrinth.com/mod/lithium) | mc1.21.11-0.21.4-fabric | mc26.2-0.25.3-fabric | LGPL-3.0-only |
| [FerriteCore](https://modrinth.com/mod/ferrite-core) | 8.2.0-fabric | 9.0.0-fabric | MIT |
| [ImmediatelyFast](https://modrinth.com/mod/immediatelyfast) | 1.14.3+1.21.11-fabric | 1.16.4+26.2-fabric | LGPL-3.0-or-later |
| [Mod Menu](https://modrinth.com/mod/modmenu) | 17.0.0 | 20.0.1 | MIT |
| [Dynamic FPS](https://modrinth.com/mod/dynamic-fps) | 3.11.6 | 3.11.9 | MIT |
| [AppleSkin](https://modrinth.com/mod/appleskin) | 3.0.8+mc1.21.11 | 3.0.10+mc26.2 | Unlicense |
| [LambDynamicLights - Dynamic Lights](https://modrinth.com/mod/lambdynamiclights) | 4.9.1+1.21.11 | 4.12.4+26.2 | LicenseRef-Lambda-License |
| [3D Skin Layers](https://modrinth.com/mod/3dskinlayers) | 1.11.2 | 1.11.2 | LicenseRef-tr7zw-Protective-License |
| [Not Enough Animations](https://modrinth.com/mod/not-enough-animations) | 1.12.4 | 1.12.4 | LicenseRef-tr7zw-Protective-License |
| [BetterF3](https://modrinth.com/mod/betterf3) | 17.0.0 | 19.0.0 | MIT |
| [Better Statistics Screen](https://modrinth.com/mod/better-stats) | 5.1.0+fabric-1.21.11 | 5.5.6+fn-26.2 | LicenseRef-Better-Statistics-Screen-License |
| [Just Enough Items (JEI)](https://modrinth.com/mod/jei) | 27.35.0.90 | 30.29.0.201 | MIT |
| [Xaero's Minimap](https://modrinth.com/mod/xaeros-minimap) | fabric-1.21.11-26.4.2 | fabric-26.2-26.4.2 | LicenseRef-All-Rights-Reserved |
| [Xaero's World Map](https://modrinth.com/mod/xaeros-world-map) | fabric-1.21.11-1.45.0 | fabric-26.2-1.45.0 | LicenseRef-All-Rights-Reserved |
| [Clumps](https://modrinth.com/mod/clumps) | 29.0.0.1 | 26.2.1 | MIT |
| [Raised](https://modrinth.com/mod/raised) | Fabric-1.21.11-5.1.2 | Fabric-26.2-5.1.2 | LGPL-3.0-or-later |
| [ScalableLux](https://modrinth.com/mod/scalablelux) | 0.1.6+fabric.c25518a | 0.2.1+fabric.2b08348 | LGPL-3.0-only |
| [Entity Culling](https://modrinth.com/mod/entityculling) | 1.10.5 | 1.10.5 | LicenseRef-tr7zw-Protective-License |
| [Custom Crosshair Mod](https://modrinth.com/mod/custom-crosshair-mod) | v1.6.4-fabric-mc1.21.11 | v1.6.7-fabric-mc26.2 | LicenseRef-All-Rights-Reserved |
| [Capes](https://modrinth.com/mod/capes) | 1.5.10+1.21.11 | 1.5.11+26.2 | LGPL-2.1-only |
| [Paper Doll](https://modrinth.com/mod/paper-doll) | 21.11.0 | 26.2.2 | MPL-2.0 |
| [Durability Tooltip](https://modrinth.com/mod/durability-tooltip) | 1.1.6-fabric-mc1.21.11 | 1.1.6-fabric-mc26.2 | LicenseRef-All-Rights-Reserved |
| [Auto Reconnect Reforged](https://modrinth.com/mod/autoreconnectrf) | 2.0.1+1.21.11 | 3.103.0+26.2 | LGPL-3.0-only |
| [Screenshot Viewer](https://modrinth.com/mod/screenshot-viewer) | 1.21.11-fabric-2 | 26.2-fabric-1 | MIT |
| [Tab Tweaks](https://modrinth.com/mod/tabtweaks) | 1.5.9 | 1.5.11 | LGPL-3.0-only |
| [Text Placeholder API](https://modrinth.com/mod/placeholder-api) | 2.8.2+1.21.10 | 3.1.0-beta.1+26.2 | LGPL-3.0-only |
| [SuperMartijn642's Config Lib](https://modrinth.com/mod/supermartijn642s-config-lib) | 1.1.8-fabric-mc1.21.11 | 1.1.8-fabric-mc26.2 | LicenseRef-All-Rights-Reserved |
| [Forge Config API Port](https://modrinth.com/mod/forge-config-api-port) | 21.11.1 | 26.2.1 | MPL-2.0 |
| [Puzzles Lib](https://modrinth.com/mod/puzzles-lib) | 21.11.13 | 26.2.3 | MPL-2.0 |
| [TCDCommons API](https://modrinth.com/mod/tcdcommons) | 5.1.0+fabric-1.21.11 | 5.5.6+fn-26.2 | LicenseRef-TCDCommons-API-License |
| [YetAnotherConfigLib (YACL)](https://modrinth.com/mod/yacl) | 3.8.2+1.21.11-fabric | 3.9.6+26.2-fabric | LGPL-3.0-or-later |
| [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin) | 1.13.13+kotlin.2.4.10 | 1.13.13+kotlin.2.4.10 | Apache-2.0 |

## Settings and compatibility

Built-in Lads controls apply immediately. Cards marked MOD open the external mod configuration or its Mod Menu entry when no configuration screen is provided. Disable external jars in the launcher and restart Minecraft; required dependencies must remain enabled while their dependents are active. Existing custom jars are preserved, and conflicting versions produce an actionable error.

The default pack includes Sodium, Lithium, FerriteCore, ImmediatelyFast, Entity Culling, Dynamic FPS, and ScalableLux. These are actual installed implementations; no controlled FPS benchmark was performed. Clumps operates only where the server or integrated world runs it. Capes displays existing supported capes and does not grant ownership. JEI does not grant server operator privileges.

Exordium is excluded after its own first-run warning reported that the current release is unmaintained and can cause visual issues. Tab Tweaks is pinned for the narrow tab-list integration, but its upstream project is archived; reassess it before future Minecraft upgrades. Full online reconnect/server behavior remains unverified pending Microsoft registration.

## Updating the pins

Run `python tools/resolve_client_mods.py --lock` only as a deliberate developer update, review both JSON diffs and project licenses, then run the installer regression suite and actual game/world checks for both versions. Launching the client never queries for arbitrary latest mod versions.
