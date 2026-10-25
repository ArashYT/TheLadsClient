package com.thelads.core.v1_8_9.feature;

/**
 * Pylon (bundled since 1.9.12) runs 1.8.9 on LWJGL 3 + SDL3 behind LWJGL 2's API, without LWJGL 2's internals: no
 * org.lwjgl.opengl.Window.undecorated property, no WindowsDisplay handle. Its SDL window, by reflection (Core compiles
 * against LWJGL 2).
 */
public final class PylonWindow189 {
    public static final boolean PRESENT = present();
    private static boolean resizedLatched;

    private PylonWindow189() {}

    private static boolean present() {
        try {
            Class.forName("pl.tomgirl.pylon.window.DisplaySdl");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * After one of Lads' own window pumps: Pylon clears Display.wasResized on every processMessages (LWJGL 2 kept it until read),
     * so a resize pumped there would be gone by Minecraft.checkWindowResize. Keep it for that check.
     */
    public static void afterPump() {
        if (PRESENT && org.lwjgl.opengl.Display.wasResized()) resizedLatched = true;
    }

    /**
     * OptiFine caches Display.getDesktopDisplayMode once, from GameSettings' constructor, before the window exists. Pylon has no
     * desktop mode then (null), so Video Settings' Fullscreen Mode crashed (Config.getLargestDisplayMode NPE). A desktop mode also
     * turns OptiFine's F11 into exclusive fullscreen instead of Lads' borderless one (1.11.0 QA), so it is filled only while Video
     * Settings is open, and put back afterwards unless the player picked a fullscreen mode there.
     */
    public static final class OptiFineDesktopMode {
        private boolean filled;

        @net.minecraftforge.fml.common.eventhandler.SubscribeEvent
        public void open(net.minecraftforge.client.event.GuiOpenEvent event) {
            try {
                java.lang.reflect.Field mode = Class.forName("Config").getDeclaredField("desktopDisplayMode");
                mode.setAccessible(true);
                if (event.gui instanceof net.minecraft.client.gui.GuiVideoSettings) {
                    if (mode.get(null) == null) {
                        mode.set(null, org.lwjgl.opengl.Display.getDesktopDisplayMode());
                        filled = true;
                    }
                } else if (filled && "Default".equals(fullscreenMode())) {
                    mode.set(null, null);
                    filled = false;
                }
            } catch (Throwable notOptiFine) {}
        }

        /** OptiFine's Fullscreen Mode setting ("Default" = Lads' borderless F11); "Default" when it can't be read. */
        private static String fullscreenMode() {
            try {
                Object settings = net.minecraft.client.Minecraft.getMinecraft().gameSettings;
                Object value = settings.getClass().getField("ofFullscreenMode").get(settings);
                return value instanceof String ? (String) value : "Default";
            } catch (Throwable e) {
                return "Default";
            }
        }
    }

    /** Minecraft.checkWindowResize's Display.wasResized, with a latched resize. */
    public static boolean wasResized() {
        boolean resized = org.lwjgl.opengl.Display.wasResized() || resizedLatched;
        resizedLatched = false;
        return resized;
    }

    private static long window() throws ReflectiveOperationException {
        Class<?> display = Class.forName("pl.tomgirl.pylon.window.DisplaySdl");
        return (Long) display.getMethod("getHandle").invoke(display.getMethod("instance").invoke(null));
    }

    /** SDL's window frame and title bar, what LWJGL 2's undecorated property controls there. */
    static void bordered(boolean bordered) throws ReflectiveOperationException {
        Class.forName("org.lwjgl.sdl.SDLVideo").getMethod("SDL_SetWindowBordered", long.class, boolean.class).invoke(null, window(), bordered);
    }

    /** The window's top-left corner on the desktop (LWJGL 2's Display.getX/getY, which Pylon lacks). */
    static int[] position() throws ReflectiveOperationException {
        java.nio.IntBuffer x = org.lwjgl.BufferUtils.createIntBuffer(1), y = org.lwjgl.BufferUtils.createIntBuffer(1);
        Class.forName("org.lwjgl.sdl.SDLVideo").getMethod("SDL_GetWindowPosition", long.class, java.nio.IntBuffer.class, java.nio.IntBuffer.class)
            .invoke(null, window(), x, y);
        return new int[] { x.get(0), y.get(0) };
    }

    /** Display.setLocation, which Pylon lacks. */
    static void move(int x, int y) throws ReflectiveOperationException {
        Class.forName("org.lwjgl.sdl.SDLVideo").getMethod("SDL_SetWindowPosition", long.class, int.class, int.class).invoke(null, window(), x, y);
    }

    /** The window's size, waiting until SDL has applied it (Display reports SDL's size). */
    static void size(int width, int height) throws ReflectiveOperationException {
        Class<?> video = Class.forName("org.lwjgl.sdl.SDLVideo");
        long window = window();
        video.getMethod("SDL_SetWindowSize", long.class, int.class, int.class).invoke(null, window, width, height);
        video.getMethod("SDL_SyncWindow", long.class).invoke(null, window);
        afterPump(); // SyncWindow may deliver the resize outside a pump
    }

    /** Display.isResizable, which Pylon lacks (SDL_WINDOW_RESIZABLE). */
    static boolean resizable() throws ReflectiveOperationException {
        return ((Long) Class.forName("org.lwjgl.sdl.SDLVideo").getMethod("SDL_GetWindowFlags", long.class).invoke(null, window()) & 32L) != 0;
    }

    /** Set whether the SDL window is resizable. */
    static void setResizable(boolean resizable) throws ReflectiveOperationException {
        Class.forName("org.lwjgl.sdl.SDLVideo").getMethod("SDL_SetWindowResizable", long.class, boolean.class).invoke(null, window(), resizable);
    }

    /** Set fullscreen mode on the SDL window. */
    static void fullscreen(boolean fullscreen) throws ReflectiveOperationException {
        Class.forName("org.lwjgl.sdl.SDLVideo").getMethod("SDL_SetWindowFullscreen", long.class, boolean.class).invoke(null, window(), fullscreen);
    }

    /** The native Windows handle of the SDL window. */
    static long hwnd() throws ReflectiveOperationException {
        int properties = (Integer) Class.forName("org.lwjgl.sdl.SDLVideo").getMethod("SDL_GetWindowProperties", long.class).invoke(null, window());
        return (Long) Class.forName("org.lwjgl.sdl.SDLProperties").getMethod("SDL_GetPointerProperty", int.class, CharSequence.class, long.class)
            .invoke(null, properties, "SDL.window.win32.hwnd", 0L);
    }
}
