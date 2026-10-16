package com.thelads.core.v26_2.feature.async;

import com.thelads.core.config.ConfigManager;
import com.thelads.core.modules.AsyncModule;
import com.thelads.core.v26_2.embedded.EmbeddedMixinPlugin;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

/**
 * None of the Async mixins apply on a dedicated server, next to the external Async mod, next to C2ME (it rewrites the chunk,
 * POI and entity code these wrap), or while the Async module was saved off: then the hottest world paths (entity queries,
 * getChunk, getBlockEntity, setBlock, POI) carry no Async wrapper at all. Decided once at launch, so switching Async on
 * applies after a restart (AsyncTicking registers it that way).
 */
public final class AsyncMixinPlugin extends EmbeddedMixinPlugin {
    private boolean apply;

    @Override protected String originalModId() { return "async"; }

    /** False where the Lads Async remake must not run at all (its module is hidden there). */
    public static boolean supported() {
        FabricLoader loader = FabricLoader.getInstance();
        return loader.getEnvironmentType() == EnvType.CLIENT && !loader.isModLoaded("async") && !loader.isModLoaded("c2me");
    }

    @Override
    public void onLoad(String mixinPackage) {
        super.onLoad(mixinPackage);
        apply = supported() && ConfigManager.savedEnabled(AsyncModule.NAME, true);
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return super.shouldApplyMixin(targetClassName, mixinClassName) && apply;
    }
}
