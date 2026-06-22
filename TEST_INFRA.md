# E2E Test Infra: Lads Client Core Porting and Integration (Proposed Updates)

## Test Philosophy
- **Requirement-Driven & Opaque-Box**: Tests focus on verifying feature requirements, correctness, robustness, and stability without relying on implementation details where possible.
- **Defensive Reflection Testing**: Integration tests dynamically use Java reflection to inspect feature classes. This allows the test suite to build and pass cleanly even before the code migration and feature ports are fully implemented.
- **Static Parsing Verification**: Mixed verification checks are used to inspect mixin files and loading screens directly from source on disk to ensure theme styling complies with branding guidelines.
- **Post-Run Validation**: Automation checks ensure old standalone mods are completely purged to guarantee a clean runtime environment.

---

## Feature Inventory
- **F1: Capes Port**: Integration of decompiled Capes mod into `TheLadsCore`. Configured via `config/capes.json5` using Gson. Supports Minecraft, OptiFine, LabyMod, Cosmetica, MinecraftCapes, and Cloaks+ sources.
- **F2: Render Scale Port**: Integration of decompiled Render Scale mod into `TheLadsCore` (Sodium config menu registration, render scale manager blit & dynamic resolution adjustments). Configured via `config/render-scale-options.json`.
- **F3: Folder Cleanup**: PURGING of old standalone jars (`capes-*.jar`, `render scale *.jar`) from modpack directories.
- **F4: Red and Black Loading Animation**: Porting the custom startup overlay styling to target a red and black color theme instead of the initial blue/indigo theme.
- **F5: Native Mod Porting & Shaded Library Integration**: Native source integration of ImmediatelyFast and 3dskinlayers (SkinLayers), shading of JustEnoughItems (JEI) and Xaero's World Map jar files, and the final Minecraft 26.2 migration properties check.

---

## Test Case Tiers

### Tier 1 - Feature Coverage (25 cases)
- **T1.1**: Capes configuration defaults to Minecraft capes enabled, other capes disabled except OptiFine.
- **T1.2**: Capes config save writes correct JSON structure to config directory.
- **T1.3**: CapeType cycle cycles from MINECRAFT to OPTIFINE, LABYMOD, COSMETICA, MINECRAFTCAPES, CLOAKSPLUS, and back.
- **T1.4**: CapeType.OPTIFINE generates correct OptiFine cape URL using name.
- **T1.5**: CapeType.LABYMOD generates correct LabyMod cape URL using UUID.
- **T1.6**: RenderScaleOptions default configuration loading.
- **T1.7**: RenderScaleOptions preset change updates render scale, scale algorithm, and dynamic resolution states.
- **T1.8**: ULTRA_PERFORMANCE preset sets render scale to 0.5f and scale algorithm to NEAREST.
- **T1.9**: BALANCED preset sets render scale to 0.75f and scale algorithm to LINEAR.
- **T1.10**: QUALITY preset sets render scale to 1.0f and scale algorithm to LINEAR.
- **T1.11**: SUPER_SAMPLING preset sets render scale to 1.5f and scale algorithm to LINEAR.
- **T1.12**: Validation of Packwiz mods folder to detect old `capes-*.jar` standalone files.
- **T1.13**: Validation of Packwiz mods folder to detect old `render scale *.jar` standalone files.
- **T1.14**: Folder cleanup validation handles non-existent mods directory gracefully.
- **T1.15**: Folder cleanup validation matches various naming patterns of old jars.
- **T1.16**: LoadingOverlayMixin background color check parses to 0x0A0A0F or 0xFF0A0A0F (or target red/black).
- **T1.17**: LadsEarlyWindow background color check parses to 0x0A / 255f (or target red/black).
- **T1.18**: LoadingOverlayMixin progress bar color check matches target red/black color value.
- **T1.19**: LoadingOverlayMixin brand logo/background redirect function returns correct color value.
- **T1.20**: Early Window progress bar rendering uses the target red/black color theme.
- **T1.21**: ImmediatelyFast module registration check in `ModuleManager` and default enabled state verification.
- **T1.22**: SkinLayers module registration check in `ModuleManager` and config save/load verification.
- **T1.23**: JEI shaded library class loading verification (`mezz.jei.api.IModPlugin` present on classpath).
- **T1.24**: XaeroWorldmap module registration check and default category setting in `ModuleManager`.
- **T1.25**: gradle.properties properties check (`minecraft_version=26.2` and `fabric_api_version` match target release).

