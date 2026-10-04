package com.thelads.core.v26_2.feature.async;

import com.thelads.core.v26_2.embedded.EmbeddedMixinPlugin;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

/** None of the Async mixins apply on a dedicated server or next to the external Async mod. */
public final class AsyncMixinPlugin extends EmbeddedMixinPlugin {
    @Override protected String originalModId() { return "async"; }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return super.shouldApplyMixin(targetClassName, mixinClassName) && FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
    }
}
