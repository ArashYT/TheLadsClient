package com.thelads.core.modules;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;
import com.thelads.core.modules.BetterF3Module.Info;
import com.thelads.core.modules.BetterF3Module.Line;
import com.thelads.core.modules.BetterF3Module.Row;
import com.thelads.core.modules.BetterF3Module.Section;
import com.thelads.core.modules.BetterF3Module.Seg;
import com.thelads.core.modules.BetterF3Module.Side;
import com.thelads.core.modules.BetterF3Module.Slide;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BetterF3ModuleTest {
    /** 1.8.9's left column as Forge's debug text event hands it over. */
    private static final List<String> LEFT_189 = List.of("Minecraft 1.8.9 (1.8.9/vanilla)", "144 fps (2 chunk updates) T: 260 vsync",
        "C: 120/4000 (s) D: 4, L: 0, pC: 000, pU: 00, aB: 00", "E: 3/40, B: 0, I: 37", "P: 0. T: All: 40", "MultiplayerChunkCache: 81, 81", "",
        "XYZ: 1.500 / 4.00000 / 2.500", "Block: 1 4 2", "Chunk: 1 4 2 in 0 0 0", "Facing: north (Towards negative Z) (180.0 / 0.0)",
        "Biome: Plains", "Light: 15 (15 sky, 0 block)", "Local Difficulty: 0.75 (Day 0)", "Looking at: 1 3 1", "",
        "Debug: Pie [shift]: hidden FPS [alt]: hidden", "For help: press F3 + Q");
    /** 1.8.9's right column: no label on the GL lines or the targeted block's id. */
    private static final List<String> RIGHT_189 = List.of("Java: 1.8.0_51 64bit", "Mem: 20% 200/1000MB", "Allocated: 30% 300MB", "",
        "CPU: 8x AMD Ryzen", "", "Display: 1920x1080 (NVIDIA Corporation)", "NVIDIA GeForce RTX", "4.6.0 NVIDIA 555", "",
        "minecraft:grass", "snowy: §cfalse");
    private static final long MS = 1_000_000L;

    private static BetterF3Module f3() { return new BetterF3Module(); }
    private static List<String> texts(List<Line> lines) { return lines.stream().map(l -> l.gap() ? "" : l.text()).toList(); }
    private static Seg seg(Line line, String text) { return line.segs().stream().filter(s -> s.text().equals(text)).findFirst().orElseThrow(); }
    private static void set(BetterF3Module f3, String option, boolean value) { ((BoolOption) f3.getOption(option)).set(value); }

    /** The owner's screenshot (26.3, the BetterF3 mod): what the game knew then. */
    private static Info screenshot() {
        Info in = new Info();
        in.fps = 394;
        in.fpsLimit = 260;
        in.x = 1016.342;
        in.y = 77.00399;
        in.z = 766.42;
        in.yaw = 89.6f;
        in.pitch = -30.3f;
        in.particles = "61";
        in.entities = "E: 0/174, SD: 12";
        in.dimension = "minecraft:overworld";
        in.light = 15;
        in.skyLight = 15;
        in.blockLight = 0;
        in.serverSky = 15;
        in.serverBlock = 0;
        in.heights = "S: 62  M: 62";
        in.serverHeights = "S: 105  O: 104  M: 104  ML: 104";
        in.biome = "minecraft:plains";
        in.difficulty = 1.5;
        in.clampedDifficulty = 0;
        in.dayTime = 3814;
        in.seed = 12345L;
        in.target = new int[] {1014, 79, 766};
        in.targetId = "minecraft:grass_block";
        in.cpu = "24x 13th Gen Intel(R) Core(TM) i7-13700KF";
        in.gpuVendor = "NVIDIA Corporation";
        in.gpu = "NVIDIA GeForce RTX 4060 Ti/PCIe/SSE2";
        in.glVersion = "4.6.0 NVIDIA 610.88";
        in.displayWidth = 1920;
        in.displayHeight = 1080;
        in.gpuUtilization = 100;
        return in;
    }

    /** Text drawn per frame, 6 px a character. */
    private static final class Screen {
        final List<String> drawn = new ArrayList<>();
        final LadsGraphics g = (LadsGraphics) java.lang.reflect.Proxy.newProxyInstance(LadsGraphics.class.getClassLoader(), new Class<?>[] {LadsGraphics.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "textWidth" -> ((String) args[0]).length() * 6;
                case "drawText" -> { drawn.add((String) args[0]); yield null; }
                case "getScaledWidth" -> 640;
                case "fontHeight" -> 9;
                default -> method.getReturnType() == boolean.class ? false : method.getReturnType() == int.class ? 0 : null;
            });
    }

    @Test
    void defaultsAreTheBetterF3LookWithEveryLineShown() {
        BetterF3Module f3 = f3();
        assertTrue(f3.isEnabled(), "the remake is on, as the BetterF3 mod was on 26.2");
        assertEquals(BetterF3Module.BETTERF3, f3.style());
        assertFalse(((BoolOption) f3.getOption("Background")).get(), "no plates, as the BetterF3 mod");
        assertTrue(((BoolOption) f3.getOption("Text Shadow")).get());
        assertTrue(((BoolOption) f3.getOption("Slide In")).get());
        assertFalse(((BoolOption) f3.getOption("Rainbow Colors")).get());
        assertFalse(((BoolOption) f3.getOption("Show Other Lines")).get());
        assertTrue(((BoolOption) f3.getOption("Hide Inessential")).get());
        for (Section section : Section.values()) assertTrue(((BoolOption) f3.getOption(section.option)).get(), section.option);
        for (Row row : Row.values()) assertTrue(((BoolOption) f3.getOption(row.option)).get(), row.option);
    }

    @Test
    void theDefaultLookIsTheScreenshotLineForLine() {
        BetterF3Module f3 = f3();
        Info in = screenshot();
        boolean slime = BetterF3Module.slimeChunk(12345L, 1016 >> 4, 766 >> 4);
        assertEquals(List.of("394 fps / Unlimited fps", "XYZ: 1016.342 77.00399 766.420", "Block: 1016 77 766", "Particles: 61",
            "Entities rendered/total: 0/174", "", "Dimension: minecraft:overworld", "Facing: West (Towards negative X)",
            "Rotation: Yaw: 89.6  Pitch: -30.3", "Light: Total: 15  Sky: 15  Block: 0", "Light (Server): Sky: 15  Block: 0",
            "Highest Block: S: 62  M: 62", "Highest Block (Server): S: 105  O: 104  M: 104  ML: 104", "Biome: minecraft:plains",
            "Local Difficulty: 1.50  Clamped: 0.00", "Current Day Ticks: 3814", "Days Played: 0", "Slime Chunk: " + slime),
            texts(f3.betterF3(LEFT_189, true, in)));
        List<String> right = texts(f3.betterF3(RIGHT_189, false, in));
        assertEquals(List.of("Targeted Block: 1014, 79, 766", "Block ID: minecraft:grass_block", ""), right.subList(0, 3));
        assertTrue(right.get(3).matches("Current Time: \\d\\d:\\d\\d:\\d\\d"), right.get(3));
        assertEquals("Java Version: " + System.getProperty("java.version") + " 64bit", right.get(4));
        assertTrue(right.get(5).matches("Memory Usage: \\d+% \\d+/\\d+ MB"), right.get(5));
        assertTrue(right.get(6).matches("Allocation Rate: \\d+ MB/s"), right.get(6));
        assertTrue(right.get(7).matches("Allocated Memory: \\d+% \\d+MB"), right.get(7));
        assertEquals(List.of("CPU: 24x 13th Gen Intel(R) Core(TM) i7-13700KF", "Display: 1920 x 1080 (NVIDIA Corporation)",
            "GPU: NVIDIA GeForce RTX 4060 Ti/PCIe/SSE2", "GPU Utilization: 100%", "OpenGL Version: 4.6.0", "GPU Driver: NVIDIA 610.88"),
            right.subList(8, right.size()));
    }

    @Test
    void keysAndValuesTakeTheirColours() {
        BetterF3Module f3 = f3();
        List<Line> left = f3.betterF3(LEFT_189, true, screenshot()), right = f3.betterF3(RIGHT_189, false, screenshot());
        assertEquals(0xFF55FF55, left.get(0).segs().get(0).color(), "fps over 60: green");
        Line xyz = left.get(1);
        assertEquals(Section.POSITION.key, seg(xyz, "XYZ: ").color());
        assertEquals(List.of(BetterF3Module.AXIS[0], BetterF3Module.AXIS[1], BetterF3Module.AXIS[2]), xyz.segs().subList(1, 4).stream().map(Seg::color).toList(),
            "x red, y green, z aqua");
        Line light = left.get(9);
        assertEquals(Section.WORLD.key, seg(light, "Light: ").color());
        assertEquals(Section.WORLD.key, seg(light, "Sky: ").color(), "the names inside a value too");
        assertEquals(Section.WORLD.value, seg(light, "15  ").color());
        assertEquals(Section.TARGET.key, seg(right.get(0), "Targeted Block: ").color());
        assertEquals(Section.SYSTEM.value, seg(right.get(8), "24x 13th Gen Intel(R) Core(TM) i7-13700KF").color());
        assertEquals(BetterF3Module.loadColor(100), seg(right.get(11), "100%").color(), "a full GPU: red");
        assertEquals(0xFF55FF55, BetterF3Module.loadColor(0));
        assertEquals(0xFFFFFF55, BetterF3Module.loadColor(50));
        assertEquals(0xFFFF5555, BetterF3Module.loadColor(100));
        set(f3, "Axis Colors", false);
        assertEquals(Section.POSITION.value, f3.betterF3(LEFT_189, true, screenshot()).get(1).segs().get(1).color());
    }

    @Test
    void whatTheGameDoesNotKnowIsLeftOutAndTogglesHideLines() {
        BetterF3Module f3 = f3();
        Info in = screenshot();
        in.target = null;
        in.serverSky = -1;
        in.serverHeights = null;
        in.seed = null;
        List<String> left = texts(f3.betterF3(LEFT_189, true, in)), right = texts(f3.betterF3(RIGHT_189, false, in));
        assertFalse(left.stream().anyMatch(t -> t.startsWith("Light (Server)") || t.startsWith("Highest Block (Server)") || t.startsWith("Slime")),
            "multiplayer: no server values, no seed");
        assertTrue(right.get(0).startsWith("Current Time"), "nothing targeted: no Target lines and no gap above the system lines");
        set(f3, "Show Particles", false);
        set(f3, "Show World", false);
        assertEquals(List.of("394 fps / Unlimited fps", "XYZ: 1016.342 77.00399 766.420", "Block: 1016 77 766", "Entities rendered/total: 0/174"),
            texts(f3.betterF3(LEFT_189, true, in)));
        f3.unsupported(Row.GPU_UTIL);
        assertFalse(texts(f3.betterF3(RIGHT_189, false, screenshot())).stream().anyMatch(t -> t.startsWith("GPU Utilization")));
        assertTrue(f3.hidden(f3.getOption("Show GPU Utilization")), "a line this version lacks has no toggle");
        assertTrue(f3.hidden(f3.getOption("Show Biome")), "a hidden section's lines have no toggles");
        assertFalse(f3.hidden(f3.getOption("Show World")));
    }

    @Test
    void otherLinesAreTheGameLinesTheListDoesNotShow() {
        BetterF3Module f3 = f3();
        set(f3, "Show Other Lines", true);
        List<String> left = texts(f3.betterF3(LEFT_189, true, screenshot()));
        assertEquals(List.of("", "Minecraft 1.8.9 (1.8.9/vanilla)", "", "Chunk: 1 4 2 in 0 0 0"), left.subList(left.indexOf("Slime Chunk: "
            + BetterF3Module.slimeChunk(12345L, 63, 47)) + 1, left.size()), "the version and chunk lines; engine counters stay hidden");
        assertFalse(texts(f3.betterF3(RIGHT_189, false, screenshot())).stream().anyMatch(t -> t.contains("NVIDIA 555") || t.contains("snowy")),
            "the GL lines under Display and the block's properties go with the lines the list shows");
        set(f3, "Hide Inessential", false);
        assertTrue(texts(f3.betterF3(LEFT_189, true, screenshot())).contains("For help: press F3 + Q"), "Hide Inessential only hides other lines");
    }

    @Test
    void theOldStyleIsKept() {
        BetterF3Module f3 = f3();
        ((DropdownOption) f3.getOption("Style")).setIndex(BetterF3Module.LADS);
        assertTrue(f3.hidden(f3.getOption("Show XYZ")) && f3.hidden(f3.getOption("World Key Color")), "the BetterF3 look's settings step aside");
        assertFalse(f3.hidden(f3.getOption("Hide Inessential")));
        Screen screen = new Screen();
        for (long t = 0; t <= 400; t += 8) f3.draw(screen.g, LEFT_189, true, 640, true, screenshot(), t * MS);
        assertTrue(screen.drawn.contains("XYZ: ") && screen.drawn.contains("1.500 / 4.00000 / 2.500"), "the game's own lines, organised");
    }

    @Test
    void labelsSortLinesIntoSections() {
        assertEquals(Section.PERFORMANCE, BetterF3Module.label("144 fps (2 chunk updates) T: 260"));
        assertEquals(Section.PERFORMANCE, BetterF3Module.label("120 fps T: 120 (fifo)"), "26.x");
        assertEquals(Section.PERFORMANCE, BetterF3Module.label("Integrated server @ 2.1/50.0 ms, 0 tx, 0 rx"));
        assertEquals(Section.POSITION, BetterF3Module.label("XYZ: 1 / 2 / 3"));
        assertEquals(Section.WORLD, BetterF3Module.label("Client Light: 15 (15 sky, 0 block)"));
        assertEquals(Section.TARGET, BetterF3Module.label("Targeted Block: 1, 2, 3"));
        assertEquals(Section.TARGET, BetterF3Module.label("minecraft:oak_log"), "1.8.9's unlabelled block id");
        assertEquals(Section.SYSTEM, BetterF3Module.label("GPU: 12%"));
        assertNull(BetterF3Module.label("NVIDIA GeForce RTX"), "no label: belongs to the line above");
        assertNull(BetterF3Module.label("facing: north"));
    }

    @Test
    void hideInessentialDropsEngineCountersAndSeparatesSections() {
        List<Line> left = f3().arrange(LEFT_189);
        assertEquals(List.of("Minecraft 1.8.9 (1.8.9/vanilla)", "144 fps (2 chunk updates) T: 260 vsync", "",
            "XYZ: 1.500 / 4.00000 / 2.500", "Block: 1 4 2", "Chunk: 1 4 2 in 0 0 0", "Facing: north (Towards negative Z) (180.0 / 0.0)", "",
            "Biome: Plains", "Light: 15 (15 sky, 0 block)", "", "Looking at: 1 3 1"), texts(left));
        assertEquals(List.of("Java: 1.8.0_51 64bit", "Mem: 20% 200/1000MB", "", "CPU: 8x AMD Ryzen", "", "Display: 1920x1080 (NVIDIA Corporation)",
            "NVIDIA GeForce RTX", "4.6.0 NVIDIA 555", "", "minecraft:grass", "snowy: §cfalse"), texts(f3().arrange(RIGHT_189)));
        BetterF3Module all = f3();
        set(all, "Hide Inessential", false);
        List<String> every = texts(all.arrange(LEFT_189));
        assertTrue(every.containsAll(List.of("C: 120/4000 (s) D: 4, L: 0, pC: 000, pU: 00, aB: 00", "Local Difficulty: 0.75 (Day 0)", "For help: press F3 + Q")));
        assertEquals(LEFT_189.size() + 2, every.size(), "every line; the two blanks stay, and a gap is added where World and Target start");
    }

    @Test
    void wrappedKeyHelpIsHiddenWithItsFirstLine() {
        List<String> column = List.of("Mem: 52% 1073/2048MiB", "Allocated: 62% 1280MiB", "Off-Heap: +423MB", "",
            "Debug charts: [F3+1] Profiler hidden; [F3+2] FPS + TPS hidden;", "[F3+3] Ping hidden; [F3+4] Lightmap hidden", "To edit: press [F3+F6]");
        assertEquals(List.of("Mem: 52% 1073/2048MiB", "Off-Heap: +423MB"), texts(f3().arrange(column)),
            "a labelled line after a hidden one stays; the unlabelled second help line goes with the first");
    }

    @Test
    void theOldStyleColoursLabelsBySectionValuesWhiteAndFpsBySpeed() {
        List<Line> left = f3().arrange(LEFT_189);
        Line version = left.get(0), fps = left.get(1), xyz = left.get(3);
        assertEquals(Section.PERFORMANCE.color, version.segs().get(0).color(), "an unlabelled first line is its section's header");
        assertEquals(new Seg("144 fps", 0xFF55FF55), fps.segs().get(0));
        assertEquals(0xFFFFFF55, BetterF3Module.fpsColor(45));
        assertEquals(0xFFFF5555, BetterF3Module.fpsColor(12));
        assertEquals(List.of(new Seg("XYZ: ", Section.POSITION.color), new Seg("1.500 / 4.00000 / 2.500", BetterF3Module.VALUE)), xyz.segs());
        List<Line> right = f3().arrange(RIGHT_189);
        assertEquals(BetterF3Module.VALUE, right.get(6).segs().get(0).color(), "the GL renderer under Display is a value");
        assertEquals(Section.TARGET.color, right.get(10).segs().get(0).color(), "a block property is a Target label");
    }

    @Test
    void sectionTogglesHideWholeSectionsAndTheirUnlabelledLines() {
        BetterF3Module f3 = f3();
        set(f3, "Show System", false);
        assertEquals(List.of("minecraft:grass", "snowy: §cfalse"), texts(f3.arrange(RIGHT_189)));
        set(f3, "Show Target", false);
        assertTrue(f3.arrange(RIGHT_189).isEmpty());
        set(f3, "Show Position", false);
        assertFalse(texts(f3.arrange(LEFT_189)).stream().anyMatch(t -> t.startsWith("XYZ") || t.startsWith("Facing")));
    }

    @Test
    void facingAndRotationReadTheYawAsTheGameDoes() {
        assertEquals("South (Towards positive Z)", BetterF3Module.facing(0));
        assertEquals("West (Towards negative X)", BetterF3Module.facing(89.6f));
        assertEquals("North (Towards negative Z)", BetterF3Module.facing(-180));
        assertEquals("East (Towards positive X)", BetterF3Module.facing(-90));
        assertEquals("East (Towards positive X)", BetterF3Module.facing(990), "past a full turn");
        assertEquals(-170f, BetterF3Module.wrapDegrees(190), 1e-4);
        assertEquals(89.6f, BetterF3Module.wrapDegrees(89.6f + 720), 1e-3);
    }

    @Test
    void slimeChunksFollowTheSeed() {
        int found = 0;
        for (int x = -50; x < 50; x++) for (int z = -50; z < 50; z++) if (BetterF3Module.slimeChunk(42L, x, z)) found++;
        assertTrue(found > 800 && found < 1200, "about one chunk in ten: " + found);
        assertNotEquals(BetterF3Module.slimeChunk(42L, 3, 7) && BetterF3Module.slimeChunk(42L, 4, 7) && BetterF3Module.slimeChunk(42L, 5, 7)
            && BetterF3Module.slimeChunk(42L, 6, 7), true, "not every chunk");
    }

    // The slide (owner: "buggy when you spam it, or sometimes it's not playing at all").

    @Test
    void theFirstFrameAfterOpeningStartsTheSlideEvenAfterAHitch() {
        Slide slide = new Slide();
        assertEquals(0, slide.step(true, 1_000 * MS, 0.35), 1e-9, "the first frame shows the start of the slide");
        assertEquals(0.05 / 0.35, slide.step(true, 1_200 * MS, 0.35), 1e-9, "a 200 ms hitch steps one 50 ms frame, never the whole slide");
        assertEquals(0.05 / 0.35, slide.step(true, 1_800 * MS, 0.35), 1e-9, "a 600 ms one (loading the world, a pause) steps nothing");
        assertEquals(0.05 / 0.35 + 0.016 / 0.35, slide.step(true, 1_816 * MS, 0.35), 1e-9);
        for (long t = 1_832; t <= 2_400; t += 16) slide.step(true, t * MS, 0.35);
        assertEquals(1, slide.progress, 1e-9, "in place");
    }

    @Test
    void spammingF3TurnsTheSlideAroundWhereItIs() {
        for (long toggleMs : new long[] {16, 50, 120, 300}) {
            Slide slide = new Slide();
            boolean open = true;
            double last = 0, maxStep = 0;
            long at = 0, nextToggle = toggleMs;
            for (int frame = 0; frame < 300; frame++, at += 8) {
                if (at >= nextToggle && at < 2_000) { open = !open; nextToggle += toggleMs; }
                double now = slide.step(open, at * MS, 0.35);
                maxStep = Math.max(maxStep, Math.abs(now - last));
                last = now;
            }
            assertTrue(maxStep <= 0.008 / 0.35 + 1e-9, "toggled every " + toggleMs + " ms: never more than one frame's step (" + maxStep + ")");
            assertEquals(open ? 1 : 0, last, 1e-9, "toggled every " + toggleMs + " ms: settles where F3 was left, never sticks half way");
        }
    }

    @Test
    void openCloseOpenWithin50msNeverRestartsOrPops() {
        Slide slide = new Slide();
        slide.step(true, 0, 0.35);
        double opened = 0;
        for (long t = 8; t <= 200; t += 8) opened = slide.step(true, t * MS, 0.35);
        double closing = slide.step(false, 208 * MS, 0.35);
        assertEquals(opened - 0.008 / 0.35, closing, 1e-9, "closing starts where the slide was");
        for (long t = 216; t <= 240; t += 8) closing = slide.step(false, t * MS, 0.35);
        double reopened = slide.step(true, 248 * MS, 0.35);
        assertEquals(closing + 0.008 / 0.35, reopened, 1e-9, "reopened 40 ms later: turns around, no jump back to 0 or to 1");
    }

    @Test
    void aSlideOutNobodyDrewHasEndedAndSlideInOffIsInstant() {
        Slide slide = new Slide();
        slide.step(true, 0, 0.35);
        for (long t = 8; t <= 400; t += 8) slide.step(true, t * MS, 0.35);
        slide.step(false, 408 * MS, 0.35);
        assertEquals(0, slide.step(true, 1_000 * MS, 0.35), 1e-9, "closed and not drawn for 600 ms (HUD hidden): opening starts from 0");
        Slide instant = new Slide();
        assertEquals(1, instant.step(true, 0, 0), 1e-9);
        assertEquals(0, instant.step(false, 8 * MS, 0), 1e-9);
    }

    @Test
    void linesSlideInByStableKeysNotByRow() {
        Side side = new Side();
        double first = side.slot("FPS"), second = side.slot("XYZ");
        assertTrue(first < second, "first seen, first in");
        assertEquals(second, side.slot("XYZ"), "the same line keeps its slot");
        side.slot("TARGET");
        assertEquals(second, side.slot("XYZ"), "a line appearing later (Targeted Block) does not move the others");
        assertEquals(0, BetterF3Module.ease(0, 0), 1e-9);
        assertEquals(1, BetterF3Module.ease(1, 1), 1e-9, "the last line is in place when the slide ends");
        assertTrue(BetterF3Module.ease(0.3, 0) > BetterF3Module.ease(0.3, 1), "later lines follow");
    }

    @Test
    void closingSlidesTheLastLinesOutThenForgetsThem() {
        BetterF3Module f3 = f3();
        Screen screen = new Screen();
        f3.draw(screen.g, LEFT_189, true, 640, true, screenshot(), 0);
        assertFalse(f3.drawn(true).containsKey("SLIME"), "the first frame: the last line has not started");
        for (long t = 8; t <= 600; t += 8) f3.draw(screen.g, LEFT_189, true, 640, true, screenshot(), t * MS);
        Map<String, Integer> open = Map.copyOf(f3.drawn(true));
        assertEquals(2, (int) open.get("FPS"), "in place at x 2");
        assertEquals(2, (int) open.get("SLIME"));
        assertTrue(f3.closing());
        long now = 608 * MS;
        for (int i = 0; i < 4; i++) f3.drawClosed(screen.g, now + 40 * MS * i);
        assertTrue(f3.drawn(true).get("SLIME") < 2, "sliding out to the left, the last line in first");
        assertEquals(2, (int) f3.drawn(true).get("FPS"), "the first line in goes last");
        for (int i = 4; i < 40; i++) f3.drawClosed(screen.g, now + 40 * MS * i);
        assertFalse(f3.closing(), "gone: nothing kept");
        screen.drawn.clear();
        f3.drawClosed(screen.g, now + 40 * MS * 41);
        assertTrue(screen.drawn.isEmpty());
    }

    @Test
    void the1911OverrideGivesEveryoneTheNewLookOnce() {
        var module = ModuleManager.getInstance().getModule("BetterF3");
        boolean before = module.isEnabled();
        try {
            module.setEnabled(true);
            ConfigManager.applyJson(JsonParser.parseString("{\"modules\":{\"BetterF3\":{\"enabled\":false,\"options\":{\"Text Shadow\":false,"
                + "\"Background\":true,\"Style\":1}}},\"hud\":{\"hud1910OverrideApplied\":true}}").getAsJsonObject());
            assertFalse(module.isEnabled(), "off stays the player's choice");
            assertTrue(((BoolOption) module.getOption("Text Shadow")).get());
            assertFalse(((BoolOption) module.getOption("Background")).get());
            assertEquals(BetterF3Module.BETTERF3, ((DropdownOption) module.getOption("Style")).getIndex(), "the BetterF3 look, once");
            ConfigManager.applyJson(JsonParser.parseString("{\"modules\":{\"BetterF3\":{\"enabled\":true,\"options\":{\"Background\":true,\"Style\":1}}},"
                + "\"hud\":{\"hud1910OverrideApplied\":true,\"betterF31911Applied\":true}}").getAsJsonObject());
            assertTrue(((BoolOption) module.getOption("Background")).get(), "after the override, the player's choices stay");
            assertEquals(BetterF3Module.LADS, ((DropdownOption) module.getOption("Style")).getIndex());
            assertTrue(ConfigManager.toJson().getAsJsonObject("hud").get("betterF31911Applied").getAsBoolean());
        } finally {
            module.getOptions().forEach(Option::reset);
            module.setEnabled(before);
        }
    }

    @Test
    void savedSettingsFrom160KeepTheNewDefaultButLaterChoicesStay() {
        var module = ModuleManager.getInstance().getModule("BetterF3");
        boolean before = module.isEnabled();
        try {
            module.setEnabled(true);
            ConfigManager.applyJson(JsonParser.parseString(
                "{\"modules\":{\"BetterF3\":{\"enabled\":false,\"options\":{\"Rainbow Colors\":false,\"Hide Inessential\":true}}}}").getAsJsonObject());
            assertTrue(module.isEnabled(), "1.6.0 could not switch it: its saved 'false' was only the old default");
            ConfigManager.applyJson(JsonParser.parseString(
                "{\"modules\":{\"BetterF3\":{\"enabled\":false,\"options\":{\"Text Shadow\":true}}}}").getAsJsonObject());
            assertFalse(module.isEnabled(), "a 1.7.0 save is the player's choice");
        } finally {
            module.setEnabled(before);
        }
    }
}
