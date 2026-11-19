package com.thelads.core.client.ladsapi.minigame;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** HUD, audio, profile, run, ghost, actions, dialog and the input gate: state with an explicit clock and fake platform hooks. */
class MinigameListStatesTest {
    private static final Gson GSON = new Gson();

    @AfterEach
    void tearDown() {
        Hooks.reset();
    }

    private static JsonObject j(String s) {
        return GSON.fromJson(s, JsonObject.class);
    }

    // ---- hud ----

    @Test
    void hudCooldownAndTimerCountFromReceipt() {
        HudState h = new HudState();
        h.apply(j("{\"op\":\"set\",\"id\":\"rocket\",\"kind\":\"cooldown\",\"title\":\"Rocket Blast\",\"icon\":\"minecraft:firework_rocket\","
            + "\"remaining_ms\":3200,\"total_ms\":8000,\"charges\":2,\"slot\":1}"), 1000);
        h.apply(j("{\"id\":\"clock\",\"kind\":\"timer\",\"ms\":90000,\"direction\":\"down\"}"), 1000);
        h.apply(j("{\"id\":\"up\",\"kind\":\"timer\",\"ms\":5000,\"direction\":\"up\"}"), 1000);
        HudState.Widget cd = h.widgets("main").stream().filter(w -> w.id().equals("rocket")).findFirst().orElseThrow();
        assertEquals(3200, HudState.cooldownRemaining(cd, 1000));
        assertEquals(1200, HudState.cooldownRemaining(cd, 3000));
        assertEquals(0, HudState.cooldownRemaining(cd, 99_000)); // never below 0
        assertEquals(0.4, HudState.cooldownFraction(cd, 1000), 1e-9);
        assertEquals(2, cd.charges());
        assertEquals("minecraft:firework_rocket", cd.icon());
        HudState.Widget down = h.widgets("main").stream().filter(w -> w.id().equals("clock")).findFirst().orElseThrow();
        assertEquals(80_000, HudState.timerMs(down, 11_000));
        assertEquals(0, HudState.timerMs(down, 1_000_000)); // stops at 0:00
        HudState.Widget up = h.widgets("main").stream().filter(w -> w.id().equals("up")).findFirst().orElseThrow();
        assertEquals(15_000, HudState.timerMs(up, 11_000));
        assertEquals(-1, h.widgets("main").stream().filter(w -> w.id().equals("clock")).findFirst().orElseThrow().charges());
    }

    @Test
    void hudKindsClampingAndSlotOrder() {
        HudState h = new HudState();
        h.apply(j("{\"id\":\"b\",\"kind\":\"meter\",\"value\":5,\"left\":\"RED\",\"right\":\"BLUE\",\"slot\":3,\"group\":\"demo\"}"), 0);
        h.apply(j("{\"id\":\"a\",\"kind\":\"text\",\"lines\":[\"1\",\"2\",\"3\",\"4\",\"5\",\"6\",\"7\"],\"slot\":1,\"group\":\"demo\"}"), 0);
        StringBuilder many = new StringBuilder("{\"id\":\"p\",\"kind\":\"pips\",\"group\":\"demo\",\"states\":[");
        for (int i = 0; i < 20; i++) many.append(i == 0 ? "" : ",").append("{\"filled\":true}");
        h.apply(j(many.append("]}").toString()), 0);
        assertEquals(List.of("demo"), h.groups());
        List<HudState.Widget> list = h.widgets("demo");
        assertEquals("a", list.get(1).id() == null ? "" : list.get(list.size() - 2).id().equals("a") ? "a" : list.get(1).id());
        HudState.Widget meter = list.stream().filter(w -> w.id().equals("b")).findFirst().orElseThrow();
        assertEquals(1, meter.value(), 1e-9);
        assertEquals("RED", meter.left());
        assertEquals(6, list.stream().filter(w -> w.id().equals("a")).findFirst().orElseThrow().lines().size());
        assertEquals(12, list.stream().filter(w -> w.id().equals("p")).findFirst().orElseThrow().pips().size());
        assertEquals("p", list.get(0).id()); // slot 0 first, then 1, then 3
        assertEquals("b", list.get(2).id());
    }

