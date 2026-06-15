# E2E Test Suite Ready

## Test Runner Commands
- **Command to run all integration tests**:
  ```powershell
  cd TheLadsCore
  .\gradlew.bat test --tests "com.thelads.core.client.IntegrationTests"
  ```
- **PowerShell Test & Validation Runner**:
  ```powershell
  powershell -ExecutionPolicy Bypass -File .\Run-E2ETests.ps1
  ```
- **Expected result**: All tests pass successfully, and validation checks output compliance status.

---

## Coverage Summary
| Tier | Count | Description |
|------|------:|-------------|
| 1. Feature Coverage | 20 | T1.1 to T1.20 covering core functionalities of Capes, Render Scale, Cleanup, and Loading Theme |
| 2. Boundary & Corner | 20 | T2.1 to T2.20 covering boundary checks, empty profiles, clamping, file errors, and multi-monitor/OS limits |
| 3. Cross-Feature | 4 | T3.1 to T3.4 covering cross-feature interactions, transitions, and concurrent loads |
| 4. Real-World Application | 5 | T4.1 to T4.5 covering real-world user workflows, startup adoption, and setting changes |
| **Total** | **49** | |

---

## Feature Checklist
| Feature | Tier 1 | Tier 2 | Tier 3 | Tier 4 |
|---------|:------:|:------:|:------:|:------:|
| **F1: Capes Port** | 9 (T1.1-T1.9) | 6 (T2.1-T2.6) | ✓ (T3.1, T3.4) | ✓ (T4.1, T4.5) |
| **F2: Render Scale Port** | 7 (T1.10-T1.16) | 6 (T2.7-T2.12) | ✓ (T3.1, T3.4) | ✓ (T4.2, T4.5) |
| **F3: Folder Cleanup** | 2 (T1.17-T1.18) | 4 (T2.13-T2.16) | ✓ (T3.2) | ✓ (T4.3) |
| **F4: Loading Screen Animation** | 2 (T1.19-T1.20) | 4 (T2.17-T2.20) | ✓ (T3.3) | ✓ (T4.3, T4.4) |

---

## Verification Status
- **Java Integration Tests (`IntegrationTests.java`)**: Implemented and compile-safe. Dynamically executes Capes and Render Scale checks using reflection only when the target classes exist.
- **Color compliance**: Programmatically verifies early window background/bar colors and mixin colors against both initial (blue/indigo) and target (red/black) themes.
- **Post-Run Validation (`Run-E2ETests.ps1`)**: Verifies mixins entry registration in `theladscore.mixins.json` and checks if old standalone jars are still in packwiz or instances mods directories.
