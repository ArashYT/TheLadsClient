package com.thelads.core.v1_20_1.gui;

import com.thelads.core.config.ModuleSupport;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.slf4j.LoggerFactory;

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
        boolean loaded = FabricLoader.getInstance().isModLoaded(id)
            || FabricLoader.getInstance().isModLoaded(id.replace("_", ""))
            || FabricLoader.getInstance().isModLoaded(id.replace("-", ""));
        ModuleSupport.registerExternal(name, display, id, loaded);
    }

    public static void open(String modId, Screen parent) {
        Minecraft mc = Minecraft.getInstance();
        if (FabricLoader.getInstance().isModLoaded("modmenu")) {
            try {
                Class<?> api = Class.forName("com.terraformersmc.modmenu.ModMenu");
                Screen target = (Screen) api.getMethod("getConfigScreen", String.class, Screen.class).invoke(null, modId, parent);
                if (target != null) {
                    mc.setScreen(target);
                    return;
                }
            } catch (Exception ignored) {}
        }
        LoggerFactory.getLogger("TheLadsCore-1.20.1").info("Open settings requested for mod: {}", modId);
    }
}