    @Test
    void hudLimitsAndBadItems() {
        HudState h = new HudState();
        h.apply(j("{\"id\":\"x\",\"kind\":\"gauge\"}"), 0); // unknown kind
        h.apply(j("{\"id\":\"BAD\",\"kind\":\"text\"}"), 0);
        h.apply(j("{\"kind\":\"text\"}"), 0);
        assertTrue(h.groups().isEmpty());
        for (int i = 0; i < 5; i++) h.apply(j("{\"id\":\"w" + i + "\",\"kind\":\"text\",\"group\":\"g" + i + "\"}"), 0);
        assertEquals(4, h.groups().size()); // max 4 groups
        for (int i = 0; i < 30; i++) h.apply(j("{\"id\":\"m" + i + "\",\"kind\":\"text\",\"group\":\"g0\"}"), 0);
        int total = 0;
        for (String g : h.groups()) total += h.widgets(g).size();
        assertEquals(16, total); // max 16 widgets
        h.apply(j("{\"op\":\"remove\",\"id\":\"w0\"}"), 0);
        h.apply(j("{\"op\":\"clear\"}"), 0);
        assertTrue(h.groups().isEmpty());
        h.apply(j("{\"id\":\"d\",\"kind\":\"text\"}"), 0);
        assertEquals(List.of("main"), h.groups()); // default group
    }

    // ---- audio ----

    private static final class FakeSound implements Hooks.SoundPlayer {
        final List<String> log = new ArrayList<>();
        final Set<String> unknown = new HashSet<>();
        final Set<String> playing = new HashSet<>();
        boolean hold;

        public boolean play(AudioState.Voice v, double volume) {
            if (unknown.contains(v.sound)) return false;
            log.add("play " + v.id + " " + v.sound + " " + String.format("%.2f", volume));
            playing.add(v.id);
            return true;
        }

        public void volume(String id, double v) {
            log.add("vol " + id + " " + String.format("%.2f", v));
        }

        public void stop(String id) {
            log.add("stop " + id);
            playing.remove(id);
        }

        public boolean playing(String id) {
            return playing.contains(id);
        }

        public void musicHold(boolean h) {
            hold = h;
        }
    }

    @Test
    void audioPlayFadeInStopAndMusicHold() {
        FakeSound s = new FakeSound();
        Hooks.sound = s;
        AudioState a = new AudioState();
        a.apply(j("{\"op\":\"play\",\"id\":\"bgm\",\"sound\":\"lads:music.arena\",\"category\":\"music\",\"volume\":0.7,\"loop\":true,"
            + "\"fade_ms\":2000,\"music_pause\":true,\"ms\":600000}"), 0);
        assertEquals("play bgm lads:music.arena 0.00", s.log.get(0)); // fading in from silence
        assertTrue(s.hold);
        a.tick(1000);
        assertEquals("vol bgm 0.35", s.log.get(s.log.size() - 1));
        a.tick(2000);
        assertEquals("vol bgm 0.70", s.log.get(s.log.size() - 1));
        a.apply(j("{\"op\":\"fade\",\"id\":\"bgm\",\"volume\":0.2,\"fade_ms\":1000}"), 2000);
        assertEquals(0.45, a.voices().get(0).volumeAt(2500), 1e-9);
        a.apply(j("{\"op\":\"stop\",\"id\":\"bgm\",\"fade_ms\":1500}"), 3000);
        a.tick(4000);
        assertEquals(1, a.voices().size());
        a.tick(4500);
        assertTrue(a.voices().isEmpty());
        assertEquals("stop bgm", s.log.get(s.log.size() - 1));
        assertFalse(s.hold); // the music comes back
    }

