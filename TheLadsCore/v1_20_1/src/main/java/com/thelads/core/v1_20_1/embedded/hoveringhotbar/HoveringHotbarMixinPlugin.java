package com.thelads.core.v1_20_1.embedded.hoveringhotbar;

import com.thelads.core.v1_20_1.perf.StandDownMixinPlugin1201;

public final class HoveringHotbarMixinPlugin extends StandDownMixinPlugin1201 {
    @Override protected String feature() { return HoveringHotbar.MOD_ID; }
    @Override protected String[] originals() { return new String[] {HoveringHotbar.MOD_ID}; }
}
