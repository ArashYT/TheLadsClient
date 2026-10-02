package com.thelads.core.v1_21_1.embedded.etf.mixin;


import com.llamalad7.mixinextras.MixinExtrasBootstrap;
import com.thelads.core.v1_21_1.embedded.EmbeddedMixinPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.service.MixinService;

import java.io.IOException;

// The Lads: embedded copy, applies nothing while the original entity_texture_features jar is installed
public class Plugin extends EmbeddedMixinPlugin {

    private static final Logger log = LoggerFactory.getLogger(Plugin.class);

    @Override
    protected String originalModId() {
        return "entity_texture_features";
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

        return !targetClassName.equals("com.thelads.core.v1_21_1.embedded.etf.mixin.CancelTarget");
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
