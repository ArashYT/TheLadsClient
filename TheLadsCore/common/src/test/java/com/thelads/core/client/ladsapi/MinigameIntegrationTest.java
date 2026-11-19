package com.thelads.core.client.ladsapi;

import com.thelads.core.client.ladsapi.minigame.Caps;
import com.thelads.core.client.ladsapi.minigame.Hooks;
import com.thelads.core.client.ladsapi.minigame.LockState;
import com.thelads.core.client.ladsapi.minigame.Minigame;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The minigame layer wired into LadsApiClient: caps, hello, resets, rate limits, the three gated sends, and the demo messages. */
class MinigameIntegrationTest {
    private final List<String> sent = new ArrayList<>();
    private long now = 5_000_000;

    @BeforeEach
    void setUp() {
        LadsApiClient.install(bytes -> sent.add(new String(bytes, StandardCharsets.UTF_8)), "26.3");
        LadsApiClient.reset();
        LadsApiClient.clock = () -> now;
        Caps.clearDeclared();
        Hooks.reset();
    }

    @AfterEach
    void tearDown() {
        LadsApiClient.reset();
        Caps.clearDeclared();
        Hooks.reset();
        Minigame.resetAll();
        LadsApiClient.capabilities.clear();
    }

    private void raw(String json) {
        LadsApiClient.receive(json.getBytes(StandardCharsets.UTF_8));
    }

    private void server(String json) {
        now += 1000;
        raw(json);
    }

    private void hello(String caps) {
        server("{\"t\":\"hello\",\"v\":1,\"server\":\"The Lads Network\"" + (caps == null ? "" : ",\"caps\":" + caps) + "}");
        sent.clear();
    }

    private void declareAll() {
        for (String c : Caps.ALL) LadsApiClient.capability(c);
    }

    // ---- caps and the wire ----

    @Test
    void nothingDeclaredLeavesTheHelloReplyUnchanged() {
        server("{\"t\":\"hello\",\"v\":1,\"server\":\"The Lads Network\"}");
        assertEquals(1, sent.size());
        assertEquals("{\"t\":\"hello\",\"v\":1,\"lads\":\"" + com.thelads.core.LadsVersion.VERSION + "\",\"mc\":\"26.3\"}", sent.get(0));
    }

    @Test
    void declaredCapsAreListedSortedWithSpectate() {
        LadsApiClient.capability("spectate");
        LadsApiClient.capability("fx");
        LadsApiClient.capability("camera");
        server("{\"t\":\"hello\",\"v\":1}");
        assertTrue(sent.get(0).endsWith(",\"caps\":[\"camera\",\"fx\",\"spectate\"]}"), sent.get(0));
    }

    @Test
    void messageForAnUndeclaredCapIsIgnoredUntilDeclared() {
        hello(null);
        server("{\"t\":\"lock\",\"ms\":5000,\"move\":true}");
        assertFalse(Minigame.locked(LockState.Lock.MOVE));
        LadsApiClient.capability("lock");
        server("{\"t\":\"lock\",\"ms\":5000,\"move\":true}");
        assertTrue(Minigame.locked(LockState.Lock.MOVE));
        assertFalse(Minigame.locked(LockState.Lock.JUMP));
        server("{\"t\":\"fx\",\"ms\":1000,\"vignette\":{\"color\":\"#FF0000\",\"intensity\":1}}");
        assertNull(Minigame.fx.vignette(now)); // fx still undeclared
    }

    @Test
    void nothingBeforeAHello() {
        declareAll();
        server("{\"t\":\"lock\",\"ms\":5000,\"move\":true}");
        assertFalse(Minigame.locked(LockState.Lock.MOVE));
        assertFalse(LadsApiClient.serverAction("x", true));
        assertTrue(sent.isEmpty());
    }

