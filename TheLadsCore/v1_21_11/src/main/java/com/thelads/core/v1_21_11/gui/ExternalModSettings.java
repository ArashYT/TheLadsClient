package com.thelads.core.v1_21_11.gui;

import com.thelads.core.config.ModuleSupport;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.slf4j.LoggerFactory;

/** Optional public Mod Menu API, resolved only on a user action. */
public final class ExternalModSettings {
    private ExternalModSettings() {}
    public static void register() {
        com.thelads.core.config.BuiltInIntegrations.setAfterApply(ExternalModSettings::afterApply);
        external("DynamicFPS", "Dynamic FPS", "dynamic_fps");
        external("DynamicLights", "LambDynamicLights", "lambdynlights");
        external("SkinLayers", "3D Skin Layers", "skinlayers3d");
        external("NotEnoughAnimations", "Not Enough Animations", "notenoughanimations");
        external("BetterF3", "BetterF3", "betterf3");
        external("BetterStats", "Better Statistics Screen", "betterstats");
        // Modern Advancements has no 1.21.x build in the pack (game-mods/1.21.11), so it stays unregistered here.
        external("Resourcify", "Resourcify", "resourcify");
        external("JEI (Just Enough Items)", "Just Enough Items", "jei");
        external("XaeroWorldmap", "Xaero's World Map", "xaeroworldmap");
        external("Clumps", "Clumps", "clumps");
        external("Raised", "Raised", "raised");
        external("ScalableLux", "ScalableLux", "scalablelux");
        if (FabricLoader.getInstance().isModLoaded("exordium")) {
            external("Exordium", "Exordium", "exordium");
        } else {
            ModuleSupport.registerUnavailable("Exordium",
                "Excluded from the tested pack: upstream warns of rendering conflicts. "
                + "Dynamic FPS provides supported background frame limiting.");
        }
        external("AppleSkin", "AppleSkin", "appleskin");
        external("Performance", "Sodium", "sodium");
        external("Lithium", "Lithium", "lithium");
        external("FerriteCore", "FerriteCore", "ferritecore");
        external("XaeroMinimap", "Xaero Minimap", "xaerominimap");
        external("Crosshair Tweaks", "Custom Crosshair Mod", "custom-crosshair-mod");
        external("Paperdoll", "Paper Doll", "paperdoll");
        external("EnhancedToolbars", "Durability Tooltip", "durabilitytooltip");
        external("AutoReconnect", "AutoReconnect", "autoreconnectrf");
        external("BetterScreenshots", "Screenshot Viewer", "screenshot_viewer");
        external("TabList", "Tab Tweaks", "tabtweaks");
        external("PingView", "Tab Tweaks", "tabtweaks");
    }
    private static void external(String name, String display, String id) {
        ModuleSupport.registerExternal(name, display, id, FabricLoader.getInstance().isModLoaded(id));
    }

    private static void afterApply(String id) {
        if (!"skinlayers3d".equals(id)) return;
        try {
            Object mod = Class.forName("dev.tr7zw.skinlayers.SkinLayersModBase").getField("instance").get(null);
            mod.getClass().getMethod("refreshLayers", net.minecraft.world.entity.player.Player.class)
                .invoke(mod, Minecraft.getInstance().player);
            Class.forName("dev.tr7zw.skinlayers.SkullRendererCache").getMethod("clearCache").invoke(null);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Could not refresh skin meshes", error);
        }
    }

    private static final String CROSSHAIR_MOD = "custom-crosshair-mod";
    private static final String CROSSHAIR_TYPE = "com.wjbaker.ccm.crosshair.CustomCrosshair";
    private static final String CROSSHAIR_EDITOR = "com.wjbaker.ccm.gui.screen.screens.editCrosshair.EditCrosshairGuiScreen";
    private static final java.util.Map<String, String> ACTION_LABELS = new java.util.HashMap<>();

    public static String actionLabel(String modId) {
        if (modId == null || modId.isBlank()) return "View installed mods";
        return ACTION_LABELS.computeIfAbsent(modId, ExternalModSettings::resolveActionLabel);
    }

    private static String resolveActionLabel(String modId) {
        try {
            if (CROSSHAIR_MOD.equals(modId) && FabricLoader.getInstance().isModLoaded(modId)) {
                Class<?> editor = Class.forName(CROSSHAIR_EDITOR);
                editor.getConstructor(Class.forName(CROSSHAIR_TYPE));
                if (Screen.class.isAssignableFrom(editor)) return "Open mod settings";
            }
            if (FabricLoader.getInstance().isModLoaded("modmenu")
                && Boolean.TRUE.equals(Class.forName("com.terraformersmc.modmenu.ModMenu")
                    .getMethod("hasConfigScreen", String.class).invoke(null, modId))) {
                return "Open mod settings";
            }
        } catch (ReflectiveOperationException | LinkageError error) {
            LoggerFactory.getLogger("TheLadsCore").debug("No settings API for {}", modId, error);
        }
        return "View in Mod Menu";
    }

