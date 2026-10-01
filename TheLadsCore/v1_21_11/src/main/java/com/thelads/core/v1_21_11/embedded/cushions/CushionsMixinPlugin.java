// Derived from the Optimized Cushions mixin plugin by NikitaCartes (MIT); see META-INF/lads-sources/cushions/LICENSE.
package com.thelads.core.v1_21_11.embedded.cushions;

import com.thelads.core.v1_21_11.embedded.EmbeddedMixinPlugin;
import net.fabricmc.loader.api.FabricLoader;

/** Only while Cushion-Backport (whose entities this optimizes) is loaded. */
public final class CushionsMixinPlugin extends EmbeddedMixinPlugin {
    @Override protected String originalModId() { return "optimizedcushionsbackport"; }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!super.shouldApplyMixin(targetClassName, mixinClassName)) return false;
        return FabricLoader.getInstance().isModLoaded("cushionbackport");
    }
}
