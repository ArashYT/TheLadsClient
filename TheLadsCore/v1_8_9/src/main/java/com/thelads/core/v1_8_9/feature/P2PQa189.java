package com.thelads.core.v1_8_9.feature;

import com.thelads.core.client.social.P2PQa;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiDisconnected;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** QA only (LADS_VERIFY_189_ONLY=p2p with LADS_VERIFY_P2P=host|guest): the common friends + P2P scenario (P2PQa) as a self-test step. */
final class P2PQa189 {
    private static final List<String> RECEIVED = new CopyOnWriteArrayList<String>();
    private static boolean registered;

    private P2PQa189() {}

    static boolean run(Minecraft mc) {
        if (!registered) {
            registered = true;
            MinecraftForge.EVENT_BUS.register(new P2PQa189.Chat());
        }
        if (!P2PQa.tick(GAME)) {
            CoreProbe.stillWorking(); // P2PQa times out each of its own steps (lads.p2pQa.stepTimeoutMs)
            return false;
        }
        CoreProbe.check(!P2PQa.failedAny(), "the friends + P2P scenario as " + P2PQa.ROLE + " (Lads p2p QA END above)");
        return true;
    }

    public static final class Chat {
        @SubscribeEvent
        public void received(ClientChatReceivedEvent event) {
            if (event.type != 2 && event.message != null) RECEIVED.add(event.message.getUnformattedText());
        }
    }

    private static final P2PQa.Game GAME = new P2PQa.Game() {
        @Override public boolean inWorld() {
            Minecraft mc = Minecraft.getMinecraft();
            return mc.theWorld != null && mc.thePlayer != null && mc.currentScreen == null && mc.thePlayer.ticksExisted > 40;
        }

        @Override public int players() {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.getIntegratedServer() != null) return mc.getIntegratedServer().getCurrentPlayerCount();
            return mc.getNetHandler() == null ? 0 : mc.getNetHandler().getPlayerInfoMap().size();
        }

        @Override public void chat(String text) { Minecraft.getMinecraft().thePlayer.sendChatMessage(text); }

        @Override public boolean received(String text) {
            for (String line : RECEIVED) if (line.contains(text)) return true;
            return false;
        }

        @Override public void screenshot(String name) { CoreProbe.screenshot(Minecraft.getMinecraft(), name); }

        @Override public void offlineServer() {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.getIntegratedServer() != null) mc.getIntegratedServer().setOnlineMode(false);
        }

        @Override public String disconnected() {
            Minecraft mc = Minecraft.getMinecraft();
            if (!(mc.currentScreen instanceof GuiDisconnected)) return null;
            StringBuilder text = new StringBuilder("Disconnected");
            for (Field field : GuiDisconnected.class.getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(mc.currentScreen);
                    if (value instanceof IChatComponent) text.append(" | ").append(((IChatComponent) value).getUnformattedText());
                    else if (value instanceof String) text.append(" | ").append(value);
                } catch (ReflectiveOperationException ignored) { }
            }
            return text.toString();
        }

        @Override public void closeScreen() { Minecraft.getMinecraft().displayGuiScreen(null); }
    };
}
