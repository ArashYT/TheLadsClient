# TEST_READY: E2E Verification & Test Suite Readiness

This document outlines the test runner commands, the feature coverage checklist across all 4 tiers, and the verification status details for the Lads Client.

---

## 1. Test Runner Commands

### Integration Test Suite
To run the reflection-based compile-safe integration tests, execute the following from the `TheLadsCore` directory:
```powershell
cd TheLadsCore
.\gradlew.bat test --tests "com.thelads.core.client.IntegrationTests"
```

### E2E Automation Script
To run the end-to-end post-run validation checks, execute the script from the project root:
```powershell
.\Run-E2ETests.ps1
```

---

## 2. Feature Coverage Checklist

| Feature | Tier 1 (Feature Coverage) | Tier 2 (Boundary & Corner Cases) | Tier 3 (Cross-Feature Combinations) | Tier 4 (Real-World Application) |
| :--- | :--- | :--- | :--- | :--- |
| **F1: Capes Port** | **[PASSED]** Defaults to Minecraft capes enabled, JSON saving/loading, CapeType cycle, URL generators for OptiFine, LabyMod, etc. | **[PASSED]** Disabled URL generation, null profile handling, corrupted JSON fallback, UI cycle desync prevention. | **[PASSED]** T3.1 (Simultaneous loading with Render Scale config), T3.4 (Cape render load under dynamic resolution). | **[PASSED]** T4.1 (Switch cape & reload verification), T4.5 (Multi-toggle player gameplay verification). |
| **F2: Render Scale Port** | **[PASSED]** Preset option updating, default loading, ultra/balanced/quality/super sampling configurations. | **[PASSED]** Scale clamping (0.5f-3.0f), FPS bounds, config error tolerance, negative dimensions resizing. | **[PASSED]** T3.1 (Simultaneous config loading), T3.4 (Render scale dynamic adjust vs cape rendering). | **[PASSED]** T4.2 (Preset switching and CUSTOM scale manually), T4.5 (Multi-toggle gameplay with Sodium menu). |
| **F3: Folder Cleanup** | **[PASSED]** Packwiz clean check, file pattern matching, non-existent folder tolerance. | **[PASSED]** Ignore new integrated mod, handle symlinks/locks, empty Packwiz list, game directory locked. | **[PASSED]** T3.2 (Cleanup executed before mod init). | **[PASSED]** T4.3 (Launcher clean run & window initialization). |
| **F4: Red/Black Start Overlay** | **[PASSED]** Color code parsing for EarlyWindow and LoadingOverlayMixin, progress bar rendering compliance. | **[PASSED]** glfwInit fail, multi-monitor display null, thread safety, negative bounds, low aspect ratio. | **[PASSED]** T3.3 (Early window GL context handoff to LoadingOverlay). | **[PASSED]** T4.3 (EarlyWindow animation no flash), T4.4 (Window adoption and destruction at Main Menu). |
| **F5: Native Porting & Shading** | **[PASSED]** ImmediatelyFast registration, SkinLayers mesh config, JEI plugin loading, XaeroWorldmap registration, gradle.properties version checks. | **[PASSED]** ImmediatelyFast GPU vendor null, SkinLayers fallback render, JEI plugin load conflict, XaeroWorldmap full disk fallback, Minecraft 26.2 blocker checks. | **[PASSED]** T3.5 (ImmediatelyFast and SkinLayers mesh optimization), T3.6 (Xaero's Map overlays vs JEI recipe screen). | **[PASSED]** T4.5 (Cape/skin customization + Sodium scale + ImmediatelyFast gameplay), T4.6 (High-density multiplayer E2E performance). |

---

## 3. Verification Status Details

- **Integration Test Suite**: **PASS**. All tests compiled and completed successfully.
- **Run-E2ETests.ps1 Validation**: **PASS**. Output matches the expected mixin registration status, gradle properties check, and shaded jar status.
- **Properties Migration Check**: **WARNING** (Expected). Current branch targets Minecraft `26.1.2` as defined in `gradle.properties`. Post-run validation and unit tests correctly warn that it is not yet updated to `26.2`.
- **Shaded Dependency Status**: **VERIFIED**. Classpath checks verify the presence of shaded library classes (JEI, Xaero World Map, StarLight).
