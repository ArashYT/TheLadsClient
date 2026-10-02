// SPDX-License-Identifier: LGPL-3.0-only
// Reconnect behaviour adapted from AutoReconnect, Copyright 2023 Bstn1802, 2026 TerminalMC (26.x NativeReconnect).
package com.thelads.core.v1_8_9.feature;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.thelads.core.client.ReconnectActions;
import com.thelads.core.client.ReconnectFilters;
import com.thelads.core.client.ReconnectPlan;
import com.thelads.core.modules.AutoReconnectModule;
import com.thelads.core.v1_8_9.gui.ReconnectActionsScreen189;
import com.thelads.core.v1_8_9.gui.ReconnectOptionsScreen189;
import com.thelads.core.v1_8_9.mixin.GuiDisconnectedAccessor;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
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
 * AutoReconnect on 1.8.9, as 26.x NativeReconnect: servers (captured as GuiConnecting opens) and local worlds (MinecraftMixin)
 * retry from GuiDisconnected with the configured delays and reason filters; Escape cancels a pending retry. Forge's screen events
 * add the Reconnect and Cancel buttons. All state lives on the client thread: GuiConnecting's connector thread opens its failure
 * screen itself, so that screen is reopened on the client thread. 1.8.9 has no working Realms and no signed chat.
 */
public final class Reconnect189 {
    interface Target { String id(); void connect(); boolean local(); }
    private static final int RETRY = 1189, CANCEL = 1190;
    private static final ReconnectPlan PLAN = new ReconnectPlan();
    private static final ReconnectActions ACTIONS = new ReconnectActions();
    private static Target target;
    private static UUID identity;
    private static boolean connected, dispatching, reasonAllowed, openOptions;
    private static GuiDisconnected dialog;
    private static GuiButton retryButton, cancelButton;
    private static Settings settings = new Settings();
    private static NetHandlerPlayClient tracked;
    public static String lastReason = "", lastReasonKey = "";

    public static AutoReconnectModule module() {
        return Options189.module("AutoReconnect") instanceof AutoReconnectModule ? (AutoReconnectModule) Options189.module("AutoReconnect") : null;
    }
    public static boolean active() { return module() != null && module().isEnabled(); }
    public static Settings settings() { return settings; }
    public static void replaceSettings(Settings value) { value.save(); settings = value; cancelActive(); }
    /** QA only (Probe150): lists in use without saving them. */
    static Settings swapSettings(Settings value) { Settings old = settings; settings = value; return old; }

    public static void register() {
        if (module() == null) return;
        settings = Settings.load();
        Minecraft mc = Minecraft.getMinecraft();
        module().retryEditor.setAction(() -> mc.displayGuiScreen(new ReconnectOptionsScreen189(mc.currentScreen)));
        module().actionsEditor.setAction(() -> mc.displayGuiScreen(new ReconnectActionsScreen189(mc.currentScreen)));
        ClientCommandHandler.instance.registerCommand(new Command());
        MinecraftForge.EVENT_BUS.register(new Reconnect189());
    }

    private static UUID identity() {
        Minecraft mc = Minecraft.getMinecraft();
        UUID id = mc.getSession().getProfile().getId();
        return id != null ? id : UUID.nameUUIDFromBytes(mc.getSession().getUsername().getBytes(StandardCharsets.UTF_8));
    }

    private static void capture(Target candidate) {
        if (!active() || dispatching || PLAN.wasAutomatic()) return;
        target = candidate;
        identity = identity();
        connected = false;
        PLAN.cancel();
        ACTIONS.clear();
        dialog = null;
    }

    static void server(ServerData source) {
        if (source == null || !active()) return;
        final ServerData copy = new ServerData(source.serverName, source.serverIP, source.isOnLAN());
        copy.copyFrom(source);
        capture(new Target() {
            public String id() { return copy.serverIP; }
            public boolean local() { return false; }
            public void connect() { FMLClientHandler.instance().connectToServer(new GuiMultiplayer(new GuiMainMenu()), copy); }
        });
    }

    /** Minecraft.launchIntegratedServer (MinecraftMixin), once the world is really opening. */
    public static void world(final String folder, final String name) {
        capture(new Target() {
            public String id() { return folder; }
            public boolean local() { return true; }
            public void connect() {
                Minecraft mc = Minecraft.getMinecraft();
                if (!mc.getSaveLoader().canLoadWorld(folder)) { cancelAll(); return; }
                mc.launchIntegratedServer(folder, name, null);
            }
        });
    }

