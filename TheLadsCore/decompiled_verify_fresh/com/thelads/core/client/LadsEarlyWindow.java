/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.fabricmc.api.EnvType
 *  net.fabricmc.loader.api.FabricLoader
 *  net.fabricmc.loader.api.ModContainer
 *  net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint
 *  org.lwjgl.glfw.GLFW
 *  org.lwjgl.glfw.GLFWImage
 *  org.lwjgl.glfw.GLFWImage$Buffer
 *  org.lwjgl.glfw.GLFWVidMode
 *  org.lwjgl.opengl.GL
 *  org.lwjgl.opengl.GL11C
 *  org.lwjgl.stb.STBImage
 *  org.lwjgl.system.JNI
 *  org.lwjgl.system.Library
 *  org.lwjgl.system.MemoryUtil
 *  org.lwjgl.system.SharedLibrary
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package com.thelads.core.client;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.lwjgl.system.Library;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.SharedLibrary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LadsEarlyWindow
implements PreLaunchEntrypoint {
    private static final Logger LOGGER = LoggerFactory.getLogger((String)"theladscore-early");
    private static final float BG_R = 0.039215688f;
    private static final float BG_G = 0.039215688f;
    private static final float BG_B = 0.05882353f;
    private static final float BAR_R = 0.36078432f;
    private static final float BAR_G = 0.32941177f;
    private static final float BAR_B = 0.93333334f;
    private static volatile long handle = 0L;
    private static volatile boolean adopted = false;
    private static volatile boolean stopRequested = false;
    private static volatile boolean iconApplied = false;
    private static Thread animThread;
    private static int fbWidth;
    private static int fbHeight;

    public void onPreLaunch() {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) {
            return;
        }
        if ("false".equalsIgnoreCase(System.getProperty("thelads.earlywindow"))) {
            return;
        }
        try {
            LadsEarlyWindow.create();
        }
        catch (Throwable t) {
            LOGGER.warn("Early window unavailable, falling back to vanilla startup", t);
            long h = handle;
            handle = 0L;
            try {
                if (h != 0L) {
                    GLFW.glfwDestroyWindow((long)h);
                }
            }
            catch (Throwable throwable) {
                // empty catch block
            }
        }
    }

    private static void create() {
        GLFWVidMode mode;
        LadsEarlyWindow.disableWindowsGhosting();
        if (!GLFW.glfwInit()) {
            LOGGER.warn("glfwInit failed in preLaunch \u2014 skipping early window");
            return;
        }
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint((int)139265, (int)196609);
        GLFW.glfwWindowHint((int)139275, (int)221185);
        GLFW.glfwWindowHint((int)139266, (int)3);
        GLFW.glfwWindowHint((int)139267, (int)3);
        GLFW.glfwWindowHint((int)139272, (int)204801);
        GLFW.glfwWindowHint((int)139270, (int)1);
        GLFW.glfwWindowHint((int)131076, (int)0);
        long h = GLFW.glfwCreateWindow((int)854, (int)480, (CharSequence)"The Lads Client", (long)0L, (long)0L);
        if (h == 0L) {
            LOGGER.warn("Early window creation failed \u2014 vanilla will create its own window");
            return;
        }
        handle = h;
        long monitor = GLFW.glfwGetPrimaryMonitor();
        if (monitor != 0L && (mode = GLFW.glfwGetVideoMode((long)monitor)) != null) {
            GLFW.glfwSetWindowPos((long)h, (int)((mode.width() - 854) / 2), (int)((mode.height() - 480) / 2));
        }
        GLFW.glfwMakeContextCurrent((long)h);
        GL.createCapabilities();
        int[] fw = new int[1];
        int[] fh = new int[1];
        GLFW.glfwGetFramebufferSize((long)h, (int[])fw, (int[])fh);
        fbWidth = Math.max(1, fw[0]);
        fbHeight = Math.max(1, fh[0]);
        GL11C.glClearColor((float)0.039215688f, (float)0.039215688f, (float)0.05882353f, (float)1.0f);
        GL11C.glClear((int)16384);
        GLFW.glfwSwapBuffers((long)h);
        GL11C.glClear((int)16384);
        GLFW.glfwSwapBuffers((long)h);
        GLFW.glfwMakeContextCurrent((long)0L);
        LadsEarlyWindow.applyIcon(h);
        GLFW.glfwShowWindow((long)h);
        stopRequested = false;
        animThread = new Thread(LadsEarlyWindow::animLoop, "Lads-EarlyWindow");
        animThread.setDaemon(true);
        animThread.start();
        LOGGER.info("Early loading window shown ({}x{} fb)", (Object)fbWidth, (Object)fbHeight);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private static void animLoop() {
        long h = handle;
        try {
            GLFW.glfwMakeContextCurrent((long)h);
            GL.createCapabilities();
            GLFW.glfwSwapInterval((int)1);
            long start = System.currentTimeMillis();
            while (!stopRequested) {
                LadsEarlyWindow.renderFrame(System.currentTimeMillis() - start);
                GLFW.glfwSwapBuffers((long)h);
                try {
                    Thread.sleep(10L);
                }
                catch (InterruptedException e) {
                    // empty catch block
                    break;
                }
            }
        }
        catch (Throwable t) {
            LOGGER.warn("Early window animation stopped", t);
        }
        finally {
            try {
                GLFW.glfwMakeContextCurrent((long)0L);
            }
            catch (Throwable start) {}
        }
    }

    private static void renderFrame(long elapsedMs) {
        int w = fbWidth;
        int hgt = fbHeight;
        GL11C.glDisable((int)3089);
        GL11C.glClearColor((float)0.039215688f, (float)0.039215688f, (float)0.05882353f, (float)1.0f);
        GL11C.glClear((int)16384);
        GL11C.glEnable((int)3089);
        int barW = Math.max(60, w / 5);
        int travel = w + barW;
        float t = (float)(elapsedMs % 1400L) / 1400.0f;
        int barX = (int)(t * (float)travel) - barW;
        int x0 = Math.max(0, barX);
        int x1 = Math.min(w, barX + barW);
        if (x1 > x0) {
            GL11C.glScissor((int)x0, (int)24, (int)(x1 - x0), (int)4);
            GL11C.glClearColor((float)0.36078432f, (float)0.32941177f, (float)0.93333334f, (float)1.0f);
            GL11C.glClear((int)16384);
        }
        int size = 10;
        int gap = 26;
        int cx = w / 2;
        int cy = hgt / 2;
        for (int i = 0; i < 3; ++i) {
            float phase = ((float)elapsedMs / 900.0f - (float)i * 0.22f) % 1.0f;
            if (phase < 0.0f) {
                phase += 1.0f;
            }
            float bright = phase < 0.5f ? phase * 2.0f : (1.0f - phase) * 2.0f;
            float mix = 0.25f + 0.75f * bright;
            GL11C.glScissor((int)(cx + (i - 1) * gap - size / 2), (int)(cy - size / 2), (int)size, (int)size);
            GL11C.glClearColor((float)(0.36078432f * mix), (float)(0.32941177f * mix), (float)(0.93333334f * mix), (float)1.0f);
            GL11C.glClear((int)16384);
        }
        GL11C.glDisable((int)3089);
    }

    public static long takeOver(int width, int height, long monitor) {
        long h = handle;
        if (h == 0L || adopted) {
            return 0L;
        }
        if (!LadsEarlyWindow.stopAnim(2000L)) {
            LOGGER.warn("Early window render thread did not stop \u2014 not adopting");
            return 0L;
        }
        try {
            if (monitor != 0L) {
                GLFWVidMode mode = GLFW.glfwGetVideoMode((long)monitor);
                int rate = mode != null ? mode.refreshRate() : -1;
                GLFW.glfwSetWindowMonitor((long)h, (long)monitor, (int)0, (int)0, (int)width, (int)height, (int)rate);
            } else {
                int[] cw = new int[1];
                int[] ch = new int[1];
                GLFW.glfwGetWindowSize((long)h, (int[])cw, (int[])ch);
                if (cw[0] != width || ch[0] != height) {
                    GLFW.glfwSetWindowSize((long)h, (int)width, (int)height);
                }
            }
        }
        catch (Throwable t) {
            LOGGER.warn("Early window adoption sizing failed (continuing)", t);
        }
        adopted = true;
        LOGGER.info("Minecraft adopted the early window");
        return h;
    }

    public static void paintBlackIfForeign(long h) {
        if (h == 0L || h == handle || adopted) {
            return;
        }
        try {
            GLFW.glfwMakeContextCurrent((long)h);
            GL.createCapabilities();
            GL11C.glClearColor((float)0.039215688f, (float)0.039215688f, (float)0.05882353f, (float)1.0f);
            GL11C.glClear((int)16384);
            GLFW.glfwSwapBuffers((long)h);
            GL11C.glClear((int)16384);
            GLFW.glfwSwapBuffers((long)h);
            GLFW.glfwMakeContextCurrent((long)0L);
            LadsEarlyWindow.applyIcon(h);
        }
        catch (Throwable t) {
            LOGGER.warn("Could not pre-clear vanilla window", t);
        }
    }

    public static void abandonIfUnused() {
        long h = handle;
        if (h == 0L || adopted) {
            return;
        }
        LOGGER.warn("Early window was never adopted \u2014 destroying it");
        LadsEarlyWindow.stopAnim(2000L);
        handle = 0L;
        try {
            GLFW.glfwDestroyWindow((long)h);
        }
        catch (Throwable throwable) {
            // empty catch block
        }
    }

    public static boolean isIconApplied() {
        return iconApplied;
    }

    private static boolean stopAnim(long timeoutMs) {
        boolean stopped;
        stopRequested = true;
        Thread t = animThread;
        if (t == null) {
            return true;
        }
        try {
            t.interrupt();
            t.join(timeoutMs);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        boolean bl = stopped = !t.isAlive();
        if (stopped) {
            animThread = null;
        }
        return stopped;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private static void applyIcon(long h) {
        block28: {
            ByteBuffer png;
            block27: {
                int[] hh;
                int[] w;
                ByteBuffer pixels;
                block24: {
                    block25: {
                        Path iconPath;
                        block22: {
                            block23: {
                                ModContainer mod;
                                block20: {
                                    block21: {
                                        png = null;
                                        pixels = null;
                                        mod = FabricLoader.getInstance().getModContainer("theladscore").orElse(null);
                                        if (mod != null) break block20;
                                        if (pixels == null) break block21;
                                        STBImage.stbi_image_free(pixels);
                                    }
                                    if (png != null) {
                                        MemoryUtil.memFree(png);
                                    }
                                    return;
                                }
                                iconPath = mod.findPath("assets/modid/icon.png").orElse(null);
                                if (iconPath != null) break block22;
                                if (pixels == null) break block23;
                                STBImage.stbi_image_free(pixels);
                            }
                            if (png != null) {
                                MemoryUtil.memFree(png);
                            }
                            return;
                        }
                        byte[] bytes = Files.readAllBytes(iconPath);
                        png = MemoryUtil.memAlloc((int)bytes.length);
                        png.put(bytes).flip();
                        w = new int[1];
                        hh = new int[1];
                        int[] comp = new int[1];
                        pixels = STBImage.stbi_load_from_memory((ByteBuffer)png, (int[])w, (int[])hh, (int[])comp, (int)4);
                        if (pixels != null) break block24;
                        if (pixels == null) break block25;
                        STBImage.stbi_image_free((ByteBuffer)pixels);
                    }
                    if (png != null) {
                        MemoryUtil.memFree((ByteBuffer)png);
                    }
                    return;
                }
                try {
                    try (GLFWImage.Buffer icons = GLFWImage.malloc((int)1);){
                        icons.position(0);
                        icons.width(w[0]);
                        icons.height(hh[0]);
                        icons.pixels(pixels);
                        GLFW.glfwSetWindowIcon((long)h, (GLFWImage.Buffer)icons);
                    }
                    iconApplied = true;
                    if (pixels == null) break block27;
                }
                catch (Throwable t) {
                    try {
                        LOGGER.warn("Could not apply branded window icon", t);
                        break block28;
                    }
                    catch (Throwable throwable) {
                        throw throwable;
                    }
                    finally {
                        if (pixels != null) {
                            STBImage.stbi_image_free(pixels);
                        }
                        if (png != null) {
                            MemoryUtil.memFree(png);
                        }
                    }
                }
                STBImage.stbi_image_free((ByteBuffer)pixels);
            }
            if (png != null) {
                MemoryUtil.memFree((ByteBuffer)png);
            }
        }
    }

    private static void disableWindowsGhosting() {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
            return;
        }
        try {
            SharedLibrary user32 = Library.loadNative((String)"theladscore", (String)"user32");
            try {
                long fn = user32.getFunctionAddress((CharSequence)"DisableProcessWindowsGhosting");
                if (fn != 0L) {
                    JNI.invokeV((long)fn);
                }
            }
            finally {
                user32.free();
            }
        }
        catch (Throwable t) {
            LOGGER.debug("DisableProcessWindowsGhosting unavailable", t);
        }
    }

    static {
        fbWidth = 854;
        fbHeight = 480;
    }
}

