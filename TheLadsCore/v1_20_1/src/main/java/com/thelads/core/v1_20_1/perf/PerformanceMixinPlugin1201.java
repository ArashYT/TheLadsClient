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
 * Plugin of the 1.20.1 perf mixin config: the render optimisations apply unless their kill switch ({@code -Dlads.NAME=false}) is
 * set or a mod that replaces the same rendering is installed (plain Fabric or Forge under Connector, see {@link ModPresence1201}).
 * Every other mixin of the config always applies. Free of Minecraft types: it runs before any game class is transformed.
 */
public final class PerformanceMixinPlugin1201 implements IMixinConfigPlugin {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore-Perf");

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith(".LevelRendererWeatherMixin")) return decide("weathercull", "particlerain", "particular");
        if (mixinClassName.endsWith(".MixinFont_StringRenderOutput")) return decide("fontcache", "immediatelyfast");
        return true;
    }

    private static boolean decide(String name, String... replacedBy) {
        if ("false".equalsIgnoreCase(System.getProperty("lads." + name))) {
            LOGGER.info("Lads {}: off (-Dlads.{}=false)", name, name);
            return false;
        }
        for (String id : replacedBy) {
            if (ModPresence1201.loaded(id)) {
                LOGGER.info("Lads {}: standing down, '{}' is installed ({})", name, id, ModPresence1201.where(id));
                return false;
            }
        }
        LOGGER.info("Lads {}: active", name);
        return true;
    }

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
