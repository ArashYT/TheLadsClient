package com.thelads.core.v1_8_9.feature;

import java.util.List;
import java.util.Set;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Plugin of the Core mixin config, for its onLoad: the earliest Core code on 1.8.9 (after every tweaker, before Minecraft's
 * main class). Applies every mixin.
 */
public final class EarlyPlugin189 implements IMixinConfigPlugin {
    @Override public void onLoad(String mixinPackage) {
        AsyncLogging189.install();
        EnumValues189.install();
    }
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) { return true; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
