package com.thelads.core.v1_21_11.feature.screenshots;

import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class ScreenshotsMixinPlugin12111 implements IMixinConfigPlugin {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String target, String mixin) {
        return FabricLoader.getInstance().isModLoaded("screenshot_viewer");
    }

    @Override
    public void acceptTargets(Set<String> ours, Set<String> others) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String name, ClassNode target, String mixin, IMixinInfo info) {}

    @Override
    public void postApply(String name, ClassNode target, String mixin, IMixinInfo info) {
        LOGGER.info("Lads 1.21.11 screenshot_viewer mixin applied: {} -> {}", mixin, name);
    }
}
