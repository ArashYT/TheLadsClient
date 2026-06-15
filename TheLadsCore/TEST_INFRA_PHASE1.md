# E2E Test Infra: Phase 1 Feature Expansion

## Test Philosophy
- Opaque-box, requirement-driven. No dependency on implementation design.
- Methodology: Category-Partition + BVA + Pairwise + Workload Testing.

## Feature Inventory
| # | Feature | Source (requirement) | Tier 1 | Tier 2 | Tier 3 |
|---|---------|---------------------|:------:|:------:|:------:|
| 1 | Game launches without Essential Mod | SCOPE §1 | 5      | 5      | ✓      |
| 2 | In-game Skin & Cape Manager UI opens correctly | SCOPE §2 | 5      | 5      | ✓      |
| 3 | Apply skins/capes via local library, URL, username | SCOPE §3 | 5      | 5      | ✓      |
| 4 | Launcher profile (`lads_profile.json`) sync | SCOPE §4 | 5      | 5      | ✓      |
| 5 | Save current skin/cape to local library | SCOPE §5 | 5      | 5      | ✓      |

## Test Architecture
- Test runner: Python `unittest` scripts / CLI framework. 
- Location: `C:\Users\Arash\Desktop\Lads Client\TheLadsCore\e2e_tests_phase1`
- Test case format: Python functions interacting with the file system (`lads_profile.json`, mods folder) and validating outcomes.

## Real-World Application Scenarios (Tier 4)
| # | Scenario | Features Exercised | Complexity |
|---|----------|--------------------|------------|
| 1 | First launch without Essential, apply skin via URL and save to library | F1, F2, F3, F5 | Medium     |
| 2 | Launch with existing profile, verify skin loads, change via username | F1, F2, F3, F4 | High       |
| 3 | Library management: Apply saved skin, update profile, restart | F2, F3, F4, F5 | High       |
| 4 | Stress: Rapidly change skins via URL/Username, save to library | F2, F3, F5 | High       |
| 5 | Offline launch: Profile syncs existing local skin | F1, F4, F5 | Medium     |

## Coverage Thresholds
- Tier 1: ≥5 per feature
- Tier 2: ≥5 per feature
- Tier 3: pairwise coverage of major feature interactions
- Tier 4: ≥5 realistic application scenarios
