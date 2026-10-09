package com.thelads.core.v26_2.feature.fastquit;

import com.thelads.core.client.FastQuit;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** FastQuit stands down entirely while the FastQuit mod is installed. */
public final class FastQuitMixinPlugin implements IMixinConfigPlugin {
    private boolean standDown;

    @Override public void onLoad(String mixinPackage) { standDown = FastQuit.otherFastQuitLoaded(); }
    @Override public boolean shouldApplyMixin(String target, String mixin) { return !standDown; }
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode targetClass, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode targetClass, String mixin, IMixinInfo info) {}
}
