package com.thelads.core.v1_20_1.embedded.fastipping;

import com.thelads.core.v1_20_1.perf.StandDownMixinPlugin1201;

public final class FastIpPingMixinPlugin extends StandDownMixinPlugin1201 {
    @Override protected String feature() { return "fastipping"; }
    @Override protected String[] originals() { return new String[] {"fastipping"}; }
}
