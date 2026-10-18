package com.thelads.core.v1_8_9.feature;

import com.thelads.core.client.gui.DraggableHudScreen;
import com.thelads.core.client.hud.HudCoverQa;
import com.thelads.core.client.hud.HudLayoutQa;
import com.thelads.core.client.hud.HudTextQa;
import com.thelads.core.v1_8_9.gui.DraggableHudScreen189;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.IntBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
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
        // LADS_QA_BGSHADOW=1: the 1.9.11 Backgrounds / Text Shadow buttons instead (BgShadowQa).
        if (com.thelads.core.client.hud.BgShadowQa.wanted()) {
            if (!com.thelads.core.client.hud.BgShadowQa.tick(DRIVER)) return false;
            CoreProbe.check(com.thelads.core.client.hud.BgShadowQa.failed() == 0, "every Backgrounds/Text Shadow check passed (Lads hud layout END)");
            return true;
        }
        // LADS_QA_F3_1911=1: the 1.9.11 Better F3 sequence instead (BetterF3Qa).
        if (com.thelads.core.client.hud.BetterF3Qa.wanted()) {
            if (!com.thelads.core.client.hud.BetterF3Qa.tick(DRIVER)) return false;
            CoreProbe.check(com.thelads.core.client.hud.BetterF3Qa.failed() == 0, "every Better F3 check passed (Lads hud layout END)");
            return true;
        }
        // LADS_QA_HUDTEXT=1: the 1.9.1 HUD text sequence instead (HudTextQa).
        if (HudTextQa.wanted()) {
            if (!HudTextQa.tick(DRIVER)) return false;
            CoreProbe.check(HudTextQa.failed() == 0, "every HUD text step passed (Lads hud layout END)");
            return true;
        }
        // LADS_QA_HUDCOVER=1: the 1.9.0 HUD cover sequence instead (HudCoverQa).
        if (HudCoverQa.wanted()) {
            if (!HudCoverQa.tick(DRIVER)) return false;
            CoreProbe.check(HudCoverQa.failed() == 0, "every HUD cover check passed (Lads hud layout END)");
            return true;
        }
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
    @Override public void shot(String name) { write(grab(), name, false); }

    @Override public void quickShot(String name) { write(grab(), name, true); }

    private static BufferedImage grab() {
        Minecraft mc = Minecraft.getMinecraft();
        Framebuffer buffer = mc.getFramebuffer();
        int w = buffer.framebufferTextureWidth, h = buffer.framebufferTextureHeight;
        if (w * h == 0) return screen(mc); // OptiFine's Fast Render draws without the framebuffer
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
        return image;
    }

    /** The last frame shown (the window's front buffer), for a game drawing without the framebuffer. */
    private static BufferedImage screen(Minecraft mc) {
        int w = mc.displayWidth, h = mc.displayHeight;
        IntBuffer pixels = BufferUtils.createIntBuffer(w * h);
        int[] values = new int[w * h];
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
        GL11.glReadBuffer(GL11.GL_FRONT);
        GL11.glReadPixels(0, 0, w, h, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, pixels);
        GL11.glReadBuffer(GL11.GL_BACK);
        pixels.get(values);
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) image.setRGB(x, h - 1 - y, values[y * w + x]);
        return image;
    }

    /** Into lads-qa/screenshots; later: off the game thread. */
    private static void write(final BufferedImage image, final String name, boolean later) {
        final File folder = new File(Minecraft.getMinecraft().mcDataDir, "lads-qa/screenshots");
        folder.mkdirs();
        Runnable save = new Runnable() {
            @Override public void run() {
                try { javax.imageio.ImageIO.write(image, "png", new File(folder, name + ".png")); }
                catch (java.io.IOException failure) { throw new IllegalStateException("shot " + name, failure); }
            }
        };
        if (later) java.util.concurrent.CompletableFuture.runAsync(save); else save.run();
    }

    @Override public void openEditor() { Minecraft.getMinecraft().displayGuiScreen(new DraggableHudScreen189(null)); }

    @Override public DraggableHudScreen editor() {
        return Minecraft.getMinecraft().currentScreen instanceof DraggableHudScreen189 ? ((DraggableHudScreen189) Minecraft.getMinecraft().currentScreen).ui() : null;
    }

    @Override public void closeScreen() { Minecraft.getMinecraft().displayGuiScreen(null); }

    @Override public void openSettings() { Minecraft.getMinecraft().displayGuiScreen(new com.thelads.core.v1_8_9.gui.LadsSettingsScreen189(null)); }

    @Override public com.thelads.core.client.gui.LadsSettingsScreen settings() {
        return Minecraft.getMinecraft().currentScreen instanceof com.thelads.core.v1_8_9.gui.LadsSettingsScreen189 ? ((com.thelads.core.v1_8_9.gui.LadsSettingsScreen189) Minecraft.getMinecraft().currentScreen).ui() : null;
    }

    @Override public void tab(boolean down) { KeyBinding.setKeyBindState(Minecraft.getMinecraft().gameSettings.keyBindPlayerList.getKeyCode(), down); }

    @Override public void debug(boolean open) { Minecraft.getMinecraft().gameSettings.showDebugInfo = open; }

    @Override public void chat(boolean open) { Minecraft.getMinecraft().displayGuiScreen(open ? new net.minecraft.client.gui.GuiChat() : null); }
}
