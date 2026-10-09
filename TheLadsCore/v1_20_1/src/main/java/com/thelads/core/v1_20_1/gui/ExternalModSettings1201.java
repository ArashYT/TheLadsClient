package com.thelads.core.v1_20_1.gui;

import com.thelads.core.config.ModuleSupport;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.slf4j.LoggerFactory;

/**
 * Third-party mod registrations and their settings screens: Mod Menu's config screen on Fabric, the mod's own Forge config
 * screen (or Forge's mod list) inside Zero SMP.
 */
public final class ExternalModSettings1201 {
    private ExternalModSettings1201() {}

    public static void register() {
        external("DynamicFPS", "Dynamic FPS", "dynamic_fps");
        external("SkinLayers", "3D Skin Layers", "skinlayers3d");
        external("NotEnoughAnimations", "Not Enough Animations", "notenoughanimations");
        external("BetterF3", "BetterF3", "betterf3");
        external("BetterStats", "Better Statistics Screen", "betterstats");
        external("JEI (Just Enough Items)", "Just Enough Items", "jei");
        external("XaeroWorldmap", "Xaero's World Map", "xaeroworldmap");
        external("XaeroMinimap", "Xaero Minimap", "xaerominimap");
        external("Clumps", "Clumps", "clumps");
        external("AppleSkin", "AppleSkin", "appleskin");
        external("FerriteCore", "FerriteCore", "ferritecore");
        external("EntityCulling", "Entity Culling", "entityculling");
        external("Jade", "Jade", "jade");
        external("MouseTweaks", "Mouse Tweaks", "mousetweaks");
    }

    private static void external(String name, String display, String id) {
        boolean loaded = ModPresence1201.isLoaded(id)
            || ModPresence1201.isLoaded(id.replace("_", ""))
            || ModPresence1201.isLoaded(id.replace("-", ""));
        ModuleSupport.registerExternal(name, display, id, loaded);
    }

    public static void open(String modId, Screen parent) {
        Minecraft mc = Minecraft.getInstance();
        Screen target = null;
        if (modId != null && !modId.isBlank()) {
            if (FabricLoader.getInstance().isModLoaded("modmenu")) {
                try {
                    Class<?> api = Class.forName("com.terraformersmc.modmenu.ModMenu");
                    target = (Screen) api.getMethod("getConfigScreen", String.class, Screen.class).invoke(null, modId, parent);
                } catch (ReflectiveOperationException | LinkageError ignored) {}
            }
            if (target == null) target = ModPresence1201.forgeConfigScreen(modId, parent);
            // A mod with no config screen of its own (JEI, Lithium, FerriteCore ...): Mod Menu's list, so the button never does nothing.
            if (target == null && FabricLoader.getInstance().isModLoaded("modmenu")) {
                try {
                    target = (Screen) Class.forName("com.terraformersmc.modmenu.gui.ModsScreen").getConstructor(Screen.class).newInstance(parent);
                } catch (ReflectiveOperationException | LinkageError ignored) {}
            }
        }
        if (target == null) target = ModPresence1201.forgeModList(parent);
        if (target != null) {
            mc.setScreen(target);
            return;
        }
        mc.getNarrator().sayNow("Install Mod Menu to view external mod details.");
        LoggerFactory.getLogger("TheLadsCore-1.20.1").info("Open settings requested for mod: {} (no settings screen found)", modId);
    }
}
