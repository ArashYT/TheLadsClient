package com.thelads.core.v1_8_9.feature;

import com.thelads.core.client.gui.DraggableHudScreen;
import com.thelads.core.client.hud.HudLayoutQa;
import com.thelads.core.v1_8_9.gui.DraggableHudScreen189;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;

/**
 * QA only (-Dthelads.verify189Only=hudlayout): the 1.9.0 HUD layout sequence (HudLayoutQa) in the 1.8.9 QA world. Frames go to
 * lads-qa/screenshots; a failed check fails the self-test.
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

    @Override public void effect(String name, int id) { command(name == null ? "effect @p clear" : "effect @p " + id + " 300 0 true"); }

    @Override public void shot(String name) { CoreProbe.screenshot(Minecraft.getMinecraft(), name); }

    @Override public void openEditor() { Minecraft.getMinecraft().displayGuiScreen(new DraggableHudScreen189(null)); }

    @Override public DraggableHudScreen editor() {
        return Minecraft.getMinecraft().currentScreen instanceof DraggableHudScreen189 ? ((DraggableHudScreen189) Minecraft.getMinecraft().currentScreen).ui() : null;
    }

    @Override public void closeScreen() { Minecraft.getMinecraft().displayGuiScreen(null); }
}