    @Test
    void helloResetSwitchJoinAndDisconnectAllClear() {
        declareAll();
        hello("[\"input\"]");
        String lock = "{\"t\":\"lock\",\"ms\":50000,\"move\":true}";
        for (int how = 0; how < 5; how++) {
            server(lock);
            assertTrue(Minigame.locked(LockState.Lock.MOVE), "set " + how);
            switch (how) {
                case 0 -> server("{\"t\":\"reset\"}");
                case 1 -> LadsApiClient.serverSwitch();
                case 2 -> LadsApiClient.joinGame(7L);
                case 3 -> server("{\"t\":\"hello\",\"v\":1}");
                default -> LadsApiClient.reset();
            }
            assertFalse(Minigame.locked(LockState.Lock.MOVE), "cleared " + how);
            if (how != 3 && how != 4) continue;
            LadsApiClient.clock = () -> now;
            hello(null);
        }
    }

    @Test
    void profileComesBackWhenStateIsDropped() {
        declareAll();
        String[] cur = {"Default"};
        Hooks.profiles = new Hooks.ProfileSwitcher() {
            public String current() {
                return cur[0];
            }

            public boolean switchTo(String name) {
                cur[0] = name;
                return true;
            }
        };
        Hooks.refusesServerChanges = () -> false;
        hello(null);
        server("{\"t\":\"profile\",\"name\":\"PvP\"}");
        assertEquals("PvP", cur[0]);
        server("{\"t\":\"reset\"}");
        assertEquals("Default", cur[0]);
        server("{\"t\":\"profile\",\"name\":\"PvP\"}");
        Hooks.refusesServerChanges = () -> true;
        LadsApiClient.reset();
        assertEquals("Default", cur[0]); // the restore does not depend on the setting
        // refused: nothing switches
        LadsApiClient.clock = () -> now;
        hello(null);
        server("{\"t\":\"profile\",\"name\":\"PvP\"}");
        assertEquals("Default", cur[0]);
    }

    // ---- rate limits ----

    @Test
    void minigameBucketIsSeparateFromTheHelloBucket() {
        declareAll();
        hello(null);
        now += 10_000;
        for (int i = 0; i < 80; i++) raw("{\"t\":\"hud\",\"id\":\"w" + (i % 3) + "\",\"kind\":\"text\"}");
        raw("{\"t\":\"lock\",\"ms\":5000,\"move\":true}"); // 81st in the same instant: dropped
        assertFalse(Minigame.locked(LockState.Lock.MOVE));
        // the 8/s bucket is untouched
        for (int i = 0; i < 16; i++) raw("{\"t\":\"presence\",\"x\":1}");
        raw("{\"t\":\"reset\"}");
        assertFalse(Minigame.locked(LockState.Lock.MOVE));
        now += 1000; // 40 tokens back
        for (int i = 0; i < 40; i++) raw("{\"t\":\"hud\",\"id\":\"w0\",\"kind\":\"text\"}");
        raw("{\"t\":\"lock\",\"ms\":5000,\"move\":true}");
        assertFalse(Minigame.locked(LockState.Lock.MOVE));
    }

    @Test
    void hudFloodDoesNotStarveAHello() {
        declareAll();
        hello(null);
        now += 10_000;
        for (int i = 0; i < 200; i++) raw("{\"t\":\"hud\",\"id\":\"w0\",\"kind\":\"text\"}");
        raw("{\"t\":\"hello\",\"v\":1}");
        assertEquals(1, sent.size()); // answered
    }

    // ---- the three gated sends ----

    @Test
    void actionsGoOutOnlyForListedIds() {
        declareAll();
        hello(null);
        assertFalse(LadsApiClient.serverAction("ult", true));
        server("{\"t\":\"actions\",\"actions\":[{\"id\":\"ult\",\"name\":\"Ult\"}]}");
        assertTrue(LadsApiClient.serverAction("ult", true));
        assertEquals("{\"t\":\"action\",\"id\":\"ult\",\"down\":true}", sent.get(0));
        assertTrue(LadsApiClient.serverAction("ult", false));
        assertEquals("{\"t\":\"action\",\"id\":\"ult\",\"down\":false}", sent.get(1));
        assertFalse(LadsApiClient.serverAction("other", true));
        assertEquals(2, sent.size());
        sent.clear();
        for (int i = 0; i < 40; i++) LadsApiClient.serverAction("ult", true);
        assertEquals(18, sent.size()); // 20 a second including the two above
    }