    @Test
    void audioLimitsUnknownSoundsUrlsAndOneShots() {
        FakeSound s = new FakeSound();
        s.unknown.add("minecraft:nope");
        Hooks.sound = s;
        AudioState a = new AudioState();
        a.apply(j("{\"op\":\"play\",\"id\":\"x\",\"sound\":\"nope\"}"), 0);
        a.apply(j("{\"op\":\"play\",\"id\":\"u\",\"sound\":\"https://evil.example/a.ogg\"}"), 0);
        a.apply(j("{\"op\":\"play\",\"id\":\"u\",\"sound\":\"../../etc/passwd\"}"), 0);
        a.apply(j("{\"op\":\"play\",\"sound\":\"entity.generic.explode\"}"), 0); // no id
        a.apply(j("{\"id\":\"noop\",\"sound\":\"entity.generic.explode\"}"), 0); // no op
        assertTrue(a.voices().isEmpty());
        for (int i = 0; i < 10; i++) a.apply(j("{\"op\":\"play\",\"id\":\"v" + i + "\",\"sound\":\"entity.generic.explode\",\"x\":1,\"y\":2,\"z\":3}"), 0);
        assertEquals(8, a.voices().size());
        assertTrue(a.voices().get(0).positional);
        assertEquals("sfx", a.voices().get(0).category);
        assertEquals("minecraft:entity.generic.explode", a.voices().get(0).sound);
        s.playing.remove("v0"); // a one-shot ended by itself
        a.tick(10);
        assertEquals(7, a.voices().size());
        a.apply(j("{\"op\":\"play\",\"id\":\"v1\",\"sound\":\"block.note_block.bell\"}"), 20); // same id restarts
        assertEquals(7, a.voices().size());
        a.apply(j("{\"op\":\"stop\"}"), 30);
        assertTrue(a.voices().isEmpty());
    }

    @Test
    void audioLoopEndsAfterMsAndNoHookMeansNothing() {
        FakeSound s = new FakeSound();
        Hooks.sound = s;
        AudioState a = new AudioState();
        a.apply(j("{\"op\":\"play\",\"id\":\"l\",\"sound\":\"music.menu\",\"loop\":true,\"ms\":1000}"), 0);
        a.tick(999);
        assertEquals(1, a.voices().size());
        a.tick(1000);
        assertTrue(a.voices().isEmpty());
        Hooks.sound = null;
        a.apply(j("{\"op\":\"play\",\"id\":\"n\",\"sound\":\"music.menu\"}"), 0);
        assertTrue(a.voices().isEmpty());
    }

    // ---- profile ----

    private static final class FakeProfiles implements Hooks.ProfileSwitcher {
        String current = "Default";
        final Set<String> has = new HashSet<>(Set.of("Default", "PvP"));
        final List<String> calls = new ArrayList<>();

        public String current() {
            return current;
        }

        public boolean switchTo(String name) {
            calls.add(name);
            for (String h : has) {
                if (h.equalsIgnoreCase(name)) {
                    current = h;
                    return true;
                }
            }
            return false;
        }
    }

    @Test
    void profileSwitchesAndComesBack() {
        FakeProfiles p = new FakeProfiles();
        Hooks.profiles = p;
        ProfileState s = new ProfileState();
        s.apply(j("{\"t\":\"profile\",\"name\":\"pvp\"}"));
        assertEquals("PvP", p.current);
        p.has.add("Build");
        s.apply(j("{\"t\":\"profile\",\"name\":\"Build\"}"));
        s.apply(j("{\"t\":\"profile\"}"));
        assertEquals("Default", p.current); // the one active before the first switch
        s.apply(j("{\"t\":\"profile\",\"name\":\"PvP\"}"));
        s.restore(); // reset / server switch / disconnect / hello
        assertEquals("Default", p.current);
        assertFalse(s.switched());
    }

    @Test
    void profileDoesNothingWhenRefusedOrUnknownOrNoHook() {
        FakeProfiles p = new FakeProfiles();
        Hooks.profiles = p;
        Hooks.refusesServerChanges = () -> true;
        ProfileState s = new ProfileState();
        s.apply(j("{\"name\":\"PvP\"}"));
        assertTrue(p.calls.isEmpty());
        Hooks.refusesServerChanges = () -> false;
        s.apply(j("{\"name\":\"Nothing\"}")); // the player has none of that name
        assertEquals("Default", p.current);
        s.restore();
        assertEquals(1, p.calls.size()); // no switch happened, so no restore either
        Hooks.profiles = null;
        s.apply(j("{\"name\":\"PvP\"}"));
        assertFalse(s.switched());
    }

