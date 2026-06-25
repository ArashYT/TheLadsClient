# Plan - Titans Porting and Minecraft 26.2 Migration

This document outlines the step-by-step strategy for natively porting the 4 remaining major mods (`3dskinlayers`, `immediatelyfast`, `jei`, `xaeros-world-map`) into `TheLadsCore` as toggleable modules, followed by the final migration to Minecraft 26.2.

## Milestones

### Milestone 1: Exploration & Codebase Analysis
- **Goal**: Research current status of the 4 target mods.
- **Tasks**:
  1. Inspect existing decompiled sources in `temp_decompiled_mods/`.
  2. Locate existing `immediatelyfast` and `3dskinlayers` classes/mixins and verify if they are currently active.
  3. Determine how to implement `jei` and `xaeros-world-map` as native modules.
  4. Draft class structure and mixin hook locations.

### Milestone 2: E2E Test Suite Design (Dual Track)
- **Goal**: Design opaque-box E2E test cases for the 4 target modules.
- **Tasks**:
  1. Define test cases for basic 3D skin extrusion (`3dskinlayers`).
  2. Define test cases for core rendering optimization verification (`immediatelyfast`).
  3. Define test cases for simple recipe search and viewing (`jei`).
  4. Define test cases for fullscreen 2D world map rendering and exploration (`xaeros-world-map`).
  5. Create/update `TEST_INFRA.md` and publish `TEST_READY.md`.

### Milestone 3: Port `immediatelyfast` & `3dskinlayers` Natively
- **Goal**: Wrap these two mods as toggleable modules in `ModuleManager.java`.
- **Tasks**:
  1. Implement `SkinLayersModule.java` and `ImmediatelyFastModule.java` extending the base `Module` class.
  2. Register them in `ModuleManager.java`.
  3. Update `3dskinlayers` and `immediatelyfast` mixins to dynamically check `isEnabled()` on their respective modules.
  4. Remove any unused initialization or config logic.
  5. Verify successful compilation.

### Milestone 4: Port `jei` & `xaeros-world-map` Natively
- **Goal**: Implement simple native module versions of JEI and Xaero's World Map, removing their shaded JAR dependencies.
- **Tasks**:
  1. Remove shaded JAR dependencies (`jei` and `xaeroworldmap`) from `build.gradle`.
  2. Remove associated external initialization logic from `TheLadsCore.java` and `TheLadsCoreClient.java`.
  3. Implement native core features for `jei` (simple recipe search UI) and `xaeros-world-map` (simple 2D map overlay/screen).
  4. Register them as toggleable modules in `ModuleManager.java`.
  5. Verify successful compilation.

### Milestone 5: Minecraft 26.2 Migration
- **Goal**: Execute the final migration to target Minecraft 26.2.
- **Tasks**:
  1. Update `gradle.properties` to target Minecraft `26.2` and `fabric_api_version=0.152.1+26.2`.
  2. Resolve all compilation errors and API breaking changes in `TheLadsCore`.
  3. Build launcher and verify setting changes if any.
  4. Run tests and ensure the compilation succeeds.

### Milestone 6: Final Verification & Integrity Audit
- **Goal**: Ensure clean, authentic implementation passing all verification gates.
- **Tasks**:
  1. Perform detailed review of all modified files.
  2. Execute E2E and unit test suites.
  3. Run Forensic Auditor to guarantee code integrity (no hardcoding or dummy implementations).
  4. Clean up temporary or cached directories.
