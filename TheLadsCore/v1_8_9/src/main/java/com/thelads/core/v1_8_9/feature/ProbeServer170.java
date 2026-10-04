package com.thelads.core.v1_8_9.feature;

import static com.thelads.core.v1_8_9.feature.CoreProbe.after;
import static com.thelads.core.v1_8_9.feature.CoreProbe.check;
import static com.thelads.core.v1_8_9.feature.CoreProbe.retry;
import static com.thelads.core.v1_8_9.feature.CoreProbe.screenshot;

import com.google.gson.JsonElement;
import com.thelads.core.client.PacketErrorPolicy;
import com.thelads.core.client.ReconnectSettings;
import com.thelads.core.config.Module;
import com.thelads.core.config.Option;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiDisconnected;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.network.play.server.S00PacketKeepAlive;
import net.minecraft.util.MessageDeserializer;
import net.minecraftforge.fml.client.FMLClientHandler;
import org.lwjgl.input.Keyboard;

/**
 * QA only: 1.7.0's multiplayer features, run by CoreProbe in its QA world after Probe160. IgnorePacketErrors (an undecodable frame
 * through a real MessageDeserializer; a packet whose handler throws on the network thread, through the real NetworkManager, staying
 * connected), then with it off the same packet disconnects the world and AutoReconnect counts down and reopens it; a closed local
 * port retried twice; Ctrl+R on the server list; the QA world reopened. Screenshots 170-*.png; modules and options put back.
 */
final class ProbeServer170 {
    static final List<CoreProbe.Step> STEPS = Arrays.<CoreProbe.Step>asList(ProbeServer170::start, ProbeServer170::decoder,
        ProbeServer170::brokenHandler, ProbeServer170::stayedConnected, ProbeServer170::guardOff, ProbeServer170::countdown,
        ProbeServer170::rejoined, ProbeServer170::rejoinedShot, ProbeServer170::closedPort, ProbeServer170::firstRetry,
        ProbeServer170::secondRetry, ProbeServer170::retryLimit, ProbeServer170::serverList, ProbeServer170::reloaded,
        ProbeServer170::reopenWorld, ProbeServer170::restore);
    private static final Map<Option, JsonElement> OPTIONS = new LinkedHashMap<>();
    private static final Map<Module, Boolean> ENABLED = new LinkedHashMap<>();
    private static ReconnectSettings listsBefore;
    private static String folder, name;
    private static int skippedBefore;
    private static GuiScreen seen;
    private static boolean started;

    private ProbeServer170() {}

    private static boolean start(Minecraft mc) {
        started = true;
        folder = mc.getIntegratedServer().getFolderName();
        name = mc.getIntegratedServer().getWorldName();
        for (String id : Arrays.asList("AutoReconnect", "IgnorePacketErrors")) {
            Module module = Options189.module(id);
            ENABLED.put(module, module.isEnabled());
            for (Option option : module.getOptions()) OPTIONS.put(option, option.save());
        }
        listsBefore = AutoReconnect189.settings();
        ReconnectSettings lists = new ReconnectSettings();
        lists.retryDelays = new ArrayList<>(Arrays.asList(3, 3));
        AutoReconnect189.useSettings(lists);
        Options189.module("AutoReconnect").setEnabled(true);
        AutoReconnect189.module().initial.set(false);
        Options189.module("IgnorePacketErrors").setEnabled(true);
        // The QA world was joined before the module was switched on here: join it again as the target.
        AutoReconnect189.session().begin(folder, () -> mc.launchIntegratedServer(folder, name, null), AutoReconnect189.account());
        AutoReconnect189.session().joined(null, lists, false, false, false, System.nanoTime());
        return after(5);
    }

