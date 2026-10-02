// Derived from the Optimized Cushions mixin plugin by NikitaCartes (MIT); see META-INF/lads-sources/cushions/LICENSE.
package com.thelads.core.v26_2.embedded.cushions;

import com.thelads.core.v26_2.embedded.EmbeddedMixinPlugin;
import net.fabricmc.loader.api.FabricLoader;

/** Client mixins stand down while OBE is loaded: it bakes cushions itself (upstream rule); the server half always applies. */
public final class CushionsMixinPlugin extends EmbeddedMixinPlugin {
    @Override protected String originalModId() { return "optimizedcushions"; }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!super.shouldApplyMixin(targetClassName, mixinClassName)) return false;
        return mixinClassName.contains(".mixin.server.") || !FabricLoader.getInstance().isModLoaded("obe");
    }
}
