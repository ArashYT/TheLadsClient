package com.thelads.core.v1_8_9.mixin.ionium;

import java.util.List;
import java.util.Set;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * theladscore.v1_8_9.ionium.mixins.json: Lads' changes to Ionium (Celeritas for Forge 1.8.9, LGPL-3.0), only when it is installed.
 * Fast fancy clouds (1.9.13): Ionium's display-list clouds give way to LadsClouds189. Kill switch: -Dlads.fastClouds=false.
 * Fast Render (1.9.13, IoniumFastRender189): its mixins target Minecraft's classes, so they apply only when Ionium is installed.
 */
public final class IoniumMixinPlugin189 implements IMixinConfigPlugin {
    private static final boolean CLOUDS = !"false".equalsIgnoreCase(System.getProperty("lads.fastClouds"));

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith(".IoniumCloudsMixin189")) return CLOUDS;
        if (mixinClassName.contains(".IoniumFastRender")) return com.thelads.core.v1_8_9.render.IoniumFastRender189.ENABLED;
        return true;
    }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
