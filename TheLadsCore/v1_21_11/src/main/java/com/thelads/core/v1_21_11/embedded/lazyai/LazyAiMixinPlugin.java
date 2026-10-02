package com.thelads.core.v1_21_11.embedded.lazyai;

import com.thelads.core.v1_21_11.embedded.EmbeddedMixinPlugin;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

/** Client only: a dedicated server running Core keeps vanilla AI; the integrated server is the target. */
public final class LazyAiMixinPlugin extends EmbeddedMixinPlugin {
    @Override protected String originalModId() { return "lazy_ai_pixelindiedev"; }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return super.shouldApplyMixin(targetClassName, mixinClassName) && FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
    }
}
