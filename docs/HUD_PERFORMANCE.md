HUD performance measurement — 2026-09-05
======================================

The common HUD now avoids repeated text formatting and width measurements when the displayed data is unchanged. In this isolated benchmark, armor/scoreboard CPU cost fell by approximately 31–37%; formatted text fell by approximately 61–68% for stable or tick-changing data. Formatting data that changed every frame had unchanged measured CPU cost. These are Java common-code CPU and allocation measurements, not Minecraft FPS, GPU timings or an end-to-end launch benchmark.

The baseline was measured against the working checkout's current **uncommitted** good code before this optimization, not against HEAD. The after results below are the final capture after preserving immediate armor/scoreboard disappearance between prepare and render. Earlier intermediate after results are not used. Later external-settings integration work is outside this benchmark.

| Scenario | CPU before (ns/frame) | CPU after (ns/frame) | Allocated before (bytes/frame) | Allocated after (bytes/frame) | Width queries before → after |
| --- | ---: | ---: | ---: | ---: | ---: |
| Armor + scoreboard, static | 1,702.99 | 1,175.06 | 952.00 | 64.00 | 86 → 35 |
| Armor + scoreboard, data changes every 8 frames | 1,651.90 | 1,047.34 | 1,136.00 | 77.50 | 63.5 → 27.5 |
| Armor + scoreboard, data changes every frame | 1,643.39 | 1,115.46 | 1,136.00 | 620.00 | 63.5 → 27.5 |
| Direction + speed + health + hunger, static | 1,336.85 | 425.75 | 3,072.00 | 0.00 | 8 → 4 |
| Direction + speed + health + hunger, data changes every 8 frames | 1,473.09 | 579.02 | 3,237.25 | 402.66 | 8 → 4 |
| Direction + speed + health + hunger, data changes every frame | 1,515.66 | 1,515.66 | 3,237.25 | 3,221.25 | 8 → 4 |

One frame means one synthetic `HudManager.render` call for the scenario's selected elements. The “every 8 frames” case is a simulated data cadence; it does not imply a particular game tick rate or frame rate. Configuration options also change every 512 frames in the changing-data scenarios.

The same harness ran before and after on Windows using Java HotSpot 21.0.12. Each scenario warms up for two batches of 262,144 frames (524,288 total), then measures seven batches of 262,144 frames. CPU is current-thread CPU time from `ThreadMXBean`, aggregated across the seven equally sized batches. Allocation is the median current-thread allocated bytes per frame. Wall time is the median and is retained in the raw captures alongside minimum/maximum batch CPU. Long batches reduce the effect of Windows CPU-timer granularity. These are one before/after JVM comparison, not confidence intervals across independent JVM forks; concurrent desktop load and JIT behavior can change timings on another run.

The fake bridge uses preallocated armor and scoreboard snapshots. The fake graphics implementation consumes text, geometry, colors, shadows and pose operations into a checksum without retaining draw-call objects. Its width function processes the characters but does not perform Minecraft font rendering. Measurement calls are counted separately and excluded from the output checksum, allowing redundant measurements to be removed without changing the rendered-command result. This does not measure native bridge collection, font atlas work, GPU submission, other mods or the real game's frame pacing.

Every measured batch had a stable checksum. All six final checksums matched the baseline, with unchanged draw-call counts:

| Raw scenario name | Before and after checksum | Draw calls/frame |
| --- | --- | ---: |
| `armor_scoreboard_static` | `4f826f6996640011` | 37 |
| `armor_scoreboard_tick_changes` | `e800d1f341b54011` | 29.5 |
| `armor_scoreboard_every_frame_changes` | `996603f4d0f50011` | 29.5 |
| `formatted_static` | `263f05cca2800011` | 8 |
| `formatted_tick_changes` | `029fa0d240da4011` | 8 |
| `formatted_every_frame_changes` | `9989c84a2c720011` | 8 |

The seven optimized production files are under `TheLadsCore/common/src/main/java/com/thelads/core/client/hud/`: `HudElement.java`, `ArmorHudElement.java`, `ScoreboardHudElement.java`, `DirectionHudElement.java`, `SpeedHudElement.java`, `HealthHudElement.java`, and `HungerHudElement.java`. Armor retains formatted equipment lines until equipment or durability mode changes. Scoreboard reuses widths prepared for the current snapshot. Both recheck live data during rendering, including removal. The four formatted text widgets cache strings by their actual data and formatting options. Widths still refresh each frame so font changes remain visible; the centered draw helper reuses that frame's measured width.

The 18 cases in [HudHotPathTest.java](../TheLadsCore/common/src/test/java/com/thelads/core/HudHotPathTest.java) cover data and option changes, mutable armor input, disappearing native data, preview separation and width refresh. The existing 151 HUD/menu/title/account-path cases also passed, giving 169 behavior cases after the optimization. The benchmark itself passed separately as one test with six scenarios. The subsequent combined behavior and integration run passed 201 tests (169 behavior + 20 AdditionalIntegrations + 12 IntegratedSettings), with zero failures or skips; integration tests are not performance samples.

Preserved raw benchmark output, copied from the original captures without rerunning or substituting newer timings:

- [baseline.txt](../artifacts/hud-performance/baseline.txt), SHA-256 `5e96d5e0eeb98c79aa14301737b63a5ef249170c506e23a9303196213b63d35c`.
- [after.txt](../artifacts/hud-performance/after.txt), SHA-256 `854c642831ca5ab71203dcd3e4925fd1b50c5a4be6c0968a6742e4500d54d46a`.
- [HudPerformanceBenchmarkTest.java](../TheLadsCore/common/src/test/java/com/thelads/core/HudPerformanceBenchmarkTest.java), SHA-256 `9027aa0d086b957b68957530b2915129d14f34c182b1ce833d134465f043e039`.

The raw files preserve the `HUD_BENCH_ENV` and six `HUD_BENCH` lines, including batch extrema, median wall times and checksums. The uncommitted baseline source was not archived as a separate checkout, so these logs do not reconstruct that historical source. Do not use a reset to HEAD to recreate it.

To reproduce the harness on the current source, run this PowerShell command from the repository root:

```powershell
Push-Location .\TheLadsCore
try {
    .\gradlew.bat :common:test --tests com.thelads.core.HudPerformanceBenchmarkTest --rerun-tasks --offline --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'HUD benchmark failed' }
    [xml]$hudBenchmarkXml = Get-Content -LiteralPath '.\common\build\test-results\test\TEST-com.thelads.core.HudPerformanceBenchmarkTest.xml' -Raw
    $hudBenchmarkXml.testsuite.'system-out'
} finally {
    Pop-Location
}
```

Gradle's normal result is `TheLadsCore/common/build/test-results/test/TEST-com.thelads.core.HudPerformanceBenchmarkTest.xml`; later test runs can replace it. The HTML report is `TheLadsCore/common/build/reports/tests/test/index.html`. The benchmark restores bridge, HUD elements, module options, positions and global HUD settings after each scenario, and does not access user configuration files. For a future before/after comparison, preserve both working-source variants first and run this unchanged harness with the same JDK and environment. Keep draw checksums equal and report CPU/allocation measurements without extrapolating them into actual FPS.
