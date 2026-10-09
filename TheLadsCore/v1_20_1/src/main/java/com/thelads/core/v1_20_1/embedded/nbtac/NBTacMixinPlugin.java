// The Lads: the embedded NBT Autocomplete mixins apply only while the original nbtac jar is not installed.
package com.thelads.core.v1_20_1.embedded.nbtac;

import com.thelads.core.v1_20_1.perf.StandDownMixinPlugin1201;

public final class NBTacMixinPlugin extends StandDownMixinPlugin1201 {
    @Override protected String feature() { return "NBTac"; }
    @Override protected String[] originals() { return new String[] {NBTac.MOD_ID}; }
}
