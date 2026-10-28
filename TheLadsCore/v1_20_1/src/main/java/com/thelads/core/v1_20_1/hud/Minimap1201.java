package com.thelads.core.v1_20_1.hud;

import com.thelads.core.client.HypixelSafetyManager;
import com.thelads.core.config.ActionOption;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.ModuleSupport;
import java.lang.reflect.Method;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.slf4j.LoggerFactory;

/** Public API adapter for Xaero's Minimap: Xaero retains terrain, waypoints, server protocol and fair-play ownership. */
public final class Minimap1201 {
    private static Object mod, map, manager, transform;
    private static Class<?> transformType;
    private static Boolean lastEnabled;
    private static int lastX = Integer.MIN_VALUE, lastY = Integer.MIN_VALUE;
    private static boolean attempted, loaded, fallbackFailed;
    /** {@link #failClosed}: Xaero's session, its minimap processor and the processor's "no minimap" switch; the processor it switched off. */
    private static Method currentSession, processor, noMinimap, setNoMinimap;
    private static Object switchedOff;

    private Minimap1201() {}

    private static Object call(Object object, String method) throws ReflectiveOperationException {
        return object.getClass().getMethod(method).invoke(object);
    }

    public static boolean available() { return map != null; }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!attempted) {
            attempted = true;
            if (!(loaded = Mods1201.loaded("xaerominimap"))) return;
            try {
                mod = Class.forName("xaero.common.HudMod").getField("INSTANCE").get(null);
                map = Class.forName("xaero.hud.minimap.BuiltInHudModules").getField("MINIMAP").get(null);
                manager = call(call(mod, "getHudConfigs"), "getClientConfigManager");
                transformType = Class.forName("xaero.hud.module.ModuleTransform");
                transform = call(map, "getConfirmedTransform");
                if (ModuleManager.getInstance().getModule("Minimap").getOption("Map and waypoint settings") instanceof ActionOption action)
                    action.setAction(() -> {
                        try {
                            @SuppressWarnings("unchecked") var factory = (Function<Screen, Screen>) call(map, "getConfigScreenFactory");
                            mc.setScreen(factory.apply(mc.screen));
                        } catch (ReflectiveOperationException failure) {
                            LoggerFactory.getLogger("TheLadsCore").warn("Cannot open Xaero settings", failure);
                        }
                    });
                ModuleSupport.registerBuiltIn("Minimap");
            } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                map = null;
                LoggerFactory.getLogger("TheLadsCore").warn("Xaero integration unavailable", failure);
            }
        }
        if (map == null) {
            if (loaded) failClosed();
            return;
        }
        try {
            boolean enabled = ModuleManager.getInstance().getModule("Minimap").isEnabled();
            if (lastEnabled == null || lastEnabled != enabled) {
                map.getClass().getMethod("setActive", Class.forName("xaero.lib.client.config.ClientConfigManager"), boolean.class).invoke(map, manager, enabled);
                lastEnabled = enabled;
            }
            // Where the map goes is the Lads HUD layout's (XaeroMinimapHudElement.placed): anchored, docked and scaled like the rest.
        } catch (ReflectiveOperationException failure) {
            LoggerFactory.getLogger("TheLadsCore").warn("Xaero integration failed", failure);
            map = null;
        }
    }

    /**
     * Xaero's HUD API above is missing (another Xaero version, a modpack's own): where the server rules block minimaps, its older
     * "no minimap" switch (the one a server's chat code sets, which its renderer honours) keeps it off, so the gate fails closed. It
     * only switches back what it switched off itself, and leaves a server's own "no minimap" alone.
     */
    private static void failClosed() {
        if (fallbackFailed) return;
        try {
            if (setNoMinimap == null) {
                Class<?> session = Class.forName("xaero.common.XaeroMinimapSession"), minimap = Class.forName("xaero.common.minimap.MinimapProcessor");
                currentSession = session.getMethod("getCurrentSession");
                processor = session.getMethod("getMinimapProcessor");
                noMinimap = minimap.getMethod("getNoMinimapMessageReceived");
                setNoMinimap = minimap.getMethod("setNoMinimapMessageReceived", boolean.class);
            }
            Object session = currentSession.invoke(null), current = session == null ? null : processor.invoke(session);
            boolean banned = HypixelSafetyManager.isMinimapBanned();
            if (current == null || banned == (current == switchedOff)) return;
            if (banned && (Boolean) noMinimap.invoke(current)) return; // the server switched it off itself
            setNoMinimap.invoke(current, banned);
            switchedOff = banned ? current : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            fallbackFailed = true;
            LoggerFactory.getLogger("TheLadsCore").warn("Xaero's Minimap cannot be switched off where the server rules block minimaps", failure);
        }
    }

    public static int[] size() {
        if (map != null) try {
            Object session = call(map, "getCurrentSession");
            if (session != null) {
                double scale = Minecraft.getInstance().getWindow().getGuiScale();
                int w = ((Number) session.getClass().getMethod("getWidth", double.class).invoke(session, scale)).intValue();
                int h = ((Number) session.getClass().getMethod("getHeight", double.class).invoke(session, scale)).intValue();
                return new int[] {Math.max(20, w), Math.max(20, h) + com.thelads.core.client.hud.XaeroMinimapHudElement.infoLinesHeight(mod)};
            }
        } catch (ReflectiveOperationException ignored) {}
        return new int[] {100, 100};
    }

    public static void position(int x, int y) {
        if (map == null || (x == lastX && y == lastY)) return;
        try {
            Object updated = call(transform, "copy");
            transformType.getField("x").setInt(updated, x);
            transformType.getField("y").setInt(updated, y);
            for (String name : new String[] {"centered", "fromRight", "fromBottom"}) transformType.getField(name).setBoolean(updated, false);
            map.getClass().getMethod("setTransform", transformType).invoke(map, updated);
            map.getClass().getMethod("confirmTransform").invoke(map);
            transform = updated;
            lastX = x;
            lastY = y;
        } catch (ReflectiveOperationException failure) {
            LoggerFactory.getLogger("TheLadsCore").warn("Cannot position Xaero map", failure);
            map = null;
        }
    }
}
