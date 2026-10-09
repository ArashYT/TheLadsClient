package com.thelads.core.v1_20_1.embedded.fixbookgui;

import com.thelads.core.v1_20_1.perf.StandDownMixinPlugin1201;

public final class FixBookGuiMixinPlugin extends StandDownMixinPlugin1201 {
    @Override protected String feature() { return FixBookGui.MOD_ID; }
    @Override protected String[] originals() { return new String[] {FixBookGui.MOD_ID}; }
}
