package com.thelads.core.v26_2.feature;

import com.mojang.realmsclient.RealmsMainScreen;
import com.mojang.realmsclient.dto.RealmsServer;
import com.mojang.realmsclient.gui.screens.RealmsLongRunningMcoTaskScreen;
import com.mojang.realmsclient.util.task.GetServerDetailsTask;
import com.thelads.core.client.ReconnectFilters;
import com.thelads.core.client.ReconnectPlan;
import com.thelads.core.client.ReconnectActions;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.modules.AutoReconnectModule;
import com.thelads.core.v26_2.gui.ReconnectActionsScreen26;
import com.thelads.core.v26_2.gui.ReconnectOptionsScreen26;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
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

/** Native reconnect engine. All state and callbacks run on the client thread; no background countdown races. */
public final class NativeReconnect {
    interface Target { String id(); void connect(); default boolean local() { return false; } }
    private static final ReconnectPlan PLAN = new ReconnectPlan();
    private static final ReconnectActions ACTIONS = new ReconnectActions();
    private static Target target;
    private static UUID identity;
    private static boolean connected, dispatching, registered, reasonAllowed;
    private static DisconnectedScreen dialog;
    private static Button retryButton, cancelButton;
    private static ReconnectSettings settings;
    private static boolean nativeAvailable;
    public static String lastReason = "", lastReasonKey = "";
    private NativeReconnect() {}

    public static AutoReconnectModule module() { return (AutoReconnectModule) NativeQualityOfLife.module("AutoReconnect"); }
    public static ReconnectSettings settings() { return settings; }
    public static void replaceSettings(ReconnectSettings value) { value.save(); settings = value; cancelActive(); }
    public static boolean active() { return nativeAvailable && module() != null && module().isEnabled(); }
    public static boolean available() { return nativeAvailable; }

