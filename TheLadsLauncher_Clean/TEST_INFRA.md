# E2E Test Infra: The Lads Launcher

## Test Philosophy
- Opaque-box, requirement-driven. No dependency on implementation design.
- Methodology: Category-Partition + BVA + Pairwise + Workload Testing.

## Feature Inventory
| # | Feature | Source (requirement) | Tier 1 | Tier 2 | Tier 3 |
|---|---------|---------------------|:------:|:------:|:------:|
| 1 | Unified Mods & Packs UI | ORIGINAL_REQUEST §R1 | 5 | 5 | ✓ |
| 2 | Modrinth Integration | ORIGINAL_REQUEST §R1 | 5 | 5 | ✓ |
| 3 | CurseForge Integration | ORIGINAL_REQUEST §R1, R2 | 5 | 5 | ✓ |
| 4 | Asset Management (Install/Update/Delete) | ORIGINAL_REQUEST §R1 | 5 | 5 | ✓ |
| 5 | Target MC Version Filtering | ORIGINAL_REQUEST §R1 | 5 | 5 | ✓ |
| 6 | API Key Secure Storage | ORIGINAL_REQUEST §R2 | 5 | 5 | ✓ |
| 7 | Premium UI Enhancements | ORIGINAL_REQUEST §R3 | 5 | 5 | ✓ |
| 8 | Particle Background Animation | ORIGINAL_REQUEST §R4 | 5 | 5 | ✓ |
| 9 | Launch Button Version Indicator | ORIGINAL_REQUEST §R5 | 5 | 5 | ✓ |
| 10 | Home Page Status Pill | ORIGINAL_REQUEST §R5 | 5 | 5 | ✓ |

## Test Architecture
- Test runner: Python `unittest` framework (tests/e2e_test_runner.py) using Mock HTTP Server and Static/Dynamic process analysis.
- Test case format: Action -> Expected Visual/System State. Subprocess execution with process tree cleanup (`taskkill`).
- Directory layout: tests/

## Real-World Application Scenarios (Tier 4)
| # | Scenario | Features Exercised | Complexity |
|---|----------|--------------------|------------|
| 1 | Full Modrinth Modpack Assembly | 1, 2, 4, 5, 7, 9, 10 | High |
| 2 | Authenticated CurseForge Asset Hunt | 1, 3, 4, 5, 6, 7 | High |
| 3 | Mixed Source Mods & Resourcepacks | 1, 2, 3, 4, 5 | High |
| 4 | Settings Modification & UI Refresh | 6, 7, 8, 9, 10 | Medium |
| 5 | Version Filtering Override | 1, 2, 3, 5 | Medium |

## Coverage Thresholds
- Tier 1: >=5 per feature
- Tier 2: >=5 per feature (where boundaries exist)
- Tier 3: pairwise coverage of major feature interactions
- Tier 4: >=5 realistic application scenarios
