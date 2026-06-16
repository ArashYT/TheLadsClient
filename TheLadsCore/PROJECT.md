# Project: TheLadsCore

## Architecture
`TheLadsCore` is a Fabric client-side mod that provides custom client features, UI screens, and performance optimizations. 

### Modules & Porting Architecture
To replace 33 external mod JARs, their features are ported directly into `TheLadsCore`:
1. **Always-On Features**: Performance optimizations and utilities that are always active.
   - Package: `com.thelads.core.features.alwayson`
   - Initialized via client entrypoint or mixins.
2. **Configurable Modules**: Toggleable modules that the user can enable/disable in the configuration.
   - Package: `com.thelads.core.features.configurable`
   - Registered with the `ModuleManager` and configured via JSON configurations.

## Milestones

| # | Milestone Name | Scope / Target Mods | Status |
|---|----------------|---------------------|--------|
| **M1** | Exploration, Decompilation & Reference Prep | Decompile 33 target mods to `temp_decompiled_mods/`, setup PROJECT.md, run test compile. | **DONE** |
| **M2** | Port Always-On Performance & Utility Mods | Port: ImmediatelyFast, Entity Culling, Very Many Players (VMP), Better Render Distance, Let Me Respawn, Hyper Launch, Smooth Scrolling, Raise Sound Limit. | **DONE** |
| **M3** | Port Always-On UI & Client Mods | Port: 3D Skin Layers, Advancements Reloaded, Better Statistic Screen, Client Sort, Quick Pack, Resource Pack Options, Raised, Ixeris. | **IN_PROGRESS** |
| **M4** | Port Configurable UI & HUD Modules | Port: immersive hotbar, screenFX, Apple Skin, Better f1, Client Tweaks, Cursors extended, enhanced toolbars, Fancy Door Animations, NotEnoughAnimations, Wavey Capes. | PLANNED |
| **M5** | Port Configurable System & Utility Modules | Port: Extreme Sound Muffler, Entity View Distance, Just Enough Items (JEI), Lamb Dynamic Lights, Passive Shield, Server Pinger Fixer, Threads. | PLANNED |
| **M6** | Registry Integration & Mod Cleanup | Register configurable modules in GUI, delete original JAR files from Packwiz/mods, final E2E test. | PLANNED |

## Interface Contracts
### Config ↔ GUI
- GUI interacts with `com.thelads.core.config.ModuleManager` to fetch modules, toggles, and sub-settings.
- Module toggle actions trigger JSON config save.
### GUI ↔ Vanilla
- Right Shift KeyBinding triggers `MinecraftClient.getInstance().setScreen(new LadsSettingsScreen())`.
- Pause menu adds a button via Mixin `GameMenuMixin` to open `LadsSettingsScreen`.
### Configurable Module Contract
- Every configurable module must extend a base module class, define its configuration metadata, and handle toggle state changes gracefully without restarting the client.

## Code Layout
- `src/main/java/com/thelads/core/...`
  - `/config/` - Module registration, config saving/loading.
  - `/client/gui/` - Lunar-style Settings Screen.
  - `/mixin/` - Hooks into Minecraft internals.
  - `/features/alwayson/` - Core always-on ported logic.
  - `/features/configurable/` - Toggleable modular ported logic.
- `temp_decompiled_mods/` - Temp decompiled sources (reference only, to be deleted in M6).
- `src/main/resources/...` - Mod assets, configs, and mixin configs.
