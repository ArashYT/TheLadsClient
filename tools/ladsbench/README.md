# ladsbench (1.9.12 perf lane)

QA-only FPS benchmark mod for 26.3 and 1.20.1 (`bench.ps1` drives sandboxes on the QA laptop; results in `out\<tag>\`). Build:
`..\..\TheLadsCore\gradlew -p . build --offline`.

## The QA bar (owner rule: every QA and every benchmark shows it, the whole time)

- **ladsbench runs** show the Lads QA boss bar in the world: `Lads QA · <label> · run k/3 · scene i/N <name> · <n> frames · OK n · SLOW 0 · FAIL 0 · CRASH 0 · ETA m:ss`
  (`bench.ps1` writes `run=` and `runs=` into `bench.properties`; the ETA is this run's remaining scenes plus the runs after it). It is the same vanilla boss
  bar on every config of an A/B pair (Lads, OneClient, variants), updated once a second (one string and one server call, no per-frame work).
- **Verification-harness runs** (`LADS_VERIFY_189_ONLY=perf|players`, the 1.8.9 perf scenes) show the Core's session/run bar (`QaProgress`): it is no longer
  switched off for benchmarks, so **both halves of an A/B pair carry it**. Its per-frame cost is a cached string and a handful of fills (nothing is formatted
  or allocated per frame); compare only runs that both have it, or set `LADS_VERIFY_QABAR=0` for both (an explicit opt-out the harness never sets itself).
- Do not also give a ladsbench sandbox the Core's bar (`-Dthelads.qaBar`/`LADS_QA_BAR`): it would show twice. `bench.ps1` sets neither.
