package com.thelads.core.v1_20_1.feature;

import com.thelads.core.client.social.P2PQa;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.network.chat.Component;

/** QA only (LADS_VERIFY_P2P=host|guest): the script verb "p2p" runs the common friends + P2P scenario (P2PQa) on 1.20.1. */
public final class P2PQa1201 {
    private static final List<String> RECEIVED = new CopyOnWriteArrayList<>();

    private P2PQa1201() {}

    public static void register() {
        if (!P2PQa.ACTIVE) return;
        ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, at) -> RECEIVED.add(message.getString()));
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> { if (!overlay) RECEIVED.add(message.getString()); });
        QaProbe1201.registerStep("p2p", args -> {
            if (!P2PQa.tick(GAME)) return false;
            if (P2PQa.failedAny()) throw new IllegalStateException("the friends + P2P scenario failed (Lads p2p QA FAILED above)");
            return true;
        });
    }

    private static final P2PQa.Game GAME = new P2PQa.Game() {
        @Override public boolean inWorld() {
            Minecraft mc = Minecraft.getInstance();
            return mc.level != null && mc.player != null && mc.screen == null && mc.player.tickCount > 40;
        }

        @Override public int players() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getSingleplayerServer() != null) return mc.getSingleplayerServer().getPlayerCount();
            return mc.getConnection() == null ? 0 : mc.getConnection().getOnlinePlayers().size();
        }

        @Override public void chat(String text) { Minecraft.getInstance().player.connection.sendChat(text); }

        @Override public boolean received(String text) {
            for (String line : RECEIVED) if (line.contains(text)) return true;
            return false;
        }

        @Override public void screenshot(String name) throws Exception { QaProbe1201.shot(Minecraft.getInstance(), name); }

        @Override public void offlineServer() {
            var server = Minecraft.getInstance().getSingleplayerServer();
            if (server != null) server.setUsesAuthentication(false);
        }

        @Override public String disconnected() {
            if (!(Minecraft.getInstance().screen instanceof DisconnectedScreen screen)) return null;
            StringBuilder text = new StringBuilder(screen.getTitle().getString());
            for (var field : DisconnectedScreen.class.getDeclaredFields()) {
                if (!Component.class.isAssignableFrom(field.getType())) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(screen);
                    if (value instanceof Component c) text.append(" | ").append(c.getString());
                } catch (ReflectiveOperationException ignored) { }
            }
            return text.toString();
        }

        @Override public void closeScreen() { Minecraft.getInstance().setScreen(null); }
    };
}
