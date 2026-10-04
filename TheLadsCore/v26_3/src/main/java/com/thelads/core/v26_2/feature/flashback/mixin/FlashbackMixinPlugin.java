package com.thelads.core.v26_2.feature.flashback.mixin;

import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Only with Flashback loaded; each part stands down while the separate mod it remakes is installed. */
public final class FlashbackMixinPlugin implements IMixinConfigPlugin {
    @Override public boolean shouldApplyMixin(String target, String mixin) {
        var loader = FabricLoader.getInstance();
        if (!loader.isModLoaded("flashback")) return false;
        return mixin.endsWith("ReplayFolderMixin") ? !loader.isModLoaded("flashbacksettings") : !loader.isModLoaded("flashbackturbo");
    }
    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode targetClass, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode targetClass, String mixin, IMixinInfo info) {}
}
