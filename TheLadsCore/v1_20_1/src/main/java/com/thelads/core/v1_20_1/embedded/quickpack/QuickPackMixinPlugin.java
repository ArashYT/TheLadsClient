package com.thelads.core.v1_20_1.embedded.quickpack;

import com.thelads.core.v1_20_1.perf.StandDownMixinPlugin1201;

public final class QuickPackMixinPlugin extends StandDownMixinPlugin1201 {
    @Override protected String feature() { return "quickpack"; }
    @Override protected String[] originals() { return new String[] {QuickPack.MOD_ID, "quickpack"}; }
}
