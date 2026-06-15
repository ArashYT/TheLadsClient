package com.thelads.core.modules;

import com.thelads.core.config.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.ping.ServerboundPingRequestPacket;

public class PingViewModule extends Module {
    private static int realTimePing = -1;
    private static long lastPingTime = -1;
    private static int lastPingId = -1;
    private static int pingIdCounter = 0;
    private static int tickCounter = 0;

    public PingViewModule() {
        super("PingView", "Shows numeric ping in tab list");
    }

    public static void onTick() {
        Module m = com.thelads.core.config.ModuleManager.getInstance().getModule("PingView");
        Module m2 = com.thelads.core.config.ModuleManager.getInstance().getModule("PingHUD");
        if ((m == null || !m.isEnabled()) && (m2 == null || !m2.isEnabled())) return;
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) {
            realTimePing = -1;
            return;
        }

        tickCounter++;
        // Send a ping packet every 20 ticks (1 second)
        if (tickCounter >= 20) {
            tickCounter = 0;
            long currentTime = System.currentTimeMillis();
            pingIdCounter++;
            lastPingId = pingIdCounter;
            lastPingTime = currentTime;
            mc.getConnection().send(new ServerboundPingRequestPacket(lastPingId));
        }
    }

    public static void onPong(long id) {
        if (lastPingId != -1 && id == lastPingId) {
            long currentTime = System.currentTimeMillis();
            realTimePing = (int) (currentTime - lastPingTime);
        }
    }

    public int getRealTimePing() {
        return realTimePing;
    }

    public String getPingText(int ping) {
        int displayPing = realTimePing >= 0 ? realTimePing : ping;
        if (displayPing < 0) {
            return "???";
        }
        return displayPing + "ms";
    }

    public int getPingColor(int ping) {
        int displayPing = realTimePing >= 0 ? realTimePing : ping;
        if (displayPing < 0) {
            return 0xFFFF5555;
        } else if (displayPing < 100) {
            return 0xFF55FF55;
        } else if (displayPing <= 200) {
            return 0xFFFFFF55;
        } else {
            return 0xFFFF5555;
        }
    }
}
