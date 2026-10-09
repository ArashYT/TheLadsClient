package com.thelads.core.v1_20_1.embedded.ksyxis;

import com.thelads.core.v1_20_1.perf.StandDownMixinPlugin1201;

public final class KsyxisMixinPlugin extends StandDownMixinPlugin1201 {
    @Override protected String feature() { return "ksyxis"; }
    @Override protected String[] originals() { return new String[] {"ksyxis"}; }
}