    @Test
    void dialogAnswerGoesOutOnceForAnOpenButton() {
        declareAll();
        hello(null);
        server("{\"t\":\"dialog\",\"op\":\"open\",\"id\":\"v\",\"style\":\"vote\",\"title\":\"T\",\"buttons\":[{\"id\":\"a\",\"label\":\"A\"}],\"ms\":10000}");
        assertFalse(LadsApiClient.dialogChoice("v", "zzz"));
        assertFalse(LadsApiClient.dialogChoice("nope", "a"));
        assertTrue(LadsApiClient.dialogChoice("v", "a"));
        assertEquals("{\"t\":\"dialog\",\"id\":\"v\",\"choice\":\"a\"}", sent.get(0));
        assertFalse(LadsApiClient.dialogChoice("v", "a"));
        server("{\"t\":\"dialog\",\"id\":\"w\",\"style\":\"modal\",\"ms\":1000}");
        now += 1500;
        assertFalse(LadsApiClient.dialogChoice("w", "dismiss")); // expired
        assertEquals(1, sent.size());
    }

    @Test
    void inputIsSentOnlyWhenTheHelloOffersItAndTheVersionDeclaresIt() {
        Hooks.input = new Hooks.InputSource() {
            public Hooks.Device device() {
                return Hooks.Device.GAMEPAD;
            }

            public Hooks.Pad pad() {
                return Hooks.Pad.PLAYSTATION;
            }
        };
        hello("[\"input\"]"); // not declared by this build
        assertFalse(LadsApiClient.pollInput());
        LadsApiClient.reset();
        LadsApiClient.clock = () -> now;
        LadsApiClient.capability("input");
        hello(null); // declared, not offered
        assertFalse(LadsApiClient.pollInput());
        hello("[\"input\"]");
        assertTrue(LadsApiClient.pollInput());
        assertEquals("{\"t\":\"input\",\"device\":\"gamepad\",\"pad\":\"playstation\"}", sent.get(0));
        assertFalse(LadsApiClient.pollInput());
        assertEquals(1, sent.size());
    }

    @Test
    void noOtherMessageSendsAnything() {
        declareAll();
        hello(null);
        String[] msgs = {
            "{\"t\":\"camera\",\"mode\":\"orbit\",\"ms\":1000,\"x\":0,\"y\":0,\"z\":0}",
            "{\"t\":\"fx\",\"flash\":{\"color\":\"#FFFFFF\",\"ms\":400}}",
            "{\"t\":\"waypoint\",\"op\":\"set\",\"id\":\"a\",\"x\":1,\"y\":2,\"z\":3}",
            "{\"t\":\"telegraph\",\"id\":\"a\",\"shape\":\"circle\",\"x\":1,\"y\":2,\"z\":3,\"ms\":1000}",
            "{\"t\":\"hud\",\"id\":\"a\",\"kind\":\"text\"}",
            "{\"t\":\"audio\",\"op\":\"play\",\"id\":\"a\",\"sound\":\"entity.generic.explode\"}",
            "{\"t\":\"run\",\"op\":\"start\",\"course\":\"c\"}",
            "{\"t\":\"ghost\",\"op\":\"stop\"}",
            "{\"t\":\"rumble\",\"low\":1,\"high\":1,\"ms\":100}",
            "{\"t\":\"actions\",\"actions\":[]}",
            "{\"t\":\"dialog\",\"id\":\"a\",\"style\":\"toast\"}",
            "{\"t\":\"camera\"}", "{\"t\":\"fx\",\"ms\":\"x\"}", "{\"t\":\"hud\"}", "{\"t\":\"ghost\",\"frames\":5}", "[1,2]", "not json"
        };
        for (String m : msgs) server(m);
        assertTrue(sent.isEmpty(), sent.toString());
    }

    // ---- rumble ----

