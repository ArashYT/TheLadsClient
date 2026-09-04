# Project: Lads Client Vulkan, UI, Skins, and Branding Updates

## Architecture
- **TheLadsCore**: Fabric Mod in Java. Extends HUD rendering, title screen UI, skin fetching/caching, and client assets.
- **TheLadsLauncher**: Avalonia C# Desktop Launcher. Contains accounts UI, skin fetch API calls, and launcher window logo assets.

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| 1 | Exploration & Analysis | Scan Vulkan rendering crashes, title screen button layout, skin fetching C# logic, and logos/icons across launcher and mod | None | DONE |
| 2 | Vulkan & UI Implementation | Fix HUD crash on Vulkan, add ImmediatelyFast native optimizations on OpenGL, purge the mod, and reorder Title screen buttons | M1 | DONE |
| 3 | Skin Fetching Implementation | Port C# skin fetching to Java, enable caching, and display player skin in account switcher | M2 | DONE |
| 4 | Branding & Logo Updates | Replace old logos and icons with resized/converted version of the new remaster logo | M3 | DONE |
| 5 | Verification & Audit | Run all E2E / unit tests, perform reviewers and challengers check, run Forensic Auditor | M4 | DONE |

## Interface Contracts
### Vulkan HUD Render State Extraction
- Separate state extraction in `extractRenderState` from rendering in `render` within `GuiMixin.java`.
- Safe multi-threaded drawing on the new Vulkan renderer.

### ImmediatelyFast Optimization Check
- Native implementation of ImmediatelyFast optimizations.
- Active ONLY when OpenGL renderer is detected. Otherwise, disabled.

### Title Screen Button Order
- "Lads Settings" button must appear in the primary main vertical list, placed above "Options".

### Java Skin Fetching & Caching
- Minotar/Mojang API fetch requests ported from C# to Java.
- Caching logic to handle skin retrieval independently of vanilla offline skin state.
