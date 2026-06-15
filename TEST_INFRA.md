# E2E Test Infra: Lads Client Core Porting and Integration

## Test Philosophy
- **Requirement-Driven & Opaque-Box**: Tests focus on verifying feature requirements, correctness, robustness, and stability without relying on implementation details where possible.
- **Defensive Reflection Testing**: Integration tests dynamically use Java reflection to inspect feature classes (`CapeConfig`, `CapeType`, `RenderScaleOptions`, `RenderScalePreset`). This allows the test suite to build and pass cleanly even before the code migration and feature ports are fully implemented.
- **Static Parsing Verification**: Mixed verification checks are used to inspect mixin files and loading screens directly from source on disk to ensure theme styling complies with branding guidelines.
- **Post-Run Validation**: Automation checks ensure old standalone mods (`capes-*.jar`, `render scale *.jar`) are completely purged from packwiz and instance directories to guarantee a clean runtime environment.

---

## Feature Inventory
- **F1: Capes Port**: Integration of decompiled Capes mod into `TheLadsCore`. Configured via `config/capes.json5` using Gson. Supports Minecraft, OptiFine, LabyMod, Cosmetica, MinecraftCapes, and Cloaks+ sources.
- **F2: Render Scale Port**: Integration of decompiled Render Scale mod into `TheLadsCore` (Sodium config menu registration, render scale manager blit & dynamic resolution adjustments). Configured via `config/render-scale-options.json`.
- **F3: Folder Cleanup**: PURGING of old standalone jars (`capes-*.jar`, `render scale *.jar`) from modpack directories.
- **F4: Red and Black Loading Animation**: Porting the custom startup overlay styling to target a red and black color theme instead of the initial blue/indigo theme.

---

## Test Case Tiers

### Tier 1 - Feature Coverage (20 cases)
- **T1.1**: Capes configuration defaults to Minecraft capes enabled, other capes disabled except OptiFine.
- **T1.2**: Capes config save writes correct JSON structure to config directory.
- **T1.3**: CapeType cycle cycles from MINECRAFT to OPTIFINE, LABYMOD, COSMETICA, MINECRAFTCAPES, CLOAKSPLUS, and back.
- **T1.4**: CapeType.OPTIFINE generates correct OptiFine cape URL using name.
- **T1.5**: CapeType.LABYMOD generates correct LabyMod cape URL using UUID.
- **T1.6**: CapeType.COSMETICA generates correct Cosmetica info URL using UUID.
- **T1.7**: CapeType.MINECRAFTCAPES generates correct MinecraftCapes URL with hyphens removed.
- **T1.8**: CapeType.CLOAKSPLUS generates correct Cloaks+ URL using name.
- **T1.9**: CapeType.MINECRAFT returns null URL (using Mojang's default rendering path).
- **T1.10**: RenderScaleOptions default configuration loading.
- **T1.11**: RenderScaleOptions preset change updates render scale, scale algorithm, and dynamic resolution states.
- **T1.12**: ULTRA_PERFORMANCE preset sets render scale to 0.5f and scale algorithm to NEAREST.
- **T1.13**: BALANCED preset sets render scale to 0.75f and scale algorithm to LINEAR.
- **T1.14**: QUALITY preset sets render scale to 1.0f and scale algorithm to LINEAR.
- **T1.15**: SUPER_SAMPLING preset sets render scale to 1.5f and scale algorithm to LINEAR.
- **T1.16**: RenderScaleOptions save writes correct JSON structure to config directory.
- **T1.17**: Validation of Packwiz mods folder to detect old `capes-*.jar` standalone files.
- **T1.18**: Validation of Packwiz mods folder to detect old `render scale *.jar` standalone files.
- **T1.19**: LoadingOverlayMixin background color check parses to 0x0A0A0F or 0xFF0A0A0F (or target red/black).
- **T1.20**: LadsEarlyWindow background color check parses to 0x0A / 255f (or target red/black).

### Tier 2 - Boundary & Corner Cases (20 cases)
- **T2.1**: CapeType URL generation returns null when the specific cape source is disabled in configuration.
- **T2.2**: CapeType URL generation handles null or empty GameProfile name or UUID gracefully.
- **T2.3**: Capes config load handles corrupted or invalid JSON by falling back to default configuration.
- **T2.4**: Capes config save handles read-only/lock files or full disk exceptions gracefully.
- **T2.5**: Capes UI menu handles rapid cycling without UI lock or state desync.
- **T2.6**: Capes skin Customization screen mixin injects correctly without duplicate buttons.
- **T2.7**: RenderScaleOptions custom scale values clamp between min boundary (0.5f) and max boundary (2.0f or 3.0f).
- **T2.8**: RenderScaleOptions dynamic resolution behavior with low/high target fps bounds.
- **T2.9**: RenderScaleOptions config load handles corrupted/invalid JSON by falling back to default options.
- **T2.10**: RenderScaleOptions config save handles file permission errors without crashing the client.
- **T2.11**: RenderScaleManager custom resolution resize with zero or negative width/height clamps to 1.
- **T2.12**: RenderScaleManager blit logic handles null shaders or texture objects without rendering crashes.
- **T2.13**: Folder cleanup validation handles non-existent mods directory gracefully.
- **T2.14**: Folder cleanup validation matches various naming patterns of old jars.
- **T2.15**: Folder cleanup validation ignores new integrated `TheLadsCore` jar.
- **T2.16**: Folder cleanup validation handles symlinks or locked mod jars.
- **T2.17**: LadsEarlyWindow handles glfwInit failure or multi-monitor setup with null display monitor gracefully.
- **T2.18**: LadsEarlyWindow thread safety: stops rendering thread before Minecraft window adoption.
- **T2.19**: LoadingOverlayMixin drawCircleOutline handles negative coordinates or zero radius bounds.
- **T2.20**: LadsEarlyWindow Windows ghosting API check handles non-Windows operating systems gracefully.

### Tier 3 - Cross-Feature Combinations (4 cases)
- **T3.1**: Capes config and Render Scale config are loaded simultaneously during initialization without thread-safety conflicts.
- **T3.2**: Standalone mods cleanup is executed before mod initialization, verifying that the launcher clean state is established.
- **T3.3**: LadsEarlyWindow adopts window and transfers GL context, and then LoadingOverlayMixin takes over rendering and transitions to the in-game display.
- **T3.4**: Dynamic resolution adjustments in Render Scale do not affect/conflict with Cape rendering or skin loading logic under high rendering load.

### Tier 4 - Real-World Application Scenarios (5 cases)
- **T4.1**: A player switches cape type to LabyMod, saves config, restarts client, and verifies LabyMod cape is resolved and loaded.
- **T4.2**: A player changes render scale preset to BALANCED, verifies scale is 0.75f, switches back to CUSTOM, and sets render scale manually to 1.2f.
- **T4.3**: The launcher runs, cleans up old mod jars, starts Minecraft, and LadsEarlyWindow displays the animated loading screen without white flashes.
- **T4.4**: Minecraft adopts the early window, initializes early window, loads mixins, and displays the main menu with early window correctly destroyed.
- **T4.5**: A player changes skin customizations, cycles through all available capes, changes render scale presets under Sodium menu, and plays the game without visual glitches.

---

## Test Runner Setup
The integration test suite resides in `TheLadsCore/src/test/java/com/thelads/core/client/IntegrationTests.java`.

- **Command to run tests**:
  ```powershell
  cd TheLadsCore
  .\gradlew.bat test --tests "com.thelads.core.client.IntegrationTests"
  ```
- **Execution Script**:
  Run from the project root directory:
  ```powershell
  .\Run-E2ETests.ps1
  ```
