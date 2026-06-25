# Project: Minecraft Client Performance Optimization

## Architecture
- **TheLadsCore (Fabric Mod, Java 25/26)**: Core client-side optimizations and benchmark hooks.
- **TheLadsLauncher (C# Avalonia UI)**: Handles process launching, resource updates, and starting offline/online games.
- **Performance Mods**: Sodium, Lithium, BadOptimizations, FerriteCore, Language Reload, More Culling, and C2ME are integrated.

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| 1 | Exploration & Benchmark Design | Investigate load points, startup, and FPS hooks. Design benchmark mixins. | None | DONE |
| 2 | Benchmark Implementation | Implement automated benchmark script and performance measuring hooks. | M1 | DONE |
| 3 | Load Time Optimizations | Implement class preloading, lazy-loading resource packs, world-load screen skip. | M2 | IN_PROGRESS |
| 4 | Rendering & FPS Optimizations | Implement optimization mixins, tune Sodium/Iris settings, optimize chunk rendering. | M2 | PLANNED |
| 5 | E2E Verification & Auditing | Run benchmarks, verify performance improvement, run forensic audit. | M3, M4 | PLANNED |
| 6 | Resource Pack Override System | Update hidden resource packs, ensure layering, add disable option under a new 'packs' tab in settings UI. | M5 | PLANNED |

## Interface Contracts
- Benchmark JVM parameter `-Dthelads.benchmark=true` launches Minecraft directly into a reproducible test world, runs for a duration (e.g. 20s), logs metrics to `benchmark_results.json`, and exits.
- Optimizations are toggleable or integrated cleanly within standard Fabric/Minecraft lifecycles.

## Code Layout
- `TheLadsCore/src/main/java/com/thelads/core/features/alwayson/benchmark/` - Benchmark logging and auto-load logic.
- `TheLadsCore/src/main/java/com/thelads/core/mixin/alwayson/optimization/` - Custom FPS, chunk loading, world load screen, and startup optimizations.
- `benchmark_runner.ps1` - Automated benchmark runner at the project root.
