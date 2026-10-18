package com.thelads.core.client.cosmetics.anim;

import com.thelads.core.client.cosmetics.Emotes;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static com.thelads.core.client.cosmetics.anim.Emote.*;
import static org.junit.jupiter.api.Assertions.*;

class EmoteAnimTest {
    private static final Emote GROOVE = EmoteLibrary.get(EmoteLibrary.GROOVE);
    private final UUID self = UUID.randomUUID(), other = UUID.randomUUID();
    private final List<String> sent = new ArrayList<>();

    @AfterEach
    void clear() {
        Emotes.remove(self);
        Emotes.remove(other);
        Emotes.setOutbound(null);
        EmoteAnimator.reset();
    }

    @Test
    void loopIsSeamless() {
        for (String id : EmoteLibrary.ids()) {
            Emote emote = EmoteLibrary.get(id);
            float[] a = new float[CHANNELS], b = new float[CHANNELS];
            emote.sample(0, a);
            emote.sample(emote.periodMs, b);
            assertArrayEquals(a, b, 1e-4f, emote.id + ": t = 0 and t = period are the same pose");
            // No jumps anywhere in the loop, the wrap included: at most 0.02 rad (or px) per millisecond on any channel.
            emote.sample(-0.5, a);
            for (double t = 0; t <= emote.periodMs + 1; t += 0.5) {
                emote.sample(t, b);
                for (int c = 0; c < CHANNELS; c++) assertTrue(Math.abs(a[c] - b[c]) < 0.01f, emote.id + " channel " + c + " jumps at " + t + " ms");
                System.arraycopy(b, 0, a, 0, CHANNELS);
            }
        }
        assertTrue(GROOVE.rightArm && GROOVE.leftArm, "the groove pumps both arms: first person shows both");
        assertEquals(4 * 60_000f / 115, GROOVE.periodMs, 0.01f, "4 beats at 115 BPM");
    }

    @Test
    void blendsInFromVanillaAndOutIntoIt() {
        Emotes.onRemote(other, EmoteLibrary.GROOVE, 0);
        long start = Emotes.get(other).startMs();
        assertEquals(0, EmoteAnimator.pose(other, false, 0, 0, 0, start).weight, 1e-4);
        float half = EmoteAnimator.pose(other, false, 0, 0, 0, start + 100).weight;
        assertTrue(half > 0.4f && half < 0.6f, "halfway through the 200 ms blend-in: " + half);
        assertEquals(1, EmoteAnimator.pose(other, false, 0, 0, 0, start + 200).weight, 1e-4);
        assertEquals(1, EmoteAnimator.pose(other, false, 0, 0, 0, start + 5000).weight, 1e-4);

        Emotes.stop(other);
        float w = 1;
        for (int ms = 50; ms < 280; ms += 50) {
            float next = EmoteAnimator.pose(other, false, 0, 0, 0, start + 5000 + ms).weight;
            assertTrue(next < w && next > 0, "blend-out falls step by step, no cut: " + next + " at " + ms + " ms");
            w = next;
        }
        EmotePose last = EmoteAnimator.pose(other, false, 0, 0, 0, start + 5000 + 300);
        assertEquals(0, last.weight, "a finished blend-out reports weight 0 once (the model goes back to vanilla)");
        assertNull(Emotes.get(other), "and leaves Emotes");
        assertNull(EmoteAnimator.pose(other, false, 0, 0, 0, start + 5400));
    }

    @Test
    void poseBlendsPartsWithVanilla() {
        EmotePose pose = new EmotePose();
        pose.v[ch(RIGHT_ARM, RX)] = -2;
        pose.v[ch(HEAD, RX)] = 0.2f;
        pose.v[ch(RIGHT_LEG, Y)] = 1;
        pose.weight = 0.5f;
        assertEquals(-0.75f, pose.rot(RIGHT_ARM, RX, 0.5f), 1e-6, "arms lerp from vanilla's walk swing to the emote");
        assertEquals(0.4f, pose.rot(HEAD, RX, 0.3f), 1e-6, "the head nods on top of where the player looks");
        assertEquals(12.5f, pose.pos(RIGHT_LEG, 1, 12), 1e-6);
    }

    @Test
    void remotePlayerWhoMovesStopsHereFirst() {
        Emotes.onRemote(other, EmoteLibrary.GROOVE, 1000);
        long t = System.currentTimeMillis();
        EmoteAnimator.pose(other, true, 10, 64, 10, t);
        EmoteAnimator.pose(other, true, 10.1, 64, 10, t + 50);
        assertFalse(Emotes.get(other).stopping(), "a small drift is not a move");
        EmoteAnimator.pose(other, true, 10.5, 64, 10, t + 100);
        assertTrue(Emotes.get(other).stopping(), "walking away blends the emote out before the network stop arrives");
    }