    // ---- run ----

    private static final String START = "{\"t\":\"run\",\"op\":\"start\",\"course\":\"easy_1\",\"name\":\"Easy 1\",\"checkpoints\":3,"
        + "\"time_ms\":2000,\"best\":[5100,9800,14200],\"best_total\":61000}";

    @Test
    void runClockSplitsAndFinish() {
        RunState r = new RunState();
        r.apply(j(START), 10_000);
        assertTrue(r.running());
        assertEquals("Easy 1", r.name());
        assertEquals(2000, r.elapsedMs(10_000));
        assertEquals(5500, r.elapsedMs(13_500));
        r.apply(j("{\"op\":\"split\",\"course\":\"easy_1\",\"cp\":2,\"time_ms\":13780}"), 20_000);
        RunState.Split sp = r.split(20_000);
        assertEquals(-420, sp.deltaMs()); // 13780 - 14200
        assertTrue(sp.ahead());
        assertEquals(13780, r.elapsedMs(20_000)); // the clock was corrected
        assertEquals(14280, r.elapsedMs(20_500));
        assertNotNull(r.split(22_999));
        assertNull(r.split(23_000)); // shown 3 s
        r.apply(j("{\"op\":\"split\",\"course\":\"easy_1\",\"cp\":1,\"time_ms\":9980}"), 21_000);
        assertEquals(180, r.split(21_000).deltaMs());
        assertFalse(r.split(21_000).ahead());
        r.apply(j("{\"op\":\"split\",\"course\":\"easy_1\",\"cp\":9,\"time_ms\":20000}"), 22_000);
        assertFalse(r.split(22_000).hasDelta()); // no best for that checkpoint: no delta
        r.apply(j("{\"op\":\"finish\",\"course\":\"easy_1\",\"time_ms\":60512}"), 30_000);
        assertFalse(r.running());
        assertEquals(60512, r.elapsedMs(99_000)); // stopped
        RunState.Finish f = r.finish(30_000);
        assertEquals(-488, f.deltaMs());
        assertTrue(f.ahead());
        assertNotNull(r.finish(149_999));
        assertNull(r.finish(150_000)); // shown 120 s
        assertFalse(r.active(150_000));
    }

    @Test
    void runRulesCourseMustMatchResetAndIdleDrop() {
        RunState r = new RunState();
        r.apply(j("{\"op\":\"split\",\"course\":\"easy_1\",\"cp\":0,\"time_ms\":1}"), 0); // no run yet
        assertFalse(r.active(0));
        r.apply(j("{\"op\":\"start\",\"course\":\"BAD COURSE\"}"), 0);
        r.apply(j("{\"op\":\"start\"}"), 0);
        assertFalse(r.active(0));
        r.apply(j(START), 0);
        r.apply(j("{\"op\":\"split\",\"course\":\"other\",\"cp\":0,\"time_ms\":1}"), 1);
        assertNull(r.split(1));
        r.apply(j("{\"op\":\"reset\"}"), 1); // reset needs the course
        assertTrue(r.active(1));
        r.apply(j("{\"op\":\"reset\",\"course\":\"other\"}"), 1);
        assertTrue(r.active(1));
        r.apply(j("{\"op\":\"reset\",\"course\":\"easy_1\"}"), 1);
        assertFalse(r.active(1));
        r.apply(j(START), 0);
        assertTrue(r.active(RunState.IDLE_DROP_MS - 1));
        assertFalse(r.active(RunState.IDLE_DROP_MS)); // 30 min of silence
        r.apply(j("{\"op\":\"start\",\"course\":\"a\",\"checkpoints\":999,\"best\":[1,\"x\",3]}"), 0);
        assertEquals(255, r.checkpoints());
        r.apply(j("{\"op\":\"start\",\"course\":\"b\"}"), 0); // start replaces
        assertEquals("b", r.course());
        assertEquals(0, r.elapsedMs(0));
        r.apply(j("{\"op\":\"finish\",\"course\":\"b\",\"time_ms\":5}"), 0);
        assertFalse(r.finish(0).hasDelta()); // no best_total: no delta
    }

