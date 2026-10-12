package com.thelads.core.v26_2.feature;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.thelads.core.client.hud.ActionBarQa;
import com.thelads.core.v26_2.gui.DraggableHudScreen26;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only (auto-world, ".lads-qa-capture-actionbar" from the harness's LADS_VERIFY_CAPTURE_ACTIONBAR): the common Action Bar routine
 * (ActionBarQa) with 26.3's action bar packet, record, /title and screenshots (screenshots/actionbar/&lt;name&gt;.png).
 */
final class ActionBarCapture {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static ActionBarQa qa;
    private static boolean running, done;
    private static String pendingShot;

    private ActionBarCapture() {}

    static boolean busy() { return running; }

    static void tick(Path game, boolean ready) {
        if (done) return;
        if (!running) {
            Path request = game.resolve(".lads-qa-capture-actionbar");
            // Not beside the screenshots gallery probe, whose screen would cover the frames.
            if (!ready || !Files.isRegularFile(request, LinkOption.NOFOLLOW_LINKS)
                || com.thelads.core.v26_2.feature.screenshots.screen.manage_screenshots.NativeScreenshotsProbe.pending()) return;
            try { Files.delete(request); } catch (Exception ignored) {}
            qa = new ActionBarQa();
            running = true;
        }
        if (pendingShot != null) return; // the next frame takes it
        try {
            if (qa.tick(GAME)) { running = false; done = true; }
        } catch (Exception | Error failure) {
            LOGGER.error("Lads action bar capture FAILED: {}", failure.toString(), failure);
            running = false;
            done = true;
        }
    }

    /** Each completed frame (NativeWorldVerification.renderedFrame): a requested screenshot. */
    static void frame(RenderTarget frame, Path game) {
        String name = pendingShot;
        if (name == null) return;
        try {
            Path output = game.resolve("screenshots").resolve("actionbar").resolve(name + ".png");
            Files.createDirectories(output.getParent());
            net.minecraft.client.Screenshot.takeScreenshot(frame, image -> {
                try { image.writeToFile(output); LOGGER.info("Lads action bar QA frame {}", output); }
                catch (Exception failure) { LOGGER.warn("Lads action bar QA frame {} not saved", name, failure); }
                finally { image.close(); }
            });
        } catch (Exception failure) {
            LOGGER.warn("Lads action bar QA frame {} not saved", name, failure);
        }
        pendingShot = null;
    }

    private static final ActionBarQa.Game GAME = new ActionBarQa.Game() {
        @Override public void actionBar(String legacy, String json) {
            Component text = json == null ? Component.literal(legacy) : net.minecraft.network.chat.ComponentSerialization.CODEC
                .parse(com.mojang.serialization.JsonOps.INSTANCE, com.google.gson.JsonParser.parseString(json)).getOrThrow();
            Minecraft.getInstance().getConnection().setActionBarText(new ClientboundSetActionBarTextPacket(text));
        }

        @Override public void nowPlaying(String song) { Minecraft.getInstance().gui.hud.setNowPlaying(Component.literal(song)); }

        @Override public boolean titleActionBar(String json) {
            Minecraft.getInstance().getConnection().sendCommand("title @s actionbar " + json);
            return true;
        }

        @Override public void hudEditor(boolean open) { Minecraft.getInstance().setScreenAndShow(open ? new DraggableHudScreen26(null) : null); }

        @Override public void screenshot(String name) { pendingShot = name; }
    };
}
