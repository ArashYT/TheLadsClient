package com.thelads.core.v1_20_1.feature;

import com.thelads.core.client.gui.DraggableHudScreen;
import com.thelads.core.client.hud.HudLayoutQa;
import com.thelads.core.v1_20_1.gui.DraggableHudScreen1201;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/** QA script step "hudlayout": the 1.9.0 HUD layout sequence (HudLayoutQa) on 1.20.1 and in Zero SMP. */
final class HudLayoutQa1201 implements HudLayoutQa.Driver {
    private static final HudLayoutQa1201 DRIVER = new HudLayoutQa1201();
    private int windowWidth, windowHeight;

    private HudLayoutQa1201() {}

    static void register() {
        if (Boolean.getBoolean("thelads.verifyAutoWorld") || System.getProperty("thelads.qaScript") != null)
            QaProbe1201.registerStep("hudlayout", args -> {
                if (!HudLayoutQa.tick(DRIVER)) return false;
                if (HudLayoutQa.failed() > 0) throw new IllegalStateException(HudLayoutQa.failed() + " HUD layout checks failed (Lads hud layout FAILED)");
                return true;
            });
    }

    @Override public void window(int width, int height) {
        var window = Minecraft.getInstance().getWindow();
        if (width > 0 && windowWidth == 0) { windowWidth = window.getWidth(); windowHeight = window.getHeight(); }
        if (width == 0) { width = windowWidth; height = windowHeight; }
        if (width > 0) GLFW.glfwSetWindowSize(window.getWindow(), width, height);
    }

    @Override public void guiScale(int scale) {
        Minecraft mc = Minecraft.getInstance();
        mc.options.guiScale().set(scale);
        mc.resizeDisplay();
    }

    @Override public void command(String command) { Minecraft.getInstance().player.connection.sendCommand(command); }

    @Override public void effect(String name, int id) { command(name == null ? "effect clear @s" : "effect give @s minecraft:" + name + " 300 0 true"); }

    @Override public void shot(String name) {
        try { QaProbe1201.shot(Minecraft.getInstance(), name); }
        catch (java.io.IOException failure) { throw new IllegalStateException("shot " + name, failure); }
    }

    @Override public void openEditor() { Minecraft.getInstance().setScreen(new DraggableHudScreen1201(null)); }

    @Override public DraggableHudScreen editor() { return Minecraft.getInstance().screen instanceof DraggableHudScreen1201 screen ? screen.ui() : null; }

    @Override public void closeScreen() { Minecraft.getInstance().setScreen(null); }
}