    // ---- ghost ----

    private static String frames(int from, int to, int step) {
        StringBuilder b = new StringBuilder("[");
        for (int t = from; t <= to; t += step) b.append(t == from ? "" : ",").append("[").append(t).append(",").append(t / 100.0)
            .append(",64,0,90,0]");
        return b.append("]").toString();
    }

    private static RunState running(String course) {
        RunState r = new RunState();
        r.apply(j("{\"op\":\"start\",\"course\":\"" + course + "\",\"time_ms\":0}"), 0);
        return r;
    }

    @Test
    void ghostInterpolatesOnTheRunClockAndVanishes() {
        GhostState g = new GhostState();
        g.apply(j("{\"op\":\"play\",\"id\":\"pb\",\"course\":\"easy_1\",\"name\":\"Record: Steve\",\"frames\":["
            + "[1000,10,64,0,350,0],[2000,20,64,10,10,20]]}"), 0);
        assertEquals(1, g.armedCount());
        RunState r = running("easy_1");
        GhostState.Pose before = g.visible(r, 0).get(0);
        assertEquals(10, before.x(), 1e-9); // holds the first pose until t0
        GhostState.Pose mid = g.visible(r, 1500).get(0);
        assertEquals(15, mid.x(), 1e-9);
        assertEquals(5, mid.z(), 1e-9);
        assertEquals(10, mid.pitch(), 1e-9);
        assertEquals(0, ((mid.yaw() % 360) + 360) % 360, 1e-6); // 350 -> 10 along the shorter arc
        assertEquals("Record: Steve", mid.name());
        assertNull(mid.skin());
        assertEquals(20, g.visible(r, 2999).get(0).x(), 1e-9); // lingers 1 s
        assertTrue(g.visible(r, 3001).isEmpty());
        assertTrue(g.visible(running("other"), 1500).isEmpty()); // another course's run
        assertTrue(g.visible(new RunState(), 1500).isEmpty()); // no run
        r.apply(j("{\"op\":\"split\",\"course\":\"easy_1\",\"cp\":0,\"time_ms\":1900}"), 1500); // follows clock corrections
        assertEquals(19, g.visible(r, 1500).get(0).x(), 1e-9);
    }

    @Test
    void ghostPartsAreReassembledInOrder() {
        GhostState g = new GhostState();
        g.apply(j("{\"op\":\"play\",\"id\":\"pb\",\"course\":\"c\",\"part\":0,\"parts\":2,\"frames\":" + frames(0, 500, 250) + "}"), 0);
        assertEquals(0, g.armedCount());
        assertEquals(1, g.pendingCount());
        g.apply(j("{\"op\":\"play\",\"id\":\"pb\",\"course\":\"c\",\"part\":1,\"parts\":2,\"frames\":" + frames(750, 1000, 250) + "}"), 1000);
        assertEquals(1, g.armedCount());
        assertEquals(0, g.pendingCount());
        assertEquals(7.5, g.visible(running("c"), 750).get(0).x(), 1e-9);
        // out of order: dropped, nothing armed
        GhostState o = new GhostState();
        o.apply(j("{\"op\":\"play\",\"id\":\"pb\",\"course\":\"c\",\"part\":1,\"parts\":2,\"frames\":" + frames(0, 500, 250) + "}"), 0);
        assertEquals(0, o.armedCount() + o.pendingCount());
        o.apply(j("{\"op\":\"play\",\"id\":\"pb\",\"course\":\"c\",\"part\":0,\"parts\":3,\"frames\":" + frames(0, 500, 250) + "}"), 0);
        o.apply(j("{\"op\":\"play\",\"id\":\"pb\",\"course\":\"c\",\"part\":2,\"parts\":3,\"frames\":" + frames(750, 1000, 250) + "}"), 1);
        assertEquals(0, o.armedCount() + o.pendingCount());
    }

