package com.thelads.core.v26_2.feature.screenshots.mixin;
import java.util.*;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.*;
public final class ScreenshotsMixinPlugin implements IMixinConfigPlugin {
    public void onLoad(String mixinPackage) {}
    public String getRefMapperConfig() { return null; }
    public boolean shouldApplyMixin(String target, String mixin) {
        var loader = FabricLoader.getInstance();
        return !loader.isModLoaded("screenshot_viewer") && !loader.isModLoaded("decentscreenshot");
    }
    public void acceptTargets(Set<String> ours, Set<String> others) {}
    public List<String> getMixins() { return null; }
    public void preApply(String name, ClassNode target, String mixin, IMixinInfo info) {}
    public void postApply(String name, ClassNode target, String mixin, IMixinInfo info) {}
}
