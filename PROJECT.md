# Project: Lads Client Titans Porting and Minecraft 26.2 Migration

## Architecture
The workspace contains:
1. **TheLadsCore (Fabric Mod, Java 25/26)**: Located at `TheLadsCore/`. Contains the core Fabric mod code.
2. **Shaded and Source-Ported Mods (The Titans)**:
   - `immediatelyfast` and `3dskinlayers` are already source-ported as always-on features, to be wrapped into toggleable modules.
   - `jei` and `xaeros-world-map` are currently shaded JARs to be completely removed and replaced with native module implementations under `com.thelads.core.modules`.
3. **Minecraft 26.2 Migration**: Upgrading the mod target from 26.1.2 to 26.2 natively.

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| 1 | Exploration & Analysis | Codebase analysis, target mod class mapping | None | DONE |
| 2 | E2E Testing Track | Design E2E tests, publish `TEST_READY.md` | M1 | DONE |
| 3 | `immediatelyfast` & `3dskinlayers` | Wrap as toggleable modules, modify mixins to check state | M1 | DONE |
| 4 | `jei` & `xaeros-world-map` | Strip shaded JARs, implement native modules, register in ModuleManager | M1 | DONE |
| 5 | Minecraft 26.2 Migration | Bump gradle.properties target, fix all 26.2 API compiler breaks | M3, M4 | DONE |
| 6 | Verification & Cleanup | E2E validation, Forensic Audit validation, delete unused files/cache | M5 | DONE |

## Interface Contracts
- Configurable modules implement dynamic toggle state checking (`isEnabled()`) in their respective mixin hooks.
- Base module settings are loaded and saved in JSON format via `ConfigManager` and `ModuleManager`.

## Code Layout
- `src/main/java/com/thelads/core/features/alwayson/immediatelyfast/` - ImmediatelyFast logic
- `src/main/java/com/thelads/core/features/alwayson/skinlayers/` - 3D Skin Layers logic
- `src/main/java/com/thelads/core/modules/` - Register new modules (`SkinLayersModule`, `ImmediatelyFastModule`, `JeiModule`, `XaeroWorldMapModule`)
- `src/main/java/com/thelads/core/mixin/alwayson/` - Mixin hooks for performance/graphics mods