    @Test
    void rumbleNeedsAGamepadAndFollowsTheVibrationScale() {
        declareAll();
        List<String> log = new ArrayList<>();
        double[] scale = {0.5};
        Hooks.rumble = new Hooks.RumbleSink() {
            public double vibrationScale() {
                return scale[0];
            }

            public void rumble(double low, double high, int ms) {
                log.add(low + "/" + high + "/" + ms);
            }

            public void stop() {
                log.add("stop");
            }
        };
        hello(null);
        log.clear(); // the hello drops old state, which stops any rumble
        server("{\"t\":\"rumble\",\"low\":1,\"high\":0.5,\"ms\":300}");
        assertTrue(log.isEmpty()); // keyboard and mouse
        Hooks.input = new Hooks.InputSource() {
            public Hooks.Device device() {
                return Hooks.Device.GAMEPAD;
            }

            public Hooks.Pad pad() {
                return Hooks.Pad.XBOX;
            }
        };
        server("{\"t\":\"rumble\",\"low\":1,\"high\":0.5,\"ms\":300}");
        assertEquals(List.of("0.5/0.25/300"), log);
        log.clear();
        scale[0] = 0;
        server("{\"t\":\"rumble\",\"low\":1,\"high\":0.5,\"ms\":300}");
        assertTrue(log.isEmpty());
        server("{\"t\":\"rumble\",\"low\":0,\"high\":0,\"ms\":0}");
        assertEquals(List.of("stop"), log);
        log.clear();
        scale[0] = 1;
        server("{\"t\":\"rumble\",\"low\":1,\"high\":1,\"ms\":99999}");
        assertEquals(List.of("1.0/1.0/2000"), log);
        log.clear();
        server("{\"t\":\"reset\"}");
        assertEquals(List.of("stop"), log); // a reset ends a running rumble
    }

    // ---- demo messages (the literal JSON /ladsapi demo produces) ----

