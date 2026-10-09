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
    private String mixinPackage = "";
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

    @Override public void onLoad(String mixinPackage) { this.mixinPackage = mixinPackage; }

    @Override public String getRefMapperConfig() { return null; }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) { return true; }

    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    /**
     * The mixins that reach into Gui.render's or renderPlayerHealth's body are not listed in the JSON (Connector's adapter patches
     * what the JSON lists): they are registered here, on plain Fabric only.
     */
    @Override
    public List<String> getMixins() {
        if (forge()) return null;
        if (mixinPackage.equals("com.thelads.core.v1_20_1.hud.mixin")) return List.of("GuiPartsMixin");
        if (mixinPackage.equals("com.thelads.core.v1_20_1.hud.items.mixin")) return List.of("FoodRowMixin");
        return null;
    }

    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
