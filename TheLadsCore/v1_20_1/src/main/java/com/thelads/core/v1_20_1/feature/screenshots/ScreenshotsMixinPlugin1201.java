package com.thelads.core.v1_20_1.feature.screenshots;

import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Switches the gallery mixins off while the real Screenshot Viewer (or Decent Screenshot) is installed. The shared screenshots
 * folder redirect is not part of the gallery and always stays on. Kept outside the mixin package and free of Minecraft
 * classes: it runs before any game class is loaded.
 */
public final class ScreenshotsMixinPlugin1201 implements IMixinConfigPlugin {
    private static final String ALWAYS = "GlobalScreenshotMixin";

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() { return null; }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return mixinClassName.endsWith(ALWAYS) || !(loaded("screenshot_viewer") || loaded("decentscreenshot"));
    }

    /** Fabric mods answer to FabricLoader; a Forge mod inside Zero SMP is asked through Forge's ModList when that exists yet. */
    private static boolean loaded(String id) {
        if (FabricLoader.getInstance().isModLoaded(id)) return true;
        try {
            Object list = Class.forName("net.minecraftforge.fml.ModList").getMethod("get").invoke(null);
            return list != null && Boolean.TRUE.equals(list.getClass().getMethod("isLoaded", String.class).invoke(list, id));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException notForge) {
            return false;
        }
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() { return null; }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
