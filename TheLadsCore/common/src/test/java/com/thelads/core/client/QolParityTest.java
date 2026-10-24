package com.thelads.core.client;

import static org.junit.jupiter.api.Assertions.*;

import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.SliderOption;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Parity 1.9.19 chat lane rows 20, 25, 28, 29, 33, 35, 36: the rules the version hooks lean on. */
class QolParityTest {
    private static Module module(String name) {
        return ModuleManager.getInstance().getModule(name);
    }

    @Test
    void tallTooltipScrollsBetweenItsTopAndBottomOnly() {
        module(TooltipScroll.NAME).setEnabled(true);
        TooltipScroll.tick();
        long now = 1_000_000_000L;
        // 300 tall in a 200 tall screen, placed by vanilla with its bottom on screen (top cut off at -103).
        TooltipScroll.place(10, -103, 100, 300, 400, 200, now);
        assertEquals(-103, TooltipScroll.y, "unscrolled");
        assertTrue(TooltipScroll.scroll(1, false, now), "the wheel scrolls a tooltip taller than the screen");
        TooltipScroll.place(10, -103, 100, 300, 400, 200, now);
        assertEquals(-73, TooltipScroll.y, "one notch at speed 3 = 30 pixels down, towards its top");
        for (int i = 0; i < 20; i++) TooltipScroll.scroll(1, false, now);
        TooltipScroll.place(10, -103, 100, 300, 400, 200, now);
        assertEquals(TooltipScroll.MARGIN, TooltipScroll.y, "stops with its top on screen");
        TooltipScroll.scroll(-1, false, now);
        TooltipScroll.place(10, -103, 100, 300, 400, 200, now);
        assertEquals(TooltipScroll.MARGIN - 30, TooltipScroll.y, "scrolling back starts at once, not after the overshoot");
        assertFalse(TooltipScroll.scroll(1, true, now), "Shift scrolls sideways: this one is not wider than the screen");
        TooltipScroll.place(10, -103, 100, 300, 400, 200, now + TooltipScroll.GONE + 1);
        assertEquals(-103, TooltipScroll.y, "a tooltip shown again later starts unscrolled");
    }

    @Test
    void wheelStaysWithTheScreenForTooltipsThatFit() {
        module(TooltipScroll.NAME).setEnabled(true);
        TooltipScroll.tick();
        TooltipScroll.place(20, 30, 100, 50, 400, 200, 5L);
        assertFalse(TooltipScroll.scroll(1, false, 5L));
        assertFalse(TooltipScroll.scroll(1, false, 5L + TooltipScroll.GONE + 1), "no tooltip on screen");
        TooltipScroll.place(4, 20, 600, 50, 400, 200, 5L);
        assertTrue(TooltipScroll.scroll(-1, true, 5L), "Shift + wheel moves a wide tooltip sideways");
        TooltipScroll.place(4, 20, 600, 50, 400, 200, 5L);
        assertEquals(-26, TooltipScroll.x);
        module(TooltipScroll.NAME).setEnabled(false);
        TooltipScroll.tick();
        assertFalse(TooltipScroll.scroll(1, true, 5L), "module off");
        module(TooltipScroll.NAME).setEnabled(true); // its default
    }

    @Test
    void crashCatcherCatchesOnlyTickExceptionsInAWorld() {
        Module module = module(CrashCatcher.NAME);
        module.setEnabled(true);
        long now = 100_000_000_000L;
        assertFalse(CrashCatcher.catches(new IllegalStateException("x"), false, now), "outside a world");
        assertFalse(CrashCatcher.catches(new OutOfMemoryError(), true, now), "an Error");
        assertFalse(CrashCatcher.catches(new RuntimeException(new StackOverflowError()), true, now), "an Error among the causes");
        CrashCatcher.reloading = true;
        assertFalse(CrashCatcher.catches(new IllegalStateException("x"), true, now), "while resources load");
        CrashCatcher.reloading = false;
        IllegalStateException crash = new IllegalStateException("bad entity");
        assertTrue(CrashCatcher.catches(new RuntimeException("Ticking entity", crash), true, now));
        CrashCatcher.caught(new RuntimeException("Ticking entity", crash), "Ticking entity", "report", now);
        assertTrue(CrashCatcher.summary.startsWith("Ticking entity\nIllegalStateException: bad entity\nat "), CrashCatcher.summary);
        assertFalse(CrashCatcher.catches(crash, true, now + CrashCatcher.REPEAT - 1), "a second crash right after");
        assertTrue(CrashCatcher.catches(crash, true, now + CrashCatcher.REPEAT));
        ((SliderOption) module.getOption(CrashCatcher.LIMIT)).setValue(1);
        assertFalse(CrashCatcher.catches(crash, true, now + 2 * CrashCatcher.REPEAT), "Crash Limit reached");
        module.getOption(CrashCatcher.LIMIT).reset();
    }

    @Test
    void directConnectPingsOnceTypingStops() {
        QuickReconnect.reset();
        assertNull(QuickReconnect.due("play.example.net", 0));
        assertNull(QuickReconnect.due("play.example.net", QuickReconnect.QUIET - 1), "still typing");
        assertEquals("play.example.net", QuickReconnect.due("play.example.net ", QuickReconnect.QUIET));
        assertNull(QuickReconnect.due("play.example.net", 2 * QuickReconnect.QUIET), "pinged already");
        assertNull(QuickReconnect.due("", 0));
        assertNull(QuickReconnect.due("", 2 * QuickReconnect.QUIET), "an empty field");
        QuickReconnect.reset();
        assertNull(QuickReconnect.due("play.example.net", 0));
        assertEquals("play.example.net", QuickReconnect.due("play.example.net", QuickReconnect.QUIET), "pinged again after a reset");
    }

