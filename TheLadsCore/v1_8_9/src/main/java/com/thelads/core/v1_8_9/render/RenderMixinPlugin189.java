package com.thelads.core.v1_8_9.render;

import com.thelads.core.perf.BuiltInRender;
import java.util.List;
import java.util.Set;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** theladscore.v1_8_9.render.mixins.json: the "Animation" mixins are Kerria's, "Probe" ones QA's, the rest Flerovium's. */
public final class RenderMixinPlugin189 implements IMixinConfigPlugin {
    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.contains("Probe")) return true; // QA timing, idle outside the render capture
        return BuiltInRender.enabled(mixinClassName.contains("Animation") ? BuiltInRender.KERRIA : BuiltInRender.FLEROVIUM);
    }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