    @Test
    void localStopRules() {
        Emotes.setOutbound((id, start) -> sent.add(String.valueOf(id)));
        EmoteRules.Input in = EmoteRules.in;
        Object level = new Object();
        String[] rules = {"moving", "jumping", "sneaking", "attacking", "using an item", "hurt", "dead", "busy", "dimension", "moved"};
        for (String rule : rules) {
            reset(in, level);
            assertNull(EmoteRules.tick(self), "nothing playing: nothing to stop");
            Emotes.playLocal(self, EmoteLibrary.GROOVE);
            assertNull(EmoteRules.tick(self), "standing still keeps dancing");
            switch (rule) {
                case "moving" -> in.moving = true;
                case "jumping" -> in.jumping = true;
                case "sneaking" -> in.sneaking = true;
                case "attacking" -> in.attacking = true;
                case "using an item" -> in.usingItem = true;
                case "hurt" -> in.hurtTime = 10;
                case "dead" -> in.dead = true;
                case "busy" -> in.busy = true;
                case "dimension" -> in.level = new Object();
                default -> in.x += 0.5;
            }
            assertEquals(rule, EmoteRules.tick(self));
            assertTrue(Emotes.get(self).stopping(), rule + " blends the emote out (not a cut)");
            assertNull(EmoteRules.tick(self), "once stopping, nothing more to do");
            Emotes.remove(self);
        }
        assertEquals(rules.length * 2, sent.size(), "each start and each stop went to the network side");
        assertEquals("null", sent.get(1));
    }

    private static void reset(EmoteRules.Input in, Object level) {
        in.moving = in.jumping = in.sneaking = in.attacking = in.usingItem = in.dead = in.busy = false;
        in.hurtTime = 0;
        in.level = level;
        in.x = in.y = in.z = 0;
        EmoteRules.tick(null);
    }

    @Test
    void samplingAllocatesNothing() {
        var threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        Emotes.onRemote(other, EmoteLibrary.GROOVE, 0);
        long start = Emotes.get(other).startMs();
        for (int i = 0; i < 2000; i++) EmoteAnimator.pose(other, true, 0, 0, 0, start + i * 7.0); // warm up
        long before = threads.getCurrentThreadAllocatedBytes(), t0 = System.nanoTime();
        float sum = 0;
        for (int i = 0; i < 1000; i++) sum += EmoteAnimator.pose(other, true, 0, 0, 0, start + 20_000 + i * 7.0).v[ch(RIGHT_ARM, RX)];
        long allocated = threads.getCurrentThreadAllocatedBytes() - before, nanos = System.nanoTime() - t0;
        assertTrue(allocated < 1000, "1000 sampled frames allocated " + allocated + " bytes (under a byte a frame: only the measuring itself)");
        assertTrue(nanos / 1000 < 50_000, "1000 frames took " + nanos / 1000 + " us");
        assertNotEquals(0, sum);
        assertNull(EmoteAnimator.pose(self, true, 0, 0, 0), "a player who is not emoting is just a lookup");
    }

    @Test
    void otherPlayersStayVanillaWhenCosmeticsHidesThem() {
        var cosmetics = (com.thelads.core.modules.CosmeticsModule) com.thelads.core.config.ModuleManager.getInstance().getModule("Cosmetics");
        Emotes.onRemote(other, EmoteLibrary.GROOVE, 1000);
        Emotes.playLocal(self, EmoteLibrary.GROOVE);
        long t = System.currentTimeMillis() + 1000;
        try {
            cosmetics.showOthers.set(false);
            assertNull(EmoteAnimator.pose(other, true, 0, 0, 0, t), "\"Show other players' emotes\" off: other players are not posed");
            assertNotNull(EmoteAnimator.pose(self, false, 0, 0, 0, t), "the local player still dances");
            cosmetics.showOthers.set(true);
            assertNotNull(EmoteAnimator.pose(other, true, 0, 0, 0, t), "on again: other players dance");
        } finally {
            cosmetics.showOthers.set(true);
        }
    }

    @Test
    void wheelPicksBySlotDirection() {
        EmoteWheel wheel = new EmoteWheel(1000);
        assertEquals(0, wheel.hovered(0, -50), "straight up is the first slot (Groove)");
        assertEquals(0, wheel.hovered(15, -200), "pointing past the ring still picks by direction");
        assertEquals(2, wheel.hovered(60, 0));
        assertEquals(-1, wheel.hovered(3, 3), "the middle picks nothing");
        assertEquals(EmoteLibrary.GROOVE, wheel.release(1100, 0, -50), "release over Groove plays it");
        assertNull(wheel.release(1100, 0, 0), "a quick tap leaves the wheel open");
        assertEquals("", wheel.release(1400, 0, 0), "a held key released in the middle closes it");
        assertEquals("", wheel.release(1400, 60, 0), "an empty slot plays nothing");
        assertNull(wheel.click(60, 0), "clicking an empty slot keeps the wheel open");
        assertEquals("", wheel.click(300, 300), "a click outside closes it");
        assertEquals(EmoteLibrary.GROOVE, wheel.click(0, -60));
    }

    /** Frames for tools/emote-preview (front, side and first-person drawings of the dance while it is tuned). */
    @Test
    void writesPreviewFrames() throws Exception {
        for (String id : EmoteLibrary.ids()) {
            Emote emote = EmoteLibrary.get(id);
            float[] v = new float[CHANNELS];
            StringBuilder json = new StringBuilder("{\"periodMs\":" + emote.periodMs + ",\"frames\":[");
            for (int f = 0; f <= 120; f++) {
                emote.sample(f * emote.periodMs / 60, v);
                json.append(f == 0 ? "[" : ",[");
                for (int c = 0; c < CHANNELS; c++) json.append(c == 0 ? "" : ",").append(String.format(Locale.ROOT, "%.4f", v[c]));
                json.append(']');
            }
            String name = emote.id.substring(emote.id.indexOf(':') + 1);
            Path out = Path.of("build", "emote-preview", name + ".json");
            Files.createDirectories(out.getParent());
            Files.writeString(out, json.append("]}").toString());
        }
    }
}
