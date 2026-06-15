package com.thelads.core.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.JNI;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.SharedLibrary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Creates the game window during Fabric preLaunch — seconds before Minecraft
 * normally would — already cleared to black with a subtle loading animation,
 * so hitting Launch never shows "nothing" or a white box while mods load.
 * Minecraft then adopts this window via {@code WindowMixin} instead of
 * creating its own.
 *
 * Disable with {@code -Dthelads.earlywindow=false}.
 */
public final class LadsEarlyWindow implements PreLaunchEntrypoint {
    private static final Logger LOGGER = LoggerFactory.getLogger("theladscore-early");

    // Near-black background + purple accent, matching the in-game loading overlay.
    private static final float BG_R = 0x0F / 255f, BG_G = 0x0A / 255f, BG_B = 0x0A / 255f;
    private static final float BAR_R = 0xEE / 255f, BAR_G = 0x54 / 255f, BAR_B = 0x5C / 255f;

    private static volatile long handle = 0L;
    private static volatile boolean adopted = false;
    private static volatile boolean stopRequested = false;
    private static volatile boolean iconApplied = false;
    private static Thread animThread;
    private static int fbWidth = 854, fbHeight = 480;

    @Override
    public void onPreLaunch() {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) return;
        if ("false".equalsIgnoreCase(System.getProperty("thelads.earlywindow"))) return;
        try {
            create();
        } catch (Throwable t) {
            LOGGER.warn("Early window unavailable, falling back to vanilla startup", t);
            long h = handle;
            handle = 0L;
            try {
                if (h != 0L) GLFW.glfwDestroyWindow(h);
            } catch (Throwable ignored) {}
        }
    }

    private static void create() {
        disableWindowsGhosting();

        if (!GLFW.glfwInit()) {
            LOGGER.warn("glfwInit failed in preLaunch — skipping early window");
            return;
        }

        GLFW.glfwDefaultWindowHints();
        // Same GL context hints the vanilla OpenGL backend uses (GlBackend.setWindowHints).
        GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, GLFW.GLFW_OPENGL_API);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_CREATION_API, GLFW.GLFW_NATIVE_CONTEXT_API);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);
        // Stay hidden until both buffers are cleared to black — no white flash, ever.
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);

        long h = GLFW.glfwCreateWindow(854, 480, "The Lads Client", 0L, 0L);
        if (h == 0L) {
            LOGGER.warn("Early window creation failed — vanilla will create its own window");
            return;
        }
        handle = h;

        // Center on the primary monitor.
        long monitor = GLFW.glfwGetPrimaryMonitor();
        if (monitor != 0L) {
            GLFWVidMode mode = GLFW.glfwGetVideoMode(monitor);
            if (mode != null) {
                GLFW.glfwSetWindowPos(h, (mode.width() - 854) / 2, (mode.height() - 480) / 2);
            }
        }

        // Paint both buffers black before the window ever becomes visible.
        GLFW.glfwMakeContextCurrent(h);
        GL.createCapabilities();
        int[] fw = new int[1], fh = new int[1];
        GLFW.glfwGetFramebufferSize(h, fw, fh);
        fbWidth = Math.max(1, fw[0]);
        fbHeight = Math.max(1, fh[0]);
        GL11C.glClearColor(BG_R, BG_G, BG_B, 1.0f);
        GL11C.glClear(GL11C.GL_COLOR_BUFFER_BIT);
        GLFW.glfwSwapBuffers(h);
        GL11C.glClear(GL11C.GL_COLOR_BUFFER_BIT);
        GLFW.glfwSwapBuffers(h);
        GLFW.glfwMakeContextCurrent(0L);

        applyIcon(h);
        GLFW.glfwShowWindow(h);

        // Animate from a worker thread (rendering is allowed off the main thread;
        // only window/event functions are main-thread bound).
        stopRequested = false;
        animThread = new Thread(LadsEarlyWindow::animLoop, "Lads-EarlyWindow");
        animThread.setDaemon(true);
        animThread.start();

        LOGGER.info("Early loading window shown ({}x{} fb)", fbWidth, fbHeight);
    }

    private static void animLoop() {
        long h = handle;
        try {
            GLFW.glfwMakeContextCurrent(h);
            GL.createCapabilities();
            GLFW.glfwSwapInterval(1);

            long start = System.currentTimeMillis();
            while (!stopRequested) {
                renderFrame(System.currentTimeMillis() - start);
                GLFW.glfwSwapBuffers(h);
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    break;
                }
            }
        } catch (Throwable t) {
            LOGGER.warn("Early window animation stopped", t);
        } finally {
            try {
                GLFW.glfwMakeContextCurrent(0L);
            } catch (Throwable ignored) {}
        }
    }

    /** Scissored clears only — works on a bare core-profile context with no shaders. */
    private static void renderFrame(long elapsedMs) {
        int w = fbWidth, hgt = fbHeight;

        GL11C.glDisable(GL11C.GL_SCISSOR_TEST);
        GL11C.glClearColor(BG_R, BG_G, BG_B, 1.0f);
        GL11C.glClear(GL11C.GL_COLOR_BUFFER_BIT);

        GL11C.glEnable(GL11C.GL_SCISSOR_TEST);

        // Sweeping indeterminate bar along the bottom (framebuffer origin = bottom-left).
        int barW = Math.max(60, w / 5);
        int travel = w + barW;
        float t = (elapsedMs % 1400L) / 1400.0f;
        int barX = (int) (t * travel) - barW;
        int x0 = Math.max(0, barX), x1 = Math.min(w, barX + barW);
        if (x1 > x0) {
            GL11C.glScissor(x0, 24, x1 - x0, 4);
            GL11C.glClearColor(BAR_R, BAR_G, BAR_B, 1.0f);
            GL11C.glClear(GL11C.GL_COLOR_BUFFER_BIT);
        }

        // Three pulsing squares in the center, lighting up in sequence.
        int size = 10, gap = 26;
        int cx = w / 2, cy = hgt / 2;
        for (int i = 0; i < 3; i++) {
            float phase = ((elapsedMs / 900.0f) - i * 0.22f) % 1.0f;
            if (phase < 0) phase += 1.0f;
            float bright = phase < 0.5f ? phase * 2f : (1f - phase) * 2f;
            float mix = 0.25f + 0.75f * bright;
            GL11C.glScissor(cx + (i - 1) * gap - size / 2, cy - size / 2, size, size);
            GL11C.glClearColor(BAR_R * mix, BAR_G * mix, BAR_B * mix, 1.0f);
            GL11C.glClear(GL11C.GL_COLOR_BUFFER_BIT);
        }

        GL11C.glDisable(GL11C.GL_SCISSOR_TEST);
    }

    /**
     * Called from WindowMixin on the thread that runs Window.createGlfwWindow.
     * Hands the early window over to Minecraft, applying the size/fullscreen
     * state vanilla asked for. Returns 0 if there is nothing to adopt.
     */
    public static long takeOver(int width, int height, long monitor) {
        long h = handle;
        if (h == 0L || adopted) return 0L;

        if (!stopAnim(2000)) {
            LOGGER.warn("Early window render thread did not stop — not adopting");
            return 0L;
        }

        try {
            if (monitor != 0L) {
                // Vanilla wanted a fullscreen window from the start.
                GLFWVidMode mode = GLFW.glfwGetVideoMode(monitor);
                int rate = mode != null ? mode.refreshRate() : GLFW.GLFW_DONT_CARE;
                GLFW.glfwSetWindowMonitor(h, monitor, 0, 0, width, height, rate);
            } else {
                int[] cw = new int[1], ch = new int[1];
                GLFW.glfwGetWindowSize(h, cw, ch);
                if (cw[0] != width || ch[0] != height) {
                    GLFW.glfwSetWindowSize(h, width, height);
                }
            }
        } catch (Throwable t) {
            LOGGER.warn("Early window adoption sizing failed (continuing)", t);
        }

        adopted = true;
        LOGGER.info("Minecraft adopted the early window");
        return h;
    }

    /**
     * Fallback when adoption didn't happen and vanilla created its own window:
     * immediately paint it black so it never flashes white.
     */
    public static void paintBlackIfForeign(long h) {
        if (h == 0L || h == handle || adopted) return;
        try {
            GLFW.glfwMakeContextCurrent(h);
            GL.createCapabilities();
            GL11C.glClearColor(BG_R, BG_G, BG_B, 1.0f);
            GL11C.glClear(GL11C.GL_COLOR_BUFFER_BIT);
            GLFW.glfwSwapBuffers(h);
            GL11C.glClear(GL11C.GL_COLOR_BUFFER_BIT);
            GLFW.glfwSwapBuffers(h);
            GLFW.glfwMakeContextCurrent(0L);
            applyIcon(h);
        } catch (Throwable t) {
            LOGGER.warn("Could not pre-clear vanilla window", t);
        }
    }

    /** Safety net: destroy the early window if Minecraft never adopted it. */
    public static void abandonIfUnused() {
        long h = handle;
        if (h == 0L || adopted) return;
        LOGGER.warn("Early window was never adopted — destroying it");
        stopAnim(2000);
        handle = 0L;
        try {
            GLFW.glfwDestroyWindow(h);
        } catch (Throwable ignored) {}
    }

    public static boolean isIconApplied() {
        return iconApplied;
    }

    private static boolean stopAnim(long timeoutMs) {
        stopRequested = true;
        Thread t = animThread;
        if (t == null) return true;
        try {
            t.interrupt();
            t.join(timeoutMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        boolean stopped = !t.isAlive();
        if (stopped) animThread = null;
        return stopped;
    }

    /** Branded window icon from the mod's own resources (replaces the icon mod). */
    private static void applyIcon(long h) {
        ByteBuffer png = null, pixels = null;
        try {
            ModContainer mod = FabricLoader.getInstance().getModContainer("theladscore").orElse(null);
            if (mod == null) return;
            Path iconPath = mod.findPath("assets/modid/icon.png").orElse(null);
            if (iconPath == null) return;

            byte[] bytes = Files.readAllBytes(iconPath);
            png = MemoryUtil.memAlloc(bytes.length);
            png.put(bytes).flip();

            int[] w = new int[1], hh = new int[1], comp = new int[1];
            pixels = STBImage.stbi_load_from_memory(png, w, hh, comp, 4);
            if (pixels == null) return;

            try (GLFWImage.Buffer icons = GLFWImage.malloc(1)) {
                icons.position(0);
                icons.width(w[0]);
                icons.height(hh[0]);
                icons.pixels(pixels);
                GLFW.glfwSetWindowIcon(h, icons);
            }
            iconApplied = true;
        } catch (Throwable t) {
            LOGGER.warn("Could not apply branded window icon", t);
        } finally {
            if (pixels != null) STBImage.stbi_image_free(pixels);
            if (png != null) MemoryUtil.memFree(png);
        }
    }

    /**
     * Stops Windows from replacing the window with a frozen "ghost" copy while
     * the main thread is busy loading mods and can't pump events.
     */
    private static void disableWindowsGhosting() {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) return;
        try {
            SharedLibrary user32 = org.lwjgl.system.Library.loadNative("theladscore", "user32");
            try {
                long fn = user32.getFunctionAddress("DisableProcessWindowsGhosting");
                if (fn != 0L) JNI.invokeV(fn);
            } finally {
                user32.free();
            }
        } catch (Throwable t) {
            LOGGER.debug("DisableProcessWindowsGhosting unavailable", t);
        }
    }
}
