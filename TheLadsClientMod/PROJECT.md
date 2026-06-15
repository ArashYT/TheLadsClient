# Project: TheLadsClientMod

## Architecture
- **Environment**: Fabric Mod for Minecraft (Version 26.1.2, matching TheLadsCore).
- **Module**: TheLadsClientMod
- **Data Flow**: A central configuration manager saves settings to JSON. The Right-Shift GUI reads/writes these settings. Mixins hook into rendering/GUI logic to apply Capes and UI scaling dynamically.

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| 1 | Mod Initialization | Setup Fabric scaffold, initialization class | none | DONE |
| 2 | Right-Shift GUI & Config | Config manager, Right-Shift keybind, Settings Screen (dark theme) | M1 | DONE |
| 3 | Feature Migration | Capes rendering mixin, UI scaling mixin hooked to config | M2 | DONE |

## Interface Contracts
### ConfigManager ↔ Features
- `ConfigManager.getConfig().isCapesEnabled()`: Returns boolean state for Cape rendering.
- `ConfigManager.getConfig().isUiScalingEnabled()`: Returns boolean state for UI scaling.

## Code Layout
- `src/main/java/com/thelads/client/TheLadsClientMod.java`
- `src/main/java/com/thelads/client/config/...`
- `src/main/java/com/thelads/client/gui/...`
- `src/main/java/com/thelads/client/features/...`
- `src/main/java/com/thelads/client/mixin/...`
- `gradle.properties` (mirroring versions from TheLadsCore)
