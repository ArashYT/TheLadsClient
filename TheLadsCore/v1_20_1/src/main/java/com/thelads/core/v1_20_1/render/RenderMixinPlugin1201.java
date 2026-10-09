package com.thelads.core.v1_20_1.render;

import com.thelads.core.perf.BuiltInRender;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** theladscore.v1_20_1.render.mixins.json: the "Animation" mixins are Kerria's, the rest Flerovium's; a stood-down remake applies none. */
public final class RenderMixinPlugin1201 implements IMixinConfigPlugin {
    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return BuiltInRender.enabled(mixinClassName.contains("Animation") ? BuiltInRender.KERRIA : BuiltInRender.FLEROVIUM);
    }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
