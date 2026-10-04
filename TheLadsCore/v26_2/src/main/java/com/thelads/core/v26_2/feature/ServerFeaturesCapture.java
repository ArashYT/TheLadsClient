package com.thelads.core.v26_2.feature;

import com.google.gson.JsonElement;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.InputConstants;
import com.thelads.core.client.PacketErrorPolicy;
import com.thelads.core.client.ReconnectSettings;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketDecoder;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.game.ClientboundStartConfigurationPacket;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.GamePacketTypes;
import net.minecraft.network.protocol.game.GameProtocols;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only (auto-world, ".lads-qa-capture-server" from the harness's LADS_VERIFY_CAPTURE_SERVER), after the other world probes:
 * IgnorePacketErrors (an undecodable frame through the real decoder; a packet whose handler throws, through the real
 * Connection, staying connected), Hide Signing Indicators (tag and toast), then with IgnorePacketErrors off the same packet
 * disconnects the world and AutoReconnect counts down and reopens it; a closed local port retried twice; Ctrl+R on the server
 * list. Screenshots server-*.png; every module and option is put back.
 */
final class ServerFeaturesCapture {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static final long SECOND = 1_000_000_000L;
    private static final List<String> FAILURES = new ArrayList<>();
    private static final Map<Option, JsonElement> OPTIONS = new LinkedHashMap<>();
    private static final Map<Module, Boolean> ENABLED = new LinkedHashMap<>();
    private static ReconnectSettings listsBefore;
    private interface Step { boolean run() throws Exception; }
    private static List<Step> steps;
    private static int step = -1, passed, skippedBefore;
    private static long due, deadline;
    private static String shot, world;
    private static boolean shooting;
    private static Screen seen;
    private ServerFeaturesCapture() {}

    static boolean busy() { return step >= 0 && steps != null && step < steps.size(); }

    static void tick(Path game, boolean ready) {
        if (step < 0) {
            if (!ready) return;
            Path request = game.resolve(".lads-qa-capture-server");
            if (!Files.isRegularFile(request, LinkOption.NOFOLLOW_LINKS)) return;
            try { Files.delete(request); } catch (Exception failure) { LOGGER.error("Lads server features capture FAILED: request", failure); return; }
            start();
            return;
        }
        if (!busy() || shot != null || System.nanoTime() < due) return;
        try {
            if (steps.get(step).run()) { step++; deadline = System.nanoTime() + 90 * SECOND; }
            else if (System.nanoTime() > deadline) throw new IllegalStateException("step " + (step + 1) + " did not finish in 90 s");
        } catch (Throwable failure) {
            FAILURES.add("step " + (step + 1) + ": " + failure);
            LOGGER.error("Lads server features capture step failed", failure);
            step = steps.size() - 1; // the last step puts everything back
        }
    }

    private static void start() {
        Minecraft mc = Minecraft.getInstance();
        world = mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName().toString();
        for (String name : List.of("AutoReconnect", "IgnorePacketErrors", "Chat")) {
            Module module = ModuleManager.getInstance().getModule(name);
            ENABLED.put(module, module.isEnabled());
            for (Option option : module.getOptions()) OPTIONS.put(option, option.save().deepCopy());
        }
        listsBefore = NativeReconnect.settings();
        ReconnectSettings lists = new ReconnectSettings();
        lists.retryDelays = new ArrayList<>(List.of(3, 3));
        NativeReconnect.useSettings(lists);
        module("AutoReconnect").setEnabled(true);
        NativeReconnect.module().initial.set(false);
        module("IgnorePacketErrors").setEnabled(true);
        ((com.thelads.core.config.BoolOption) module("Chat").getOption("Hide Signing Indicators")).set(true);
        // The QA world was joined before AutoReconnect was switched on here: it becomes the target as if joined with it on.
        NativeReconnect.session().begin(world, () -> NativeReconnect.openWorld(world), mc.getUser().getProfileId());
        NativeReconnect.session().joined(null, lists, false, false, false, System.nanoTime());
        steps = List.of(ServerFeaturesCapture::decoder, ServerFeaturesCapture::brokenHandler, ServerFeaturesCapture::stayedConnected,
            ServerFeaturesCapture::signing, ServerFeaturesCapture::signingShot, ServerFeaturesCapture::guardOff, ServerFeaturesCapture::countdown,
            ServerFeaturesCapture::countdownShot, ServerFeaturesCapture::rejoined, ServerFeaturesCapture::rejoinedShot,
            ServerFeaturesCapture::closedPort, ServerFeaturesCapture::firstRetry, ServerFeaturesCapture::secondRetry,
            ServerFeaturesCapture::retryLimit, ServerFeaturesCapture::serverList, ServerFeaturesCapture::reloaded,
            ServerFeaturesCapture::reopenWorld, ServerFeaturesCapture::finish);
        step = 0;
        deadline = System.nanoTime() + 90 * SECOND;
        LOGGER.info("Lads server features capture BEGIN: world {}", world);
    }

    /** An unreadable play frame through the real PacketDecoder: skipped with IgnorePacketErrors, and the next frame still decodes. */
    private static boolean decoder() {
        Minecraft mc = Minecraft.getInstance();
        ProtocolInfo<ClientGamePacketListener> play = GameProtocols.CLIENTBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(mc.getConnection().registryAccess()));
        EmbeddedChannel channel = new EmbeddedChannel(new PacketDecoder<>(play));
        try {
            channel.writeInbound(badFrame());
            check(channel.readInbound() == null && channel.isOpen(), "IgnorePacketErrors: an unreadable play frame is dropped, the channel stays open");
            ByteBuf good = Unpooled.buffer();
            play.codec().encode(good, new ClientboundKeepAlivePacket(4242L));
            channel.writeInbound(good);
            Object next = channel.readInbound();
            check(next instanceof ClientboundKeepAlivePacket alive && alive.getId() == 4242L, "IgnorePacketErrors: the next frame decodes normally (" + next + ")");
        } finally { channel.finishAndReleaseAll(); }
        module("IgnorePacketErrors").setEnabled(false);
        EmbeddedChannel vanilla = new EmbeddedChannel(new PacketDecoder<>(play));
        try {
            vanilla.writeInbound(badFrame());
            check(false, "with IgnorePacketErrors off the same frame must fail");
        } catch (io.netty.handler.codec.DecoderException expected) {
            check(true, "with IgnorePacketErrors off the same frame fails as in vanilla (" + expected.getMessage() + ")");
        } finally {
            // Vanilla kept the bad frame's tail, which closing would decode again: only the write above is under test.
            try { vanilla.finishAndReleaseAll(); } catch (RuntimeException leftover) { }
            module("IgnorePacketErrors").setEnabled(true);
        }
        return true;
    }

    private static ByteBuf badFrame() { return Unpooled.wrappedBuffer(new byte[] {(byte) 0xFF, (byte) 0xFF, 0x03, 1, 2, 3}); }

    /** A play packet whose handler throws, handed to the client's real Connection as the network thread does. */
    private static boolean brokenHandler() throws Exception {
        skippedBefore = PacketErrorPolicy.skippedCount();
        inject();
        wait(2);
        return true;
    }

    private static boolean stayedConnected() {
        Minecraft mc = Minecraft.getInstance();
        check(PacketErrorPolicy.skippedCount() == skippedBefore + 1, "IgnorePacketErrors: the failing packet was logged and skipped");
        check(mc.getConnection() != null && mc.getConnection().getConnection().isConnected() && mc.level != null && mc.gui.screen() == null,
            "IgnorePacketErrors: still connected and playing after the failing packet");
        check(!ConnectionTweaks.skipFailedHandler(ClientboundStartConfigurationPacket.INSTANCE, new IllegalStateException("Lads QA")),
            "IgnorePacketErrors: a failed protocol switch (start configuration) still gets vanilla's error");
        return true;
    }

    /** Hide Signing Indicators: a not-secure chat line has no tag, and the unverified-chat toast is dropped while others show. */
    private static boolean signing() {
        Minecraft mc = Minecraft.getInstance();
        GuiMessage message = new GuiMessage(0, Component.literal("Lads QA unsigned chat"), null, GuiMessageSource.PLAYER, GuiMessageTag.chatNotSecure());
        check(new GuiMessage.Line(message, Component.literal("x").getVisualOrderText(), true).tag() == null, "chat signing: a not-secure line shows no indicator");
        mc.gui.hud.getChat().addPlayerMessage(Component.literal("<Lads QA> unsigned chat, no indicator bar"), null, GuiMessageTag.chatNotSecure());
        var toasts = mc.gui.toastManager();
        toasts.addToast(new SystemToast(SystemToast.SystemToastId.UNSECURE_SERVER_WARNING, Component.literal("Chat messages can't be verified"), Component.literal("Lads QA")));
        toasts.addToast(new SystemToast(SystemToast.SystemToastId.PERIODIC_NOTIFICATION, Component.literal("Lads QA control toast"), Component.literal("other toasts still show")));
        check(toasts.getToast(SystemToast.class, SystemToast.SystemToastId.UNSECURE_SERVER_WARNING) == null
            && toasts.getToast(SystemToast.class, SystemToast.SystemToastId.PERIODIC_NOTIFICATION) != null, "chat signing: the unverified-chat toast is dropped, another toast shows");
        wait(1);
        return true;
    }

    private static boolean signingShot() { return take("server-1-signing-chat"); }

    /** IgnorePacketErrors off: the same packet disconnects (vanilla), and AutoReconnect takes over the disconnect screen. */
    private static boolean guardOff() throws Exception {
        module("IgnorePacketErrors").setEnabled(false);
        inject();
        return true;
    }

    private static boolean countdown() {
        if (!(Minecraft.getInstance().gui.screen() instanceof DisconnectedScreen screen) || NativeReconnect.retryButton() == null) return false;
        seen = screen;
        check(NativeReconnect.session().counting() && NativeReconnect.session().target().equals(world)
            && NativeReconnect.session().reason().equals(Component.translatable("disconnect.packetError").getString()),
            "AutoReconnect: Network Protocol Error from the local world counts down (" + NativeReconnect.retryButton().getMessage().getString() + ")");
        wait(1);
        return true;
    }

    private static boolean countdownShot() { return take("server-2-reconnect-countdown"); }

    private static boolean rejoined() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.gui.screen() != null) return false;
        check(NativeReconnect.session().target().equals(world), "AutoReconnect: the countdown reopened the same world (" + world + ") and joined it");
        module("IgnorePacketErrors").setEnabled(true);
        wait(4);
        return true;
    }

    private static boolean rejoinedShot() { return take("server-3-reconnected"); }

    /** A server that refuses connections: Retry Initial Failures on, two delays, so two automatic re-dials and then the limit. */
    private static boolean closedPort() {
        Minecraft mc = Minecraft.getInstance();
        NativeReconnect.module().initial.set(true);
        ServerData server = new ServerData("Lads QA closed port", "127.0.0.1:1", ServerData.Type.OTHER);
        ConnectScreen.startConnecting(new JoinMultiplayerScreen(new TitleScreen()), mc, ServerAddress.parseString(server.ip), server, false, null);
        seen = null;
        return true;
    }

    private static boolean firstRetry() { return retryScreen("server-4-closed-port-countdown", "the refused server counts down (first attempt failed)"); }
    private static boolean secondRetry() { return retryScreen(null, "the automatic re-dial failed and the second delay counts down"); }

    private static boolean retryScreen(String name, String what) {
        if (!(Minecraft.getInstance().gui.screen() instanceof DisconnectedScreen screen) || screen == seen || NativeReconnect.retryButton() == null) return false;
        seen = screen;
        check(NativeReconnect.session().counting() && NativeReconnect.session().target().equals("127.0.0.1:1"), "AutoReconnect: " + what
            + " (" + NativeReconnect.retryButton().getMessage().getString() + "; " + NativeReconnect.session().reason() + ")");
        if (name != null) shot = name;
        return true;
    }

    private static boolean retryLimit() {
        if (!(Minecraft.getInstance().gui.screen() instanceof DisconnectedScreen screen) || screen == seen || NativeReconnect.retryButton() == null) return false;
        seen = screen;
        String label = NativeReconnect.retryButton().getMessage().getString();
        check(!NativeReconnect.session().counting() && label.equals("Retry limit reached · Reconnect"), "AutoReconnect: after both delays it stops (" + label + ")");
        shot = "server-5-retry-limit";
        return true;
    }

    /** Ctrl+R through the game's key handler on the server list: the list is rebuilt and every server pinged again. */
    private static boolean serverList() {
        Minecraft.getInstance().gui.setScreen(new JoinMultiplayerScreen(new TitleScreen()));
        seen = Minecraft.getInstance().gui.screen();
        wait(2);
        return true;
    }

    private static boolean reloaded() throws Exception {
        Minecraft mc = Minecraft.getInstance();
        press(new KeyEvent(InputConstants.KEY_R, 0, InputConstants.MOD_CONTROL));
        check(mc.gui.screen() instanceof JoinMultiplayerScreen && mc.gui.screen() != seen, "Ctrl+R: the server list was refreshed (a new list screen pinging every server)");
        Screen refreshed = mc.gui.screen();
        press(new KeyEvent(InputConstants.KEY_R, 0, 0));
        check(mc.gui.screen() == refreshed, "R without Ctrl leaves the list alone");
        shot = "server-6-server-list-refreshed";
        return true;
    }

    private static boolean reopenWorld() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            if (!(mc.gui.screen() instanceof JoinMultiplayerScreen)) return false;
            NativeReconnect.module().initial.set(false);
            mc.createWorldOpenFlows().openWorld(world, () -> FAILURES.add("the QA world did not reopen"));
            wait(2);
            return false;
        }
        return mc.player != null && mc.gui.screen() == null;
    }

    private static boolean finish() {
        OPTIONS.forEach(Option::load);
        ENABLED.forEach(Module::setEnabled);
        NativeReconnect.useSettings(listsBefore);
        if (FAILURES.isEmpty()) LOGGER.info("Lads server features capture END: {} passed, 0 failed", passed);
        else LOGGER.error("Lads server features capture FAILED: {} | {} passed", String.join(" | ", FAILURES), passed);
        return true;
    }

    /** Each completed frame: saves the requested screenshot. */
    static void frame(RenderTarget target, Path game) {
        if (shot == null || shooting) return;
        shooting = true;
        String name = shot;
        try {
            Path folder = game.resolve("screenshots");
            Files.createDirectories(folder);
            Path output = folder.resolve(name + ".png");
            net.minecraft.client.Screenshot.takeScreenshot(target, image -> {
                try { image.writeToFile(output); LOGGER.info("Lads server features frame {}", output); }
                catch (Exception failure) { FAILURES.add(name + ": " + failure); }
                finally { image.close(); Minecraft.getInstance().execute(() -> { shot = null; shooting = false; }); }
            });
        } catch (Exception failure) {
            FAILURES.add(name + ": " + failure);
            shot = null;
            shooting = false;
        }
    }

    private static boolean take(String name) { shot = name; return true; }

    private static void inject() throws Exception {
        Connection connection = Minecraft.getInstance().getConnection().getConnection();
        Field field = Connection.class.getDeclaredField("channel");
        field.setAccessible(true);
        Channel channel = (Channel) field.get(connection);
        channel.eventLoop().execute(() -> {
            try { connection.channelRead(channel.pipeline().context(connection), new FailingPacket()); }
            catch (Exception failure) { LOGGER.error("Lads server features capture: injection failed", failure); }
        });
    }

    /** A play packet whose handler fails on the client thread, as a broken server or mod packet does. */
    private record FailingPacket() implements Packet<ClientGamePacketListener> {
        @Override public PacketType<? extends Packet<ClientGamePacketListener>> type() { return GamePacketTypes.CLIENTBOUND_SET_TIME; }
        @Override public void handle(ClientGamePacketListener listener) {
            PacketUtils.ensureRunningOnSameThread(this, listener, Minecraft.getInstance().packetProcessor());
            throw new IllegalStateException("Lads QA: this packet's handler fails on purpose");
        }
    }

    private static void press(KeyEvent key) throws ReflectiveOperationException {
        Minecraft mc = Minecraft.getInstance();
        Method keyPress = KeyboardHandler.class.getDeclaredMethod("keyPress", long.class, int.class, KeyEvent.class);
        keyPress.setAccessible(true);
        keyPress.invoke(mc.keyboardHandler, mc.getWindow().handle(), InputConstants.PRESS, key);
        keyPress.invoke(mc.keyboardHandler, mc.getWindow().handle(), InputConstants.RELEASE, key);
    }

    private static Module module(String name) { return ModuleManager.getInstance().getModule(name); }
    private static void wait(int seconds) { due = System.nanoTime() + seconds * SECOND; }
    private static void check(boolean result, String what) {
        if (!result) throw new IllegalStateException(what);
        passed++;
        LOGGER.info("Lads server features capture PASS: {}", what);
    }
}
