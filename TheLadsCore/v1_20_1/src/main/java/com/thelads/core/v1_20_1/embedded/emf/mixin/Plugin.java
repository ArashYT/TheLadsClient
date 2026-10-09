package com.thelads.core.v1_20_1.embedded.emf.mixin;


import com.llamalad7.mixinextras.MixinExtrasBootstrap;
import com.thelads.core.v1_20_1.compat.ModPresence1201;
import com.thelads.core.v1_20_1.perf.StandDownMixinPlugin1201;

// The Lads: embedded copy, applies nothing while the original entity_model_features jar is installed (Fabric jar, or the
// Forge jar under Sinytra Connector), and only runs on top of the embedded ETF
public class Plugin extends StandDownMixinPlugin1201 {

    @Override
    protected String feature() {
        return "EMF";
    }

    @Override
    protected String[] originals() {
        return new String[] {"entity_model_features"};
    }

    @Override
    protected boolean extraCondition() {
        return !ModPresence1201.loaded("entity_texture_features");
    }

    @Override
    public void onLoad(final String mixinPackage) {
        super.onLoad(mixinPackage);
        MixinExtrasBootstrap.init(); // Initialize Mixin Extras if it isn't already initialized
    }

    @Override
    public boolean shouldApplyMixin(final String targetClassName, final String mixinClassName) {
        return super.shouldApplyMixin(targetClassName, mixinClassName)
                && !targetClassName.equals("com.thelads.core.v1_20_1.embedded.etf.mixin.CancelTarget");
    }
}
