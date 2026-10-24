package com.thelads.core.v1_8_9.cit;

import java.util.List;
import java.util.Set;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * theladscore.v1_8_9.cit.mixins.json: Custom Item Textures' hooks (Cit189), only without OptiFine, which draws CIT packs itself.
 * Kill switch: -Dlads.cit=false. No Minecraft class may load here (this runs before Minecraft's classes are transformed).
 */
public final class CitMixinPlugin189 implements IMixinConfigPlugin {
    /** Custom Item Textures runs in this game: OptiFine is not installed and the kill switch is not set. */
    public static final boolean ON = !"false".equalsIgnoreCase(System.getProperty("lads.cit"))
        && CitMixinPlugin189.class.getClassLoader().getResource("optifine/OptiFineForgeTweaker.class") == null;

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) { return ON; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
