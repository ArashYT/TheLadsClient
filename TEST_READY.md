# TEST_READY: Opaque-Box E2E Test Suite Readiness

**Project**: The Lads Client Modernization & Parity  
**Track**: E2E Testing Suite (Tiers 1–4)  
**Status**: **READY**  
**Date**: 2026-09-04  

---

## 1. Test Runner Commands

The comprehensive opaque-box E2E test suite can be run via PowerShell or Python from the project root:

### PowerShell (Primary Windows Runner)
```powershell
# Run the complete test suite (all 4 tiers)
pwsh -File tests\Invoke-E2ETests.ps1

# Run with verbose output and test result report exports
pwsh -File tests\Invoke-E2ETests.ps1 -VerboseOutput -JsonOutput tests\results.json -MarkdownOutput tests\results.md

# Run specific tiers
pwsh -File tests\Invoke-E2ETests.ps1 -Tier 1
pwsh -File tests\Invoke-E2ETests.ps1 -Tier 1,2
pwsh -File tests\Invoke-E2ETests.ps1 -Tier 3
pwsh -File tests\Invoke-E2ETests.ps1 -Tier 4

# Run specific feature tests (e.g., F1: Git Hygiene & Repo Size)
pwsh -File tests\Invoke-E2ETests.ps1 -Feature F01
```

### Python CLI Runner (Cross-Platform)
```bash
# Run all tiers
python tests/run_tests.py

# Run specific tier with verbose execution
python tests/run_tests.py --tier 1 --verbose

# Run with JSON and Markdown report export
python tests/run_tests.py --json-output tests/results.json --markdown-output tests/results.md
```

---

## 2. Test Suite Composition & Coverage Numbers

The test suite contains **166 individual opaque-box test cases** structured across 4 rigorous tiers:

| Tier | Category | Scope / Target | Test Count | Current Status |
|:---:|:---|:---|:---:|:---:|
| **Tier 1** | **Feature Coverage** | Happy-path verification for Features F1 through F15 (>= 5 cases each) | **75** | 58 Passed, 17 Pending M2/M3 |
| **Tier 2** | **Boundary & Corner Cases** | Edge cases, limits, malformed payloads, error handling for F1–F15 (>= 5 cases each) | **75** | 74 Passed, 1 Pending M2/M3 |
| **Tier 3** | **Cross-Feature Combinations** | Pairwise cross-module integration across Launcher, Core, and Packwiz | **10** | 9 Passed, 1 Pending M2/M3 |
| **Tier 4** | **Real-World Workloads** | Full session lifecycles, cold-start onboarding, and pipeline gatekeepers | **6** | 6 Passed |
| **TOTAL** | **Full E2E Suite** | **Comprehensive Opaque-Box System Verification** | **166** | **147 Passed, 19 Pending** |

---

## 3. Baseline Verification Results

```
===========================================================================
 E2E TEST EXECUTION SUMMARY
===========================================================================
 Total Tests Run: 166
 Passed:          147
 Failed:          19 (pending M2/M3 feature completion by worker agents)
 Errors:          0
 Skipped:         0
 Elapsed Time:    5.73 seconds
===========================================================================
```

### Verified Milestone 1 Features (100% Pass):
- **Feature 1 (Git History Purge & Repo Size)**: 10/10 tests pass (Pack size ~13.13 MB, zero blobs > 50 MB, temp_auth.zip purged).
- **Feature 2 (Packwiz Metadata Migration)**: 10/10 tests pass (0 jars tracked, .pw.toml valid metadata, index.toml valid).
- **Feature 3 (Workspace Clutter Hygiene)**: 10/10 tests pass (TestLogin, TheLadsLauncher_Clean, loose exes purged).
- **Feature 4 (Strict .gitignore Configuration)**: 10/10 tests pass (Blocks .exe, .jar, .zip, bin/, obj/, .gradle/, build/).

### Pending Features (Expected Failures on Pre-M2/M3 State):
The 19 failing tests accurately reflect active features currently under implementation by `worker_m2_launcher` and `worker_m3_client_core`:
- F5 (0-warning launcher compilation): Pending worker_m2 warning remediation.
- F9 (Zero hardcoded personal paths in launcher): Pending worker_m2 dynamic PathService refactor.
- F11 (Multi-project Gradle build): Pending worker_m3 subproject creation (:common, :v1_21_1, :v26_2).
- F12 (LadsGraphics render bridge & adapters): Pending worker_m3 adapter implementation.
- F13 (TitleScreen button layout parity in both submodules): Pending worker_m3 mixin porting.
- F14 (AccountSwitcherScreen dynamic paths): Pending worker_m3 ClientPaths integration.
- F15 (Version-specific mixin configs): Pending worker_m3 mixin split.

---

## 4. Verification Gatekeeper Hand-off

The test suite is fully self-contained, idempotent, and ready for continuous execution during Milestone 4:
1. `worker_m2_launcher` completes M2 -> Rerunning `pwsh tests\Invoke-E2ETests.ps1 -Feature F05` and `-Feature F09` will verify resolution of launcher warnings and hardcoded paths.
2. `worker_m3_client_core` completes M3 -> Rerunning `pwsh tests\Invoke-E2ETests.ps1 -Feature F11` through `F15` will verify multi-version subproject compilation, LadsGraphics bridge, and title screen parity.
3. Orchestrator executes full suite -> Reaches 166/166 (100%) pass rate.
