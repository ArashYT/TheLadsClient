package com.thelads.core.v1_8_9.feature;

import static com.thelads.core.v1_8_9.feature.CoreProbe.after;
import static com.thelads.core.v1_8_9.feature.CoreProbe.check;
import static com.thelads.core.v1_8_9.feature.CoreProbe.screenshot;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.config.SliderOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.scoreboard.IScoreObjectiveCriteria;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.ChatComponentText;

/**
 * QA only: the 1.5.0 module ports, run by CoreProbe in its QA world after Probe145, each through the real 1.8.9 path and put
 * back as it was found.
 */
final class Probe150 {
    static final List<CoreProbe.Step> STEPS = new ArrayList<>(Arrays.<CoreProbe.Step>asList(Probe150::threadsStart, Probe150::threads,
        Probe150::discord, Probe150::tabStart, Probe150::tab));
    private static boolean wasEnabled;
    private static double wasValue;
    private static int priority;
    private static boolean pingWas, tabWas, countWas;
    private static long pings;
    private static ScoreObjective listed, objective;

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

    static Module module(String name) {
        return ModuleManager.getInstance().getModule(name);
    }
}
