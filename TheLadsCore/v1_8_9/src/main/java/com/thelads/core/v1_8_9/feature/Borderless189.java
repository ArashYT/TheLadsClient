package com.thelads.core.v1_8_9.feature;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.thelads.core.client.BorderlessWindow;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;

/**
 * Borderless Fullscreen on 1.8.9: with the module on, fullscreen (F11, Video Settings, fullscreen:true at launch) is a normal
 * window without decorations covering the primary monitor, the monitor LWJGL's exclusive fullscreen uses, so capture tools see
 * a regular window. On Windows the window is {@link BorderlessWindow#EXTRA_HEIGHT} rows taller than the monitor (the extra row is off
 * screen, ScaledResolutionMixin keeps the GUI in the visible part): a window that exactly covers a monitor is promoted to
 * fullscreen/independent flip, which OBS and Discord cannot capture. LWJGL 2 reads its undecorated property only when it creates a
 * window, and setDisplayMode always recreates it (with the same GL context, as vanilla's own fullscreen toggle does). The row is
 * added to the native window afterwards: LWJGL's display mode stays the monitor's, which OptiFine's fullscreen check compares
 * (any other mode and it turns the window into exclusive fullscreen again). MinecraftMixin routes toggleFullscreen here.
 */
public final class Borderless189 {
    private static final Logger LOGGER = LogManager.getLogger("TheLadsCore-1.8.9");
    private static final String UNDECORATED = "org.lwjgl.opengl.Window.undecorated";
    /** The window's place and size before borderless. */
    private static int windowX, windowY, windowWidth, windowHeight;
    private static boolean active;
    /** The borderless window's size, and its rows below the monitor (0 away from Windows). */
    private static int width, height, extraRows;

    private Borderless189() {}

    public static boolean enabled() {
        Module module = ModuleManager.getInstance().getModule("BorderlessFullscreen");
        return module != null && module.isEnabled();
    }

    public static boolean active() {
        return active;
    }

    /** True if the user enabled true 1:1 borderless without the capture-safe extra row. */
    public static boolean trueBorderless() {
        Module module = ModuleManager.getInstance().getModule("BorderlessFullscreen");
        if (module == null) return false;
        com.thelads.core.config.Option opt = module.getOption("True borderless");
        return opt instanceof com.thelads.core.config.BoolOption && ((com.thelads.core.config.BoolOption) opt).get();
    }

    /** Rows of the borderless window that lie below the monitor; the GUI is laid out without them. */
    public static int extraRows() {
        return active ? extraRows : 0;
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
        } catch (Exception | LinkageError e) { // LinkageError: a window API the running LWJGL lacks never crashes F11
            LOGGER.error("Couldn't toggle borderless fullscreen", e);
        }
        mc.gameSettings.fullScreen = active;
        if (active) mc.resize(width, height);
        else mc.resize(Display.getWidth(), Display.getHeight());
        Display.setVSyncEnabled(mc.gameSettings.enableVsync);
        mc.updateDisplay();
        return active;
    }

    private static void enter() throws Exception {
        int[] position = position();
        windowX = position[0];
        windowY = position[1];
        windowWidth = Display.getWidth();
        windowHeight = Display.getHeight();
        DisplayMode monitor = Display.getDesktopDisplayMode();
        System.setProperty(UNDECORATED, "true");
        Display.setResizable(false); // a resize frame would be a border
        // SDL ignores LWJGL 2's undecorated property; frameless before the move, so no title bar is placed above the monitor
        if (PylonWindow189.PRESENT) PylonWindow189.bordered(false);
        move(0, 0);
        Display.setDisplayMode(new DisplayMode(monitor.getWidth(), monitor.getHeight()));
        width = monitor.getWidth();
        height = monitor.getHeight();
        extraRows = 0;
        if (System.getProperty("os.name", "").startsWith("Windows")) {
            boolean trueBorderless = trueBorderless();
            int add = trueBorderless ? 0 : BorderlessWindow.EXTRA_HEIGHT;
            try {
                // Pylon's Display learns sizes from SDL only: resize through SDL there
                if (PylonWindow189.PRESENT) PylonWindow189.size(width, height + add);
                else User32.INSTANCE.SetWindowPos(hwnd(), null, 0, 0, width, height + add, 0x16); // SWP_NOMOVE | SWP_NOZORDER | SWP_NOACTIVATE
                extraRows = add;
                height += extraRows;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                LOGGER.error("Couldn't add the capture-safe row to the borderless window; it covers exactly the monitor", e);
            }
        }
        active = true;
    }

    private static void leave() throws Exception {
        System.setProperty(UNDECORATED, "false");
        active = false;
        if (PylonWindow189.PRESENT) { // Pylon's setDisplayMode sizes only the GL framebuffer, never the SDL window
            PylonWindow189.size(Math.max(1, windowWidth), Math.max(1, windowHeight));
            PylonWindow189.bordered(true);
        }
        move(windowX, windowY);
        Display.setDisplayMode(new DisplayMode(Math.max(1, windowWidth), Math.max(1, windowHeight)));
        resizable();
    }

    /** The window's desktop position: LWJGL 2's, or Pylon's SDL window's. */
    static int[] position() throws ReflectiveOperationException {
        return PylonWindow189.PRESENT ? PylonWindow189.position() : new int[] { Display.getX(), Display.getY() };
    }

    private static void move(int x, int y) throws ReflectiveOperationException {
        if (PylonWindow189.PRESENT) PylonWindow189.move(x, y); else Display.setLocation(x, y);
    }

    static boolean resizableNow() throws ReflectiveOperationException {
        return PylonWindow189.PRESENT ? PylonWindow189.resizable() : Display.isResizable();
    }

    /** LWJGL 2's native window (Windows): WindowsDisplay keeps its handle in a private field. */
    static WinDef.HWND hwnd() throws ReflectiveOperationException {
        if (PylonWindow189.PRESENT) return new WinDef.HWND(new Pointer(PylonWindow189.hwnd()));
        Field impl = Display.class.getDeclaredField("display_impl");
        impl.setAccessible(true);
        Object display = impl.get(null);
        Field handle = display.getClass().getDeclaredField("hwnd");
        handle.setAccessible(true);
        return new WinDef.HWND(new Pointer(handle.getLong(display)));
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
