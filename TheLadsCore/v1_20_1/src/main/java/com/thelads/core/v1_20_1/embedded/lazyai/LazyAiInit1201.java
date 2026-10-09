package com.thelads.core.v1_20_1.embedded.lazyai;

/** Called once at client initialization while the original Lazy AI jar is not installed (see EmbeddedMods1201). */
public final class LazyAiInit1201 {
    private LazyAiInit1201() {}

    public static void init() {
        // The mixins need no setup; only the QA probe (-Dthelads.perfProbe=true) has anything to do.
        if (Boolean.getBoolean("thelads.perfProbe")) LazyAi.selfTest();
    }
}
