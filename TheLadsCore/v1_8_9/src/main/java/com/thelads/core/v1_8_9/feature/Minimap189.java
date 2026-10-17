package com.thelads.core.v1_8_9.feature;

import com.thelads.core.client.HypixelSafetyManager;
import com.thelads.core.config.ActionOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.ModuleSupport;
import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.common.Loader;
import org.apache.logging.log4j.LogManager;

/**
 * Minimap (1.9.11), as 26.x MinimapIntegration: Lads controls for Xaero's Minimap, which the launcher's 1.8.9 pack installs (Xaero
 * keeps terrain, waypoints, its HUD position editor and server rules). The module's switch is Xaero's own "minimap" setting (a
 * change made in Xaero's settings or with its key comes back to the switch), off on Hypixel as every Lads minimap; Map and waypoint
 * settings opens Xaero's settings screen. Without the jar the module says the mod is required.
 */
public final class Minimap189 {
    public static final String NAME = "Minimap", MOD_ID = "XaeroMinimap";
    private static Object settings;
    private static Field shown;
    private static Boolean written;
    private static boolean attempted;

    private Minimap189() {}

    /** After registerStatuses. Xaero makes its settings in its own init, after ours: the switch connects on the first client tick. */
    public static void register() {
        boolean loaded = Loader.isModLoaded(MOD_ID);
        ModuleSupport.registerExternal("XaeroMinimap", "Xaero Minimap", MOD_ID, loaded);
        ModuleSupport.registerExternal(NAME, "Xaero's Minimap", MOD_ID, loaded);
        attempted = !loaded;
    }

    private static void connect() {
        attempted = true;
        try {
            final Object mod = Class.forName("xaero.minimap.XaeroMinimap").getField("instance").get(null);
            settings = mod.getClass().getMethod("getSettings").invoke(mod);
            if (settings == null) throw new IllegalStateException("Xaero's Minimap has no settings yet");
            shown = settings.getClass().getDeclaredField("minimap");
            shown.setAccessible(true);
            // Xaero's first settings page, as its own key opens it (GuiMinimapSettings is abstract).
            final java.lang.reflect.Constructor<?> screen = Class.forName("xaero.minimap.gui.GuiMinimap").getConstructor(GuiScreen.class);
            ((ActionOption) module().getOption("Map and waypoint settings")).setAction(() -> {
                Minecraft mc = Minecraft.getMinecraft();
                try {
                    mc.displayGuiScreen((GuiScreen) screen.newInstance(mc.currentScreen));
                } catch (ReflectiveOperationException | RuntimeException e) {
                    LogManager.getLogger("TheLadsCore").warn("Cannot open Xaero's Minimap settings", e);
                }
            });
            ModuleSupport.registerBuiltIn(NAME);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            settings = null;
            LogManager.getLogger("TheLadsCore").warn("Xaero's Minimap integration unavailable; it runs with its own settings", e);
        }
    }

    static Module module() { return ModuleManager.getInstance().getModule(NAME); }

    /** Whether the Lads switch drives Xaero's map (QA). */
    static boolean integrated() { return settings != null; }

    /** Xaero's own minimap setting (QA), null without the integration. */
    static Boolean xaeroShown() {
        try {
            return settings == null ? null : shown.getBoolean(settings);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    public static void tick() {
        if (!attempted) connect();
        if (settings == null) return;
        try {
            Module module = module();
            boolean hypixel = HypixelSafetyManager.isHypixel(), xaero = shown.getBoolean(settings);
            // Changed in Xaero's settings or with its key since the last write: the switch follows.
            if (written != null && xaero != written && !hypixel) module.setEnabled(xaero);
            boolean want = module.isEnabled() && !hypixel;
            if (written == null || want != xaero) shown.setBoolean(settings, want);
            written = want;
        } catch (IllegalAccessException | RuntimeException e) {
            settings = null;
            LogManager.getLogger("TheLadsCore").warn("Xaero's Minimap integration failed", e);
        }
    }
}
