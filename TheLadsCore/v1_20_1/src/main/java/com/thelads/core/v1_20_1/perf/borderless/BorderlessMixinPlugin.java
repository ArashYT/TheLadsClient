package com.thelads.core.v1_20_1.perf.borderless;

import com.thelads.core.v1_20_1.perf.StandDownMixinPlugin1201;

/** BorderlessFullscreen's window mixin applies only while the Borderless Window jar is not installed. */
public final class BorderlessMixinPlugin extends StandDownMixinPlugin1201 {
    @Override protected String feature() { return "BorderlessFullscreen"; }
    @Override protected String[] originals() { return new String[] {"borderlesswindow"}; }
}