    public static void open(String modId, Screen parent) {
        Minecraft mc = Minecraft.getInstance();
        Screen target = null;
        if (CROSSHAIR_MOD.equals(modId) && FabricLoader.getInstance().isModLoaded(modId)) {
            try {
                // Exactly the same public constructor and live configuration used by the mod's own keybinding.
                Class<?> modClass = Class.forName("com.wjbaker.ccm.CustomCrosshairMod");
                Object instance = modClass.getField("INSTANCE").get(null);
                if (instance == null) throw new ReflectiveOperationException("Crosshair mod is not initialized");
                Object properties = modClass.getMethod("properties").invoke(instance);
                Object crosshair = properties.getClass().getMethod("getCrosshair").invoke(properties);
                target = (Screen) Class.forName(CROSSHAIR_EDITOR)
                    .getConstructor(Class.forName(CROSSHAIR_TYPE)).newInstance(crosshair);
            } catch (ReflectiveOperationException | LinkageError error) {
                LoggerFactory.getLogger("TheLadsCore").warn("Cannot open the Crosshair editor", error);
            }
        }
        if (target != null) {
            show(target);
            return;
        }
        if (!FabricLoader.getInstance().isModLoaded("modmenu")) {
            mc.getNarrator().saySystemNow("Install Mod Menu to view external mod details.");
            LoggerFactory.getLogger("TheLadsCore").warn("Cannot open external mod details: Mod Menu is not installed");
            return;
        }
        if (modId != null && !modId.isBlank()) {
            try {
                Class<?> api = Class.forName("com.terraformersmc.modmenu.ModMenu");
                target = (Screen) api.getMethod("getConfigScreen", String.class, Screen.class).invoke(null, modId, parent);
            } catch (ReflectiveOperationException | LinkageError error) {
                LoggerFactory.getLogger("TheLadsCore").warn("Mod config unavailable for {}; opening its Mod Menu entry", modId, error);
            }
        }
        if (target != null) {
            show(target);
            return;
        }
        try {
            target = (Screen) Class.forName("com.terraformersmc.modmenu.gui.ModsScreen")
                .getConstructor(Screen.class).newInstance(parent);
            show(target); // Screen initialization must finish before selecting its list entry.
            if (modId != null && !modId.isBlank()) {
                ACTION_LABELS.put(modId, "View in Mod Menu");
                selectModEntry(target, modId);
            }
        } catch (ReflectiveOperationException | LinkageError error) {
            // Never leave a requested-mod navigation showing an unrelated default selection.
            show(parent);
            LoggerFactory.getLogger("TheLadsCore").error("Cannot open Mod Menu entry for {}", modId, error);
            mc.getNarrator().saySystemNow("The requested mod entry could not be opened. See the game log.");
        }
    }

    private static void selectModEntry(Screen screen, String modId) throws ReflectiveOperationException {
        Class<?> listType = Class.forName("com.terraformersmc.modmenu.gui.widget.ModListWidget");
        Class<?> entryType = Class.forName("com.terraformersmc.modmenu.gui.widget.entries.ModListEntry");
        Class<?> modType = Class.forName("com.terraformersmc.modmenu.util.mod.Mod");
        Object list = null;
        for (var child : screen.children()) {
            if (listType.isInstance(child)) { list = child; break; }
        }
        if (!(list instanceof net.minecraft.client.gui.components.events.ContainerEventHandler container)) {
            throw new ReflectiveOperationException("Mod Menu list is not initialized");
        }
        Object selected = null;
        for (var entry : container.children()) {
            if (!entryType.isInstance(entry)) continue;
            Object mod = entryType.getMethod("getMod").invoke(entry);
            if (modId.equals(modType.getMethod("getId").invoke(mod))) { selected = entry; break; }
        }
        if (selected == null) {
            // Explicit navigation may target a library hidden by the user's current filters.
            // Add the real loaded mod's entry without changing those global filter preferences.
            Object metadata = ((java.util.Map<?, ?>) Class.forName("com.terraformersmc.modmenu.ModMenu")
                .getField("MODS").get(null)).get(modId);
            if (metadata == null) throw new ReflectiveOperationException("Unknown Mod Menu id: " + modId);
            selected = entryType.getConstructor(modType, listType).newInstance(metadata, list);
            listType.getMethod("addEntry", entryType).invoke(list, selected);
        }
        listType.getMethod("select", entryType).invoke(list, selected);
        listType.getMethod("ensureVisible", entryType).invoke(list, selected);
        Object actualEntry = screen.getClass().getMethod("getSelectedEntry").invoke(screen);
        Object actualMod = actualEntry == null ? null : entryType.getMethod("getMod").invoke(actualEntry);
        if (actualMod == null || !modId.equals(modType.getMethod("getId").invoke(actualMod))) {
            throw new ReflectiveOperationException("Mod Menu did not select " + modId);
        }
    }

    private static void show(Screen screen) {
        Minecraft.getInstance().setScreen(screen);
    }
}
