package com.thelads.core.v1_20_1.hud;

import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Mixin config plugin of the HUD lane's configs. Forge's ForgeGui replaces Gui.render and Sinytra Connector rewrites Fabric
 * mixins that reach into it, so mixins that only make sense inside vanilla's Gui.render / renderPlayerHealth are left out under
 * Forge; their Forge counterparts (ForgeOverlays1201, FoodForgeGuiMixin) do the same job there.
 */
public final class HudMixinPlugin implements IMixinConfigPlugin {
    /** Mixins that reach into Gui.render's or renderPlayerHealth's body: plain Fabric only. */
    private static final Set<String> FABRIC_ONLY = Set.of(
        "com.thelads.core.v1_20_1.hud.mixin.GuiPartsMixin",
        "com.thelads.core.v1_20_1.hud.items.mixin.FoodRowMixin");
    private static Boolean forge;

    /** True when this game runs on Forge (Zero SMP, through Connector). */
    static boolean forge() {
        if (forge == null) {
            boolean found = FabricLoader.getInstance().isModLoaded("connector");
            try {
                found |= HudMixinPlugin.class.getClassLoader().getResource("net/minecraftforge/client/event/RenderGuiOverlayEvent.class") != null;
            } catch (RuntimeException ignored) {}
            forge = found;
        }
        return forge;
    }

    @Override public void onLoad(String mixinPackage) {}

    @Override public String getRefMapperConfig() { return null; }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return !(forge() && FABRIC_ONLY.contains(mixinClassName));
    }

    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override public List<String> getMixins() { return null; }

    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
