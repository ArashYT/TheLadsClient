package com.thelads.core.v26_2.feature;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.thelads.core.client.hypixel.HypixelQa;
import com.thelads.core.client.hypixel.RealPing;
import com.thelads.core.v26_2.gui.LadsSettingsScreen26;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only (auto-world, ".lads-qa-capture-hypixel" from the harness's LADS_VERIFY_CAPTURE_HYPIXEL): the common Hypixel routine
 * (HypixelQa) with 26.3's commands, tab key, chat and settings screens and screenshots (screenshots/hypixel/&lt;name&gt;.png). The
 * integrated server answers the Mod API ping (NativeHypixel registers that answer for this run).
 */
final class HypixelCapture {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static HypixelQa qa;
    private static boolean running, done;
    private static String pendingShot;

    private HypixelCapture() {}

    static boolean busy() { return running; }

    static void tick(Path game, boolean ready) {
        if (done) return;
        if (!running) {
            Path request = game.resolve(".lads-qa-capture-hypixel");
            if (!ready || !Files.isRegularFile(request, LinkOption.NOFOLLOW_LINKS)) return;
            try { Files.delete(request); } catch (Exception ignored) {}
            qa = new HypixelQa();
            running = true;
        }
        if (pendingShot != null) return; // the next frame takes it
        try {
            if (qa.tick(GAME)) { running = false; done = true; }
        } catch (Exception | Error failure) {
            LOGGER.error("Lads hypixel capture FAILED: {}", failure.toString(), failure);
            running = false;
            done = true;
        }
    }

    /** Each completed frame (NativeWorldVerification.renderedFrame): a requested screenshot. */
    static void frame(RenderTarget frame, Path game) {
        String name = pendingShot;
        if (name == null) return;
        try {
            Path output = game.resolve("screenshots").resolve("hypixel").resolve(name + ".png");
            Files.createDirectories(output.getParent());
            net.minecraft.client.Screenshot.takeScreenshot(frame, image -> {
                try { image.writeToFile(output); LOGGER.info("Lads hypixel QA frame {}", output); }
                catch (Exception failure) { LOGGER.warn("Lads hypixel QA frame {} not saved", name, failure); }
                finally { image.close(); }
            });
        } catch (Exception failure) {
            LOGGER.warn("Lads hypixel QA frame {} not saved", name, failure);
        }
        pendingShot = null;
    }

    private static final HypixelQa.Game GAME = new HypixelQa.Game() {
        @Override public void command(String command) { Minecraft.getInstance().getConnection().sendCommand(command); }
        @Override public boolean serverAnswersPings() { return NativeHypixel.answersQa(); }
        @Override public void pong(byte[] payload) { RealPing.receive(RealPing.CHANNEL, payload, System.nanoTime()); }
        @Override public void holdTab(boolean down) { Minecraft.getInstance().options.keyPlayerList.setDown(down); }
        @Override public void openChat() { Minecraft.getInstance().setScreenAndShow(new ChatScreen("", false)); }
        @Override public com.thelads.core.client.gui.LadsSettingsScreen openSettings(String module) {
            LadsSettingsScreen26 menu = new LadsSettingsScreen26(null);
            Minecraft.getInstance().setScreenAndShow(menu);
            if (module != null) menu.openModule(module);
            return menu.ui();
        }
        @Override public void closeScreen() { Minecraft.getInstance().setScreenAndShow(null); }
        @Override public void screenshot(String name) { pendingShot = name; }
    };
}
