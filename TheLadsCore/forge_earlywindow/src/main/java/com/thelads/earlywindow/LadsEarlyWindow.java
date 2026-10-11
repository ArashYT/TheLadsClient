package com.thelads.earlywindow;

import com.thelads.earlywindow.theme.LadsGraphics;
import com.thelads.earlywindow.theme.LoadingScreenTheme;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import net.minecraftforge.fml.earlydisplay.ColourScheme;
import net.minecraftforge.fml.earlydisplay.DisplayWindow;
import net.minecraftforge.fml.earlydisplay.ElementShader;
import net.minecraftforge.fml.earlydisplay.RenderElement;
import net.minecraftforge.fml.earlydisplay.SimpleBufferBuilder;
import net.minecraftforge.fml.loading.FMLPaths;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.JNI;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.Platform;
import org.lwjgl.system.windows.WindowsLibrary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.lwjgl.opengl.GL32C.*;

/**
 * Forge's early loading window wearing the Lads loading screen (fml.toml: earlyWindowProvider = "ladsearlywindow").
 * <p>
 * Forge loads its ImmediateWindowProvider with ServiceLoader from the BOOT module layer, before mods/ is even scanned, so this
 * jar must be on the game's classpath (the launcher adds it to -cp), never in mods/. It keeps everything of Forge's window
 * (GLFW setup, GL fallbacks, the hand-over to Minecraft, ForgeLoadingOverlay) and only swaps the picture: Forge's render thread
 * paints its element list each frame, and that list is replaced by one whose paint step draws LoadingScreenTheme, the same
 * code the Core's in-game loading overlay draws, on the same clock, so the hand-over to the Core's overlay is seamless.
 * <p>
 * It also turns off Windows' window ghosting for the process: Forge's main thread scans and transforms mods for long stretches
 * without pumping window messages (only GLFW's creating thread may), so Windows replaced the window with a frozen, greyed
 * "(Not Responding)" ghost. Without ghosting the render thread's frames stay on screen, animated.
 * <p>
 * Anything unexpected in Forge's internals leaves Forge's own picture in place; a missing jar makes Forge fall back to no early
 * window at all ("Failed to find ImmediateWindowProvider ladsearlywindow, disabling").
 */
public class LadsEarlyWindow extends DisplayWindow {
    private static final Logger LOG = LoggerFactory.getLogger("LadsEarlyWindow");
    private static final int LOGO_UNIT = 15; // a texture unit neither Forge's early window nor Minecraft uses

    @Override
    public String name() {
        return "ladsearlywindow";
    }

    @Override
    public Runnable initialize(String[] arguments) {
        disableGhosting();
        Runnable tick = super.initialize(arguments); // creates the window on this (main) thread and schedules Forge's renderer
        if (!"forge".equals(System.getProperty("lads.earlywindow.look"))) {
            try {
                field("colourScheme").set(this, ColourScheme.BLACK); // letterbox and clear colour: near the Lads background, never Forge red
                long window = (long) field("window").get(this);
                if (window != 0) GLFW.glfwSetWindowTitle(window, "The Lads Client");
                // Same single render thread, queued after Forge's initRender (scheduled first): the element list exists by then.
                ((ScheduledExecutorService) field("renderScheduler").get(this)).schedule(this::takeOver, 5, TimeUnit.MILLISECONDS);
            } catch (ReflectiveOperationException | RuntimeException failure) {
                LOG.warn("Forge's early window internals are not as expected; its own picture is kept", failure);
            }
        }
        return tick;
    }

    /**
     * Forge's version calls getClass().getModule().addReads(forge), which only the module itself may do: from a subclass in this
     * module it throws IllegalCallerException. Same steps, done by this module for itself; Forge's loadingOverlay() then works as is.
     */
    @Override
    public void updateModuleReads(ModuleLayer layer) {
        Module forge = layer.findModule("forge").orElseThrow();
        LadsEarlyWindow.class.getModule().addReads(forge);
        try {
            Class<?> overlay = Class.forName(forge, "net.minecraftforge.client.loading.ForgeLoadingOverlay");
            Method newInstance = Arrays.stream(overlay.getMethods())
                .filter(m -> Modifier.isStatic(m.getModifiers()) && m.getName().equals("newInstance")).findFirst().orElseThrow();
            field("loadingOverlay").set(this, newInstance);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Forge's loading overlay hand-over is not as expected", failure);
        }
    }

