package com.thelads.core.v26_2.feature.tabtweaks;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.thelads.core.config.*;
import com.thelads.core.config.Module;
import com.thelads.core.v26_2.feature.NativeQualityOfLife;
import com.thelads.core.v26_2.feature.tabtweaks.config.TabTweaksConfig;
import java.util.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.state.gui.*;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.*;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.joml.Matrix3x2f;
import org.slf4j.LoggerFactory;

/** Executes transformed vanilla rendering with local mock profiles; never installs players in a connection. */
public final class NativeTabTweaksProbe {
    private static boolean done;
    private static int ticks, passed;
    public static void tick() {
        var mc = Minecraft.getInstance();
        if (done || !Boolean.getBoolean("thelads.verifyIntegrations") || !NativeTabTweaks.active()
                || mc.player == null || mc.level == null || ++ticks < 90) return;
        done = true;
        var tab = NativeQualityOfLife.module("TabList"); var ping = NativeQualityOfLife.module("PingView");
        var previousTab = new Snapshot(tab); var previousPing = new Snapshot(ping);
        try {
            var game = FabricLoader.getInstance().getGameDir().toRealPath();
            check(game.getParent().getFileName().toString().equals("verification")
                    && game.getParent().getParent().getFileName().toString().equals("artifacts"), "isolated verification directory");
            tab.getOptions().forEach(Option::reset); ping.getOptions().forEach(Option::reset);
            tab.setEnabled(false); ping.setEnabled(false); TabTweaksConfig.refresh();
            var overlay = new PlayerTabOverlay(mc, mc.gui.hud);
            var access = (TabProbeAccess) overlay;
            var players = new ArrayList<PlayerInfo>();
            for (int i = 0; i < 82; i++) players.add(new FixturePlayer(i, 80));
            access.ladsTab$fixture(players);
            check(access.ladsTab$players().size() == 80, "disabled engine keeps vanilla player limit");
            var ordinary = ping(access, new FixturePlayer(0, 80));
            check(ordinary.text.isEmpty() && !ordinary.blits.isEmpty(), "disabled ping renders vanilla bars without extra numeric text");
            tab.setEnabled(true); ping.setEnabled(true);
            number("TabList", "Max Players", 7); number("TabList", "Players Per Column", 3);
            bool("TabList", "Below Boss Bars", false); number("TabList", "Y Offset", 0);
            color("TabList", "Header Color", 0x91223344); color("TabList", "Body Color", 0x92556677);
            color("TabList", "Footer Color", 0x938899aa); color("TabList", "Player Row Color", 0x9499aabb);
            overlay.setHeader(Component.literal("Probe header")); overlay.setFooter(Component.literal("Probe footer"));
            var scoreboard = new Scoreboard();
            var rendered = render(overlay, scoreboard, null);
            check(access.ladsTab$players().size() == 7, "native max-player limit changes actual selected profiles");
            check(rendered.text.stream().filter(t -> t.value.startsWith("Player")).count() == 7, "actual player names respect the configured cap");
            check(rendered.text.stream().filter(t -> t.value.startsWith("Player")).map(t -> t.x).distinct().count() == 3,
                    "players-per-column creates three actual columns");
            check(rendered.rectangles.stream().anyMatch(r -> r.col1() == 0x91223344)
                    && rendered.rectangles.stream().anyMatch(r -> r.col1() == 0x92556677)
                    && rendered.rectangles.stream().anyMatch(r -> r.col1() == 0x938899aa)
                    && rendered.rectangles.stream().filter(r -> r.col1() == 0x9499aabb).count() == 7,
                    "all four panel colors reach actual rectangle geometry");
            bool("TabList", "Header Shadow", false); bool("TabList", "Footer Shadow", false);
            rendered = render(overlay, scoreboard, null);
            check(!rendered.named("Probe header").shadow && !rendered.named("Probe footer").shadow
                    && rendered.named("Player000").shadow, "header and footer shadows are independent of player names");
            bool("TabList", "Body Shadow", false);
            check(!render(overlay, scoreboard, null).named("Player000").shadow, "body-shadow control changes actual name text");
            bool("TabList", "Hide Header", true); bool("TabList", "Hide Footer", true);
            rendered = render(overlay, scoreboard, null);
            check(rendered.text.stream().noneMatch(t -> t.value.startsWith("Probe")), "hidden header and footer produce no text");
            bool("TabList", "Hide Header", false); bool("TabList", "Hide Footer", false);
            int heads = render(overlay, scoreboard, null).blits.size();
            bool("TabList", "Hide Heads", true);
            check(render(overlay, scoreboard, null).blits.size() == heads - 14, "head switch removes both skin and hat for each rendered player");
            bool("TabList", "Hide Heads", false);
            access.ladsTab$fixture(List.of(new FixturePlayer(0, 80, 2), new FixturePlayer(1, 80, 4)));
            int allHeads = render(overlay, scoreboard, null).blits.size(); bool("TabList", "Hide NPC Heads", true);
            check(render(overlay, scoreboard, null).blits.size() == allHeads - 2, "UUID-v2 NPC head hiding retains genuine-player heads");
            bool("TabList", "Hide NPC Heads", false); access.ladsTab$fixture(players);
            var objective = scoreboard.addObjective("probe", ObjectiveCriteria.DUMMY, Component.literal("Probe score"), ObjectiveCriteria.RenderType.INTEGER, false, null);
            scoreboard.getOrCreatePlayerScore(ScoreHolder.forNameOnly("Player000"), objective).set(12345);
            check(render(overlay, scoreboard, objective).text.stream().anyMatch(t -> t.value.contains("12345")), "actual scoreboard objective renders");
            bool("TabList", "Hide Objectives", true);
            check(render(overlay, scoreboard, objective).text.stream().noneMatch(t -> t.value.contains("12345")), "objective switch removes score rendering");
            bool("TabList", "Hide Objectives", false);
            number("TabList", "Size", 150); number("TabList", "X Offset", 17); number("TabList", "Y Offset", 23);
            rendered = render(overlay, scoreboard, null);
            check(Math.abs(rendered.named("Player000").pose.m00() - 1.5F) < 0.001F
                    && Math.abs(rendered.named("Player000").pose.m20() - 25.5F) < 0.001F
                    && Math.abs(rendered.named("Player000").pose.m21() - 34.5F) < 0.001F,
                    "scale and both offsets reach transformed text geometry");
            check(rendered.restored, "tab rendering restores the caller matrix");
            number("TabList", "Size", 100); number("TabList", "X Offset", 0); number("TabList", "Y Offset", 0);
            int[] latency = {-1,0,74,75,144,145,199,200,299,300,399,400};
            int[] colors = {-5636096,-15466667,-15466667,-14773218,-14773218,-4733653,-4733653,-13779,-13779,-6458098,-6458098,-4318437};
            for (int i = 0; i < latency.length; i++) {
                var value = ping(access, new FixturePlayer(0, latency[i]));
                check(value.text.size() == 1 && value.text.getFirst().value.equals(Integer.toString(latency[i]))
                        && value.text.getFirst().color == colors[i] && value.blits.isEmpty(), "actual numerical ping band " + latency[i]);
            }
            bool("PingView", "Small Numbers", true); bool("PingView", "Text Shadow", false);
            var small = ping(access, new FixturePlayer(0, 100));
            check(small.text.getFirst().pose.m00() == 0.5F && !small.text.getFirst().shadow && small.restored,
                    "small ping uses half-scale text without leaking matrix or shadow state");
            bool("PingView", "Hide False Ping", true);
            for (int fake : new int[] {-1, 0, 1, 999, 1000})
                check(ping(access, new FixturePlayer(0, fake)).text.stream().allMatch(t -> t.value.isEmpty()), "false ping hidden at " + fake);
            check(ping(access, new FixturePlayer(0, 2)).text.size() == 1 && ping(access, new FixturePlayer(0, 998)).text.size() == 1,
                    "valid latency boundaries remain visible");
            bool("PingView", "Hide False Ping", false); bool("PingView", "Small Numbers", false);
            ((DropdownOption) ping.getOption("Color Mode")).setIndex(1); color("PingView", "Static Color", 0xffabcdef);
            check(ping(access, new FixturePlayer(0, 450)).text.getFirst().color == 0xffabcdef, "existing static color mode controls native numbers");
            bool("PingView", "Show Numbers", false);
            var bars = ping(access, new FixturePlayer(0, 80));
            check(bars.text.isEmpty() && !bars.blits.isEmpty(), "numbers-off returns to bars without reference fall-through bug");
            bool("PingView", "Hide Ping", true);
            var noPing = render(overlay, scoreboard, null);
            check(noPing.blits.size() == 14, "hide-ping option removes bars while keeping heads");
            headAndBoss(mc);
            NativeTabTweaks.applyLegacy(JsonParser.parseString("{\"tabScale\":1.23,\"maxTabPlayers\":42,\"removeHeaderShadow\":true,\"showPingInTab\":true,\"tabHeaderColor\":-1234567,\"pingColorSix\":-7654321}").getAsJsonObject());
            TabTweaksConfig.refresh();
            check(TabTweaksConfig.current().tabScale == 1.23F && TabTweaksConfig.current().maxTabPlayers == 42
                    && TabTweaksConfig.current().removeHeaderShadow && TabTweaksConfig.current().showPingInTab
                    && TabTweaksConfig.current().tabHeaderColor == -1234567, "upstream config scalars, inverted switches and colors migrate into Lads");
            LoggerFactory.getLogger("TheLadsCore").info("Lads tab tweaks probe END: {} passed, 0 failed", passed);
        } catch (Throwable failure) {
            LoggerFactory.getLogger("TheLadsCore").error("Lads native feature probe FAILED: tab tweaks after {} checks", passed, failure);
        } finally {
            previousTab.restore(tab); previousPing.restore(ping); TabTweaksConfig.refresh();
        }
    }
    private static void headAndBoss(Minecraft mc) throws Exception {
        var state = new GuiRenderState(); var graphics = new GuiGraphicsExtractor(mc, state, 0, 0);
        var before = new Matrix3x2f(graphics.pose());
        ((Head) new PlayerFaceExtractor()).ladsTab$draw(graphics, DefaultPlayerSkin.getDefaultTexture(), 10, 20, 8, true, true, -1);
        var drawn = capture(state, graphics.pose().equals(before));
        check(drawn.blits.size() == 2 && drawn.blits.stream().anyMatch(b -> b.x1() - b.x0() == 9)
                && drawn.blits.stream().anyMatch(b -> b.v1() < b.v0()) && drawn.restored,
                "improved player hat expands to nine pixels, supports flipping and restores pose");
        var boss = new BossHealthOverlay(mc);
        var eventsField = BossHealthOverlay.class.getDeclaredField("events"); eventsField.setAccessible(true);
        @SuppressWarnings("unchecked") var events = (Map<UUID, LerpingBossEvent>) eventsField.get(boss);
        UUID id = new UUID(4, 8);
        events.put(id, new LerpingBossEvent(id, Component.literal("Local probe boss"), 0.5F, BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.PROGRESS, false, false, false));
        boss.extractRenderState(new GuiGraphicsExtractor(mc, new GuiRenderState(), 0, 0));
        check(((Shifter) boss).ladsTab$getShift() == 12, "actual boss overlay exposes the first occupied row");
        boss.reset(); boss.extractRenderState(new GuiGraphicsExtractor(mc, new GuiRenderState(), 0, 0));
        check(((Shifter) boss).ladsTab$getShift() == 0, "removed boss bars do not leave a stale displacement");
    }
    private static Rendered render(PlayerTabOverlay overlay, Scoreboard scoreboard, Objective objective) throws Exception {
        var state = new GuiRenderState(); var graphics = new GuiGraphicsExtractor(Minecraft.getInstance(), state, 0, 0);
        var before = new Matrix3x2f(graphics.pose()); overlay.extractRenderState(graphics, 800, scoreboard, objective);
        return capture(state, graphics.pose().equals(before));
    }
    private static Rendered ping(TabProbeAccess access, PlayerInfo player) throws Exception {
        TabTweaksConfig.refresh();
        var state = new GuiRenderState(); var graphics = new GuiGraphicsExtractor(Minecraft.getInstance(), state, 0, 0);
        var before = new Matrix3x2f(graphics.pose()); access.ladsTab$ping(graphics, player);
        return capture(state, graphics.pose().equals(before));
    }
    private static Rendered capture(GuiRenderState state, boolean restored) throws Exception {
        var text = new ArrayList<Text>(); var blits = new ArrayList<BlitRenderState>(); var rectangles = new ArrayList<ColoredRectangleRenderState>();
        var collected = new ArrayList<GuiTextRenderState>(); state.forEachText(collected::add);
        for (var value : collected) {
            var builder = new StringBuilder();
            ((FormattedCharSequence) field(value, "text")).accept((index, style, codepoint) -> { builder.appendCodePoint(codepoint); return true; });
            text.add(new Text(builder.toString(), (int) field(value,"color"), (boolean) field(value,"dropShadow"), (int) field(value,"x"), new Matrix3x2f(value.pose)));
        }
        state.forEachElement(e -> { if (e instanceof BlitRenderState b) blits.add(b); if (e instanceof ColoredRectangleRenderState r) rectangles.add(r); }, GuiRenderState.TraverseRange.ALL);
        return new Rendered(text, blits, rectangles, restored);
    }
    private static Object field(Object object, String name) throws Exception { var field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object); }
    private record Text(String value, int color, boolean shadow, int x, Matrix3x2f pose) {}
    private record Rendered(List<Text> text, List<BlitRenderState> blits, List<ColoredRectangleRenderState> rectangles, boolean restored) {
        Text named(String name) { return text.stream().filter(t -> t.value.equals(name)).findFirst().orElseThrow(); }
    }
    private static final class FixturePlayer extends PlayerInfo {
        private final int ping;
        FixturePlayer(int index, int ping) { this(index, ping, 4); }
        FixturePlayer(int index, int ping, int version) {
            super(new GameProfile(new UUID(((long) version << 12) | index, index + 1L), String.format(Locale.ROOT,"Player%03d",index)), false);
            this.ping = ping; setShowHat(true);
        }
        @Override public int getLatency() { return ping; }
        @Override public PlayerSkin getSkin() { return DefaultPlayerSkin.get(getProfile()); }
        @Override public GameType getGameMode() { return GameType.SURVIVAL; }
        @Override public PlayerTeam getTeam() { return null; }
    }
    private static final class Snapshot {
        final Map<String, JsonElement> options = new HashMap<>(); final boolean enabled; final long modified;
        Snapshot(Module module) { module.getOptions().forEach(o -> options.put(o.getName(), o.save())); enabled = module.isEnabled(); modified = module.getLastModified(); }
        void restore(Module module) { module.getOptions().forEach(o -> o.load(options.get(o.getName()))); module.setEnabled(enabled); module.setLastModified(modified); }
    }
    private static void number(String module,String option,double value) { ((SliderOption) NativeQualityOfLife.module(module).getOption(option)).setValue(value); }
    private static void bool(String module,String option,boolean value) { ((BoolOption) NativeQualityOfLife.module(module).getOption(option)).set(value); }
    private static void color(String module,String option,int value) { var c = (ColorOption) NativeQualityOfLife.module(module).getOption(option); c.setUseGlobal(false); c.setColor(value); }
    private static void check(boolean value,String message) { if (!value) throw new IllegalStateException(message); passed++; }
}
