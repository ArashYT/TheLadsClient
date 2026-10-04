package com.thelads.core.v26_2.feature;

import com.thelads.core.client.ReconnectSession;
import com.thelads.core.client.ReconnectSettings;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.modules.AutoReconnectModule;
import com.thelads.core.v26_2.gui.ReconnectActionsScreen26;
import com.thelads.core.v26_2.gui.ReconnectOptionsScreen26;
import com.thelads.core.v26_2.mixin.DisconnectedScreenAccess;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.LoggerFactory;

/**
 * AutoReconnect on 26.x: ReconnectSession fed by the server connect hook (ReconnectTargetMixin), Fabric's join event (local
 * worlds), the disconnect screen's init (Reconnect and Cancel buttons) and the client tick (countdown, join actions).
 */
public final class NativeReconnect {
    private static final ReconnectSession SESSION = new ReconnectSession();
    private static ReconnectSettings lists = new ReconnectSettings();
    private static boolean available;
    private static Button retry, cancel;
    private NativeReconnect() {}

    public static AutoReconnectModule module() { return (AutoReconnectModule) NativeQualityOfLife.module("AutoReconnect"); }
    public static ReconnectSettings settings() { return lists; }
    public static void replaceSettings(ReconnectSettings value) { value.save(file()); lists = value; SESSION.cancelCountdown(); }
    static ReconnectSession session() { return SESSION; }
    static void useSettings(ReconnectSettings value) { lists = value; }
    public static boolean active() { return available && module() != null && module().isEnabled(); }
    private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("theladscore/reconnect.json"); }
    private static UUID account() { return Minecraft.getInstance().getUser().getProfileId(); }

    public static void register() {
        if (available || FabricLoader.getInstance().isModLoaded("autoreconnectrf")) return;
        available = true;
        lists = ReconnectSettings.load(file());
        Minecraft mc = Minecraft.getInstance();
        module().retryEditor.setAction(() -> mc.gui.setScreen(new ReconnectOptionsScreen26(mc.gui.screen())));
        module().actionsEditor.setAction(() -> mc.gui.setScreen(new ReconnectActionsScreen26(mc.gui.screen())));
        ModuleSupport.registerBuiltIn("AutoReconnect");
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> joined(client, handler));
        ClientTickEvents.END_CLIENT_TICK.register(client -> { NativeReconnectProbe.tick(); tick(client); });
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof DisconnectedScreen disconnected) addButtons(disconnected);
        });
    }

    /** ConnectScreen.startConnecting: the server being joined (also a server transfer). Kept as given, so a retry dials the same address. */
    public static void connecting(ServerAddress address, ServerData server, TransferState transfer) {
        if (!active() || server == null) return;
        SESSION.begin(server.ip, () -> ConnectScreen.startConnecting(new JoinMultiplayerScreen(new TitleScreen()), Minecraft.getInstance(),
            address, server, false, transfer), account());
    }

    private static void joined(Minecraft mc, ClientPacketListener connection) {
        if (!active()) return;
        var local = mc.getSingleplayerServer();
        if (local != null) {
            String folder = local.getWorldPath(LevelResource.ROOT).normalize().getFileName().toString();
            if (!folder.equals(SESSION.target())) SESSION.begin(folder, () -> openWorld(folder), account());
        }
        AutoReconnectModule module = module();
        if (SESSION.joined(connection, lists, module.actionsEnabled.get(), module.regexIds.get(), module.signedCommands.get(), System.nanoTime()))
            LoggerFactory.getLogger("TheLadsCore").info("Lads AutoReconnect: reconnected to {}", SESSION.target());
    }

    static void openWorld(String folder) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.getLevelSource().levelExists(folder)) { SESSION.clear(); return; }
        mc.createWorldOpenFlows().openWorld(folder, () -> mc.gui.setScreen(new SelectWorldScreen(new TitleScreen())));
    }

    /** Below the screen's last button; re-added when the screen re-initializes (resize), which never restarts the countdown. */
    private static void addButtons(DisconnectedScreen screen) {
        retry = cancel = null;
        if (!active() || !SESSION.canRetry(account(), module().initial.get())) return;
        List<AbstractWidget> widgets = Screens.getWidgets(screen);
        AbstractWidget last = null;
        for (AbstractWidget widget : widgets) if (widget instanceof Button) last = widget;
        int width = last == null ? 200 : last.getWidth(), x = last == null ? (screen.width - width) / 2 : last.getX();
        int y = last == null ? screen.height / 2 : last.getY() + 24;
        retry = Button.builder(Component.literal("Reconnect"), button -> SESSION.reconnectNow()).bounds(x, y, width, 20).build();
        cancel = Button.builder(Component.literal("Cancel reconnect"), button -> { SESSION.cancelCountdown(); refresh(); }).bounds(x, y + 24, width, 20).build();
        widgets.add(retry);
        widgets.add(cancel);
        Component reason = ((DisconnectedScreenAccess) screen).lads$details().reason();
        List<String> keys = new ArrayList<>();
        translationKeys(reason, keys, 0);
        SESSION.disconnected(screen, keys, reason.getString(), lists, module().reasonMode.getIndex() == 1, module().infinite.get(), System.nanoTime());
        refresh();
        // Vanilla ignores Escape here; with a countdown running it cancels the countdown.
        ScreenKeyboardEvents.allowKeyPress(screen).register((shown, key) -> {
            if (!key.isEscape() || !SESSION.cancelCountdown()) return true;
            refresh();
            return false;
        });
    }

    private static void translationKeys(Component component, List<String> keys, int depth) {
        if (depth > 8 || keys.size() > 32) return;
        if (component.getContents() instanceof TranslatableContents translatable) {
            keys.add(translatable.getKey());
            for (Object argument : translatable.getArgs()) if (argument instanceof Component child) translationKeys(child, keys, depth + 1);
        }
        for (Component sibling : component.getSiblings()) translationKeys(sibling, keys, depth + 1);
    }

    private static void refresh() {
        if (retry == null) return;
        retry.setMessage(Component.literal(SESSION.retryLabel(System.nanoTime())));
        cancel.active = SESSION.counting();
    }

    static Button retryButton() { return retry; }
    static Button cancelButton() { return cancel; }

    private static void tick(Minecraft mc) {
        if (!available) return;
        Screen screen = mc.gui.screen();
        if (!module().isEnabled() || screen instanceof TitleScreen || screen instanceof JoinMultiplayerScreen || screen instanceof SelectWorldScreen) {
            SESSION.clear();
            return;
        }
        long now = System.nanoTime();
        if (SESSION.due(screen, now)) {
            if (SESSION.canRetry(account(), module().initial.get())) SESSION.reconnect();
        } else if (screen instanceof DisconnectedScreen) refresh();
        ClientPacketListener connection = mc.getConnection();
        SESSION.sendDue(mc.player == null ? null : connection, account(), module().actionsEnabled.get(), now, (text, signed) -> {
            if (!text.startsWith("/")) connection.sendChat(text);
            else if (signed) connection.sendCommand(text.substring(1));
            else connection.sendUnattendedCommand(text.substring(1), mc.gui.screen());
        });
    }
}
