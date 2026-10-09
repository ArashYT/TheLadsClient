package com.thelads.core.v1_20_1.embedded.etf.mixin;


import com.llamalad7.mixinextras.MixinExtrasBootstrap;
import com.thelads.core.v1_20_1.perf.StandDownMixinPlugin1201;
import org.spongepowered.asm.service.MixinService;

import java.io.IOException;

// The Lads: embedded copy, applies nothing while the original entity_texture_features jar is installed (Fabric jar, or the
// Forge jar under Sinytra Connector)
public class Plugin extends StandDownMixinPlugin1201 {

    @Override
    protected String feature() {
        return "ETF";
    }

    @Override
    protected String[] originals() {
        return new String[] {"entity_texture_features"};
    }

    @Override
    public void onLoad(final String mixinPackage) {
        super.onLoad(mixinPackage);
        MixinExtrasBootstrap.init(); // Initialize Mixin Extras if it isn't already initialized
    }

    @Override
    public boolean shouldApplyMixin(final String targetClassName, final String mixinClassName) {
        if (!super.shouldApplyMixin(targetClassName, mixinClassName)) return false;
        if (mixinClassName.endsWith("MixinModelPartSodium")) {
            return hasClass("me.jellysquid.mods.sodium.client.render.immediate.model.EntityRenderer");
        }

        return !targetClassName.equals("com.thelads.core.v1_20_1.embedded.etf.mixin.CancelTarget");
    }

    private boolean hasClass(final String className) {
        try {
            MixinService.getService().getBytecodeProvider().getClassNode(className);
            return true;
        } catch (ClassNotFoundException | IOException e) {
            return false;
        }
    }
}