### Tier 2 - Boundary & Corner Cases (25 cases)
- **T2.1**: CapeType URL generation returns null when the specific cape source is disabled in configuration.
- **T2.2**: CapeType URL generation handles null or empty GameProfile name or UUID gracefully.
- **T2.3**: Capes config load handles corrupted or invalid JSON by falling back to default configuration.
- **T2.4**: Capes config save handles read-only/lock files or full disk exceptions gracefully.
- **T2.5**: Capes UI menu handles rapid cycling without UI lock or state desync.
- **T2.6**: RenderScaleOptions custom scale values clamp between min boundary (0.5f) and max boundary (3.0f).
- **T2.7**: RenderScaleOptions dynamic resolution behavior with low/high target fps bounds.
- **T2.8**: RenderScaleOptions config load handles corrupted/invalid JSON by falling back to default options.
- **T2.9**: RenderScaleOptions config save handles file permission errors without crashing the client.
- **T2.10**: RenderScaleManager custom resolution resize with zero or negative width/height clamps to 1.
- **T2.11**: Folder cleanup validation ignores new integrated `TheLadsCore` jar.
- **T2.12**: Folder cleanup validation handles symlinks or locked mod jars.
- **T2.13**: Packwiz index check when no mods are listed in index file.
- **T2.14**: Cleanup script execution when multiple file instances of the same mod exist.
- **T2.15**: Purging behaviour when the game directory is locked by a running instance of Minecraft.
- **T2.16**: LadsEarlyWindow handles glfwInit failure or multi-monitor setup with null display monitor gracefully.
- **T2.17**: LadsEarlyWindow thread safety: stops rendering thread before Minecraft window adoption.
- **T2.18**: LoadingOverlayMixin drawCircleOutline handles negative coordinates or zero radius bounds.
- **T2.19**: LadsEarlyWindow Windows ghosting API check handles non-Windows operating systems gracefully.
- **T2.20**: Early Window rendering behaves correctly under extremely low aspect ratios (e.g. 1:1 window size).
- **T2.21**: ImmediatelyFast behaves correctly when GPU device information (vendor/renderer) is null or unrecognized.
- **T2.22**: SkinLayers handles empty/corrupt skin texture resource paths or non-64x64 skins by falling back to default rendering.
- **T2.23**: JEI handles load-time conflicts with other mod plugins (e.g., Appleskin JEI plugin) without crashing the startup process.
- **T2.24**: XaeroWorldmap handles disk write permissions or full disk exceptions when attempting to write map databases.
- **T2.25**: Minecraft 26.2 migration block check: verifying migration scripts fail/warn appropriately when any Modrinth queries fail or return blocking mods.

### Tier 3 - Cross-Feature Combinations (6 cases)
- **T3.1**: Capes config and Render Scale config are loaded simultaneously during initialization without thread-safety conflicts.
- **T3.2**: Standalone mods cleanup is executed before mod initialization, verifying that the launcher clean state is established.
- **T3.3**: LadsEarlyWindow adopts window and transfers GL context, and then LoadingOverlayMixin takes over rendering and transitions to the in-game display.
- **T3.4**: Dynamic resolution adjustments in Render Scale do not affect/conflict with Cape rendering or skin loading logic under high rendering load.
- **T3.5**: ImmediatelyFast and SkinLayers rendering optimizations interact correctly during player render states without causing skin visual glitches or missing 3D layers.
- **T3.6**: Xaero's World Map rendering overlays do not conflict with JEI recipe screens when the map interface is opened.

### Tier 4 - Real-World Application Scenarios (6 cases)
- **T4.1**: A player switches cape type to LabyMod, saves config, restarts client, and verifies LabyMod cape is resolved and loaded.
- **T4.2**: A player changes render scale preset to BALANCED, verifies scale is 0.75f, switches back to CUSTOM, and sets render scale manually to 1.2f.
- **T4.3**: The launcher runs, cleans up old mod jars, starts Minecraft, and LadsEarlyWindow displays the animated loading screen without white flashes.
- **T4.4**: Minecraft adopts the early window, initializes early window, loads mixins, and displays the main menu with early window correctly destroyed.
- **T4.5**: A player changes skin customizations, cycles through all available capes, changes render scale presets under Sodium menu, and plays the game without visual glitches.
- **T4.6**: A player joins a high-density multiplayer server, opens Xaero's World Map, views active players with 3D skin layers, looks up recipes in JEI, and experiences immediatelyfast rendering optimizations with high stable FPS.

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
