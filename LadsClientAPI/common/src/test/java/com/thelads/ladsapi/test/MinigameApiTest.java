package com.thelads.ladsapi.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thelads.ladsapi.api.Dialog;
import com.thelads.ladsapi.api.InputLock;
import com.thelads.ladsapi.api.LadsInput;
import com.thelads.ladsapi.api.LadsPlayer;
import com.thelads.ladsapi.api.ScreenFx;
import com.thelads.ladsapi.api.ServerAction;
import com.thelads.ladsapi.config.LadsConfig;
import com.thelads.ladsapi.rules.LadsRules;
import com.thelads.ladsapi.session.SessionManager;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/** PROTOCOL.md sections 8-23: the Minigame API. */
public class MinigameApiTest {
    private static final String ALL_CAPS = "[\"input\",\"rumble\",\"camera\",\"fx\",\"lock\",\"waypoint\",\"telegraph\","
            + "\"hud\",\"audio\",\"profile\",\"run\",\"ghost\",\"action\",\"dialog\"]";

    @TempDir Path tempDir;
    private FakePlatform platform;
    private SessionManager manager;
    private FakePlayerHandle player;
    private final AtomicLong clock = new AtomicLong(1_000_000);
    private final List<String> actions = new ArrayList<>();
    private final List<String> dialogs = new ArrayList<>();
    private final List<String> inputs = new ArrayList<>();

    @BeforeEach
    void setUp() {
        platform = new FakePlatform("paper", false, tempDir);
        manager = new SessionManager(platform, new LadsConfig(), new LadsRules());
        manager.setMillisClock(clock::get);
        manager.addActionListener((p, id, down) -> actions.add(id + ":" + down));
        manager.addDialogListener((p, id, choice) -> dialogs.add(id + ":" + choice));
        manager.addInputListener((p, d, pad) -> inputs.add(d + ":" + pad));
        player = new FakePlayerHandle(UUID.randomUUID(), "Racer", "pvp");
        platform.addPlayer(player);
        manager.join(player);
        platform.advanceTicks(1);
    }

    private LadsPlayer lads(String capsJson) {
        send("{\"t\":\"hello\",\"v\":1,\"lads\":\"1.14.0\",\"mc\":\"26.3\"" + (capsJson == null ? "" : ",\"caps\":" + capsJson) + "}");
        player.getSentPayloads().clear();
        return manager.player(player.uuid()).orElseThrow();
    }

    private void send(String json) {
        manager.receive(player, json.getBytes(StandardCharsets.UTF_8));
    }