    private void takeOver() {
        try {
            Field elements = field("elements");
            if (elements.get(this) == null) throw new IllegalStateException("Forge's early window has no element list (renderer failed to start)");
            elements.set(this, new Scene(new Painter(this, guiScaleOption())));
            LOG.info("Lads early loading screen active");
        } catch (ReflectiveOperationException | RuntimeException failure) {
            LOG.warn("Could not take over Forge's early window picture; its own is kept", failure);
        }
    }

    private static Field field(String name) throws NoSuchFieldException {
        Field field = DisplayWindow.class.getDeclaredField(name); // fmlearlydisplay is an automatic module: open to reflection
        field.setAccessible(true);
        return field;
    }

    /** Windows: no "(Not Responding)" ghost replaces the window while the main thread is busy. Process-wide, harmless elsewhere. */
    static void disableGhosting() {
        if (Platform.get() != Platform.WINDOWS || Boolean.getBoolean("lads.earlywindow.keepGhosting")) return;
        try (WindowsLibrary user32 = new WindowsLibrary("user32")) {
            long function = user32.getFunctionAddress("DisableProcessWindowsGhosting");
            if (function != 0) JNI.callV(function);
            LOG.info("Window ghosting disabled (no 'Not Responding' overlay while Forge loads)");
        } catch (Throwable failure) {
            LOG.warn("Could not disable window ghosting", failure);
        }
    }

    /** options.txt guiScale (0 = auto), so the early screen has the size the Core's overlay will draw at. */
    private static int guiScaleOption() {
        try {
            for (String line : Files.readAllLines(FMLPaths.GAMEDIR.get().resolve("options.txt")))
                if (line.startsWith("guiScale:")) return Integer.parseInt(line.substring(9).trim());
        } catch (Exception ignored) {}
        return 0;
    }

    /** Forge's element list: its per-frame removeIf(render) becomes one Lads frame. Forge's own elements are never drawn. */
    private static final class Scene extends ArrayList<RenderElement> {
        private final transient Painter painter;

        Scene(Painter painter) {
            this.painter = painter;
        }

        @Override
        public boolean removeIf(Predicate<? super RenderElement> filter) {
            painter.paint();
            return false;
        }
    }

    /** LadsGraphics on Forge's early-display GL helpers: coloured quads batched in one buffer, the wordmark as a textured quad. */
    private static final class Painter implements LadsGraphics {
        private final DisplayWindow window;
        private final int guiScaleOption;
        private final ArrayDeque<float[]> poses = new ArrayDeque<>();
        private float tx, ty, s;
        private SimpleBufferBuilder buffer;
        private ElementShader shader;
        private boolean building;
        private int logo = -1;
        private boolean failed;

        Painter(DisplayWindow window, int guiScaleOption) {
            this.window = window;
            this.guiScaleOption = guiScaleOption;
        }

        void paint() {
            if (failed) return;
            try {
                var context = window.context();
                if (buffer == null) buffer = new SimpleBufferBuilder(1 << 20);
                shader = context.elementShader();
                int width = context.scaledWidth(), height = context.scaledHeight();
                int gui = guiScale(width, height);
                tx = 0;
                ty = 0;
                s = gui;
                poses.clear();
                LoadingScreenTheme.render(this, width / gui, height / gui, 0f, 1f, 1f, this::logo);
                flush();
            } catch (Throwable failure) {
                failed = true; // a blank frame is better than a crash loop in the render thread
                LOG.error("Lads early loading screen failed; drawing stops", failure);
            }
        }

        /** Minecraft's automatic GUI scale (largest that keeps 320x240), capped by options.txt like the game does. */
        private int guiScale(int width, int height) {
            int scale = 1;
            while ((guiScaleOption == 0 || scale < guiScaleOption) && width / (scale + 1) >= 320 && height / (scale + 1) >= 240) scale++;
            return scale;
        }

