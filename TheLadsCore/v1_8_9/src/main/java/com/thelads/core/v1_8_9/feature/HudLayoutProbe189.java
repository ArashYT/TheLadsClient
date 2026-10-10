package com.thelads.core.v1_8_9.feature;

import com.thelads.core.client.gui.DraggableHudScreen;
import com.thelads.core.client.hud.HudLayoutQa;
import com.thelads.core.v1_8_9.gui.DraggableHudScreen189;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.IntBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.server.MinecraftServer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/**
 * QA only (-Dthelads.verify189Only=hudlayout): the 1.9.0 HUD layout sequence (HudLayoutQa) in the 1.8.9 QA world. Frames go to
 * lads-qa/screenshots, read straight from the framebuffer (ScreenShotHelper's toasts would cover the top right); a failed check
 * fails the self-test. Run it with LADS_VERIFY_NO_RESIZE=1: the window is made 1600 x 900 for GUI scale 3, and the QA tile guard
 * would size the recreated window back.
 */
final class HudLayoutProbe189 implements HudLayoutQa.Driver {
    private static final HudLayoutProbe189 DRIVER = new HudLayoutProbe189();
    private int windowWidth, windowHeight;

    private HudLayoutProbe189() {}

    static boolean run(Minecraft mc) {
        if (!HudLayoutQa.tick(DRIVER)) return false;
        CoreProbe.check(HudLayoutQa.failed() == 0, "every HUD layout check passed (Lads hud layout END)");
        return true;
    }

    @Override public void window(int width, int height) {
        Minecraft mc = Minecraft.getMinecraft();
        if (width > 0 && windowWidth == 0) { windowWidth = Display.getWidth(); windowHeight = Display.getHeight(); }
        if (mc.guiAchievement != null) mc.guiAchievement.clearAchievements(); // "Press 'E' to open your inventory" over the top right
        if (width == 0) { width = windowWidth; height = windowHeight; }
        if (width <= 0) return;
        try {
            Display.setDisplayMode(new DisplayMode(width, height));
            mc.resize(Display.getWidth(), Display.getHeight());
        } catch (Exception failure) {
            throw new IllegalStateException("window " + width + "x" + height, failure);
        }
    }

    @Override public void guiScale(int scale) { Minecraft.getMinecraft().gameSettings.guiScale = scale; }

    @Override public void command(final String command) {
        final MinecraftServer server = Minecraft.getMinecraft().getIntegratedServer();
        server.addScheduledTask(() -> server.getCommandManager().executeCommand(server, command));
    }

    @Override public void effect(String name, int id) { command(name == null ? "effect @a clear" : "effect @a " + id + " 300 0 true"); }

    /** The framebuffer as ScreenShotHelper reads it, without the helper. */
    @Override public void shot(String name) {
        Minecraft mc = Minecraft.getMinecraft();
        Framebuffer buffer = mc.getFramebuffer();
        int w = buffer.framebufferTextureWidth, h = buffer.framebufferTextureHeight;
        IntBuffer pixels = BufferUtils.createIntBuffer(w * h);
        int[] values = new int[w * h];
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
        GlStateManager.bindTexture(buffer.framebufferTexture);
        GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, pixels);
        pixels.get(values);
        TextureUtil.processPixelValues(values, w, h);
        BufferedImage image = new BufferedImage(buffer.framebufferWidth, buffer.framebufferHeight, BufferedImage.TYPE_INT_RGB);
        int skip = h - buffer.framebufferHeight;
        for (int y = skip; y < h; y++) for (int x = 0; x < buffer.framebufferWidth; x++) image.setRGB(x, y - skip, values[y * w + x]);
        File folder = new File(mc.mcDataDir, "lads-qa/screenshots");
        folder.mkdirs();
        try { javax.imageio.ImageIO.write(image, "png", new File(folder, name + ".png")); }
        catch (java.io.IOException failure) { throw new IllegalStateException("shot " + name, failure); }
    }

    @Override public void openEditor() { Minecraft.getMinecraft().displayGuiScreen(new DraggableHudScreen189(null)); }

    @Override public DraggableHudScreen editor() {
        return Minecraft.getMinecraft().currentScreen instanceof DraggableHudScreen189 ? ((DraggableHudScreen189) Minecraft.getMinecraft().currentScreen).ui() : null;
    }

    @Override public void closeScreen() { Minecraft.getMinecraft().displayGuiScreen(null); }
}
