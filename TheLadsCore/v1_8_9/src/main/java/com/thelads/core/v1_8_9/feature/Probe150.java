package com.thelads.core.v1_8_9.feature;

import static com.thelads.core.v1_8_9.feature.CoreProbe.after;
import static com.thelads.core.v1_8_9.feature.CoreProbe.check;
import static com.thelads.core.v1_8_9.feature.CoreProbe.screenshot;

import com.mojang.authlib.GameProfile;
import com.thelads.core.client.killbanner.KillBanners;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.config.SliderOption;
import com.thelads.core.modules.AutoReconnectModule;
import com.thelads.core.modules.KillBannerModule;
import com.thelads.core.v1_8_9.gui.LadsSettingsScreen189;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiDisconnected;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSelectWorld;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.event.ClickEvent;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.scoreboard.IScoreObjectiveCriteria;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import org.lwjgl.input.Keyboard;

/**
 * QA only: the 1.5.0 module ports, run by CoreProbe in its QA world after Probe145, each through the real 1.8.9 path and put
 * back as it was found.
 */
final class Probe150 {
    static final List<CoreProbe.Step> STEPS = new ArrayList<>(Arrays.<CoreProbe.Step>asList(Probe150::threadsStart, Probe150::threads,
        Probe150::discord, Probe150::tabStart, Probe150::tab, Probe150::chatStart, Probe150::chat,
        Probe150::crosshairStart, Probe150::crosshair, Probe150::crosshairHidden, Probe150::crosshairEditor, Probe150::crosshairDone,
        Probe150::reconnectStart, Probe150::reconnectDialog, Probe150::reconnectCancelled, Probe150::reconnectBack, Probe150::reconnectEditor,
        Probe150::reconnectDone, Probe150::killStart, Probe150::killShown, Probe150::killPicker));
    private static boolean wasEnabled;
    private static double wasValue;
    private static int priority;
    private static boolean pingWas, tabWas, countWas;
    private static long pings;
    private static ScoreObjective listed, objective;
    private static final String[] CHAT = {"Timestamps", "Screenshot Link Buttons", "Message Animations", "Chat Background"};
    private static final boolean[] chatWas = new boolean[CHAT.length];
    private static boolean chatOn;
    private static double widthWas;
    private static long messages, animated;
    private static boolean crosshairWas, hiddenWas;
    private static long crosshairs;
    private static boolean reconnectWas, initialWas;
    private static Reconnect189.Settings reconnectSettings;
    private static GuiScreen dialog;
    private static boolean killWas, mobsWas;
    private static long banners, thumbs;

    private Probe150() {}

    /** Threads: the monitor (every second) raises 1.8.9's "Client thread" to the render priority, and restores it when off. */
    private static boolean threadsStart(Minecraft mc) {
        Module threads = module("Threads");
        SliderOption render = (SliderOption) threads.getOption("Render thread priority");
        wasEnabled = threads.isEnabled();
        wasValue = render.getValue();
        threads.setEnabled(false);
        priority = Thread.currentThread().getPriority();
        render.setValue(priority == 7 ? 6 : 7);
        threads.setEnabled(true);
        return after(30);
    }

    private static boolean threads(Minecraft mc) {
        Module threads = module("Threads");
        SliderOption render = (SliderOption) threads.getOption("Render thread priority");
        Thread client = Thread.currentThread();
        check("Client thread".equals(client.getName()) && client.getPriority() == (int) render.getValue(),
            "Threads: 1.8.9's " + client.getName() + " runs at the render priority " + client.getPriority());
        threads.setEnabled(false);
        check(client.getPriority() == priority, "Threads off: its priority " + priority + " is restored");
        render.setValue(wasValue);
        threads.setEnabled(wasEnabled);
        return after(1);
    }

    /** DiscordRPC: built in as the "Soon" card the other versions show, which cannot be switched on and connects nowhere. */
    private static boolean discord(Minecraft mc) {
        check(ModuleSupport.isBuiltIn("DiscordRPC") && !ModuleSupport.isToggleable("DiscordRPC"),
            "DiscordRPC: the built-in \"Soon\" card, not switchable, as on the other versions");
        return after(1);
    }

