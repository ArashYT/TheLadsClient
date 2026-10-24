package com.thelads.core.v26_2.feature;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.thelads.core.client.social.P2PQa;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only: the friends + P2P scenario (common P2PQa) on 26.3, started by ".lads-qa-capture-p2p" (harness LADS_VERIFY_P2P=host|guest)
 * once the QA world is ready. It keeps running when the guest leaves its world for the host's.
 */
public final class P2PCapture26 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static final List<String> RECEIVED = new CopyOnWriteArrayList<>();
    private static boolean running, done, registered;
    private static volatile String pendingShot;

    private P2PCapture26() {}

    public static boolean busy() { return running; }

    public static void tick(Path game, boolean ready) {
        if (done || !P2PQa.ACTIVE) return;
        if (!registered) {
            registered = true;
            ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, at) -> RECEIVED.add(message.getString()));
            ClientReceiveMessageEvents.GAME.register((message, overlay) -> { if (!overlay) RECEIVED.add(message.getString()); });
        }
        if (!running) {
            Path request = game.resolve(".lads-qa-capture-p2p");
            if (!ready || !Files.isRegularFile(request, LinkOption.NOFOLLOW_LINKS)) return;
            try { Files.delete(request); } catch (Exception ignored) {}
            running = true;
            LOGGER.info("Lads p2p capture BEGIN (26.3)");
        }
        if (pendingShot != null) return; // the next completed frame takes it
        if (P2PQa.tick(GAME)) {
            running = false;
            done = true;
        }
    }

    /** Each completed frame (NativeWorldVerification.renderedFrame): saves the frame the scenario asked for. */
    public static void frame(RenderTarget frame, Path game) {
        String name = pendingShot;
        if (name == null) return;
        pendingShot = null;
        try {
            Path output = game.resolve("screenshots").resolve(name + ".png");
            Files.createDirectories(output.getParent());
            Screenshot.takeScreenshot(frame, image -> {
                try (image) { image.writeToFile(output); LOGGER.info("Lads p2p QA frame {}", output); }
                catch (Exception failure) { LOGGER.warn("Lads p2p QA frame {} not saved", name, failure); }
            });
        } catch (Exception failure) {
            LOGGER.warn("Lads p2p QA frame {} not saved", name, failure);
        }
    }

    private static final P2PQa.Game GAME = new P2PQa.Game() {
        @Override public boolean inWorld() {
            Minecraft mc = Minecraft.getInstance();
            return mc.level != null && mc.player != null && mc.gui.screen() == null && mc.player.tickCount > 40;
        }

        @Override public int players() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getSingleplayerServer() != null) return mc.getSingleplayerServer().getPlayerCount();
            return mc.getConnection() == null ? 0 : mc.getConnection().getOnlinePlayers().size();
        }

        @Override public void chat(String text) { Minecraft.getInstance().getConnection().sendChat(text); }

        @Override public boolean received(String text) {
            for (String line : RECEIVED) if (line.contains(text)) return true;
            return false;
        }

        @Override public void screenshot(String name) { pendingShot = name; }

        @Override public void offlineServer() {
            var server = Minecraft.getInstance().getSingleplayerServer();
            if (server != null) server.setUsesAuthentication(false);
        }

        @Override public String disconnected() {
            if (!(Minecraft.getInstance().gui.screen() instanceof DisconnectedScreen screen)) return null;
            StringBuilder text = new StringBuilder(screen.getTitle().getString());
            for (var field : DisconnectedScreen.class.getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(screen);
                    if (value instanceof Component c) text.append(" | ").append(c.getString());
                    else if (value != null && value.getClass().isRecord()) text.append(" | ").append(value); // DisconnectionDetails
                } catch (ReflectiveOperationException | RuntimeException ignored) { }
            }
            return text.toString();
        }

        @Override public void closeScreen() { Minecraft.getInstance().setScreenAndShow(null); }
    };
}
