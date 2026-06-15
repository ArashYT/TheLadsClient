/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.screens.ConnectScreen
 *  net.minecraft.client.gui.screens.DisconnectedScreen
 *  net.minecraft.client.multiplayer.ServerData
 *  net.minecraft.client.multiplayer.resolver.ServerAddress
 */
package com.thelads.core.client;

import com.thelads.core.config.CycleOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

public class AutoReconnect {
    private static final AutoReconnect INSTANCE = new AutoReconnect();
    private static final long[] DELAY_OPTIONS = new long[]{3000L, 5000L, 10000L, 30000L};
    private int attempts = 0;
    private Screen parent;
    private ServerData server;
    private long shownAt;
    private boolean cancelled;

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
        CycleOption c;
        int idx;
        Option o;
        Module m = ModuleManager.getInstance().getModule("AutoReconnect");
        if (m != null && (o = m.getOption("Delay")) instanceof CycleOption && (idx = (c = (CycleOption)o).getIndex()) >= 0 && idx < DELAY_OPTIONS.length) {
            return DELAY_OPTIONS[idx];
        }
        return 5000L;
    }

    private int getMaxAttempts() {
        Option o;
        Module m = ModuleManager.getInstance().getModule("AutoReconnect");
        if (m != null && (o = m.getOption("Max attempts")) instanceof CycleOption) {
            CycleOption c = (CycleOption)o;
            return switch (c.getIndex()) {
                case 0 -> 1;
                case 1 -> 2;
                case 2 -> 3;
                default -> Integer.MAX_VALUE;
            };
        }
        return Integer.MAX_VALUE;
    }

    public void cancel() {
        this.cancelled = true;
    }

    public boolean isCancelled() {
        return this.cancelled;
    }

    public long secondsLeft() {
        long delay = this.getDelayMs();
        return Math.max(0L, (delay - (System.currentTimeMillis() - this.shownAt) + 999L) / 1000L);
    }

    public void reconnect() {
        ServerData data;
        Minecraft mc = Minecraft.getInstance();
        ServerData serverData = data = this.server != null ? this.server : mc.getCurrentServer();
        if (data == null) {
            return;
        }
        this.cancelled = true;
        ServerAddress addr = ServerAddress.parseString((String)data.ip);
        ConnectScreen.startConnecting((Screen)(this.parent != null ? this.parent : mc.screen), (Minecraft)mc, (ServerAddress)addr, (ServerData)data, (boolean)false, null);
    }

    public void tick(Minecraft mc) {
        if (!this.isModuleEnabled() || this.cancelled || this.server == null) {
            return;
        }
        if (!(mc.screen instanceof DisconnectedScreen)) {
            this.attempts = 0;
            return;
        }
        if (System.currentTimeMillis() - this.shownAt >= this.getDelayMs()) {
            if (this.attempts < this.getMaxAttempts()) {
                ++this.attempts;
                this.reconnect();
            } else {
                this.cancelled = true;
            }
        }
    }
}

