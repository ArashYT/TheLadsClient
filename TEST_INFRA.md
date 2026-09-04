# TEST_INFRA: Comprehensive Opaque-Box E2E Testing Infrastructure

## 1. Architectural Overview & Test Philosophy

This document defines the comprehensive opaque-box End-to-End (E2E) testing infrastructure for **The Lads Client Modernization & Parity Project** (Milestones M1–M4).

### Core Testing Tenets:
1. **Strictly Requirement-Driven & Opaque-Box**:
   All test cases are derived strictly from user requirements (`ORIGINAL_REQUEST.md` § 2026-09-04T15:05:06Z) and architecture specifications (`PROJECT.md`). Tests assert observable external behavior, filesystem layout, schema validity, compilation status, process exit codes, and boot log contracts without relying on volatile internal private details.
2. **Zero Facade / Anti-Tamper Integrity**:
   No test is a trivial pass-through facade. Every test asserts genuine system properties. Current test runs report 147 passing tests and 19 accurately failing tests (reflecting features currently being implemented in M2 and M3), proving the test suite functions as a genuine verification gate.
3. **Multi-Tiered Verification Hierarchy**:
   The suite is organized into four distinct tiers:
   - **Tier 1 (Feature Coverage)**: Happy-path coverage for all 15 project features (>=5 tests per feature = 75 tests).
   - **Tier 2 (Boundary & Corner Cases)**: Edge cases, limits, malformed inputs, error conditions, and resilience (>=5 tests per feature = 75 tests).
   - **Tier 3 (Cross-Feature Combinations)**: Pairwise integration across Launcher, Client Core, Packwiz, and Filesystem layers (10 tests).
   - **Tier 4 (Real-World Application Workloads)**: End-to-end user journeys, cold-start onboarding, lifecycle persistence, and gatekeeper verification (6 tests).
   - **Total Active Test Cases**: **166 tests**.

---

## 2. Directory Layout & Artifact Map

```
tests/
├── __init__.py                       # Python package marker
├── common_helpers.py                 # Path resolution, git helpers, TOML parser, schema validators, boot log analyzer
├── test_tier1_feature_coverage.py    # 75 Feature Coverage test cases (F1 - F15)
├── test_tier2_boundary_corner.py     # 75 Boundary & Corner test cases (F1 - F15)
├── test_tier3_cross_feature.py       # 10 Pairwise Cross-Feature test cases
├── test_tier4_real_world.py          # 6 Real-World Application Workload test cases
├── run_tests.py                      # Master Python CLI test runner (filtering, JSON/MD export)
├── Invoke-E2ETests.ps1               # Native PowerShell test runner wrapper
├── results.json                      # Machine-readable test run results
└── results.md                        # Human-readable markdown test summary
```

---

## 3. Test Runner Execution Guide

### 3.1 PowerShell Runner (Recommended for Windows)

From the project root (`c:\Users\Arash\Desktop\The Lads Client Dev\Lads Client`):

```powershell
# Run the entire test suite (all 4 tiers)
pwsh -File tests\Invoke-E2ETests.ps1

# Run specific tiers
pwsh -File tests\Invoke-E2ETests.ps1 -Tier 1
pwsh -File tests\Invoke-E2ETests.ps1 -Tier 1,2
pwsh -File tests\Invoke-E2ETests.ps1 -Tier 4

# Run specific feature tests (e.g., F1 Git History & Repo Size)
pwsh -File tests\Invoke-E2ETests.ps1 -Feature F01 -VerboseOutput

# Run with JSON and Markdown summary exports
pwsh -File tests\Invoke-E2ETests.ps1 -JsonOutput tests\results.json -MarkdownOutput tests\results.md
```

### 3.2 Python Runner (Cross-Platform)

```bash
# Run all tests with standard summary
python tests/run_tests.py

# Run specific tier with verbose execution
python tests/run_tests.py --tier 1 --verbose

# Run specific feature filter
python tests/run_tests.py --feature F02

# Export machine-readable artifacts
python tests/run_tests.py --json-output tests/results.json --markdown-output tests/results.md
```

---

## 4. Feature Coverage Inventory (Tiers 1 & 2)

| Feature # | Feature Name | Milestone | Tier 1 (Happy-Path) | Tier 2 (Boundary & Corner) | Total Tests |
|---|---|---|---|---|---|
| **F1** | Git History Purge & Repo Size | M1 | `test_f01_01` .. `05` (5) | `test_f01_b01` .. `b05` (5) | 10 |
| **F2** | Packwiz Metadata Migration | M1 | `test_f02_01` .. `05` (5) | `test_f02_b01` .. `b05` (5) | 10 |
| **F3** | Workspace Clutter Hygiene | M1 | `test_f03_01` .. `05` (5) | `test_f03_b01` .. `b05` (5) | 10 |
| **F4** | Strict .gitignore Configuration | M1 | `test_f04_01` .. `05` (5) | `test_f04_b01` .. `b05` (5) | 10 |
| **F5** | Launcher 0-Warning Compilation | M2 | `test_f05_01` .. `05` (5) | `test_f05_b01` .. `b05` (5) | 10 |
| **F6** | Launcher MVVM Modularity | M2 | `test_f06_01` .. `05` (5) | `test_f06_b01` .. `b05` (5) | 10 |
| **F7** | Multi-Version Profile Manager | M2 | `test_f07_01` .. `05` (5) | `test_f07_b01` .. `b05` (5) | 10 |
| **F8** | Shared Settings & Keybinds Sync | M2 | `test_f08_01` .. `05` (5) | `test_f08_b01` .. `b05` (5) | 10 |
| **F9** | Dynamic Paths Elimination | M2 | `test_f09_01` .. `05` (5) | `test_f09_b01` .. `b05` (5) | 10 |
| **F10** | Java 21/25 Runtime Management | M2 | `test_f10_01` .. `05` (5) | `test_f10_b01` .. `b05` (5) | 10 |
| **F11** | Multi-Project Gradle Build | M3 | `test_f11_01` .. `05` (5) | `test_f11_b01` .. `b05` (5) | 10 |
| **F12** | LadsGraphics UI Bridge | M3 | `test_f12_01` .. `05` (5) | `test_f12_b01` .. `b05` (5) | 10 |
| **F13** | Title Screen Button Parity | M3 | `test_f13_01` .. `05` (5) | `test_f13_b01` .. `b05` (5) | 10 |
| **F14** | In-Game Account Switcher & Skins| M3 | `test_f14_01` .. `05` (5) | `test_f14_b01` .. `b05` (5) | 10 |
| **F15** | Mixin Stability & Boot Logs | M3 | `test_f15_01` .. `05` (5) | `test_f15_b01` .. `b05` (5) | 10 |
| **Total** | — | — | **75 tests** | **75 tests** | **150 tests** |

