package com.thelads.core.v1_20_1.game.survival;

import com.thelads.core.v1_20_1.compat.ModPresence1201;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Plugin of a mixin config that only fits plain Fabric. On Forge (Zero SMP, through Sinytra Connector) the HUD is ForgeGui, which
 * never runs the vanilla Gui health code, and Connector re-targets Fabric injections into it at ForgeGui methods, which fails on
 * Gui. Keep this class free of Minecraft types: it runs before any game class is transformed.
 */
public final class FabricOnlyMixinPlugin1201 implements IMixinConfigPlugin {
    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) { return !ModPresence1201.onForge(); }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