    @Test
    void ghostIncompleteIsDroppedAfter10sAndAtMost3() {
        GhostState g = new GhostState();
        g.apply(j("{\"op\":\"play\",\"id\":\"a\",\"course\":\"c\",\"part\":0,\"parts\":2,\"frames\":" + frames(0, 500, 250) + "}"), 0);
        g.apply(j("{\"op\":\"stop\",\"id\":\"zzz\"}"), 9_999);
        assertEquals(1, g.pendingCount());
        g.apply(j("{\"op\":\"stop\",\"id\":\"zzz\"}"), 10_000);
        assertEquals(0, g.pendingCount());
        for (int i = 0; i < 5; i++) {
            g.apply(j("{\"op\":\"play\",\"id\":\"g" + i + "\",\"course\":\"c\",\"frames\":" + frames(0, 500, 250) + "}"), 20_000);
        }
        assertEquals(3, g.armedCount());
        g.apply(j("{\"op\":\"stop\",\"id\":\"g0\"}"), 20_000);
        assertEquals(2, g.armedCount());
        g.apply(j("{\"op\":\"stop\"}"), 20_000);
        assertEquals(0, g.armedCount());
    }

    @Test
    void ghostBadFrameEndsTheListThereAndLimitsApply() {
        GhostState g = new GhostState();
        g.apply(j("{\"op\":\"play\",\"id\":\"a\",\"course\":\"c\",\"frames\":[[0,0,0,0,0,0],[500,5,0,0,0,0],[400,9,0,0,0,0],[900,50,0,0,0,0]]}"), 0);
        assertEquals(1, g.armedCount());
        assertEquals(5, g.visible(running("c"), 800).get(0).x(), 1e-9); // the list ended at the bad frame (t not increasing)
        g.apply(j("{\"op\":\"play\",\"id\":\"b\",\"course\":\"c\",\"frames\":[[0,0,0,0,0,0],[\"x\",1,1,1,1,1]]}"), 0);
        g.apply(j("{\"op\":\"play\",\"id\":\"d\",\"frames\":[[0,0,0,0,0,0]]}"), 0); // no course
        g.apply(j("{\"op\":\"play\",\"course\":\"c\",\"frames\":[[0,0,0,0,0,0]]}"), 0); // no id
        g.apply(j("{\"op\":\"play\",\"id\":\"e\",\"course\":\"c\",\"frames\":[]}"), 0); // nothing to play
        g.apply(j("{\"op\":\"play\",\"id\":\"f\",\"course\":\"c\",\"frames\":[[4000000,0,0,0,0,0]]}"), 0); // t out of range
        assertEquals(2, g.armedCount());
        StringBuilder big = new StringBuilder("[");
        for (int i = 0; i < 700; i++) big.append(i == 0 ? "" : ",").append("[").append(i).append(",0,0,0,0,0]");
        GhostState h = new GhostState();
        h.apply(j("{\"op\":\"play\",\"id\":\"a\",\"course\":\"c\",\"frames\":" + big + "]}"), 0);
        assertEquals(1, h.armedCount());
        assertEquals(1, h.visible(running("c"), 599).size());
        assertEquals(1, h.visible(running("c"), 1599).size()); // last frame t = 599, plus 1 s
        assertTrue(h.visible(running("c"), 1601).isEmpty());
    }

    // ---- actions ----

    private static final class FakeKeys implements Hooks.ServerKeys {
        final Set<String> taken = new HashSet<>();
        List<ActionsState.Action> last;
        int calls;

        public boolean keyFree(String k) {
            return !taken.contains(k);
        }

        public void changed(List<ActionsState.Action> a) {
            last = a;
            calls++;
        }
    }

