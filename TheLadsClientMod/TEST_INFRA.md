# E2E Test Infra: TheLadsClientMod

## Test Philosophy
- Opaque-box, requirement-driven. No dependency on implementation design.
- Methodology: Category-Partition + BVA + Pairwise + Workload Testing.

## Feature Inventory
| # | Feature | Source (requirement) | Tier 1 | Tier 2 | Tier 3 |
|---|---------|---------------------|:------:|:------:|:------:|
| 1 | Mod Initialization | ORIGINAL_REQUEST R1 | 5      | 5      | ✓      |
| 2 | Right-Shift GUI    | ORIGINAL_REQUEST R2 | 5      | 5      | ✓      |
| 3 | Config Save/Load   | ORIGINAL_REQUEST R3 | 5      | 5      | ✓      |
| 4 | Cape Toggle        | ORIGINAL_REQUEST R3 | 5      | 5      | ✓      |
| 5 | UI Scaling Toggle  | ORIGINAL_REQUEST R3 | 5      | 5      | ✓      |

## Test Architecture
- Test runner: `./gradlew test` (or `./gradlew runTestmod` using Fabric API's Test Framework / Game Test if applicable, or unit/integration tests that mock Minecraft environment).
- Test case format: JUnit/GameTest automated tests validating config file modifications, input simulation, and internal state changes.
- Directory layout: `src/test/java/` or `src/testmod/java/`

## Real-World Application Scenarios (Tier 4)
| # | Scenario | Features Exercised | Complexity |
|---|----------|--------------------|------------|
| 1 | User opens mod, toggles capes off, restarts mod, and verifies cape remains off. | 1, 2, 3, 4 | Medium |
| 2 | User opens mod, changes UI scale, verifies scale adjusts. | 1, 2, 3, 5 | Medium |
| 3 | User rapidly toggles settings multiple times and verifies config consistency. | 2, 3, 4, 5 | High |
| 4 | User starts mod without config file, verifies default config generation and load. | 1, 3 | Medium |
| 5 | User modifies config file externally, opens game, and verifies GUI reflects changes. | 2, 3, 4, 5 | High |

## Coverage Thresholds
- Tier 1: ≥5 per feature
- Tier 2: ≥5 per feature (where boundaries exist)
- Tier 3: pairwise coverage of major feature interactions
- Tier 4: ≥5 realistic application scenarios
