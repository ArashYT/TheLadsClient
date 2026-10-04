package com.thelads.core.v26_2.feature;

import com.mojang.blaze3d.platform.InputConstants;
import com.thelads.core.client.PacketErrorPolicy;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.v26_2.mixin.ServerListRefresh;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import org.slf4j.LoggerFactory;

/**
 * Small multiplayer features with no settings of their own: Ctrl+R refreshes the server list, IgnorePacketErrors' switch, and
 * the chat-signing hiding (Chat's Hide Signing Indicators). Each stands down while the mod it replaces is installed.
 */
public final class ConnectionTweaks {
    private static final boolean PACKET_MOD = FabricLoader.getInstance().isModLoaded("netprodis");
    private static final boolean SIGNING_MOD = FabricLoader.getInstance().isModLoaded("chatsigninghider");
    private ConnectionTweaks() {}

    public static void register() {
        if (PACKET_MOD) ModuleSupport.registerExternal("IgnorePacketErrors", "Network Protocol Disconnect", "netprodis", true);
        else ModuleSupport.registerBuiltIn("IgnorePacketErrors");
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof JoinMultiplayerScreen) ScreenKeyboardEvents.allowKeyPress(screen).register((shown, key) -> {
                if (!refreshShortcut(key) || shown.getFocused() instanceof EditBox) return true;
                LoggerFactory.getLogger("TheLadsCore").info("Lads server list: Ctrl+R, pinging every server again");
                ((ServerListRefresh) shown).lads$refresh();
                return false;
            });
        });
    }

    static boolean refreshShortcut(KeyEvent key) { return key.key() == InputConstants.KEY_R && key.hasControlDownWithQuirk(); }

    public static boolean ignorePacketErrors() { return !PACKET_MOD && NativeQualityOfLife.enabled("IgnorePacketErrors"); }

    /**
     * IgnorePacketErrors for a play packet whose handler threw. Never for the packet that switches protocol (the terminal one)
     * or the ones that build the world (login, respawn): half-done, they leave the client hanging until it times out, so they
     * keep vanilla's error screen.
     */
    public static boolean skipFailedHandler(Packet<?> packet, Exception failure) {
        boolean setsUpPlay = packet.isTerminal() || packet instanceof ClientboundLoginPacket || packet instanceof ClientboundRespawnPacket;
        return !setsUpPlay && ignorePacketErrors() && PacketErrorPolicy.skippable(failure);
    }

    /** Chat signing indicators and the unverified-chat toast are hidden unless the player turned the option off. */
    public static boolean hideChatSigning() { return !SIGNING_MOD && NativeQualityOfLife.bool("Chat", "Hide Signing Indicators", true); }
}
