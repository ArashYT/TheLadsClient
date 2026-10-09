package com.thelads.core.v1_20_1.embedded.lazyai;

/** Called once at client initialization while the original Lazy AI jar is not installed (see EmbeddedMods1201). */
public final class LazyAiInit1201 {
    private LazyAiInit1201() {}

    public static void init() {
        // The mixins need no setup; only the QA probe (-Dthelads.perfProbe=true) has anything to do.
        if (com.thelads.core.v1_20_1.perf.PerfProbe1201.passive()) LazyAi.selfTest();
    }
}