    @Test
    void actionsListBindingsAndPressLimit() {
        FakeKeys k = new FakeKeys();
        k.taken.add("key.keyboard.w");
        Hooks.keys = k;
        ActionsState a = new ActionsState();
        a.apply(j("{\"t\":\"actions\",\"actions\":[{\"id\":\"ultimate\",\"name\":\"Cast Ultimate\",\"key\":\"key.keyboard.r\",\"pad\":\"y\"},"
            + "{\"id\":\"dash\",\"key\":\"key.keyboard.r\",\"pad\":\"y\"},{\"id\":\"walk\",\"name\":\"W\",\"key\":\"key.keyboard.w\",\"pad\":\"zz\"},"
            + "{\"id\":\"ultimate\",\"name\":\"dup\"},{\"id\":\"BAD\"}]}"));
        List<ActionsState.Action> l = a.list();
        assertEquals(3, l.size());
        assertEquals("key.keyboard.r", l.get(0).key());
        assertEquals("y", l.get(0).pad());
        assertEquals("dash", l.get(1).name()); // no name: the id
        assertNull(l.get(1).key()); // the key another action uses is not taken: starts unbound
        assertNull(l.get(1).pad());
        assertNull(l.get(2).key()); // a vanilla binding
        assertNull(l.get(2).pad()); // unknown pad button
        assertSame(a.list(), k.last);
        a.apply(j("{\"t\":\"actions\"}")); // no array: ignored
        assertEquals(3, a.list().size());
        a.apply(j("{\"t\":\"actions\",\"actions\":[]}"));
        assertTrue(a.list().isEmpty());
        assertTrue(k.last.isEmpty());
    }

    @Test
    void actionsAtMostEightAndPressRules() {
        ActionsState a = new ActionsState();
        StringBuilder b = new StringBuilder("{\"actions\":[");
        for (int i = 0; i < 12; i++) b.append(i == 0 ? "" : ",").append("{\"id\":\"a").append(i).append("\"}");
        a.apply(j(b.append("]}").toString()));
        assertEquals(8, a.list().size());
        assertFalse(a.press("a9", 0)); // not in the list
        assertFalse(a.press(null, 0));
        for (int i = 0; i < 20; i++) assertTrue(a.press("a0", 100 + i), "press " + i);
        assertFalse(a.press("a0", 500)); // 21st within a second
        assertTrue(a.press("a0", 1100)); // the window moved on
        a.clear();
        assertFalse(a.press("a0", 5000));
    }

    // ---- dialog ----

    private static final String VOTE = "{\"op\":\"open\",\"id\":\"vote1\",\"style\":\"vote\",\"title\":\"Pick\",\"body\":\"Ends in 20 s\","
        + "\"buttons\":[{\"id\":\"a\",\"label\":\"Desert\",\"color\":\"#FFAA00\",\"icon\":\"minecraft:sand\"},{\"id\":\"b\",\"label\":\"Snow\"}],\"ms\":20000}";

    @Test
    void dialogAnswerOnceOnlyForOpenOnesAndNamedButtons() {
        DialogState d = new DialogState();
        d.apply(j(VOTE), 1000);
        DialogState.Dialog v = d.open(1000).get(0);
        assertEquals(DialogState.Style.VOTE, v.style());
        assertEquals("Desert", v.buttons().get(0).label());
        assertEquals(0xFFAA00, v.buttons().get(0).color());
        assertEquals(0xFFFFFF, v.buttons().get(1).color());
        assertFalse(d.answer("vote1", "c", 2000)); // not one of its buttons
        assertFalse(d.answer("nope", "a", 2000));
        assertFalse(d.answer("vote1", null, 2000));
        assertTrue(d.answer("vote1", "a", 2000));
        assertFalse(d.answer("vote1", "a", 2001)); // once per dialog
        d.apply(j(VOTE), 1000);
        assertTrue(d.answer("vote1", "dismiss", 3000));
        d.apply(j(VOTE), 1000);
        assertFalse(d.answer("vote1", "a", 21_000)); // expired: nothing is sent
        d.apply(j(VOTE), 1000);
        d.apply(j("{\"op\":\"close\",\"id\":\"vote1\"}"), 1500);
        assertFalse(d.answer("vote1", "dismiss", 1600)); // closed by the server
    }

