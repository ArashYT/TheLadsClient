package com.thelads.core.v1_8_9.feature;

import com.thelads.core.client.ReconnectSession;
import com.thelads.core.client.ReconnectSettings;
import com.thelads.core.modules.AutoReconnectModule;
import com.thelads.core.v1_8_9.gui.ReconnectActionsScreen189;
import com.thelads.core.v1_8_9.gui.ReconnectOptionsScreen189;
import com.thelads.core.v1_8_9.mixin.GuiDisconnectedAccessor;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiDisconnected;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSelectWorld;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.FMLClientHandler;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.lwjgl.input.Keyboard;

/**
 * AutoReconnect on 1.8.9: ReconnectSession fed by Forge's screen events (a server as its GuiConnecting opens, the disconnect
 * screen's Reconnect and Cancel buttons and Escape) and the client tick (a local world once joined, the countdown, join actions).
 */
public final class AutoReconnect189 {
    private static final int RETRY = 1189, CANCEL = 1190;
    private static final ReconnectSession SESSION = new ReconnectSession();
    private static ReconnectSettings lists = new ReconnectSettings();
    private static GuiButton retry, cancel;
    private static NetHandlerPlayClient joinedVia;

    public static AutoReconnectModule module() {
        return Options189.module("AutoReconnect") instanceof AutoReconnectModule ? (AutoReconnectModule) Options189.module("AutoReconnect") : null;
    }
    public static boolean active() { return module() != null && module().isEnabled(); }
    public static ReconnectSettings settings() { return lists; }
    public static void replaceSettings(ReconnectSettings value) { value.save(file().toPath()); lists = value; SESSION.cancelCountdown(); }
    static ReconnectSession session() { return SESSION; }
    static void useSettings(ReconnectSettings value) { lists = value; }
    static GuiButton retryButton() { return retry; }
    static GuiButton cancelButton() { return cancel; }
    private static File file() { return new File(Loader.instance().getConfigDir(), "theladscore/reconnect.json"); }

    /** Offline sessions have no profile id; their name stands in, as for any 1.8.9 account check. */
    static UUID account() {
        Minecraft mc = Minecraft.getMinecraft();
        UUID id = mc.getSession().getProfile().getId();
        return id != null ? id : UUID.nameUUIDFromBytes(mc.getSession().getUsername().getBytes(StandardCharsets.UTF_8));
    }

    public static void register() {
        if (module() == null) return;
        lists = ReconnectSettings.load(file().toPath());
        final Minecraft mc = Minecraft.getMinecraft();
        module().retryEditor.setAction(() -> mc.displayGuiScreen(new ReconnectOptionsScreen189(mc.currentScreen)));
        module().actionsEditor.setAction(() -> mc.displayGuiScreen(new ReconnectActionsScreen189(mc.currentScreen)));
        MinecraftForge.EVENT_BUS.register(new AutoReconnect189());
    }

    @SubscribeEvent
    public void open(GuiOpenEvent event) {
        final Minecraft mc = Minecraft.getMinecraft();
        if (!active()) return;
        // GuiConnecting's own thread shows its failure screen; show it again from the client thread, where the session lives.
        if (event.gui instanceof GuiDisconnected && !mc.isCallingFromMinecraftThread()) {
            final GuiScreen failure = event.gui;
            event.setCanceled(true);
            mc.addScheduledTask(() -> mc.displayGuiScreen(failure));
            return;
        }
        // GuiConnecting's constructor already made its server the current one.
        if (event.gui instanceof GuiConnecting && mc.getCurrentServerData() != null) {
            final ServerData server = mc.getCurrentServerData();
            SESSION.begin(server.serverIP, () -> FMLClientHandler.instance().connectToServer(new GuiMultiplayer(new GuiMainMenu()), server), account());
        }
    }

