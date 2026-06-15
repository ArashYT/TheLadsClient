# E2E Test Infra: TheLadsCore

## Test Philosophy
- Opaque-box, requirement-driven. No dependency on implementation design.
- Methodology: Category-Partition + BVA + Pairwise + Workload Testing.

## Feature Inventory
| # | Feature | Source (requirement) | Tier 1 | Tier 2 | Tier 3 |
|---|---------|---------------------|:------:|:------:|:------:|
| 1 | Settings GUI | ORIGINAL_REQUEST §13 | 5      | 5      | ✓      |
| 2 | Right Shift Hook | ORIGINAL_REQUEST §13 | 5      | 5      | ✓      |
| 3 | Pause Menu Hook | ORIGINAL_REQUEST §13 | 5      | 5      | ✓      |
| 4 | JSON Config Module System | ORIGINAL_REQUEST §16 | 5      | 5      | ✓      |
| 5 | BetterF3 Module | ORIGINAL_REQUEST §20 | 5      | 5      | ✓      |
| 6 | Dynamic FPS Module | ORIGINAL_REQUEST §21 | 5      | 5      | ✓      |
| 7 | PingView Module | ORIGINAL_REQUEST §22 | 5      | 5      | ✓      |

## Test Architecture
- Test runner: `gradlew test` (using JUnit 5)
- Test case format: JUnit `@Test` methods using mocked/headless Minecraft environment variables where necessary.
- Directory layout: `src/test/java/com/thelads/core/e2e/...`

## Real-World Application Scenarios (Tier 4)
| # | Scenario | Features Exercised | Complexity |
|---|----------|--------------------|------------|
| 1 | Full User Session | 1,2,3,4,5,6,7 | High |
| 2 | Heavy Mod Toggling | 4,5,6,7 | Medium |
| 3 | Vanilla Fallback Verification | 4,5,6,7 | Medium |

## Coverage Thresholds
- Tier 1: ≥5 per feature
- Tier 2: ≥5 per feature (where boundaries exist)
- Tier 3: pairwise coverage of major feature interactions
- Tier 4: ≥3 realistic application scenarios
