package com.thelads.core.v1_20_1.feature.clumps;

import com.thelads.core.v1_20_1.perf.StandDownMixinPlugin1201;

/** The native Clumps mixins apply only while a real Clumps jar is not installed. */
public final class ClumpsMixinPlugin1201 extends StandDownMixinPlugin1201 {
    @Override protected String feature() { return "Clumps"; }
    @Override protected String[] originals() { return new String[] {"clumps"}; }
}