    /** An unreadable play frame through a real MessageDeserializer: dropped with IgnorePacketErrors, and the next frame decodes. */
    private static boolean decoder(Minecraft mc) throws Exception {
        EmbeddedChannel channel = playDecoder();
        channel.writeInbound(Unpooled.wrappedBuffer(new byte[] {0x7F, 1, 2, 3}));
        check(channel.readInbound() == null && channel.isOpen(), "IgnorePacketErrors: an unreadable play frame is dropped, the channel stays open");
        PacketBuffer good = new PacketBuffer(Unpooled.buffer());
        good.writeVarIntToBuffer(0);
        good.writeVarIntToBuffer(4242);
        channel.writeInbound(good);
        Object next = channel.readInbound();
        check(next instanceof S00PacketKeepAlive && ((S00PacketKeepAlive) next).func_149134_c() == 4242, "IgnorePacketErrors: the next frame decodes normally (" + next + ")");
        channel.finish();
        Options189.module("IgnorePacketErrors").setEnabled(false);
        EmbeddedChannel vanilla = playDecoder();
        boolean failed = false;
        try { vanilla.writeInbound(Unpooled.wrappedBuffer(new byte[] {0x7F, 1, 2, 3})); }
        catch (Exception expected) { failed = true; }
        vanilla.finish();
        Options189.module("IgnorePacketErrors").setEnabled(true);
        check(failed, "with IgnorePacketErrors off the same frame fails as in vanilla");
        return after(1);
    }

    private static EmbeddedChannel playDecoder() {
        EmbeddedChannel channel = new EmbeddedChannel(new MessageDeserializer(EnumPacketDirection.CLIENTBOUND));
        channel.attr(NetworkManager.attrKeyConnectionState).set(EnumConnectionState.PLAY);
        return channel;
    }

    /** A play packet whose handler throws on the network thread, fired into the client's real connection pipeline. */
    private static boolean brokenHandler(Minecraft mc) {
        skippedBefore = PacketErrorPolicy.skippedCount();
        inject(mc);
        return after(20);
    }

    private static boolean stayedConnected(Minecraft mc) {
        check(PacketErrorPolicy.skippedCount() == skippedBefore + 1, "IgnorePacketErrors: the failing packet was logged and skipped");
        check(mc.theWorld != null && mc.getNetHandler() != null && mc.getNetHandler().getNetworkManager().isChannelOpen() && mc.currentScreen == null,
            "IgnorePacketErrors: still connected and playing after the failing packet");
        return after(1);
    }

    /** IgnorePacketErrors off: the same packet disconnects ("Internal Exception"), and AutoReconnect takes the disconnect screen. */
    private static boolean guardOff(Minecraft mc) {
        Options189.module("IgnorePacketErrors").setEnabled(false);
        inject(mc);
        return after(1);
    }

    private static boolean countdown(Minecraft mc) {
        if (!(mc.currentScreen instanceof GuiDisconnected) || AutoReconnect189.retryButton() == null) return retry(1);
        check(AutoReconnect189.session().counting() && AutoReconnect189.session().target().equals(folder),
            "AutoReconnect: the failed packet's disconnect counts down to this world (" + AutoReconnect189.retryButton().displayString + "; "
            + AutoReconnect189.session().reason() + ")");
        screenshot(mc, "170-reconnect-countdown");
        return after(1);
    }

    private static boolean rejoined(Minecraft mc) {
        if (mc.theWorld == null || mc.thePlayer == null || mc.currentScreen != null) return retry(5);
        check(AutoReconnect189.session().target().equals(folder), "AutoReconnect: the countdown reopened the same world (" + folder + ") and joined it");
        Options189.module("IgnorePacketErrors").setEnabled(true);
        return after(80);
    }

    private static boolean rejoinedShot(Minecraft mc) {
        screenshot(mc, "170-reconnected");
        return after(2);
    }

    /** A server that refuses connections: Retry Initial Failures on, two delays, so two automatic re-dials and then the limit. */
    private static boolean closedPort(Minecraft mc) {
        AutoReconnect189.module().initial.set(true);
        seen = null;
        FMLClientHandler.instance().connectToServer(new GuiMultiplayer(new GuiMainMenu()), new ServerData("Lads QA closed port", "127.0.0.1:1", false));
        return after(1);
    }

    private static boolean firstRetry(Minecraft mc) { return retryScreen(mc, "170-closed-port-countdown", "the refused server counts down (first attempt failed)"); }
    private static boolean secondRetry(Minecraft mc) { return retryScreen(mc, null, "the automatic re-dial failed and the second delay counts down"); }

