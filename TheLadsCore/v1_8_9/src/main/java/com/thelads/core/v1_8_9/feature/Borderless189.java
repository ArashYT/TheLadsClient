package com.thelads.core.v1_8_9.feature;

import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;

/**
 * Borderless Fullscreen on 1.8.9: with the module on, fullscreen (F11, Video Settings, fullscreen:true at launch) is a normal
 * window without decorations covering the primary monitor, the monitor LWJGL's exclusive fullscreen uses, so capture tools see
 * a regular window. LWJGL 2 reads its undecorated property only when it creates a window, and setDisplayMode always recreates
 * it (with the same GL context, as vanilla's own fullscreen toggle does). MinecraftMixin routes toggleFullscreen here.
 */
public final class Borderless189 {
    private static final Logger LOGGER = LogManager.getLogger("TheLadsCore-1.8.9");
    private static final String UNDECORATED = "org.lwjgl.opengl.Window.undecorated";
    /** The window's place and size before borderless. */
    private static int windowX, windowY, windowWidth, windowHeight;
    private static boolean active;

    private Borderless189() {}

    public static boolean enabled() {
        Module module = ModuleManager.getInstance().getModule("BorderlessFullscreen");
        return module != null && module.isEnabled();
    }

    public static boolean active() {
        return active;
    }

    /** Every client tick: exclusive fullscreen (fullscreen:true at launch) becomes borderless, and borderless ends with the module. */
    public static void tick(Minecraft mc) {
        if (enabled() && !active && mc.isFullScreen() && Display.isFullscreen()) {
            mc.toggleFullscreen(); // vanilla: to the window
            mc.toggleFullscreen(); // here: to borderless
        } else if (!enabled() && active) {
            mc.toggleFullscreen();
        }
    }

    /** Minecraft.toggleFullscreen from a window with the module on, or from borderless; returns whether it is borderless now. */
    public static boolean toggle(Minecraft mc) {
        try {
            if (active) leave(); else enter();
        } catch (Exception e) {
            LOGGER.error("Couldn't toggle borderless fullscreen", e);
        }
        mc.gameSettings.fullScreen = active;
        mc.resize(Display.getWidth(), Display.getHeight());
        Display.setVSyncEnabled(mc.gameSettings.enableVsync);
        mc.updateDisplay();
        return active;
    }

    private static void enter() throws Exception {
        windowX = Display.getX();
        windowY = Display.getY();
        windowWidth = Display.getWidth();
        windowHeight = Display.getHeight();
        DisplayMode monitor = Display.getDesktopDisplayMode();
        System.setProperty(UNDECORATED, "true");
        Display.setResizable(false); // a resize frame would be a border
        Display.setLocation(0, 0);
        Display.setDisplayMode(new DisplayMode(monitor.getWidth(), monitor.getHeight()));
        active = true;
    }

    private static void leave() throws Exception {
        System.setProperty(UNDECORATED, "false");
        active = false;
        Display.setLocation(windowX, windowY);
        Display.setDisplayMode(new DisplayMode(Math.max(1, windowWidth), Math.max(1, windowHeight)));
        resizable();
    }

    /**
     * A window LWJGL 2 recreates (leaving fullscreen) is created without a resize frame: WindowsDisplay remembers the old
     * window's resizable flag and skips setting it again (vanilla bug MC-68754). Flipping it applies the style to the new window.
     */
    public static void resizable() {
        Display.setResizable(false);
        Display.setResizable(true);
    }
}