    @Test
    void dialogLimitsAndBadItems() {
        DialogState d = new DialogState();
        d.apply(j("{\"id\":\"x\",\"style\":\"popup\"}"), 0); // unknown style
        d.apply(j("{\"id\":\"x\"}"), 0); // no style
        d.apply(j("{\"id\":\"BAD ID\",\"style\":\"toast\"}"), 0);
        d.apply(j("{\"op\":\"frobnicate\",\"id\":\"x\",\"style\":\"toast\"}"), 0);
        assertTrue(d.open(0).isEmpty());
        for (int i = 0; i < 6; i++) d.apply(j("{\"id\":\"d" + i + "\",\"style\":\"toast\",\"ms\":1}"), 0); // op defaults to open
        assertEquals(4, d.open(0).size());
        assertEquals(1000, d.open(0).get(0).ms()); // ms clamped to 1000-60000
        assertTrue(d.open(1000).isEmpty());
        StringBuilder b = new StringBuilder("{\"id\":\"m\",\"style\":\"modal\",\"title\":\"" + "t".repeat(100) + "\",\"buttons\":[");
        for (int i = 0; i < 9; i++) b.append(i == 0 ? "" : ",").append("{\"id\":\"b").append(i % 7).append("\"}");
        d.apply(j(b.append("]}").toString()), 0);
        DialogState.Dialog m = d.open(0).get(0);
        assertEquals(64, m.title().length());
        assertEquals(6, m.buttons().size()); // duplicate ids dropped, at most 6
        assertEquals(15_000, m.ms()); // default
    }

    // ---- input gate ----

    private static final class Pad implements Hooks.InputSource {
        Hooks.Device device = Hooks.Device.KBM;
        Hooks.Pad pad = Hooks.Pad.GENERIC;

        public Hooks.Device device() {
            return device;
        }

        public Hooks.Pad pad() {
            return pad;
        }
    }

    @Test
    void inputBuildersAreExactAndNeverCarryMore() {
        assertEquals("{\"t\":\"input\",\"device\":\"kbm\"}", InputGate.build(Hooks.Device.KBM, Hooks.Pad.XBOX));
        assertEquals("{\"t\":\"input\",\"device\":\"gamepad\",\"pad\":\"playstation\"}",
            InputGate.build(Hooks.Device.GAMEPAD, Hooks.Pad.PLAYSTATION));
        assertEquals("{\"t\":\"input\",\"device\":\"gamepad\",\"pad\":\"generic\"}", InputGate.build(Hooks.Device.GAMEPAD, null));
        assertNull(InputGate.build(Hooks.Device.UNKNOWN, null));
    }

    @Test
    void inputIsSentOnlyWhenOfferedOncePerHelloAndOnChangeAtMostEvery5Seconds() {
        Pad src = new Pad();
        Hooks.input = src;
        InputGate g = new InputGate();
        assertNull(g.poll(0)); // never offered
        g.hello(false);
        assertNull(g.poll(0));
        g.hello(true);
        assertNotNull(g.poll(1000)); // once after the hello
        assertNull(g.poll(1001)); // nothing changed
        src.device = Hooks.Device.GAMEPAD;
        src.pad = Hooks.Pad.XBOX;
        assertNull(g.poll(2000)); // changed, but within 5 s
        assertEquals("{\"t\":\"input\",\"device\":\"gamepad\",\"pad\":\"xbox\"}", g.poll(6000)); // the latest value wins
        assertNull(g.poll(6001));
        src.device = Hooks.Device.UNKNOWN;
        assertNull(g.poll(20_000)); // unknown is never sent
        src.device = Hooks.Device.KBM;
        assertEquals("{\"t\":\"input\",\"device\":\"kbm\"}", g.poll(20_001));
        g.hello(true); // a new hello: once more (still behind the 5 s gap)
        assertNull(g.poll(21_000));
        assertNotNull(g.poll(25_001));
        g.clear();
        assertFalse(g.offered());
        assertNull(g.poll(99_000));
        Hooks.input = null;
        g.hello(true);
        assertNull(g.poll(99_000)); // no source: nothing to say
    }

    @SuppressWarnings("unused")
    private static Map<String, String> unused() {
        return new HashMap<>();
    }
}