    /** Below vanilla's back button, re-added when the screen re-initializes (resize), which never restarts the countdown. */
    @SubscribeEvent
    public void init(GuiScreenEvent.InitGuiEvent.Post event) {
        if (!(event.gui instanceof GuiDisconnected)) return;
        retry = cancel = null;
        if (!active() || !SESSION.canRetry(account(), module().initial.get()) || event.buttonList.isEmpty()) return;
        GuiButton back = event.buttonList.get(event.buttonList.size() - 1);
        retry = new GuiButton(RETRY, back.xPosition, back.yPosition + 24, back.getButtonWidth(), 20, "Reconnect");
        cancel = new GuiButton(CANCEL, back.xPosition, back.yPosition + 48, back.getButtonWidth(), 20, "Cancel reconnect");
        event.buttonList.add(retry);
        event.buttonList.add(cancel);
        IChatComponent reason = ((GuiDisconnectedAccessor) event.gui).getMessage();
        List<String> keys = new ArrayList<>();
        translationKeys(reason, keys, 0);
        SESSION.disconnected(event.gui, keys, reason.getUnformattedText(), lists, module().reasonMode.getIndex() == 1, module().infinite.get(), System.nanoTime());
        refresh();
    }

    private static void translationKeys(IChatComponent component, List<String> keys, int depth) {
        if (depth > 8 || keys.size() > 32) return;
        if (component instanceof ChatComponentTranslation) {
            keys.add(((ChatComponentTranslation) component).getKey());
            for (Object argument : ((ChatComponentTranslation) component).getFormatArgs())
                if (argument instanceof IChatComponent) translationKeys((IChatComponent) argument, keys, depth + 1);
        }
        for (IChatComponent sibling : component.getSiblings()) translationKeys(sibling, keys, depth + 1);
    }

    @SubscribeEvent
    public void press(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (!(event.gui instanceof GuiDisconnected) || retry == null) return;
        if (event.button == retry) SESSION.reconnectNow();
        else if (event.button == cancel) { SESSION.cancelCountdown(); refresh(); }
        else return;
        event.setCanceled(true);
    }

    /** Vanilla ignores Escape here; with a countdown running it cancels the countdown. */
    @SubscribeEvent
    public void key(GuiScreenEvent.KeyboardInputEvent.Pre event) {
        if (event.gui instanceof GuiDisconnected && retry != null && Keyboard.getEventKeyState() && Keyboard.getEventKey() == Keyboard.KEY_ESCAPE
            && SESSION.cancelCountdown()) {
            refresh();
            event.setCanceled(true);
        }
    }

    private static void refresh() {
        if (retry == null) return;
        retry.displayString = SESSION.retryLabel(System.nanoTime());
        cancel.enabled = SESSION.counting();
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        GuiScreen screen = mc.currentScreen;
        if (!active() || screen instanceof GuiMainMenu || screen instanceof GuiMultiplayer || screen instanceof GuiSelectWorld) {
            SESSION.clear();
            joinedVia = mc.getNetHandler();
            return;
        }
        NetHandlerPlayClient connection = mc.getNetHandler();
        if (connection != joinedVia) {
            joinedVia = connection;
            if (connection != null) joined(mc, connection);
        }
        long now = System.nanoTime();
        if (SESSION.due(screen, now)) {
            if (SESSION.canRetry(account(), module().initial.get())) SESSION.reconnect();
        } else if (screen instanceof GuiDisconnected) refresh();
        // 1.8.9 chat is unsigned; Forge's client commands run first, as GuiChat does.
        SESSION.sendDue(mc.thePlayer == null ? null : connection, account(), module().actionsEnabled.get(), now, (text, signed) -> {
            if (ClientCommandHandler.instance.executeCommand(mc.thePlayer, text) == 0) mc.thePlayer.sendChatMessage(text);
        });
    }

    private static void joined(Minecraft mc, NetHandlerPlayClient connection) {
        IntegratedServer local = mc.getIntegratedServer();
        if (mc.isIntegratedServerRunning() && local != null) {
            final String folder = local.getFolderName(), name = local.getWorldName();
            if (!folder.equals(SESSION.target())) SESSION.begin(folder, () -> openWorld(folder, name), account());
        }
        AutoReconnectModule module = module();
        if (SESSION.joined(connection, lists, module.actionsEnabled.get(), module.regexIds.get(), module.signedCommands.get(), System.nanoTime()))
            LogManager.getLogger("TheLadsCore").info("Lads AutoReconnect: reconnected to {}", SESSION.target());
    }

    private static void openWorld(String folder, String name) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!mc.getSaveLoader().canLoadWorld(folder)) { SESSION.clear(); return; }
        mc.launchIntegratedServer(folder, name, null);
    }
}
