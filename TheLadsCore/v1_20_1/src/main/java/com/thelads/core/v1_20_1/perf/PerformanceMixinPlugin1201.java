package com.thelads.core.v1_20_1.perf;

import com.thelads.core.v1_20_1.compat.ModPresence1201;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Mixin configuration plugin for TheLadsCore 1.20.1 native performance engines.
 * Dynamically evaluates mod presence across Fabric and Forge/Connector classloaders
 * and cancels individual mixins when external counterpart mods are detected.
 *
 * <p>Free of Minecraft types so it safely executes before game class transformation.
 */
public final class PerformanceMixinPlugin1201 implements IMixinConfigPlugin {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore-PerfPlugin");

    @Override
    public void onLoad(String mixinPackage) {
        LOGGER.info("TheLadsCore 1.20.1 PerformanceMixinPlugin initialized for {}", mixinPackage);
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // Entity Animation LOD stand-down:
        if (mixinClassName.contains("LivingEntityRendererAnimLodMixin")) {
            if (ModPresence1201.loaded("entity_model_features", "lodmobanimations", "entityculling", "sodium", "embeddium")) {
                LOGGER.info("Lads EntityAnimLod: standing down, external animation/culling mod detected");
                return false;
            }
            if ("false".equalsIgnoreCase(System.getProperty("thelads.entityanimlod"))) {
                return false;
            }
        }

        // Weather optimization stand-down:
        if (mixinClassName.contains("LevelRendererWeatherMixin")) {
            if (ModPresence1201.loaded("particlerain", "particular")) {
                LOGGER.info("Lads WeatherOpt: standing down, external weather mod detected");
                return false;
            }
            if ("false".equalsIgnoreCase(System.getProperty("thelads.weatheropt"))) {
                return false;
            }
        }

        // Model deduplication stand-down:
        if (mixinClassName.contains("ModelBakeryDeduplicationMixin")) {
            if (ModPresence1201.loaded("ferritecore", "modernfix")) {
                LOGGER.info("Lads ModelDedup: standing down, FerriteCore or ModernFix detected");
                return false;
            }
            if ("false".equalsIgnoreCase(System.getProperty("thelads.modeldedup"))) {
                return false;
            }
        }

        // Font text rendering stand-down:
        if (mixinClassName.contains("MixinFont_StringRenderOutput")) {
            if (ModPresence1201.loaded("immediatelyfast")) {
                LOGGER.info("Lads FontGlyphCache: standing down, external ImmediatelyFast detected");
                return false;
            }
            if ("false".equalsIgnoreCase(System.getProperty("thelads.immediatelyfast"))) {
                return false;
            }
        }

        return true;
    }

    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