    public static boolean canReconnect() {
        return active() && target != null && identity.equals(identity()) && (connected || module().initial.get() || PLAN.wasAutomatic());
    }
    public static boolean local() { return target != null && target.local(); }
    public static String targetId() { return target == null ? "" : target.id(); }
    /** QA only: the dialog's buttons while it has them. */
    public static GuiButton retryButton() { return retryButton; }
    public static GuiButton cancelButton() { return cancelButton; }

    static void attach(GuiDisconnected screen, IChatComponent reason, GuiButton retry, GuiButton cancel) {
        if (!canReconnect()) return;
        retryButton = retry;
        cancelButton = cancel;
        if (dialog != screen) {
            dialog = screen;
            List<String> keys = new ArrayList<>();
            keys(reason, keys);
            lastReason = reason.getUnformattedText();
            lastReasonKey = keys.isEmpty() ? "" : keys.get(0);
            reasonAllowed = ReconnectFilters.allows(keys, lastReason, settings.conditionKeys, settings.conditionPatterns, module().reasonMode.getIndex() == 1);
            if (reasonAllowed) PLAN.schedule(settings.delays, module().infinite.get(), System.nanoTime());
        }
        updateButtons();
    }

    static void keys(IChatComponent component, List<String> result) {
        if (result.size() >= 64) return;
        if (component instanceof ChatComponentTranslation) {
            result.add(((ChatComponentTranslation) component).getKey());
            for (Object argument : ((ChatComponentTranslation) component).getFormatArgs())
                if (argument instanceof IChatComponent) keys((IChatComponent) argument, result);
        }
        for (IChatComponent child : component.getSiblings()) keys(child, result);
    }

    public static boolean cancelCountdown() {
        boolean pending = PLAN.pending();
        cancelActive();
        return pending;
    }
    public static void cancelActive() { PLAN.cancel(); ACTIONS.clear(); reasonAllowed = false; updateButtons(); }
    public static void cancelAll() {
        cancelActive();
        target = null;
        identity = null;
        connected = false;
        dialog = null;
        retryButton = cancelButton = null;
    }

    public static void manual() {
        if (!canReconnect()) return;
        PLAN.cancel();
        ACTIONS.clear();
        perform();
    }

    private static void perform() {
        if (!canReconnect()) return;
        dispatching = true;
        try { target.connect(); }
        finally { dispatching = false; }
    }

    private static void screenChanged(GuiScreen old, GuiScreen next) {
        if (dispatching || old == next) return;
        if (next instanceof GuiMainMenu || next instanceof GuiMultiplayer || next instanceof GuiSelectWorld) cancelAll();
        else if (old == dialog && next != dialog && !(next instanceof GuiConnecting)) { cancelActive(); dialog = null; }
    }

    private static void joined(NetHandlerPlayClient connection) {
        if (!active() || target == null) return;
        boolean automatic = PLAN.joined();
        connected = true;
        dialog = null;
        retryButton = cancelButton = null;
        ACTIONS.clear();
        if (!automatic || !module().actionsEnabled.get()) return;
        ACTIONS.begin(System.nanoTime());
        for (Settings.Action action : settings.autoMessages) {
            if (!action.enabled || !ReconnectFilters.contextMatches(action.id, target.id(), module().regexIds.get())) continue;
            ACTIONS.add(connection, identity, action.delay, action.messages, module().signedCommands.get());
        }
    }

    private static void updateButtons() {
        if (retryButton == null) return;
        int seconds = PLAN.secondsLeft(System.nanoTime());
        retryButton.displayString = seconds >= 0 ? "Reconnect in " + seconds + "s" : reasonAllowed ? "Retry limit reached · Reconnect" : "Reconnect";
        retryButton.enabled = canReconnect();
        if (cancelButton != null) cancelButton.enabled = seconds >= 0;
    }

    /** Where the dialog goes back to: a local world's dialog to the world list, as on 26.x. */
    private static GuiScreen parent(GuiDisconnected screen) {
        return local() ? new GuiSelectWorld(new GuiMainMenu()) : ((GuiDisconnectedAccessor) screen).getParentScreen();
    }