    public static void register() {
        if (registered || FabricLoader.getInstance().isModLoaded("autoreconnectrf")) return;
        registered = nativeAvailable = true;
        settings = ReconnectSettings.load(module());
        module().retryEditor.setAction(() -> Minecraft.getInstance().gui.setScreen(new ReconnectOptionsScreen26(Minecraft.getInstance().gui.screen())));
        module().actionsEditor.setAction(() -> Minecraft.getInstance().gui.setScreen(new ReconnectActionsScreen26(Minecraft.getInstance().gui.screen())));
        ModuleSupport.registerBuiltIn("AutoReconnect");
        ClientTickEvents.END_CLIENT_TICK.register(client -> { NativeReconnectProbe.tick(); tick(client); });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> joined(handler));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ACTIONS.clear());
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> {
            for (String alias : List.of("ladsreconnect", "autoreconnectrf")) {
                dispatcher.register(literal(alias).executes(context -> {
                    Minecraft client = Minecraft.getInstance();
                    client.execute(() -> client.gui.setScreen(new ReconnectOptionsScreen26(client.gui.screen())));
                    return 1;
                }).then(literal("disconnect").executes(context -> {
                    var connection = Minecraft.getInstance().getConnection();
                    if (connection == null) return 0;
                    // This diagnostic disconnect occurs only when the user explicitly types the command.
                    connection.getConnection().disconnect(Component.literal("Disconnected by your Lads reconnect command"));
                    return 1;
                })));
            }
        });
    }

    private static void capture(Target candidate) {
        if (!active()) return;
        if (dispatching || PLAN.wasAutomatic()) return;
        target = candidate;
        identity = Minecraft.getInstance().getUser().getProfileId();
        connected = false;
        PLAN.cancel(); ACTIONS.clear(); dialog = null;
    }

    public static void server(ServerAddress address, ServerData source, TransferState transfer) {
        if (source == null || !active()) return;
        ServerData copy = ServerData.read(source.write());
        ServerAddress attemptedAddress = address;
        capture(new Target() {
            public String id() { return copy.ip; }
            public void connect() {
                ConnectScreen.startConnecting(new JoinMultiplayerScreen(new TitleScreen()), Minecraft.getInstance(),
                    attemptedAddress, copy, false, transfer);
            }
        });
    }

    public static void world(String levelId) {
        capture(new Target() {
            public String id() { return levelId; }
            public boolean local() { return true; }
            public void connect() {
                Minecraft minecraft = Minecraft.getInstance();
                if (!minecraft.getLevelSource().levelExists(levelId)) { cancelAll(); return; }
                minecraft.setScreenAndShow(new GenericMessageScreen(Component.translatable("selectWorld.data_read")));
                minecraft.createWorldOpenFlows().openWorld(levelId, () -> minecraft.gui.setScreen(new SelectWorldScreen(new TitleScreen())));
            }
        });
    }

    public static void realm(RealmsServer original) {
        RealmsServer server = original.copy();
        capture(new Target() {
            public String id() { return server.getName(); }
            public void connect() {
                TitleScreen parent = new TitleScreen();
                Minecraft.getInstance().gui.setScreen(new RealmsLongRunningMcoTaskScreen(parent,
                    new GetServerDetailsTask(new RealmsMainScreen(parent), server)));
            }
        });
    }

    public static boolean canReconnect() {
        return active() && target != null && identity.equals(Minecraft.getInstance().getUser().getProfileId())
            && (connected || module().initial.get() || PLAN.wasAutomatic());
    }
    public static boolean local() { return target != null && target.local(); }
    public static String targetId() { return target == null ? "" : target.id(); }

    public static void attach(DisconnectedScreen screen, Component reason, Button retry, Button cancel) {
        if (!canReconnect()) return;
        retryButton = retry; cancelButton = cancel;
        if (dialog != screen) {
            dialog = screen;
            var keys = new ArrayList<String>(); keys(reason, keys);
            lastReason = reason.getString(); lastReasonKey = keys.isEmpty() ? "" : keys.getFirst();
            reasonAllowed = ReconnectFilters.allows(keys, lastReason, settings.conditionKeys, settings.conditionPatterns, module().reasonMode.getIndex() == 1);
            if (reasonAllowed) PLAN.schedule(settings.delays, module().infinite.get(), System.nanoTime());
        }
        updateButtons();
    }

    static void keys(Component component, List<String> result) {
        if (result.size() >= 64) return;
        if (component.getContents() instanceof TranslatableContents translated) {
            result.add(translated.getKey());
            for (Object argument : translated.getArgs()) if (argument instanceof Component child) keys(child, result);
        }
        for (Component child : component.getSiblings()) keys(child, result);
    }

    public static boolean cancelCountdown() {
        boolean pending = PLAN.pending(); cancelActive(); return pending;
    }
    public static void cancelActive() { PLAN.cancel(); ACTIONS.clear(); reasonAllowed = false; updateButtons(); }
    public static void cancelAll() { cancelActive(); target = null; identity = null; connected = false; dialog = null; retryButton = cancelButton = null; }

    public static void manual() {
        if (!canReconnect()) return;
        PLAN.cancel(); ACTIONS.clear();
        perform();
    }
    private static void perform() {
        if (!canReconnect()) return;
        dispatching = true;
        try { target.connect(); }
        finally { dispatching = false; }
    }

    public static void screenChanged(Screen old, Screen next) {
        if (!nativeAvailable || dispatching || old == next) return;
        if (next instanceof TitleScreen || next instanceof JoinMultiplayerScreen || next instanceof SelectWorldScreen || next instanceof RealmsMainScreen) {
            cancelAll();
        } else if (old == dialog && next != dialog && !(next instanceof ConnectScreen)) {
            cancelActive(); dialog = null;
        }
    }

    static void joined(ClientPacketListener connection) {
        if (!active() || target == null) return;
        boolean automatic = PLAN.joined(); connected = true; dialog = null;
        retryButton = cancelButton = null; ACTIONS.clear();
        if (!automatic || !module().actionsEnabled.get()) return;
        ACTIONS.begin(System.nanoTime());
        for (ReconnectSettings.Action action : settings.autoMessages) {
            if (!action.enabled || !ReconnectFilters.contextMatches(action.id, target.id(), module().regexIds.get())) continue;
            ACTIONS.add(connection, identity, action.delay, action.messages, module().signedCommands.get());
        }
    }

    private static void tick(Minecraft minecraft) {
        if (!active() || (identity != null && !identity.equals(minecraft.getUser().getProfileId()))) { cancelAll(); return; }
        if (dialog != null && minecraft.gui.screen() == dialog && PLAN.pending()) {
            if (PLAN.takeDue(System.nanoTime())) perform();
            else updateButtons();
        }
        ClientPacketListener connection = minecraft.getConnection();
        ACTIONS.drain(minecraft.player == null ? null : connection, minecraft.getUser().getProfileId(),
            module().actionsEnabled.get(), System.nanoTime(), (message, signed) -> {
                if (message.startsWith("/")) {
                    if (signed) connection.sendCommand(message.substring(1));
                    else connection.sendUnattendedCommand(message.substring(1), minecraft.gui.screen());
                } else connection.sendChat(message);
            });
    }

    private static void updateButtons() {
        if (retryButton == null) return;
        int seconds = PLAN.secondsLeft(System.nanoTime());
        retryButton.setMessage(Component.literal(seconds >= 0 ? "Reconnect in " + seconds + "s" : reasonAllowed ? "Retry limit reached · Reconnect" : "Reconnect"));
        retryButton.active = canReconnect();
        if (cancelButton != null) cancelButton.active = seconds >= 0;
    }
}