        @Override
        public void fill(int minX, int minY, int maxX, int maxY, int color) {
            if ((color >>> 24) == 0) return;
            if (!building) {
                buffer.begin(SimpleBufferBuilder.Format.POS_TEX_COLOR, SimpleBufferBuilder.Mode.QUADS);
                building = true;
            }
            float r = (color >> 16 & 255) / 255f, g = (color >> 8 & 255) / 255f, b = (color & 255) / 255f, a = (color >>> 24) / 255f;
            float x0 = tx + minX * s, x1 = tx + maxX * s, y0 = ty + minY * s, y1 = ty + maxY * s;
            buffer.pos(x0, y0).tex(0, 0).colour(r, g, b, a).endVertex();
            buffer.pos(x1, y0).tex(0, 0).colour(r, g, b, a).endVertex();
            buffer.pos(x0, y1).tex(0, 0).colour(r, g, b, a).endVertex();
            buffer.pos(x1, y1).tex(0, 0).colour(r, g, b, a).endVertex();
        }

        private void flush() {
            if (!building) return;
            building = false;
            shader.updateRenderTypeUniform(ElementShader.RenderType.BAR);
            buffer.draw();
        }

        private void logo(int x, int y, int width, int height, float alpha) {
            flush(); // the glow rows under the wordmark first
            if (logo < 0) logo = loadLogo();
            if (logo == 0) return;
            int previousUnit = glGetInteger(GL_ACTIVE_TEXTURE);
            glActiveTexture(GL_TEXTURE0 + LOGO_UNIT);
            glBindTexture(GL_TEXTURE_2D, logo);
            shader.updateTextureUniform(LOGO_UNIT);
            shader.updateRenderTypeUniform(ElementShader.RenderType.TEXTURE);
            float x0 = tx + x * s, x1 = tx + (x + width) * s, y0 = ty + y * s, y1 = ty + (y + height) * s;
            buffer.begin(SimpleBufferBuilder.Format.POS_TEX_COLOR, SimpleBufferBuilder.Mode.QUADS);
            buffer.pos(x0, y0).tex(0, 0).colour(1, 1, 1, alpha).endVertex();
            buffer.pos(x1, y0).tex(1, 0).colour(1, 1, 1, alpha).endVertex();
            buffer.pos(x0, y1).tex(0, 1).colour(1, 1, 1, alpha).endVertex();
            buffer.pos(x1, y1).tex(1, 1).colour(1, 1, 1, alpha).endVertex();
            buffer.draw();
            glActiveTexture(previousUnit);
        }

        /** The Core's wordmark PNG (copied into this jar by build.gradle), uploaded once; 0 when unavailable. */
        private static int loadLogo() {
            try (InputStream in = LadsEarlyWindow.class.getResourceAsStream("/lads_loading_logo.png")) {
                if (in == null) throw new IllegalStateException("lads_loading_logo.png is missing from the jar");
                byte[] bytes = in.readAllBytes();
                ByteBuffer file = BufferUtils.createByteBuffer(bytes.length).put(bytes).flip();
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    IntBuffer w = stack.mallocInt(1), h = stack.mallocInt(1), channels = stack.mallocInt(1);
                    ByteBuffer pixels = STBImage.stbi_load_from_memory(file, w, h, channels, 4);
                    if (pixels == null) throw new IllegalStateException(STBImage.stbi_failure_reason());
                    int previousUnit = glGetInteger(GL_ACTIVE_TEXTURE);
                    glActiveTexture(GL_TEXTURE0 + LOGO_UNIT);
                    int texture = glGenTextures();
                    glBindTexture(GL_TEXTURE_2D, texture);
                    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
                    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
                    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
                    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
                    glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
                    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w.get(0), h.get(0), 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
                    STBImage.stbi_image_free(pixels);
                    glActiveTexture(previousUnit);
                    return texture;
                }
            } catch (Exception failure) {
                LOG.warn("Lads early loading screen: no wordmark", failure);
                return 0;
            }
        }

        @Override
        public void pushPose() {
            poses.push(new float[] {tx, ty, s});
        }

        @Override
        public void popPose() {
            float[] pose = poses.pop();
            tx = pose[0];
            ty = pose[1];
            s = pose[2];
        }

        @Override
        public void translate(float x, float y) {
            tx += x * s;
            ty += y * s;
        }

        @Override
        public void scale(float sx, float sy) {
            s *= sx; // LoadingScreenTheme only scales uniformly
        }
    }
}
