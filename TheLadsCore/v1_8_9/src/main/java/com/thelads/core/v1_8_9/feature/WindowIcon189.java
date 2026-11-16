package com.thelads.core.v1_8_9.feature;

import com.thelads.core.client.WindowIcons;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import javax.imageio.ImageIO;
import org.apache.logging.log4j.LogManager;
import org.lwjgl.opengl.Display;

/**
 * The Lads artwork as the 1.8.9 window icon. LWJGL 2's Display.setIcon takes any sizes on Linux but on Windows only 16 and 32
 * pixels, so a high-DPI taskbar and Alt-Tab would stretch those. Once the window exists, Windows is given the artwork at the
 * sizes the system asks for (SM_CXSMICON, SM_CXICON: 16/32, 24/48 at 150%, 32/64 at 200%), as GLFW does on the newer versions.
 */
public final class WindowIcon189 {
    private static final boolean MAC = System.getProperty("os.name", "").startsWith("Mac"), WINDOWS = System.getProperty("os.name", "").startsWith("Windows");

    private WindowIcon189() {}

    /** Display.setIcon with every size (the platform takes what it can use); false leaves vanilla's icon alone. */
    public static boolean set() {
        if (MAC || !WindowIcons.available()) return false;
        try {
            ByteBuffer[] icons = new ByteBuffer[WindowIcons.SIZES.length];
            for (int i = 0; i < icons.length; i++) icons[i] = buffer(WindowIcons.SIZES[i]);
            Display.setIcon(icons);
            LogManager.getLogger("TheLadsCore-1.8.9").info("Lads window icon applied ({} sizes)", icons.length);
            return true;
        } catch (Exception | LinkageError e) {
            LogManager.getLogger("TheLadsCore-1.8.9").warn("Lads window icon not applied: {}", e.toString());
            return false;
        }
    }

    /**
     * The window exists: hands Display every size again (RetroFuturaBootstrap's LWJGL 3 shim only applies an icon to a live window;
     * LWJGL 2 applies the cached one at creation and takes a new one at once), then on LWJGL 2 for Windows the DPI sizes. Never throws.
     */
    public static void afterDisplay() {
        if (MAC || !WindowIcons.available()) return;
        try {
            ByteBuffer[] icons = new ByteBuffer[WindowIcons.SIZES.length];
            for (int i = 0; i < icons.length; i++) icons[i] = buffer(WindowIcons.SIZES[i]);
            Display.setIcon(icons);
        } catch (Throwable e) {
            LogManager.getLogger("TheLadsCore-1.8.9").warn("Lads window icon not applied to the window: {}", e.toString());
        }
        setForDpi();
    }

    /** Windows: the small and big window icon at the sizes this display's DPI wants. Never throws. */
    private static void setForDpi() {
        if (!WINDOWS || !WindowIcons.available()) return;
        try {
            Field implementation = Display.class.getDeclaredField("display_impl");
            implementation.setAccessible(true);
            Object display = implementation.get(null);
            // The class LWJGL itself loaded (a second copy from the mod class loader has no natives).
            Class<?> windowsDisplay = display == null ? null : display.getClass();
            if (windowsDisplay == null || !windowsDisplay.getName().equals("org.lwjgl.opengl.WindowsDisplay")) return;
            Field window = windowsDisplay.getDeclaredField("hwnd");
            window.setAccessible(true);
            long hwnd = window.getLong(display);
            Method metrics = windowsDisplay.getDeclaredMethod("getSystemMetrics", int.class);
            Method create = windowsDisplay.getDeclaredMethod("createIcon", int.class, int.class, IntBuffer.class);
            Method send = windowsDisplay.getDeclaredMethod("sendMessage", long.class, long.class, long.class, long.class);
            metrics.setAccessible(true);
            create.setAccessible(true);
            send.setAccessible(true);
            if (hwnd == 0) return;
            int small = pick((Integer) metrics.invoke(null, 49)), big = pick((Integer) metrics.invoke(null, 11)); // SM_CXSMICON, SM_CXICON
            for (int[] kind : new int[][]{{0, small}, {1, big}}) { // ICON_SMALL, ICON_BIG
                long icon = (Long) create.invoke(null, kind[1], kind[1], buffer(kind[1]).asIntBuffer());
                if (icon != 0) send.invoke(null, hwnd, 0x80L, (long) kind[0], icon); // WM_SETICON
            }
            LogManager.getLogger("TheLadsCore-1.8.9").info("Lads window icon sized for this display: small {} big {}", small, big);
        } catch (NoSuchFieldException e) {
            // Not LWJGL 2 (RetroFuturaBootstrap's LWJGL 3 shim): its Display.setIcon already hands every size to GLFW.
        } catch (Throwable e) {
            LogManager.getLogger("TheLadsCore-1.8.9").warn("Lads window icon keeps LWJGL's 16/32: {}", e.toString());
        }
    }

    /** The smallest artwork size that is at least what the system asked for (the largest when it asks for more). */
    private static int pick(int wanted) {
        for (int size : WindowIcons.SIZES) if (size >= wanted) return size;
        return WindowIcons.SIZES[WindowIcons.SIZES.length - 1];
    }

    /** Vanilla's readImageToBuffer: RGBA, one big-endian int per pixel. */
    private static ByteBuffer buffer(int size) throws Exception {
        BufferedImage image;
        try (InputStream in = WindowIcons.open(size)) { image = ImageIO.read(in); }
        int[] argb = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
        ByteBuffer bytes = ByteBuffer.allocate(4 * argb.length);
        for (int pixel : argb) bytes.putInt(pixel << 8 | pixel >> 24 & 255);
        bytes.flip();
        return bytes;
    }
}
