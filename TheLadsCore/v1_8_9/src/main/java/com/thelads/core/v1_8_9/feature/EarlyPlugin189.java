package com.thelads.core.v1_8_9.feature;

import java.util.List;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Plugin of the Core mixin config, for its onLoad: the earliest Core code on 1.8.9 (after every tweaker, before Minecraft's
 * main class). Applies mixins conditionally: leaves the IntegratedServer logout redirect to Essential when Essential is present.
 */
public final class EarlyPlugin189 implements IMixinConfigPlugin {
    private static final Logger LOGGER = LogManager.getLogger("TheLadsCore-1.8.9");

    @Override public void onLoad(String mixinPackage) {
        com.llamalad7.mixinextras.MixinExtrasBootstrap.init();
        AsyncLogging189.install();
        EnumValues189.install();
    }
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // Ionium (Celeritas for Forge 1.8.9) has the same sky-blend cache fix and sign text caches as required redirects: leave it those calls.
        if ((mixinClassName.endsWith(".ForgeSkyBlendCacheMixin") || mixinClassName.endsWith(".TileEntitySignRendererMixin"))
            && EarlyPlugin189.class.getClassLoader().getResource("org/taumc/ionium/launch/IoniumTweaker.class") != null) {
            LOGGER.info("Ionium present; its own version of " + mixinClassName + " applies instead of Lads'");
            return false;
        }
        // OptiFine patches LongHashMap itself (patch/nq.class.xdelta): a hash of ours could disagree with its lookups. Its shaders also
        // set lightmap parameters and lighting on their own paths, so vanilla's re-sending them stays (LightmapParamsMixin189,
        // TesrBatchMixin189, EntityPassLightingMixin189).
        if ((mixinClassName.endsWith(".LongHashMapHashMixin") || mixinClassName.endsWith(".LightmapParamsMixin189")
            || mixinClassName.endsWith(".TesrBatchMixin189") || mixinClassName.endsWith(".EntityPassLightingMixin189"))
            && EarlyPlugin189.class.getClassLoader().getResource("optifine/OptiFineForgeTweaker.class") != null) {
            LOGGER.info("OptiFine present; " + mixinClassName + " not applied");
            return false;
        }
        if (mixinClassName.endsWith(".IntegratedServerLogoutMixin189")) {
            boolean essential = hasEssentialMixins();
            if (essential) {
                LOGGER.info("Essential mixins detected; IntegratedServer logout redirect left to Essential");
                return false;
            }
            LOGGER.info("Essential mixins not detected; applying IntegratedServer logout redirect");
            return true;
        }
        return true;
    }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    /** Essential's jar is on the launch class loader by now (its tweaker runs before any mixin config is prepared). */
    private static boolean hasEssentialMixins() {
        return EarlyPlugin189.class.getClassLoader().getResource("mixins.essential.json") != null;
    }
}
