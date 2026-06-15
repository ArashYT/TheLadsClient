# Project: TheLadsCore

## Architecture
- `com.thelads.core.config`: Module system and JSON configuration backend.
- `com.thelads.core.client.gui`: Custom Lunar-style Screen, accessible via Right Shift and Pause Menu.
- `com.thelads.core.modules`: Implementation of modules (DynamicFPS, PingView, BetterF3).
- `com.thelads.core.mixin`: Mixins required to hook into Vanilla functionality for GUI access and module behaviors.

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| 1 | CoreFramework | Module System (JSON Config) + Settings GUI Framework | none | DONE |
| 2 | DynamicFPS | Frame rate reduction on unfocus logic + Module definition | M1 | DONE |
| 3 | PingView | Show ping in player tab list + Module definition | M1 | DONE |
| 4 | BetterF3 | Clean F3 screen logic + Module definition | M1 | DONE |
| 5 | Phase 1 - Remove Essential | Delete Essential Mod from Packwiz, config, and codebase references | none | DONE |
| 6 | Phase 1 - Skin/Cape UI | Custom Skin & Cape manager UI in-game | M5 | IN_PROGRESS |
| 7 | Phase 1 - Profile Sync | Sync with launcher's lads_profile.json & Local library save | M6 | PLANNED |

## Interface Contracts
### Config ↔ GUI
- GUI interacts with `ModuleManager` to get all modules, their toggled states, and settings.
### GUI ↔ Vanilla
- Right Shift KeyBinding triggers `MinecraftClient.getInstance().setScreen(new LadsSettingsScreen())`.
- Pause menu adds a button via Mixin `GameMenuMixin` to open `LadsSettingsScreen`.

## Code Layout
- `src/main/java/com/thelads/core/...`
- `src/main/resources/...`