    private static boolean retryScreen(Minecraft mc, String shot, String what) {
        if (!(mc.currentScreen instanceof GuiDisconnected) || mc.currentScreen == seen || AutoReconnect189.retryButton() == null) return retry(1);
        seen = mc.currentScreen;
        check(AutoReconnect189.session().counting() && AutoReconnect189.session().target().equals("127.0.0.1:1"),
            "AutoReconnect: " + what + " (" + AutoReconnect189.retryButton().displayString + "; " + AutoReconnect189.session().reason() + ")");
        if (shot != null) screenshot(mc, shot);
        return after(1);
    }

    private static boolean retryLimit(Minecraft mc) {
        if (!(mc.currentScreen instanceof GuiDisconnected) || mc.currentScreen == seen || AutoReconnect189.retryButton() == null) return retry(1);
        seen = mc.currentScreen;
        String label = AutoReconnect189.retryButton().displayString;
        check(!AutoReconnect189.session().counting() && label.equals("Retry limit reached · Reconnect"), "AutoReconnect: after both delays it stops (" + label + ")");
        screenshot(mc, "170-retry-limit");
        return after(1);
    }

    private static boolean serverList(Minecraft mc) {
        mc.displayGuiScreen(new GuiMultiplayer(new GuiMainMenu()));
        seen = mc.currentScreen;
        return after(40);
    }

    /**
     * Ctrl+R through GuiScreen.handleInput, where Forge's keyboard event runs. LWJGL reads Ctrl from its key state, which its
     * per-frame poll resets, so QA holds it there only around the queued R.
     */
    private static boolean reloaded(Minecraft mc) throws Exception {
        Field field = Keyboard.class.getDeclaredField("keyDownBuffer");
        field.setAccessible(true);
        ByteBuffer keys = (ByteBuffer) field.get(null);
        try {
            keys.put(Keyboard.KEY_LCONTROL, (byte) 1);
            CoreProbe.tap(Keyboard.KEY_R, 'r');
            mc.currentScreen.handleInput();
        } finally {
            keys.put(Keyboard.KEY_LCONTROL, (byte) 0);
        }
        check(mc.currentScreen instanceof GuiMultiplayer && mc.currentScreen != seen, "Ctrl+R: the server list was refreshed (a new list screen pinging every server)");
        GuiScreen refreshed = mc.currentScreen;
        CoreProbe.tap(Keyboard.KEY_R, 'r');
        mc.currentScreen.handleInput();
        check(mc.currentScreen == refreshed, "R without Ctrl leaves the list alone");
        screenshot(mc, "170-server-list-refreshed");
        return after(5);
    }

    private static boolean reopenWorld(Minecraft mc) {
        if (mc.theWorld == null && mc.currentScreen instanceof GuiMultiplayer) {
            AutoReconnect189.module().initial.set(false);
            mc.launchIntegratedServer(folder, name, null);
            return retry(20);
        }
        if (mc.theWorld == null || mc.thePlayer == null || mc.currentScreen != null) return retry(5);
        check(true, "the QA world reopened for the remaining checks");
        return after(40);
    }

    private static boolean restore(Minecraft mc) {
        stop();
        return after(1);
    }

    /** Puts every module, option and list back (also after a failed step). */
    static void stop() {
        if (!started) return;
        started = false;
        OPTIONS.forEach(Option::load);
        ENABLED.forEach(Module::setEnabled);
        AutoReconnect189.useSettings(listsBefore);
    }

    private static void inject(Minecraft mc) {
        final NetworkManager connection = mc.getNetHandler().getNetworkManager();
        final Channel channel = connection.channel();
        channel.eventLoop().execute(() -> channel.pipeline().fireChannelRead(new FailingPacket()));
    }

    /** A play packet whose handler fails on the network thread, as a broken server or mod packet can. */
    private static final class FailingPacket implements Packet<INetHandlerPlayClient> {
        @Override public void readPacketData(PacketBuffer buffer) { }
        @Override public void writePacketData(PacketBuffer buffer) { }
        @Override public void processPacket(INetHandlerPlayClient handler) { throw new IllegalStateException("Lads QA: this packet's handler fails on purpose"); }
    }
}