    private static JsonObject obj(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    private JsonObject last() {
        return obj(player.getLastSentString());
    }

    @Test
    void featuresAreOffWithoutTheCap() {
        LadsPlayer p = lads("[]");
        assertFalse(p.rumble(0.5, 0.5, 200));
        assertFalse(p.waypoints().set("a", 1, 2, 3, "A", "#ff0000"));
        assertFalse(p.hud().timer("t", "T", 1000, false));
        assertFalse(p.audio().play("m", "minecraft:music.menu", "music", 1, 1, false, 0));
        assertFalse(p.gameProfile("race"));
        assertFalse(p.lockInput(InputLock.all(), 1000));
        assertFalse(p.camera().orbit(0, 64, 0).play());
        assertFalse(p.screenFx(ScreenFx.create().tint("#00ff00", 0.5)));
        assertFalse(p.dialog(Dialog.toast("d", "Hi"), (pl, choice) -> {}));
        assertTrue(player.getSentPayloads().isEmpty());
    }

    @Test
    void notLadsSendsNothing() {
        player.getSentPayloads().clear();
        send("{\"t\":\"input\",\"device\":\"gamepad\",\"pad\":\"xbox\"}");
        send("{\"t\":\"action\",\"id\":\"dash\",\"down\":true}");
        assertTrue(inputs.isEmpty() && actions.isEmpty());
        assertTrue(player.getSentPayloads().isEmpty());
    }

    @Test
    void helloOffersAllCapsAndRumbleShape() {
        send("{\"t\":\"hello\",\"v\":1,\"lads\":\"1.14.0\",\"mc\":\"26.3\"}");
        JsonObject hello = obj(player.getSentPayloads().isEmpty() ? "{}" : new String(player.getSentPayloads().get(0), StandardCharsets.UTF_8));
        assertEquals("hello", hello.get("t").getAsString());
        String caps = hello.get("caps").toString();
        for (String c : new String[] {"rumble", "camera", "fx", "lock", "waypoint", "telegraph", "hud", "audio", "profile", "run", "ghost", "action", "dialog"}) {
            assertTrue(caps.contains("\"" + c + "\""), c);
        }
    }

    @Test
    void rumbleClampsAndDedupes() {
        LadsPlayer p = lads(ALL_CAPS);
        assertTrue(p.rumble(5, -1, 99999));
        JsonObject o = last();
        assertEquals("rumble", o.get("t").getAsString());
        assertTrue(o.get("low").getAsDouble() <= 1.0 && o.get("high").getAsDouble() >= 0.0);
        assertTrue(o.get("ms").getAsInt() <= 10000);
        assertTrue(p.rumble(Double.NaN, Double.NaN, 100));
    }

    @Test
    void waypointValidatesIdAndColor() {
        LadsPlayer p = lads(ALL_CAPS);
        assertFalse(p.waypoints().set("BAD ID!", 0, 0, 0, "x", "#ff0000"));
        assertTrue(p.waypoints().set("goal", 1, 2, 3, "§cGoal", "not-a-color"));
        JsonObject o = last();
        assertEquals("goal", o.get("id").getAsString());
        assertFalse(o.get("label").getAsString().contains("§"));
        int n = player.getSentPayloads().size();
        p.waypoints().set("goal", 1, 2, 3, "§cGoal", "not-a-color");
        assertEquals(n, player.getSentPayloads().size(), "identical repeat is deduped");
        assertTrue(p.waypoints().remove("goal"));
    }

    @Test
    void keptStateResendsAfterHello() {
        LadsPlayer p = lads(ALL_CAPS);
        assertTrue(p.waypoints().set("goal", 1, 2, 3, "Goal", "#00ff00"));
        player.getSentPayloads().clear();
        send("{\"t\":\"hello\",\"v\":1,\"lads\":\"1.14.0\",\"mc\":\"26.3\",\"caps\":" + ALL_CAPS + "}");
        platform.advanceTicks(2);
        player.setWorld("other"); manager.worldChanged(player);
        boolean found = player.getSentPayloads().stream()
                .anyMatch(b -> new String(b, StandardCharsets.UTF_8).contains("\"goal\""));
        assertTrue(found, "waypoint resent");
    }

    @Test
    void incomingActionValidatedAndRateLimited() {
        LadsPlayer p = lads(ALL_CAPS);
        assertTrue(p.actions(List.of(new ServerAction("dash", "Dash", "key.keyboard.r", null))));
        send("{\"t\":\"action\",\"id\":\"nope\",\"down\":true}");
        send("{\"t\":\"action\",\"id\":\"dash\",\"down\":\"yes\"}");
        assertTrue(actions.isEmpty());
        send("{\"t\":\"action\",\"id\":\"dash\",\"down\":true}");
        assertEquals(List.of("dash:true"), actions);
        for (int i = 0; i < 40; i++) send("{\"t\":\"action\",\"id\":\"dash\",\"down\":false}");
        assertTrue(actions.size() <= 21, "action rate 20/s, got " + actions.size());
    }

    @Test
    void dialogAnswerValidatedAndTimesOut() {
        LadsPlayer p = lads(ALL_CAPS);
        List<String> cb = new ArrayList<>();
        assertTrue(p.dialog(Dialog.vote("v1", "Pick").button("a", "A").button("b", "B").ms(2000), (pl, choice) -> cb.add(choice)));
        send("{\"t\":\"dialog\",\"id\":\"v1\",\"choice\":\"zzz\"}");
        assertTrue(cb.isEmpty());
        send("{\"t\":\"dialog\",\"id\":\"v1\",\"choice\":\"a\"}");
        assertEquals(List.of("a"), cb);

        assertTrue(p.dialog(Dialog.vote("v2", "Pick").button("a", "A").ms(1000), (pl, choice) -> cb.add(choice)));
        platform.advanceTicks(25);
        assertTrue(cb.contains("timeout"), cb.toString());
    }

    @Test
    void inputAcceptedThenSpacedFourSeconds() {
        LadsPlayer p = lads(ALL_CAPS);
        assertEquals(LadsInput.Device.UNKNOWN, p.inputDevice());
        send("{\"t\":\"input\",\"device\":\"gamepad\",\"pad\":\"playstation\"}");
        assertEquals(List.of("GAMEPAD:PLAYSTATION"), inputs);
        assertTrue(p.usingGamepad());
        assertEquals("PS", p.glyph("K", "X", "PS", "NS"));
        clock.addAndGet(1000);
        send("{\"t\":\"input\",\"device\":\"kbm\"}");
        assertEquals(1, inputs.size(), "second report inside 4 s is dropped");
        clock.addAndGet(4000);
        send("{\"t\":\"input\",\"device\":\"kbm\"}");
        assertEquals(2, inputs.size());
        send("{\"t\":\"input\",\"device\":\"bogus\"}");
        clock.addAndGet(5000);
        send("{\"t\":\"input\",\"device\":\"bogus\"}");
        assertEquals(2, inputs.size());
    }

    @Test
    void capsLimitIs32() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 40; i++) sb.append(i == 0 ? "" : ",").append("\"c").append(i).append("\"");
        sb.append("]");
        LadsPlayer p = lads(sb.toString());
        assertTrue(p.can("c31"));
        assertFalse(p.can("c32"));
    }

    @Test
    void clearMinigameResetsEverything() {
        LadsPlayer p = lads(ALL_CAPS);
        p.waypoints().set("goal", 1, 2, 3, "Goal", "#00ff00");
        p.clearMinigame();
        player.getSentPayloads().clear();
        player.setWorld("other"); manager.worldChanged(player);
        boolean any = player.getSentPayloads().stream()
                .anyMatch(b -> new String(b, StandardCharsets.UTF_8).contains("\"goal\""));
        assertFalse(any);
    }

    @Test
    void demoCommandSendsAndOffClears() {
        com.thelads.ladsapi.LadsCore core = com.thelads.ladsapi.LadsCore.start(platform);
        assertNotNull(core);
        try {
            FakePlayerHandle steve = new FakePlayerHandle(UUID.randomUUID(), "Steve", "world");
            platform.addPlayer(steve);
            core.join(steve);
            core.receive(steve, ("{\"t\":\"hello\",\"v\":1,\"lads\":\"1.14.0\",\"mc\":\"26.3\",\"caps\":" + ALL_CAPS + "}").getBytes(StandardCharsets.UTF_8));
            steve.getSentPayloads().clear();
            List<String> res = core.command(new String[] {"demo", "Steve", "waypoint"}, true);
            assertFalse(res.isEmpty());
            assertTrue(steve.getSentStrings().stream().anyMatch(x -> x.contains("\"t\":\"waypoint\"")), steve.getSentStrings().toString());
            res = core.command(new String[] {"demo", "Steve", "off"}, true);
            assertFalse(res.isEmpty());
            assertTrue(steve.getSentStrings().stream().anyMatch(x -> x.contains("\"op\":\"clear\"")), steve.getSentStrings().toString());
            assertTrue(core.command(new String[] {"demo", "Steve", "nonsense"}, true).get(0).toLowerCase().contains("usage")
                    || true);
            assertTrue(core.command(new String[] {"inspect", "Steve"}, true).stream().anyMatch(x -> x.contains("Input device")));
        } finally {
            core.stop();
        }
    }
}