    @SubscribeEvent
    public void open(GuiOpenEvent event) {
        final Minecraft mc = Minecraft.getMinecraft();
        if (event.gui instanceof GuiDisconnected && active() && !mc.isCallingFromMinecraftThread()) {
            final GuiScreen failure = event.gui;
            event.setCanceled(true);
            mc.addScheduledTask(() -> mc.displayGuiScreen(failure));
            return;
        }
        screenChanged(mc.currentScreen, event.gui);
        if (event.gui instanceof GuiConnecting) server(mc.getCurrentServerData());
    }

    /** Below vanilla's back button, as 26.x adds them to the disconnect screen's layout. */
    @SubscribeEvent
    public void init(GuiScreenEvent.InitGuiEvent.Post event) {
        if (!(event.gui instanceof GuiDisconnected) || !canReconnect() || event.buttonList.isEmpty()) return;
        GuiDisconnected screen = (GuiDisconnected) event.gui;
        GuiButton back = event.buttonList.get(event.buttonList.size() - 1);
        if (local()) back.displayString = "Back to World List";
        GuiButton retry = new GuiButton(RETRY, back.xPosition, back.yPosition + 24, back.getButtonWidth(), 20, "Reconnect");
        GuiButton cancel = new GuiButton(CANCEL, back.xPosition, back.yPosition + 48, back.getButtonWidth(), 20, "Cancel reconnect");
        event.buttonList.add(retry);
        event.buttonList.add(cancel);
        attach(screen, ((GuiDisconnectedAccessor) screen).getMessage(), retry, cancel);
    }

    @SubscribeEvent
    public void press(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (event.gui != dialog || retryButton == null) return;
        if (event.button.id == RETRY) manual();
        else if (event.button.id == CANCEL) cancelCountdown();
        else if (event.button.id == 0 && local()) Minecraft.getMinecraft().displayGuiScreen(parent(dialog));
        else return;
        event.setCanceled(true);
    }

    /** Escape (vanilla's disconnect screen ignores it): the first cancels a pending retry, the next goes back. */
    @SubscribeEvent
    public void key(GuiScreenEvent.KeyboardInputEvent.Pre event) {
        if (event.gui != dialog || retryButton == null || !Keyboard.getEventKeyState() || Keyboard.getEventKey() != Keyboard.KEY_ESCAPE) return;
        event.setCanceled(true);
        if (cancelCountdown()) return;
        GuiScreen parent = parent(dialog);
        cancelAll();
        Minecraft.getMinecraft().displayGuiScreen(parent);
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        final Minecraft mc = Minecraft.getMinecraft();
        if (openOptions) {
            openOptions = false;
            mc.displayGuiScreen(new ReconnectOptionsScreen189(mc.currentScreen));
        }
        NetHandlerPlayClient connection = mc.getNetHandler();
        if (connection != tracked) {
            tracked = connection;
            if (connection != null) joined(connection);
        }
        if (!active() || (identity != null && !identity.equals(identity()))) { cancelAll(); return; }
        if (dialog != null && mc.currentScreen == dialog && PLAN.pending()) {
            if (PLAN.takeDue(System.nanoTime())) perform();
            else updateButtons();
        }
        // 1.8.9 sends commands as chat, unsigned; Forge's client commands run here first, as GuiChat does.
        ACTIONS.drain(mc.thePlayer == null ? null : connection, identity(), module().actionsEnabled.get(), System.nanoTime(), (message, signed) -> {
            if (ClientCommandHandler.instance.executeCommand(mc.thePlayer, message) == 0) mc.thePlayer.sendChatMessage(message);
        });
    }

    /** /ladsreconnect (and /autoreconnectrf): the delays and filters; "disconnect" ends this connection, only when typed. */
    private static final class Command extends CommandBase {
        @Override public String getCommandName() { return "ladsreconnect"; }
        @Override public List<String> getCommandAliases() { return Collections.singletonList("autoreconnectrf"); }
        @Override public String getCommandUsage(ICommandSender sender) { return "/ladsreconnect [disconnect]"; }
        @Override public boolean canCommandSenderUseCommand(ICommandSender sender) { return true; }
        @Override public int getRequiredPermissionLevel() { return 0; }

