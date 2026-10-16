package com.thelads.core.v26_2.embedded.ksyxis;

import com.thelads.core.v26_2.embedded.EmbeddedMixinPlugin;
import com.thelads.core.v26_2.embedded.EmbeddedMods;

/**
 * Stands down next to the Ksyxis jar, and in Lads SMP: that pack left Ksyxis out on purpose, because its 3 -> 0 spawn ticket
 * radius lets a joining player spawn before the terrain around them exists.
 */
public final class KsyxisMixinPlugin extends EmbeddedMixinPlugin {
    @Override protected String originalModId() { return "ksyxis"; }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return super.shouldApplyMixin(targetClassName, mixinClassName) && EmbeddedMods.active("ladssmp");
    }

    /** Whether the embedded Ksyxis runs in this game. */
    public static boolean runs() { return EmbeddedMods.active("ksyxis") && EmbeddedMods.active("ladssmp"); }
}