    /** PingView and TabList: Tab held over a tab-list objective (1.8.9 shows a lone player's list only with one), header and footer. */
    private static boolean tabStart(Minecraft mc) {
        pingWas = module("PingView").isEnabled();
        tabWas = module("TabList").isEnabled();
        module("PingView").setEnabled(true);
        module("TabList").setEnabled(true);
        BoolOption count = (BoolOption) module("TabList").getOption("Show Player Count");
        countWas = count.get();
        count.set(true);
        Scoreboard board = mc.theWorld.getScoreboard();
        listed = board.getObjectiveInDisplaySlot(0);
        objective = board.addScoreObjective("lads_qa_tab", IScoreObjectiveCriteria.DUMMY);
        board.getValueFromObjective(mc.thePlayer.getName(), objective).setScorePoints(150);
        board.setObjectiveInDisplaySlot(0, objective);
        mc.ingameGUI.getTabList().setHeader(new ChatComponentText("Lads QA header"));
        mc.ingameGUI.getTabList().setFooter(new ChatComponentText("Lads QA footer"));
        pings = TabTweaks189.pings;
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindPlayerList.getKeyCode(), true);
        return after(10);
    }

    private static boolean tab(Minecraft mc) {
        check(TabTweaks189.layout && TabTweaks189.showPingInTab && TabTweaks189.showPlayerCount,
            "PingView and TabList: the tab list options reach GuiPlayerTabOverlayMixin's snapshot");
        check(TabTweaks189.pings > pings, "PingView: the held tab list drew its ping as a number in " + (TabTweaks189.pings - pings) + " rows");
        screenshot(mc, "150-tab-list");
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindPlayerList.getKeyCode(), false);
        mc.ingameGUI.getTabList().resetFooterHeader();
        Scoreboard board = mc.theWorld.getScoreboard();
        board.setObjectiveInDisplaySlot(0, listed);
        board.removeObjective(objective);
        ((BoolOption) module("TabList").getOption("Show Player Count")).set(countWas);
        module("PingView").setEnabled(pingWas);
        module("TabList").setEnabled(tabWas);
        return after(1);
    }

    /** Chat: a message and a screenshot message in a 200 px chat without line backgrounds, timestamped, the newest sliding in. */
    private static boolean chatStart(Minecraft mc) {
        Module chat = module("Chat");
        chatOn = chat.isEnabled();
        for (int i = 0; i < CHAT.length; i++) chatWas[i] = ((BoolOption) chat.getOption(CHAT[i])).get();
        widthWas = ((SliderOption) chat.getOption("Chat Width")).getValue();
        chat.setEnabled(true);
        for (int i = 0; i < CHAT.length; i++) ((BoolOption) chat.getOption(CHAT[i])).set(i < 3);
        ((SliderOption) chat.getOption("Chat Width")).setValue(200);
        messages = Chat189.messages;
        animated = Chat189.animated;
        mc.ingameGUI.getChatGUI().printChatMessage(new ChatComponentText("Lads QA chat"));
        ChatComponentText name = new ChatComponentText("lads-qa.png");
        name.getChatStyle().setChatClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, new File(mc.mcDataDir, "screenshots/lads-qa.png").getAbsolutePath()));
        mc.ingameGUI.getChatGUI().printChatMessage(new ChatComponentTranslation("screenshot.success", name));
        return after(2);
    }

    private static boolean chat(Minecraft mc) {
        check(Chat189.messages >= messages + 2, "Chat: both messages went through printChatMessageWithOptionalDeletion");
        check(Chat189.animated > animated, "Chat: the newest message's lines slid in over " + (Chat189.animated - animated) + " line draws");
        check(mc.ingameGUI.getChatGUI().getChatWidth() == 200, "Chat: Chat Width sizes 1.8.9's chat (" + mc.ingameGUI.getChatGUI().getChatWidth() + " px)");
        ChatComponentText name = new ChatComponentText("lads-qa.png");
        name.getChatStyle().setChatClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, new File(mc.mcDataDir, "screenshots/lads-qa.png").getAbsolutePath()));
        String text = Chat189.message(new ChatComponentTranslation("screenshot.success", name)).getUnformattedText();
        check(text.startsWith("[") && text.indexOf("] ") == 6 && text.endsWith("lads-qa.png [Open File] [Open Folder]"),
            "Chat: a timestamp and the screenshot buttons (" + text + ")");
        screenshot(mc, "150-chat");
        Module chat = module("Chat");
        for (int i = 0; i < CHAT.length; i++) ((BoolOption) chat.getOption(CHAT[i])).set(chatWas[i]);
        ((SliderOption) chat.getOption("Chat Width")).setValue(widthWas);
        chat.setEnabled(chatOn);
        return after(1);
    }

    /** Crosshair Tweaks: drawn in place of vanilla's through Forge's crosshair overlay event. */
    private static boolean crosshairStart(Minecraft mc) {
        crosshairWas = module("Crosshair Tweaks").isEnabled();
        module("Crosshair Tweaks").setEnabled(true);
        crosshairs = Crosshair189.frames;
        return after(5);
    }

    private static boolean crosshair(Minecraft mc) {
        check(Crosshair189.frames > crosshairs, "Crosshair Tweaks: the Lads crosshair replaced vanilla's in " + (Crosshair189.frames - crosshairs) + " frames");
        screenshot(mc, "150-crosshair");
        BoolOption hidden = (BoolOption) module("Crosshair Tweaks").getOption("Visible with Hidden HUD");
        hiddenWas = hidden.get();
        hidden.set(true);
        mc.gameSettings.hideGUI = true;
        crosshairs = Crosshair189.frames;
        return after(5);
    }

    /** F1 skips Forge's overlay: Visible with Hidden HUD draws the crosshair after the world. */
    private static boolean crosshairHidden(Minecraft mc) {
        check(Crosshair189.frames > crosshairs, "Crosshair Tweaks: Visible with Hidden HUD keeps it under F1 (" + (Crosshair189.frames - crosshairs) + " frames)");
        screenshot(mc, "150-crosshair-f1");
        mc.gameSettings.hideGUI = false;
        ((BoolOption) module("Crosshair Tweaks").getOption("Visible with Hidden HUD")).set(hiddenWas);
        ((com.thelads.core.modules.CrosshairModule) module("Crosshair Tweaks")).drawingEditor.run();
        return after(5);
    }

    private static boolean crosshairEditor(Minecraft mc) throws Exception {
        check(mc.currentScreen instanceof com.thelads.core.v1_8_9.gui.CrosshairDrawingScreen189, "Crosshair Tweaks: Drawing Editor opens the drawing editor");
        screenshot(mc, "150-crosshair-editor");
        CoreProbe.tap(org.lwjgl.input.Keyboard.KEY_ESCAPE, (char) 27);
        return after(5);
    }

    private static boolean crosshairDone(Minecraft mc) {
        check(mc.currentScreen == null, "Crosshair Tweaks: Escape leaves the editor unsaved, back to gameplay");
        module("Crosshair Tweaks").setEnabled(crosshairWas);
        return after(1);
    }

    /**
     * AutoReconnect: this QA world as the target (Retry Initial Failures, as it was opened before the module was on), then a
     * disconnect screen with a kick reason no filter matches. A 60 s delay, so no retry can start while the world runs.
     */
    private static boolean reconnectStart(Minecraft mc) {
        AutoReconnectModule module = Reconnect189.module();
        reconnectWas = module.isEnabled();
        initialWas = module.initial.get();
        module.setEnabled(true);
        module.initial.set(true);
        Reconnect189.Settings test = new Reconnect189.Settings();
        test.delays = new ArrayList<>(Collections.singletonList(60));
        reconnectSettings = Reconnect189.swapSettings(test);
        Reconnect189.world(mc.getIntegratedServer().getFolderName(), mc.getIntegratedServer().getWorldName());
        mc.displayGuiScreen(new GuiDisconnected(new GuiMultiplayer(new GuiMainMenu()), "disconnect.lost", new ChatComponentText("Lads QA kick")));
        dialog = mc.currentScreen;
        return after(5);
    }

    private static boolean reconnectDialog(Minecraft mc) throws Exception {
        GuiButton retry = Reconnect189.retryButton(), cancel = Reconnect189.cancelButton();
        check(mc.currentScreen == dialog && retry != null && retry.displayString.startsWith("Reconnect in ") && cancel != null && cancel.enabled
            && "Lads QA kick".equals(Reconnect189.lastReason), "AutoReconnect: the disconnect screen counts down to a retry of this world (" + (retry == null ? null : retry.displayString) + ")");
        screenshot(mc, "150-reconnect");
        CoreProbe.tap(Keyboard.KEY_ESCAPE, (char) 27);
        return after(2);
    }

    private static boolean reconnectCancelled(Minecraft mc) throws Exception {
        GuiButton retry = Reconnect189.retryButton(), cancel = Reconnect189.cancelButton();
        check(mc.currentScreen == dialog && retry != null && "Reconnect".equals(retry.displayString) && retry.enabled && !cancel.enabled,
            "AutoReconnect: the first Escape cancels the pending retry and keeps the screen (" + (retry == null ? null : retry.displayString) + ")");
        CoreProbe.tap(Keyboard.KEY_ESCAPE, (char) 27);
        return after(5);
    }

    private static boolean reconnectBack(Minecraft mc) {
        check(mc.currentScreen instanceof GuiSelectWorld && Reconnect189.targetId().isEmpty(), "AutoReconnect: the next Escape goes back to the world list, target cleared");
        Reconnect189.module().retryEditor.run();
        return after(5);
    }

    private static boolean reconnectEditor(Minecraft mc) throws Exception {
        check(mc.currentScreen instanceof com.thelads.core.v1_8_9.gui.ReconnectOptionsScreen189, "AutoReconnect: Delays and Disconnect Filters opens its editor");
        screenshot(mc, "150-reconnect-editor");
        CoreProbe.tap(Keyboard.KEY_ESCAPE, (char) 27);
        return after(5);
    }

    private static boolean reconnectDone(Minecraft mc) {
        check(mc.currentScreen instanceof GuiSelectWorld, "AutoReconnect: Escape leaves the editor unsaved");
        mc.displayGuiScreen(null);
        Reconnect189.swapSettings(reconnectSettings);
        Reconnect189.module().initial.set(initialWas);
        Reconnect189.module().setEnabled(reconnectWas);
        return after(5);
    }

    /** KillBanner: a preview banner (Reaver, 3 kills) drawn after Forge's overlay, with its sound registered from sounds.json. */
    private static boolean killStart(Minecraft mc) {
        KillBannerModule module = (KillBannerModule) module("KillBanner");
        killWas = module.isEnabled();
        mobsWas = module.mobs.get();
        module.setEnabled(true);
        module.mobs.set(true);
        banners = KillBanner189.frames;
        KillBanner189.trigger(3, true);
        return after(10);
    }

    /** Then the real signals: a hit mob's death status, and a hit player's kill message in chat. Client-side entities only. */
    private static boolean killShown(Minecraft mc) {
        check(KillBanner189.frames > banners, "KillBanner: the banner drew in " + (KillBanner189.frames - banners) + " frames after Forge's overlay");
        check(mc.getSoundHandler().getSound(new ResourceLocation("theladscore:reaver_kill_3")) != null, "KillBanner: 1.8.9 loaded the Kill Banner sounds.json");
        screenshot(mc, "150-kill-banner");
        KillBanner189.reset();
        EntityPig pig = new EntityPig(mc.theWorld);
        pig.setPosition(mc.thePlayer.posX + 2, mc.thePlayer.posY, mc.thePlayer.posZ);
        mc.theWorld.addEntityToWorld(-1500, pig);
        MinecraftForge.EVENT_BUS.post(new AttackEntityEvent(mc.thePlayer, pig));
        mc.getNetHandler().handleEntityStatus(new S19PacketEntityStatus(pig, (byte) 3));
        mc.theWorld.removeEntityFromWorld(-1500);
        check(KillBanners.TIMELINE.age(System.nanoTime()) >= 0 && !KillBanners.TIMELINE.preview() && KillBanners.TIMELINE.sequence() == 1,
            "KillBanner: a mob the player hit dies (entity status 3): its banner");
        EntityOtherPlayerMP victim = new EntityOtherPlayerMP(mc.theWorld, new GameProfile(UUID.randomUUID(), "LadsQaVictim"));
        mc.theWorld.addEntityToWorld(-1501, victim);
        MinecraftForge.EVENT_BUS.post(new AttackEntityEvent(mc.thePlayer, victim));
        MinecraftForge.EVENT_BUS.post(new ClientChatReceivedEvent((byte) 1, new ChatComponentText("LadsQaVictim was slain by " + mc.thePlayer.getName())));
        mc.theWorld.removeEntityFromWorld(-1501);
        check(KillBanners.TIMELINE.sequence() == 2, "KillBanner: a hit player's kill message in chat is the second kill (" + KillBanners.TIMELINE.sequence() + ")");
        LadsSettingsScreen189 settings = new LadsSettingsScreen189(null);
        mc.displayGuiScreen(settings);
        settings.openModule("KillBanner");
        thumbs = KillBanner189.thumbs;
        return after(10);
    }

    private static boolean killPicker(Minecraft mc) {
        check(KillBanner189.thumbs > thumbs, "KillBanner: the settings picker drew the banner art (" + (KillBanner189.thumbs - thumbs) + " thumbnails)");
        screenshot(mc, "150-kill-banner-picker");
        mc.displayGuiScreen(null);
        KillBanner189.reset();
        KillBannerModule module = (KillBannerModule) module("KillBanner");
        module.mobs.set(mobsWas);
        module.setEnabled(killWas);
        return after(5);
    }

    static Module module(String name) {
        return ModuleManager.getInstance().getModule(name);
    }
}