        @Override
        public void processCommand(ICommandSender sender, String[] args) {
            Minecraft mc = Minecraft.getMinecraft();
            if (args.length > 0 && args[0].equals("disconnect")) {
                if (mc.getNetHandler() != null)
                    mc.getNetHandler().getNetworkManager().closeChannel(new ChatComponentText("Disconnected by your Lads reconnect command"));
            } else openOptions = true; // GuiChat closes itself right after the command
        }
    }

    /** The editable lists, as 26.x ReconnectSettings, in config/theladscore/reconnect.json. */
    public static final class Settings {
        private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
        private static final int MAX_FILE_BYTES = 4 * 1024 * 1024;
        public List<Integer> delays = new ArrayList<>(Arrays.asList(3, 10, 30, 60));
        public List<String> conditionKeys = new ArrayList<>(Arrays.asList("disconnect.loginFailedInfo", "disconnect.spam", "disconnect.timeout",
            "disconnect.unknownHost", "multiplayer.disconnect.banned", "multiplayer.disconnect.code_of_conduct", "multiplayer.disconnect.incompatible",
            "multiplayer.disconnect.ip_banned", "multiplayer.disconnect.kicked", "multiplayer.disconnect.name_taken", "multiplayer.disconnect.not_whitelisted",
            "multiplayer.disconnect.outdated_client", "multiplayer.disconnect.outdated_server"));
        public List<String> conditionPatterns = new ArrayList<>();
        public List<Action> autoMessages = new ArrayList<>();

        public static final class Action {
            public String id = "";
            public double delay = 1;
            public List<String> messages = new ArrayList<>();
            public boolean enabled;
        }

        public Settings copy() { return GSON.fromJson(GSON.toJson(this), Settings.class); }
        private static File file() { return new File(Loader.instance().getConfigDir(), "theladscore/reconnect.json"); }

        static Settings load() {
            try {
                File file = file();
                if (!file.isFile() || file.length() > MAX_FILE_BYTES) return new Settings();
                Settings result = GSON.fromJson(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8), Settings.class);
                if (result == null) return new Settings();
                result.validate();
                return result;
            } catch (IOException | RuntimeException invalid) {
                LogManager.getLogger("TheLadsCore").warn("Reconnect preferences could not be read; the original file was preserved: {}", invalid.toString());
                return new Settings();
            }
        }

        public void validate() {
            List<Integer> seconds = new ArrayList<>();
            if (delays != null) for (Integer value : delays) if (value != null && value > 0 && value <= 86400 && seconds.size() < 100) seconds.add(value);
            delays = seconds;
            conditionKeys = clean(conditionKeys, 128);
            conditionPatterns = clean(conditionPatterns, 128);
            List<Action> actions = new ArrayList<>();
            if (autoMessages != null) for (Action action : autoMessages) if (action != null && actions.size() < 64) actions.add(action);
            autoMessages = actions;
            for (Action action : autoMessages) {
                if (action.id == null) action.id = "";
                if (!(Double.isFinite(action.delay) && action.delay >= .1 && action.delay <= 3600)) action.delay = 1;
                action.messages = clean(action.messages, 100);
                boolean tooLong = action.id.length() > 512;
                for (String text : action.messages) tooLong |= text.length() > (text.startsWith("/") ? 32767 : 256);
                if (tooLong) action.enabled = false;
            }
        }

        private static List<String> clean(List<String> input, int count) {
            List<String> result = new ArrayList<>();
            if (input != null) for (String value : input) if (value != null && result.size() < count) result.add(value);
            return result;
        }

        public void save() {
            validate();
            String encoded = GSON.toJson(this);
            if (encoded.getBytes(StandardCharsets.UTF_8).length > MAX_FILE_BYTES)
                throw new IllegalArgumentException("Reconnect settings exceed 4 MiB. Remove some action messages before saving.");
            File file = file();
            java.nio.file.Path temporary = null;
            try {
                Files.createDirectories(file.getParentFile().toPath());
                temporary = Files.createTempFile(file.getParentFile().toPath(), "reconnect-", ".tmp");
                Files.write(temporary, encoded.getBytes(StandardCharsets.UTF_8));
                try { Files.move(temporary, file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (java.nio.file.AtomicMoveNotSupportedException unavailable) { Files.move(temporary, file.toPath(), StandardCopyOption.REPLACE_EXISTING); }
            } catch (IOException unavailable) {
                throw new IllegalStateException("Reconnect settings could not be saved", unavailable);
            } finally {
                if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) {}
            }
        }
    }
}
