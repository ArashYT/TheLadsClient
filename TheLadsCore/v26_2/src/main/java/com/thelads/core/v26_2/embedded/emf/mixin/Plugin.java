package com.thelads.core.v26_2.embedded.emf.mixin;


import com.llamalad7.mixinextras.MixinExtrasBootstrap;
import com.thelads.core.v26_2.embedded.EmbeddedMixinPlugin;
import com.thelads.core.v26_2.embedded.EmbeddedMods;

// The Lads: embedded copy, applies nothing while the original entity_model_features jar is installed,
// and only runs on top of the embedded ETF
public class Plugin extends EmbeddedMixinPlugin {

    //private static final Logger log = LoggerFactory.getLogger(Plugin.class);

    @Override
    protected String originalModId() {
        return "entity_model_features";
    }

    @Override
    public void onLoad(final String mixinPackage) {
        super.onLoad(mixinPackage);
        MixinExtrasBootstrap.init(); // Initialize Mixin Extras if it isn't already initialized
    }

    @Override
    public boolean shouldApplyMixin(final String targetClassName, final String mixinClassName) {
        return super.shouldApplyMixin(targetClassName, mixinClassName) && EmbeddedMods.active("entity_texture_features")
                && !targetClassName.equals("com.thelads.core.v26_2.embedded.etf.mixin.CancelTarget");
    }

//    private boolean hasClass(final String className) {
//        try {
//            MixinService.getService().getBytecodeProvider().getClassNode(className);
//            return true;
//        } catch (ClassNotFoundException | IOException e) {
//            return false;
//        }
//    }
}
