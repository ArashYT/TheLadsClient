package com.thelads.core.v1_21_1.feature;

import com.thelads.core.config.ActionOption;
import com.thelads.core.config.HudSettings;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.ModuleSupport;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.slf4j.LoggerFactory;

/**
 * Public API adapter (26.x MinimapIntegration). Xaero retains terrain, waypoints, server protocol and fair-play ownership.
 * On 1.21.1 Xaero draws its map at the start of the hotbar layer, so a hidden HUD skips it with that layer (NativeHudMixin).
 */
public final class MinimapIntegration {
    private static Object map, manager, transform;
    private static Class<?> transformType;
    private static Boolean lastEnabled;
    private static int lastX = Integer.MIN_VALUE, lastY = Integer.MIN_VALUE;
    private static boolean attempted;
    private MinimapIntegration() {}
    private static Object call(Object object, String method) throws ReflectiveOperationException { return object.getClass().getMethod(method).invoke(object); }
    public static boolean available() { return map != null; }
    public static void tick() {
        var mc = Minecraft.getInstance();
        if (!attempted) {
            attempted = true;
            if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("xaerominimap")) return;
            try {
                Object mod = Class.forName("xaero.common.HudMod").getField("INSTANCE").get(null);
                map = Class.forName("xaero.hud.minimap.BuiltInHudModules").getField("MINIMAP").get(null);
                manager = call(call(mod, "getHudConfigs"), "getClientConfigManager");
                transformType = Class.forName("xaero.hud.module.ModuleTransform");
                transform = call(map, "getConfirmedTransform");
                ((ActionOption) ModuleManager.getInstance().getModule("Minimap").getOption("Map and waypoint settings")).setAction(() -> {
                    try { @SuppressWarnings("unchecked") var factory = (Function<Screen, Screen>) call(map, "getConfigScreenFactory"); mc.setScreen(factory.apply(mc.screen)); }
                    catch (ReflectiveOperationException failure) { LoggerFactory.getLogger("TheLadsCore").warn("Cannot open Xaero settings", failure); }
                });
                ModuleSupport.registerBuiltIn("Minimap");
            } catch (ReflectiveOperationException | RuntimeException failure) { map = null; LoggerFactory.getLogger("TheLadsCore").warn("Xaero integration unavailable", failure); }
        }
        if (map == null) return;
        try {
            boolean enabled = ModuleManager.getInstance().getModule("Minimap").isEnabled();
            if (lastEnabled == null || lastEnabled != enabled) {
                map.getClass().getMethod("setActive", Class.forName("xaero.lib.client.config.ClientConfigManager"), boolean.class).invoke(map, manager, enabled); lastEnabled = enabled;
            }
            if (mc.screen instanceof com.thelads.core.v1_21_1.gui.DraggableHudScreen121) return;
            int[] saved = HudSettings.getInstance().getPosition("Minimap");
            if (saved != null) position(saved[0], saved[1]);
            else { int[] size = size(); position(Math.max(0, mc.getWindow().getGuiScaledWidth() - size[0] - 5), 44); }
        } catch (ReflectiveOperationException failure) { LoggerFactory.getLogger("TheLadsCore").warn("Xaero integration failed", failure); map = null; }
    }
    public static int[] size() {
        if (map != null) try {
            Object session = call(map, "getCurrentSession");
            if (session != null) {
                double scale = Minecraft.getInstance().getWindow().getGuiScale();
                int w = ((Number) session.getClass().getMethod("getWidth", double.class).invoke(session, scale)).intValue();
                int h = ((Number) session.getClass().getMethod("getHeight", double.class).invoke(session, scale)).intValue();
                return new int[] {Math.max(20, w), Math.max(20, h)};
            }
        } catch (ReflectiveOperationException ignored) {}
        return new int[] {100, 100};
    }
    public static void position(int x, int y) {
        if (map == null || (x == lastX && y == lastY)) return;
        try {
            Object updated = call(transform, "copy");
            transformType.getField("x").setInt(updated, x); transformType.getField("y").setInt(updated, y);
            for (String name : new String[] {"centered", "fromRight", "fromBottom"}) transformType.getField(name).setBoolean(updated, false);
            map.getClass().getMethod("setTransform", transformType).invoke(map, updated);
            map.getClass().getMethod("confirmTransform").invoke(map);
            transform = updated; lastX = x; lastY = y;
        } catch (ReflectiveOperationException failure) { LoggerFactory.getLogger("TheLadsCore").warn("Cannot position Xaero map", failure); map = null; }
    }
}