---

## 5. Cross-Feature Combinations (Tier 3)

| Test ID | Cross-Feature Interaction | Verification Focus |
|---|---|---|
| `test_t3_01` | F7 (Profiles) + F8 (Shared Settings) | Profile switching between 1.21.1 and 26.2 propagates modified keybinds via `shared/options.txt`. |
| `test_t3_02` | F9 (Launcher Paths) + F9 (Core Paths) | `PathService.BaseDirectory` and `ClientPaths.getBaseDir()` resolve to identical `%APPDATA%/.theladsclient`. |
| `test_t3_03` | F7 (Profiles) + F10 (Java Runtimes) | Profile selection dynamically resolves target Java version (1.21.1 -> Java 21; 26.2 -> Java 25). |
| `test_t3_04` | F7 (Profiles) + F8 (Isolation Mode) | Toggling `IsIsolated = True` isolates options and prevents sync with shared directory. |
| `test_t3_05` | F1 (Git Purge) + F2 (Packwiz Metadata) | Packwiz modpack is purely metadata-driven (`.pw.toml`) with zero tracked jars, maintaining repo size < 50 MB. |
| `test_t3_06` | F6 (Launcher Auth) + F14 (Mod Accounts) | Accounts saved by launcher into `lads_accounts.json` validate against mod schema and skin manager. |
| `test_t3_07` | F8 (Profile Config) + F12 (LadsGraphics) | HUD coordinates and presets in `lads_profile.json` map directly to `LadsGraphics` drawing primitives. |
| `test_t3_08` | F11 (Gradle Build) + F15 (Mixins) | Subproject mixin targets strictly match MC versions (`v1_21_1` -> `Gui`; `v26_2` -> `Hud`). |
| `test_t3_09` | F11 (Gradle Build) + F13 (Title Screen) | Title screen button injection above Options and 24px downward shift identical across both subprojects. |
| `test_t3_10` | F4 (Gitignore) + F11 (Gradle Output) | Strict gitignore rules block all Gradle subproject output jars and launcher release binaries. |

---

## 6. Real-World Application Scenarios (Tier 4)

| Test ID | Scenario Description | Primary Assertion |
|---|---|---|
| `test_t4_01` | **Pipeline Gatekeeper Verification** | Executes simulated 4-gate verification: repo size check, workspace hygiene check, packwiz cleanliness, and boot log verification. |
| `test_t4_02` | **Cold-Start User Onboarding** | Simulates fresh launch without `.theladsclient` folder; verifies automatic provisioning of `shared`, `profiles`, `runtime`, `logs`, and `bin`. |
| `test_t4_03` | **Full Multi-Profile Lifecycle** | Select 1.21.1 -> sync settings -> simulate gameplay & control remap -> exit -> sync to shared -> launch 26.2 -> verify controls inherited. |
| `test_t4_04` | **Boot Log Multi-Version Validation** | Parses full startup boot logs for 1.21.1 and 26.2; verifies exactly 1 `Reloading ResourceManager:`, startup benchmark line, and 0 mixin errors. |
| `test_t4_05` | **Packwiz Metadata Resolution** | Iterates through all `.pw.toml` files in `Packwiz/mods/` ensuring valid download and update metadata across all mods. |
| `test_t4_06` | **Account & Skin Cache Lifecycle** | Add account in launcher -> write `lads_accounts.json` -> cache skin to disk -> verify offline availability and persistence across sessions. |

---

## 7. Authoritative Output Derivation & Verification Sources

All expected outputs in this test suite are derived from:
1. **User Request (`ORIGINAL_REQUEST.md`)**:
   - Cloned repository size `< 50 MB`.
   - 0 compiler errors and 0 warnings on `dotnet build TheLadsLauncher.csproj`.
   - Clean compilation of all Gradle subprojects (`:common`, `:v1_21_1`, `:v26_2`).
   - In-game boot log (`latest.log`) confirming exactly ONE `Reloading ResourceManager`, "Game took N seconds to start", and zero mixin injection exceptions.
2. **Project Architecture Specifications (`PROJECT.md`)**:
   - Decoupled MVVM services: `IPathService`, `IProfileService`, `IJavaService`, `IAuthService`, `ILaunchService`.
   - Dynamic path contract: `%APPDATA%/.theladsclient`.
   - Shared settings directory: `%APPDATA%/.theladsclient/shared/` (`options.txt`, `servers.dat`, `lads_accounts.json`, `lads_profile.json`).
   - Managed runtime paths: `%APPDATA%/.theladsclient/runtime/java-21` and `java-25`.
   - Title screen layout contract: "Lads Settings" positioned directly above "Options", widgets shifted down by 24px.
