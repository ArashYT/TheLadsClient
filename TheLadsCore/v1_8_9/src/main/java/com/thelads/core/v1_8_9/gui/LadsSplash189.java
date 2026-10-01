package com.thelads.core.v1_8_9.gui;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.title.LoadingScreenTheme;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Iterator;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.client.SplashProgress;
import net.minecraftforge.fml.common.ProgressManager;
import org.apache.logging.log4j.LogManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/**
 * The Lads loading screen on Forge's splash thread (SplashProgressMixin). That thread has its own GL context while the main
 * thread loads mods, so this draws with raw GL only: Minecraft's GlStateManager and Tessellator belong to the main thread.
 */
public final class LadsSplash189 implements LadsGraphics {
    private static final boolean CAPTURE = Boolean.getBoolean("thelads.verifyLoadingScreen");
    private static int logo = -1;
    private static long captureStart;
    private static int captured;
    private final int width, height;

    private LadsSplash189(int width, int height) { this.width = width; this.height = height; }

    /** One frame over Forge's, just before it is shown. GUI units of about 1/360 of the window height. */
    public static void frame() {
        int w = Display.getWidth(), h = Display.getHeight();
        float scale = Math.max(1, h / 360f);
        GL11.glViewport(0, 0, w, h);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        GL11.glOrtho(0, w / scale, h / scale, 0, -1, 1);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        LoadingScreenTheme.render(new LadsSplash189((int)(w / scale), (int)(h / scale)), (int)(w / scale), (int)(h / scale),
            progress(), 1, 1, LadsSplash189::drawLogo);
        GL11.glColor4f(1, 1, 1, 1);
        if (CAPTURE) capture(w, h);
    }

    /** Forge's bars: the outer "Loading" steps, plus the current step's own progress. */
    private static float progress() {
        Iterator<ProgressManager.ProgressBar> bars = ProgressManager.barIterator();
        if (!bars.hasNext()) return 0;
        ProgressManager.ProgressBar outer = bars.next();
        float inner = 0;
        if (bars.hasNext()) {
            ProgressManager.ProgressBar next = bars.next();
            inner = next.getSteps() == 0 ? 0 : (float) next.getStep() / next.getSteps();
        }
        return outer.getSteps() == 0 ? 0 : Math.min(1, (outer.getStep() + inner) / outer.getSteps());
    }

    private static void drawLogo(int x, int y, int w, int h, float alpha) {
        if (logo == -1) logo = upload();
        if (logo == 0) return;
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, logo);
        GL11.glColor4f(1, 1, 1, alpha);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0, 0); GL11.glVertex2f(x, y);
        GL11.glTexCoord2f(0, 1); GL11.glVertex2f(x, y + h);
        GL11.glTexCoord2f(1, 1); GL11.glVertex2f(x + w, y + h);
        GL11.glTexCoord2f(1, 0); GL11.glVertex2f(x + w, y);
        GL11.glEnd();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
    }

    private static int upload() {
        try (InputStream in = LoadingScreenTheme.class.getResourceAsStream(LoadingScreenTheme.LOGO)) {
            BufferedImage image = ImageIO.read(in);
            int w = image.getWidth(), h = image.getHeight();
            IntBuffer pixels = BufferUtils.createIntBuffer(w * h);
            pixels.put(image.getRGB(0, 0, w, h, null, 0, w)).flip();
            int name;
            synchronized (SplashProgress.class) { name = GL11.glGenTextures(); } // as Forge: the main context shares names
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, name);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, w, h, 0, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, pixels);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            return name;
        } catch (Exception failure) {
            LogManager.getLogger("TheLadsCore").error("Lads loading screen logo unavailable", failure);
            return 0;
        }
    }

    /** The splash thread ends: free the logo while its context is still current. */
    public static void finish() {
        if (logo > 0) GL11.glDeleteTextures(logo);
        logo = -1;
    }

    /** QA only (-Dthelads.verifyLoadingScreen): two frames of the splash, 0.3 s apart, from the back buffer. */
    private static void capture(int w, int h) {
        long now = System.nanoTime();
        if (captureStart == 0) captureStart = now;
        if (captured >= 2 || now - captureStart < 400_000_000L + captured * 300_000_000L) return;
        captured++;
        try {
            ByteBuffer rgba = BufferUtils.createByteBuffer(w * h * 4);
            GL11.glReadPixels(0, 0, w, h, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, rgba);
            BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                int i = ((h - 1 - y) * w + x) * 4;
                image.setRGB(x, y, (rgba.get(i) & 255) << 16 | (rgba.get(i + 1) & 255) << 8 | rgba.get(i + 2) & 255);
            }
            File folder = new File(Minecraft.getMinecraft().mcDataDir, "screenshots");
            if (!folder.isDirectory() && !folder.mkdirs()) throw new IllegalStateException("Cannot create " + folder);
            File output = new File(folder, "lads-loading-" + captured + ".png");
            ImageIO.write(image, "png", output);
            LogManager.getLogger("TheLadsCore").info("Lads loading screen capture {}: {}", captured, output);
        } catch (Exception failure) {
            LogManager.getLogger("TheLadsCore").error("Lads loading screen capture failed", failure);
        }
    }

    @Override
    public void fill(int minX, int minY, int maxX, int maxY, int color) {
        if (minX >= maxX || minY >= maxY) return;
        GL11.glColor4ub((byte)(color >> 16), (byte)(color >> 8), (byte)color, (byte)(color >>> 24));
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(minX, minY);
        GL11.glVertex2f(minX, maxY);
        GL11.glVertex2f(maxX, maxY);
        GL11.glVertex2f(maxX, minY);
        GL11.glEnd();
    }

    @Override public void pushPose() { GL11.glPushMatrix(); }
    @Override public void popPose() { GL11.glPopMatrix(); }
    @Override public void translate(float x, float y) { GL11.glTranslatef(x, y, 0); }
    @Override public void scale(float sx, float sy) { GL11.glScalef(sx, sy, 1); }
    @Override public int getScaledWidth() { return width; }
    @Override public int getScaledHeight() { return height; }
    // The loading screen draws no text, textures through LadsGraphics, heads or scissored areas.
    @Override public void drawText(String text, int x, int y, int color, boolean shadow) {}
    @Override public void drawCenteredText(String text, int centerX, int y, int color, boolean shadow) {}
    @Override public int textWidth(String text) { return 0; }
    @Override public int fontHeight() { return 9; }
    @Override public void enableScissor(int minX, int minY, int maxX, int maxY) {}
    @Override public void disableScissor() {}
    @Override public void blit(String texture, int x, int y, int u, int v, int width, int height) {}
    @Override public void drawHead(String username, String uuid, int x, int y, int size) {}
}