    @Test
    void everyDemoFeatureLandsInTheState() {
        declareAll();
        hello(null);
        // camera spline
        raw("{\"t\":\"camera\",\"mode\":\"spline\",\"ms\":5000,\"ease\":\"smooth\",\"bars\":true,\"hide_hud\":true,\"points\":["
            + "{\"x\":0.0,\"y\":70.0,\"z\":0.0,\"yaw\":0.0,\"pitch\":10.0},{\"x\":10.0,\"y\":75.0,\"z\":10.0,\"yaw\":90.0,\"pitch\":20.0},"
            + "{\"x\":20.0,\"y\":70.0,\"z\":0.0,\"yaw\":180.0,\"pitch\":10.0}]}");
        assertTrue(Minigame.camera.active(now));
        assertTrue(Minigame.camera.hidesHud(now));
        // fx
        raw("{\"t\":\"fx\",\"ms\":4000,\"fade_ms\":400,\"vignette\":{\"color\":\"#FF0000\",\"intensity\":0.6,\"pulse_ms\":1200},"
            + "\"tint\":{\"color\":\"#0044AA\",\"intensity\":0.3},\"shader\":\"grayscale\"}");
        assertNotNull(Minigame.fx.vignette(now + 2000));
        assertNotNull(Minigame.fx.tint(now + 2000));
        assertNotNull(Minigame.fx.shader(now + 2000));
        raw("{\"t\":\"fx\",\"flash\":{\"color\":\"#FFFFFF\",\"ms\":400}}");
        // lock
        raw("{\"t\":\"lock\",\"ms\":5000,\"move\":true,\"jump\":true,\"attack\":true,\"use\":true}");
        assertTrue(Minigame.locked(LockState.Lock.MOVE) && Minigame.locked(LockState.Lock.USE));
        assertFalse(Minigame.locked(LockState.Lock.SNEAK));
        // waypoint, telegraphs
        raw("{\"t\":\"waypoint\",\"op\":\"set\",\"id\":\"demo-ahead\",\"x\":0.5,\"y\":70.0,\"z\":12.5,\"label\":\"Ahead\","
            + "\"icon\":\"diamond\",\"color\":\"#55FFFF\",\"beam\":true,\"through_walls\":false}");
        assertEquals(1, Minigame.waypoints.all().size());
        for (String shape : new String[] {"circle", "ring", "cone", "line", "rect"}) {
            raw("{\"t\":\"telegraph\",\"id\":\"demo-" + shape + "\",\"shape\":\"" + shape + "\",\"x\":1.0,\"y\":70.0,\"z\":2.0,"
                + "\"yaw\":0.0,\"radius\":4.0,\"ms\":3000,\"fill_ms\":2500,\"color\":\"#FF5555\"}");
        }
        assertEquals(5, Minigame.telegraphs.active(now + 1000).size());
        assertEquals(0, Minigame.telegraphs.active(now + 3000).size());
        // hud
        raw("{\"t\":\"hud\",\"op\":\"set\",\"group\":\"demo\",\"id\":\"demo-cd\",\"kind\":\"cooldown\",\"title\":\"Dash\","
            + "\"remaining_ms\":8000,\"total_ms\":10000,\"charges\":2}");
        raw("{\"t\":\"hud\",\"op\":\"set\",\"group\":\"demo\",\"id\":\"demo-pips\",\"kind\":\"pips\",\"states\":[{\"filled\":true},{\"filled\":false}]}");
        raw("{\"t\":\"hud\",\"op\":\"set\",\"group\":\"demo\",\"id\":\"demo-meter\",\"kind\":\"meter\",\"value\":0.4,\"left\":\"A\",\"right\":\"B\"}");
        raw("{\"t\":\"hud\",\"op\":\"set\",\"group\":\"demo\",\"id\":\"demo-text\",\"kind\":\"text\",\"lines\":[\"Hello\",\"World\"]}");
        raw("{\"t\":\"hud\",\"op\":\"set\",\"group\":\"demo\",\"id\":\"demo-timer\",\"kind\":\"timer\",\"ms\":60000,\"direction\":\"down\"}");
        assertEquals(5, Minigame.hud.widgets("demo").size());
        // audio (no sound player: nothing plays, nothing breaks)
        raw("{\"t\":\"audio\",\"op\":\"play\",\"id\":\"demo\",\"sound\":\"minecraft:entity.experience_orb.pickup\",\"category\":\"sfx\"}");
        assertTrue(Minigame.audio.voices().isEmpty());
        // run, split, finish
        raw("{\"t\":\"run\",\"op\":\"start\",\"course\":\"demo-course\",\"name\":\"Demo course\",\"checkpoints\":3,"
            + "\"best\":[12000,25000,40000],\"best_total\":40000}");
        assertTrue(Minigame.run.running());
        now += 1000;
        raw("{\"t\":\"run\",\"op\":\"split\",\"course\":\"demo-course\",\"cp\":0,\"time_ms\":5580}");
        assertEquals(-6420, Minigame.run.split(now).deltaMs());
        // ghost: 101 frames in one part (parts omitted)
        StringBuilder g = new StringBuilder("{\"t\":\"ghost\",\"op\":\"play\",\"id\":\"demo-ghost\",\"course\":\"demo-course\","
            + "\"name\":\"Demo ghost\",\"frames\":[");
        for (int i = 0; i <= 100; i++) g.append(i == 0 ? "" : ",").append("[").append(i * 100).append(",").append(i * 0.1).append(",70.0,0.0,0.0,0.0]");
        raw(g.append("]}").toString());
        assertEquals(1, Minigame.ghosts.armedCount());
        assertEquals(1, Minigame.ghosts.visible(Minigame.run, now).size());
        // actions
        raw("{\"t\":\"actions\",\"actions\":[{\"id\":\"demo-ult\",\"name\":\"Ultimate\",\"key\":\"key.keyboard.r\",\"pad\":\"y\"},"
            + "{\"id\":\"demo-dash\",\"name\":\"Dash\",\"pad\":\"rb\"}]}");
        assertEquals(2, Minigame.actions.list().size());
        // dialogs in all three styles
        for (String style : new String[] {"toast", "vote", "modal"}) {
            raw("{\"t\":\"dialog\",\"op\":\"open\",\"id\":\"demo-" + style + "\",\"style\":\"" + style + "\",\"title\":\"Demo\",\"body\":\"Hi\","
                + "\"buttons\":[{\"id\":\"yes\",\"label\":\"Yes\"},{\"id\":\"no\",\"label\":\"No\"}],\"ms\":20000}");
        }
        assertEquals(3, Minigame.dialogs.open(now).size());
        // run reset drops the ghosts; a reset drops all
        raw("{\"t\":\"run\",\"op\":\"reset\",\"course\":\"demo-course\"}");
        assertEquals(0, Minigame.ghosts.armedCount());
        raw("{\"t\":\"reset\"}");
        assertTrue(Minigame.hud.groups().isEmpty());
        assertTrue(Minigame.dialogs.open(now).isEmpty());
        assertTrue(sent.isEmpty());
    }
}
