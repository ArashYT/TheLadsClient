# TEST_READY: E2E Verification & Test Suite Readiness

This document outlines the test runner commands, the feature coverage checklists, and the verification status details for the Lads Client, specifically highlighting the verification of recent bug fixes and native port features.

---

## 1. Test Runner Commands

### Programmatic Integration & E2E Test Suite
To run all automated Java unit and integration tests (including the new E2E Polish tests), execute the following from the `TheLadsCore` directory:
```powershell
cd TheLadsCore
.\gradlew.bat test --tests "com.thelads.core.client.*"
```
Or to run only the E2E Polish tests:
```powershell
.\gradlew.bat test --tests "com.thelads.core.client.E2EPolishTests"
```

### E2E Automation Script
To execute the automated end-to-end post-run validation checks along with the test suite execution, run the script from the project root:
```powershell
.\Run-E2ETests.ps1
```

---

## 2. Feature Coverage Checklist

| Verification Category | Test Case | Target / Focus | Status |
| :--- | :--- | :--- | :--- |
| **Advancements Screen Crash** | `testAdvancementsScreenNoCrash` | Mocks screen lifecycle (`init`, `onAddAdvancementRoot`, `onAdvancementsCleared`, `removed`) and verifies no NPEs occur during description/type/background parsing. | **[PASSED]** |
| **Capes preferred selection** | `testCapesPreferredSelectionAndCycle` | Verifies CapeType enums cycle through selections correctly and generate correct URLs (OptiFine, LabyMod, MinecraftCapes, Cosmetica, CloaksPlus). | **[PASSED]** |
| **Capes texture loading** | `testPlayerHandlerOnLoadTexture` | Verifies that preferred cape selection loads the texture and registers the player handler config. | **[PASSED]** |
| **Not Enough Animations glitches**| `testNotEnoughAnimationsModuleAndProvider`| Verifies the module is registered and enabled, and animation providers initialize/load animations cleanly without runtime class errors. | **[PASSED]** |
| **Skin Layers duplication** | `testSkinLayersModelPartMixinEradicatesDuplication`| Verifies that the custom model rendering intercepts and replaces the vanilla ModelPart rendering when 3D layers mesh is injected. | **[PASSED]** |
| **Skin Layers configuration** | `testSkinLayersDisabledSetup3dLayersReturnsFalse`| Verifies that the feature toggle works, disabling 3D layers correctly when the config option is toggled off. | **[PASSED]** |

---

## 3. Verification Status Details

- **Programmatic E2E Polish Tests**: **PASS**. All tests compiled and completed successfully.
- **Integration Test Suite**: **PASS**. All reflection-based checks pass.
- **Run-E2ETests.ps1 Validation**: **PASS**. Output matches the expected mixin registration status, gradle properties check, and shaded jar status.
- **Old Standalone Jars Cleanup**: **PASS**. Automated check verified that no legacy standalone jars are present in the packwiz or Curseforge directories.