    @Test
    void cursorReturnsOnlyWhenTheNextContainerOpensRightAway() {
        module(KeepCursor.NAME).setEnabled(true);
        KeepCursor.tick();
        KeepCursor.closed(120, 340, 10);
        assertTrue(KeepCursor.reopened(10 + KeepCursor.WINDOW));
        assertEquals(120, KeepCursor.x);
        assertEquals(340, KeepCursor.y);
        assertFalse(KeepCursor.reopened(11), "once per closing");
        KeepCursor.closed(1, 2, 10);
        assertFalse(KeepCursor.reopened(11 + KeepCursor.WINDOW), "too late: the player opened it themselves");
        module(KeepCursor.NAME).setEnabled(false);
        KeepCursor.tick();
        KeepCursor.closed(1, 2, 10);
        assertFalse(KeepCursor.reopened(11));
        module(KeepCursor.NAME).setEnabled(true); // its default
    }

    @Test
    void soundsFindTheirGroupOnEveryVersion() {
        assertEquals("Explosions", label(SoundVolumes.group("entity.generic.explode")));
        assertEquals("Note Blocks", label(SoundVolumes.group("block.note_block.harp")));
        assertEquals("Rain", label(SoundVolumes.group("weather.rain.above")));
        assertEquals("Footsteps", label(SoundVolumes.group("block.stone.step")));
        assertEquals("Glass Breaking", label(SoundVolumes.group("block.glass.break")));
        assertEquals("Block Breaking and Placing", label(SoundVolumes.group("block.stone.break")));
        assertEquals("Doors and Gates", label(SoundVolumes.group("block.wooden_trapdoor.open")));
        assertEquals("Buttons and Levers", label(SoundVolumes.group("block.stone_button.click_on")));
        assertEquals("Hostile Mobs", label(SoundVolumes.group("entity.zombie_villager.ambient")));
        assertEquals("Explosions", label(SoundVolumes.group("entity.creeper.primed")), "the fuse, not the creeper");
        assertEquals(-1, SoundVolumes.group("music.game"));
        SoundVolumes.legacy = true;
        try {
            assertEquals("Explosions", label(SoundVolumes.group("random.explode")));
            assertEquals("Note Blocks", label(SoundVolumes.group("note.harp")));
            assertEquals("Rain", label(SoundVolumes.group("ambient.weather.rain")));
            assertEquals("Footsteps", label(SoundVolumes.group("step.grass")));
            assertEquals("Doors and Gates", label(SoundVolumes.group("random.door_open")));
            assertEquals("Item Pickup", label(SoundVolumes.group("random.pop")));
        } finally {
            SoundVolumes.legacy = false;
        }
    }

    @Test
    void soundVolumeFollowsItsSlider() {
        Module module = module(SoundVolumes.NAME);
        module.setEnabled(true);
        SliderOption rain = (SliderOption) module.getOption("Rain");
        SoundVolumes.tick();
        assertEquals(0.2F, SoundVolumes.volume(0.2F, "weather.rain"), "all at 100%");
        rain.setValue(200);
        SoundVolumes.tick();
        assertEquals(0.4F, SoundVolumes.volume(0.2F, "weather.rain"), 1e-6);
        assertEquals(0.2F, SoundVolumes.volume(0.2F, "entity.generic.explode"), "other groups keep theirs");
        module.setEnabled(false);
        SoundVolumes.tick();
        assertEquals(0.2F, SoundVolumes.volume(0.2F, "weather.rain"), "module off");
        rain.reset();
        module.setEnabled(true); // its default
    }

    @Test
    void guiScaleNeverLeavesLessThanVanillasSmallestGui() {
        Module module = module(GuiScaleOverride.NAME);
        module.setEnabled(true);
        int before = GuiScaleOverride.revision;
        GuiScaleOverride.tick();
        assertTrue(GuiScaleOverride.revision != before, "turning it on lays the GUI out again");
        assertEquals(2.5, GuiScaleOverride.scale(1920, 1080), 1e-9);
        assertEquals(2.0, GuiScaleOverride.scale(640, 1000), 1e-9, "as large as fits 320 pixels across");
        ((SliderOption) module.getOption(GuiScaleOverride.SCALE)).setValue(6);
        GuiScaleOverride.tick();
        assertEquals(4.5, GuiScaleOverride.scale(1920, 1080), 1e-9, "1080 / 240");
        assertEquals(1, GuiScaleOverride.scale(300, 200), 1e-9, "never below 1");
        module.setEnabled(false);
        GuiScaleOverride.tick();
        module.getOption(GuiScaleOverride.SCALE).reset();
    }

    @Test
    void chunkBordersDrawCornersGridAndSections() {
        module(ChunkBorders.NAME).setEnabled(true);
        ChunkBorders.tick();
        List<double[]> lines = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        ChunkBorders.draw(-1, 2, 0, 32, (x0, y0, z0, x1, y1, z1, argb) -> {
            lines.add(new double[] {x0, y0, z0, x1, y1, z1});
            colors.add(argb);
        });
        // 16 corners, 4 walls x 7 grid lines, grid rings at y 2..30 but not 16 (14 x 4), section rings at 0, 16, 32 (3 x 4).
        assertEquals(16 + 28 + 56 + 12, lines.size());
        assertArrayEquals(new double[] {-32, 0, 16, -32, 32, 16}, lines.get(0), "nearest corner of the chunk to the north-west");
        assertEquals(4, colors.stream().filter(c -> c == 0xFFFFFF00).count(), "the four corners of the chunk you stand in");
        module(ChunkBorders.NAME).setEnabled(false);
        ChunkBorders.tick();
    }

    private static String label(int group) {
        return group < 0 ? null : SoundVolumes.GROUPS.get(group).label();
    }
}
