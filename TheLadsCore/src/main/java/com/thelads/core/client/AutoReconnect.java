package com.thelads.core.client;

import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.SliderOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

/**
 * Auto-reconnect state + logic. The DisconnectedScreen mixin records the screen
 * and server here and adds the Reconnect / Cancel buttons; the client tick fires
 * the automatic reconnect after a short delay (when the module is enabled).
 */
public class AutoReconnect {
    private static final AutoReconnect INSTANCE = new AutoReconnect();
    private static final long[] DELAY_OPTIONS = {3000L, 5000L, 10000L, 30000L};
    private int attempts = 0;

    private Screen parent;
    private ServerData server;
    private long shownAt;
    private boolean cancelled;
    private Button cancelButton; // the disconnect screen's Cancel button, for the live countdown
    private ServerData lastServer;

    public void setLastServer(ServerData server) {
        this.lastServer = server;
    }

    public ServerData getLastServer() {
        return this.lastServer;
    }

    public void setCancelButton(Button b) {
        this.cancelButton = b;
    }

    public static AutoReconnect get() {
        return INSTANCE;
    }

    public boolean isModuleEnabled() {
        Module m = ModuleManager.getInstance().getModule("AutoReconnect");
        return m != null && m.isEnabled();
    }

    public void onScreenShown(Screen parent, ServerData server) {
        this.parent = parent;
        this.server = server;
        this.shownAt = System.currentTimeMillis();
        this.cancelled = false;
    }

    private long getDelayMs() {
        Module m = ModuleManager.getInstance().getModule("AutoReconnect");
        if (m != null) {
            Option o = m.getOption("Delay");
            if (o instanceof DropdownOption c) {
                int idx = c.getIndex();
                if (idx >= 0 && idx < DELAY_OPTIONS.length) return DELAY_OPTIONS[idx];
            }
        }
        return 5000L;
    }

    private int getMaxAttempts() {
        Module m = ModuleManager.getInstance().getModule("AutoReconnect");
        if (m != null) {
            Option o = m.getOption("Max attempts");
            if (o instanceof DropdownOption c) {
                return switch (c.getIndex()) {
                    case 0 -> 1;
                    case 1 -> 2;
                    case 2 -> 3;
                    default -> Integer.MAX_VALUE;
                };
            }
        }
        return Integer.MAX_VALUE;
    }

    public void cancel() {
        this.cancelled = true;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public long secondsLeft() {
        long delay = getDelayMs();
        return Math.max(0, (delay - (System.currentTimeMillis() - shownAt) + 999) / 1000);
    }

    public void reconnect() {
        Minecraft mc = Minecraft.getInstance();
        ServerData data = (server != null) ? server : mc.getCurrentServer();
        if (data == null) {
            return;
        }
        cancelled = true;
        ServerAddress addr = ServerAddress.parseString(data.ip);
        ConnectScreen.startConnecting(parent != null ? parent : mc.gui.screen(), mc, addr, data, false, null);
    }

    public void tick(Minecraft mc) {
        if (!(mc.gui.screen() instanceof DisconnectedScreen)) {
            attempts = 0;
            cancelButton = null;
            return;
        }
        // Live countdown on the Cancel button: "Cancel Auto-Reconnect (5s)".
        if (cancelButton != null) {
            cancelButton.setMessage(isModuleEnabled() && !cancelled
                ? Component.literal("Cancel Auto-Reconnect (" + secondsLeft() + "s)")
                : Component.literal("Cancel Auto-Reconnect"));
        }
        if (!isModuleEnabled() || cancelled || server == null) return;
        if (System.currentTimeMillis() - shownAt >= getDelayMs()) {
            if (attempts < getMaxAttempts()) {
                attempts++;
                reconnect();
            } else {
                cancelled = true;
            }
        }
    }
}
