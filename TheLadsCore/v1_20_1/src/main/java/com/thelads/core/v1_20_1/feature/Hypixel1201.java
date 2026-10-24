package com.thelads.core.v1_20_1.feature;

import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.gui.LadsSettingsScreen;
import com.thelads.core.client.hypixel.HypixelMods;
import com.thelads.core.client.hypixel.HypixelQa;
import com.thelads.core.client.hypixel.RealPing;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.v1_20_1.gui.LadsSettingsScreen1201;
import com.thelads.core.v1_20_1.hud.Mods1201;
import io.netty.buffer.Unpooled;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Hypixel modules and the real ping (common HypixelMods, RealPing) on 1.20.1, plain Fabric and Zero SMP: server chat through
 * Fabric's game-message event (Chat Cleanup cancels it), a client tick, and the Mod API ping on the hypixel:ping channel (its
 * receiver runs on the network thread, so the arrival time is the reply's). With the official Hypixel Mod API mod installed the
 * channel is its own: the ping then uses status pings. The channel is announced (minecraft:register) only on the connection about
 * to send the first ping, and RealPing pings Hypixel hosts only: no other server sees it.
 */
public final class Hypixel1201 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static final ResourceLocation CHANNEL = new ResourceLocation(RealPing.CHANNEL);
    private static boolean channel;
    private static final ClientPlayNetworking.PlayChannelHandler RECEIVER = (client, handler, buf, sender) -> {
        long arrived = System.nanoTime();
        byte[] data = new byte[buf.readableBytes()];
        buf.readBytes(data);
        RealPing.receive(RealPing.CHANNEL, data, arrived);
    };

    private Hypixel1201() {}

    public static void register() {
        ModuleSupport.registerBuiltIn(HypixelMods.HYPIXEL_MODS, "Session Stats", "Bed Wars Height Limit");
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> overlay || !HypixelMods.chat(message.getString(), System.nanoTime()));
        ClientReceiveMessageEvents.MODIFY_GAME.register((message, overlay) -> {
            if (!overlay && HypixelMods.isStreamerMode()) {
                String raw = message.getString();
                String masked = HypixelMods.filterIncomingChat(raw);
                if (!masked.equals(raw)) return net.minecraft.network.chat.Component.literal(masked);
            }
            return message;
        });
        ClientSendMessageEvents.ALLOW_COMMAND.register(command -> !HypixelMods.handleOutgoingCommand(command));
        ClientSendMessageEvents.MODIFY_CHAT.register(HypixelMods::handleOutgoingChat);
        ClientSendMessageEvents.MODIFY_COMMAND.register(HypixelMods::handleOutgoingChat);
        ClientTickEvents.END_CLIENT_TICK.register(client -> HypixelMods.tick(LadsGameBridge.get(), client.level, client.getConnection(), System.nanoTime()));
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClientSide() && com.thelads.core.client.hypixel.OpenedChestHighlight.isEnabled() && hitResult != null) {
                net.minecraft.core.BlockPos pos = hitResult.getBlockPos();
                if (pos != null) {
                    com.thelads.core.client.hypixel.OpenedChestHighlight.onChestOpened(pos.getX(), pos.getY(), pos.getZ());
                }
            }
            return net.minecraft.world.InteractionResult.PASS;
        });
        if (Mods1201.loaded("hypixel_mod_api")) return;
        channel = !ClientPlayNetworking.getGlobalReceivers().contains(CHANNEL);
        if (!channel) LOGGER.info("Lads real ping: {} is registered by another mod, using status pings", RealPing.CHANNEL);
        if (!Boolean.getBoolean("thelads.verifyAutoWorld") && System.getProperty("thelads.qaScript") == null) return;
        // QA only: the integrated server answers like Hypixel ("pong"), so the QA world exercises the whole round trip; and the script
        // verb "hypixel" runs the common Hypixel routine (HypixelQa, screenshots/hypixel-*.png). Plain Fabric and Zero SMP.
        // The answer waits 37 ms, so the measured round trip is told apart from the listed 0 ms of singleplayer.
        if (channel) ServerPlayNetworking.registerGlobalReceiver(CHANNEL, (server, player, handler, buf, sender) ->
            CompletableFuture.delayedExecutor(37, TimeUnit.MILLISECONDS).execute(() ->
                sender.sendPacket(CHANNEL, new FriendlyByteBuf(Unpooled.wrappedBuffer(new byte[] {1, 1, 4, 'p', 'o', 'n', 'g'})))));
        QaProbe1201.registerStep("channels", args -> { String when = args.length > 1 ? args[1] : "script"; QuietChannelsQa1201.sample(when, when.contains("after")); return true; });
        QaProbe1201.registerStep("hypixel", args -> {
            if (qa == null) qa = new HypixelQa();
            try {
                if (!qa.tick(QA)) return false;
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
            if (!qa.failures().isEmpty()) throw new IllegalStateException("hypixel QA: " + qa.failures());
            return true;
        });
    }

    private static HypixelQa qa;
    private static final HypixelQa.Game QA = new HypixelQa.Game() {
        @Override public void command(String command) { Minecraft.getInstance().player.connection.sendCommand(command); }
        @Override public boolean serverAnswersPings() { return channel; }
        @Override public void pong(byte[] payload) { RealPing.receive(RealPing.CHANNEL, payload, System.nanoTime()); }
        @Override public void holdTab(boolean down) { Minecraft.getInstance().options.keyPlayerList.setDown(down); }
        @Override public void openChat() { Minecraft.getInstance().setScreen(new ChatScreen("")); }
        @Override public LadsSettingsScreen openSettings(String module) {
            LadsSettingsScreen1201 menu = new LadsSettingsScreen1201(null);
            Minecraft.getInstance().setScreen(menu);
            if (module != null) menu.openModule(module);
            return menu.ui();
        }
        @Override public void closeScreen() { Minecraft.getInstance().setScreen(null); }
        @Override public void channels(String when) { QuietChannelsQa1201.sample(when, when.contains("after the ping")); }
        @Override public void screenshot(String name) throws Exception { QaProbe1201.shot(Minecraft.getInstance(), name); }
    };

    /** RealPing's Mod API request (the bridge's sendPluginMessage). */
    public static boolean send(String name, byte[] data) {
        if (!channel || !RealPing.CHANNEL.equals(name) || Minecraft.getInstance().getConnection() == null) return false;
        try {
            if (ClientPlayNetworking.getGlobalReceivers().contains(CHANNEL)) return false; // another mod's since startup: status pings
            // This connection's receiver, registered (and announced) just before its first ping; gone with the connection.
            if (!ClientPlayNetworking.getReceived().contains(CHANNEL)) ClientPlayNetworking.registerReceiver(CHANNEL, RECEIVER);
            ClientPlayNetworking.send(CHANNEL, new FriendlyByteBuf(Unpooled.wrappedBuffer(data)));
            return true;
        } catch (RuntimeException failure) {
            LOGGER.debug("Lads real ping: could not send {}: {}", name, failure.toString());
            return false;
        }
    }
}
