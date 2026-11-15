package com.thelads.core.v1_21_11.feature;

import com.thelads.core.client.gui.DraggableHudScreen;
import com.thelads.core.client.hud.HudLayoutQa;
import com.thelads.core.v1_21_11.gui.DraggableHudScreen12111;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import org.lwjgl.glfw.GLFW;
import org.slf4j.LoggerFactory;

/** QA script step "hudedit": the HUD editor sequence (HudEditorQa) on 1.21.11, which has no HUD layout probe of its own. */
final class HudLayoutQa12111 implements HudLayoutQa.Driver {
    static final HudLayoutQa12111 DRIVER = new HudLayoutQa12111();
    private int windowWidth, windowHeight;

    private HudLayoutQa12111() {}

    @Override public void window(int width, int height) {
        var window = Minecraft.getInstance().getWindow();
        if (width > 0 && windowWidth == 0) { windowWidth = window.getWidth(); windowHeight = window.getHeight(); }
        if (width == 0) { width = windowWidth; height = windowHeight; }
        if (width > 0) GLFW.glfwSetWindowSize(window.handle(), width, height);
    }

    @Override public void guiScale(int scale) {
        Minecraft mc = Minecraft.getInstance();
        mc.options.guiScale().set(scale);
        mc.resizeDisplay();
    }

    @Override public void command(String command) {
        var server = Minecraft.getInstance().getSingleplayerServer();
        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command));
    }

    @Override public void effect(String name, int id) { command(name == null ? "effect clear @a" : "effect give @a minecraft:" + name + " 300 0 true"); }

    @Override public void shot(String name) {
        Path output = FabricLoader.getInstance().getGameDir().resolve("screenshots").resolve(name + ".png");
        try { Files.createDirectories(output.getParent()); } catch (java.io.IOException failure) { throw new IllegalStateException("shot " + name, failure); }
        Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget(), image -> {
            try (image) { image.writeToFile(output); LoggerFactory.getLogger("TheLadsCore").info("Lads hud layout saved {}", output); }
            catch (Exception failure) { LoggerFactory.getLogger("TheLadsCore").error("Lads hud layout frame {} not saved", name, failure); }
        });
    }

    @Override public void openEditor() { Minecraft.getInstance().setScreen(new DraggableHudScreen12111(null)); }

    @Override public DraggableHudScreen editor() { return Minecraft.getInstance().screen instanceof DraggableHudScreen12111 screen ? screen.ui() : null; }

    @Override public boolean scroll(double x, double y, double amount) {
        if (!(Minecraft.getInstance().screen instanceof DraggableHudScreen12111 screen)) return false;
        return screen.mouseScrolled(x, y, 0, amount);
    }

    @Override public void closeScreen() { Minecraft.getInstance().setScreen(null); }

    @Override public void tab(boolean down) { Minecraft.getInstance().options.keyPlayerList.setDown(down); }

    @Override public void debug(boolean open) {}
}
