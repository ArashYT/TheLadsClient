package com.thelads.core.v1_8_9.feature;

import com.thelads.core.config.Module;
import com.thelads.core.v1_8_9.mixin.GuiMultiplayerInvoker;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.lwjgl.input.Keyboard;

/** Ctrl+R refreshes the server list (1.8.9's has no text field to type in), and IgnorePacketErrors' switch, as 26.x ConnectionTweaks. */
public final class ConnectionTweaks189 {
    /** Every GuiConnecting still referenced (a few at most): which one a connector thread's NetworkManager belongs to. */
    private static final java.util.Set<net.minecraft.client.multiplayer.GuiConnecting> CONNECTING =
        java.util.Collections.synchronizedSet(java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>()));

    public static void connecting(net.minecraft.client.multiplayer.GuiConnecting screen) { CONNECTING.add(screen); }

    /**
     * The NetworkManager a GuiConnecting's connector thread just opened, after the player pressed Cancel there: vanilla 1.8.9 had
     * nothing to close while the socket was still opening, so the thread went on and logged in in the background (chat from a server
     * the player never joined). False for every other connection (singleplayer, LAN, Realms, other mods).
     */
    public static boolean cancelledConnect(net.minecraft.network.NetworkManager network) {
        synchronized (CONNECTING) {
            for (net.minecraft.client.multiplayer.GuiConnecting screen : CONNECTING) {
                com.thelads.core.v1_8_9.mixin.GuiConnectingAccessor connecting = (com.thelads.core.v1_8_9.mixin.GuiConnectingAccessor) screen;
                if (connecting.lads$getNetworkManager() == network) return connecting.lads$isCancelled();
            }
        }
        return false;
    }

    public static boolean ignorePacketErrors() {
        Module module = Options189.module("IgnorePacketErrors");
        return module != null && module.isEnabled();
    }

    @SubscribeEvent
    public void key(GuiScreenEvent.KeyboardInputEvent.Pre event) {
        if (!(event.gui instanceof GuiMultiplayer) || !Keyboard.getEventKeyState() || Keyboard.getEventKey() != Keyboard.KEY_R || !GuiScreen.isCtrlKeyDown()) return;
        LogManager.getLogger("TheLadsCore").info("Lads server list: Ctrl+R, pinging every server again");
        ((GuiMultiplayerInvoker) event.gui).ladsRefresh();
        event.setCanceled(true);
    }
}
