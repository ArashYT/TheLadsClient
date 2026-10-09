package com.thelads.core.v1_20_1.perf.dynamicfps;

import com.thelads.core.v1_20_1.perf.StandDownMixinPlugin1201;

/** The embedded Dynamic FPS engine's mixins apply only while the original Dynamic FPS jar is not installed. */
public final class DynamicFpsMixinPlugin extends StandDownMixinPlugin1201 {
    @Override protected String feature() { return "DynamicFPS"; }
    @Override protected String[] originals() { return new String[] {"dynamic_fps"}; }
}
