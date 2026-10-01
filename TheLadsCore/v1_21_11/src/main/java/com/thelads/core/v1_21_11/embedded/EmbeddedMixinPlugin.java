package com.thelads.core.v1_21_11.embedded;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Plugin of an embedded mod's mixin config: applies none of its mixins while the original mod is installed. */
public abstract class EmbeddedMixinPlugin implements IMixinConfigPlugin {
    private boolean active;

    /** The upstream mod id this config replaces. */
    protected abstract String originalModId();

    @Override public void onLoad(String mixinPackage) { active = EmbeddedMods.active(originalModId()); }
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) { return active; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
