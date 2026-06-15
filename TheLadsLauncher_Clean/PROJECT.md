# Project: The Lads Launcher & Client Massive Expansion

## Architecture
- Target 1 (Launcher): C:\Users\Arash\Desktop\Lads Client\TheLadsLauncher_Clean
- Tech Stack 1: .NET 8 (Windows), Avalonia UI v12.0.4
- Target 2 (Client Mod): C:\Users\Arash\Desktop\Lads Client\TheLadsCore
- Tech Stack 2: Java, Fabric Modding API (Minecraft 1.21.2)

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| 1 | Launcher UI Overhaul & File Editor | Implement built-in Skin & Cape Manager, redesign Gallery/Mods pages, add config file editor, update startup experience (animated loading, no white flash). | none | PLANNED |
| 2 | Utility Modules (Zoom & Hotbar) | Implement Zoom module (scroll to zoom, smooth animations) and Smooth Hotbar scrolling. | none | PLANNED |
| 3 | HUD & Texture Packs Modules | Xaero's minimap & Voice Chat HUD movable modules, Texture Packs module, Discord RPC. | none | PLANNED |
| 4 | Client Bug Fixes & Refactoring | Fix Scoreboard alignment, view bobbing jitter, OBS cursor disappearing, remove Essential Mod. Recreate OldAnimationsMod features. | none | PLANNED |
| 5 | Multiplayer & Performance Systems | Auto Mod Sync via custom handshake protocol. Performance Manager (dynamic render distance). | none | PLANNED |

## Code Layout
- Launcher UI Components: MainWindow.axaml, MainWindow.axaml.cs
- Client HUD & Modules: TheLadsCore/src/main/java/com/thelads/core/

## Interface Contracts
- Launcher: Read/Write to local settings.json and .minecraft instance configuration files.
- Mod Sync: Custom handshake protocol sending mod IDs on server join.
