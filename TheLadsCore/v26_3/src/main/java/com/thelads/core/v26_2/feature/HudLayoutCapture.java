package com.thelads.core.v26_2.feature;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.thelads.core.client.gui.DraggableHudScreen;
import com.thelads.core.client.hud.HudCoverQa;
import com.thelads.core.client.hud.HudLayoutQa;
import com.thelads.core.client.hud.HudTextQa;
import com.thelads.core.v26_2.gui.DraggableHudScreen26;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import org.lwjgl.sdl.SDLVideo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only (auto-world, ".lads-qa-capture-hudlayout" from the harness's LADS_VERIFY_CAPTURE_HUDLAYOUT): the 1.9.0 HUD layout
 * sequence (HudLayoutQa) in the QA world; its frames go to screenshots/hud-*.png.
 */
final class HudLayoutCapture implements HudLayoutQa.Driver {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static final HudLayoutCapture DRIVER = new HudLayoutCapture();
    private static boolean started, done;
    private static String pending;
    /** The pending shot is written off the game thread. */
    private static boolean quick;
    private static Path game;
    private DraggableHudScreen editor;
    private int windowWidth, windowHeight;

    private HudLayoutCapture() {}

    static boolean busy() { return started && !done; }

    /** Each client tick of the auto-world run: starts once the world is ready and the request exists, then steps the sequence (the HUD editor open too). */
    static void tick(Path gameDirectory, boolean ready) {
        if (done) return;
        if (!started) {
            if (!ready) return;
            Path request = gameDirectory.resolve(".lads-qa-capture-hudlayout");
            if (!Files.isRegularFile(request, LinkOption.NOFOLLOW_LINKS)) return;
            try { Files.delete(request); } catch (Exception failure) { LOGGER.error("Lads hud layout FAILED: request", failure); done = true; return; }
            started = true;
            game = gameDirectory;
        }
        // LADS_QA_HUDCOVER=1: the 1.9.0 HUD cover sequence instead, with the same markers.
        // LADS_QA_HUDTEXT=1: the 1.9.1 HUD text sequence (HudTextQa).
        // LADS_QA_F3_1911=1: the 1.9.11 Better F3 sequence (BetterF3Qa).
        // LADS_QA_BGSHADOW=1: the 1.9.11 Backgrounds / Text Shadow buttons (BgShadowQa).
        done = com.thelads.core.client.hud.BgShadowQa.wanted() ? com.thelads.core.client.hud.BgShadowQa.tick(DRIVER)
            : com.thelads.core.client.hud.BetterF3Qa.wanted() ? com.thelads.core.client.hud.BetterF3Qa.tick(DRIVER)
            : HudTextQa.wanted() ? HudTextQa.tick(DRIVER) : HudCoverQa.wanted() ? HudCoverQa.tick(DRIVER) : HudLayoutQa.tick(DRIVER);
    }

    /** Each completed game frame (NativeWorldVerification.renderedFrame): writes a requested shot. */
    static void frame(RenderTarget target, Path gameDirectory) {
        if (pending == null) return;
        String name = pending;
        pending = null;
        try {
            Path folder = gameDirectory.resolve("screenshots");
            Files.createDirectories(folder);
            if (!folder.toRealPath().startsWith(gameDirectory)) throw new java.io.IOException("QA screenshot folder is outside the isolated game directory");
            Path output = folder.resolve(name + ".png");
            boolean later = quick;
            quick = false;
            net.minecraft.client.Screenshot.takeScreenshot(target, image -> {
                Runnable write = () -> {
                    try { image.writeToFile(output); LOGGER.info("Lads hud layout saved {}", output); }
                    catch (Exception failure) { LOGGER.error("Lads hud layout frame {} not saved", name, failure); }
                    finally { image.close(); }
                };
                if (later) java.util.concurrent.CompletableFuture.runAsync(write); else write.run();
            });
        } catch (Exception failure) {
            LOGGER.error("Lads hud layout frame {} not saved", name, failure);
        }
    }

    @Override public void window(int width, int height) {
        var window = Minecraft.getInstance().getWindow();
        if (width > 0 && windowWidth == 0) { windowWidth = window.getWidth(); windowHeight = window.getHeight(); }
        if (width == 0) { width = windowWidth; height = windowHeight; }
        if (width > 0) SDLVideo.SDL_SetWindowSize(window.handle(), width, height);
    }

    @Override public void guiScale(int scale) {
        Minecraft mc = Minecraft.getInstance();
        mc.options.guiScale().set(scale);
        mc.resizeGui();
    }

    @Override public void command(String command) {
        var server = Minecraft.getInstance().getSingleplayerServer();
        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command));
    }

    @Override public void effect(String name, int id) { command(name == null ? "effect clear @a" : "effect give @a minecraft:" + name + " 300 0 true"); }

    @Override public void shot(String name) { pending = name; }

    @Override public void quickShot(String name) { pending = name; quick = true; }

    @Override public void openEditor() {
        editor = new DraggableHudScreen();
        Minecraft.getInstance().setScreenAndShow(new DraggableHudScreen26(null, editor));
    }

    @Override public DraggableHudScreen editor() { return Minecraft.getInstance().gui.screen() instanceof DraggableHudScreen26 ? editor : null; }

    @Override public void closeScreen() { if (Minecraft.getInstance().gui.screen() != null) Minecraft.getInstance().setScreenAndShow(null); }

    @Override public void openSettings() { Minecraft.getInstance().setScreenAndShow(new com.thelads.core.v26_2.gui.LadsSettingsScreen26(null)); }

    @Override public com.thelads.core.client.gui.LadsSettingsScreen settings() {
        return Minecraft.getInstance().gui.screen() instanceof com.thelads.core.v26_2.gui.LadsSettingsScreen26 screen ? screen.ui() : null;
    }

    @Override public void tab(boolean down) { Minecraft.getInstance().options.keyPlayerList.setDown(down); }

    @Override public void debug(boolean open) { Minecraft.getInstance().debugEntries.setOverlayVisible(open); }

    @Override public void chat(boolean open) { Minecraft.getInstance().setScreenAndShow(open ? new net.minecraft.client.gui.screens.ChatScreen("", false) : null); }
}
