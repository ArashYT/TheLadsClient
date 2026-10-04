package com.thelads.core.modules;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.BetterF3Module.Line;
import com.thelads.core.modules.BetterF3Module.Section;
import java.util.List;
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

    private static BetterF3Module f3() { return new BetterF3Module(); }
    private static String text(Line line) { return line.label() + line.value(); }
    private static List<String> texts(List<Line> lines) { return lines.stream().map(l -> l.gap() ? "" : text(l)).toList(); }

    @Test
    void defaultsAreOnWithEverySectionShown() {
        BetterF3Module f3 = f3();
        assertTrue(f3.isEnabled(), "the remake is on, as the BetterF3 mod was on 26.2");
        assertFalse(((BoolOption) f3.getOption("Rainbow Colors")).get());
        assertTrue(((BoolOption) f3.getOption("Hide Inessential")).get());
        for (Section section : Section.values()) assertTrue(((BoolOption) f3.getOption(section.option)).get(), section.option);
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
        ((BoolOption) all.getOption("Hide Inessential")).set(false);
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
    void labelsTakeTheSectionColourValuesWhiteAndFpsItsSpeed() {
        List<Line> left = f3().arrange(LEFT_189);
        Line version = left.get(0), fps = left.get(1), xyz = left.get(3);
        assertEquals(Section.PERFORMANCE.color, version.valueColor(), "an unlabelled first line is its section's header");
        assertEquals("144 fps", fps.label());
        assertEquals(0xFF55FF55, fps.labelColor());
        assertEquals(0xFFFFFF55, BetterF3Module.fpsColor(45));
        assertEquals(0xFFFF5555, BetterF3Module.fpsColor(12));
        assertEquals("XYZ: ", xyz.label());
        assertEquals(Section.POSITION.color, xyz.labelColor());
        assertEquals(BetterF3Module.VALUE, xyz.valueColor());
        List<Line> right = f3().arrange(RIGHT_189);
        assertEquals(BetterF3Module.VALUE, right.get(6).valueColor(), "the GL renderer under Display is a value");
        assertEquals(Section.TARGET.color, right.get(10).labelColor(), "a block property is a Target label");
    }

    @Test
    void sectionTogglesHideWholeSectionsAndTheirUnlabelledLines() {
        BetterF3Module f3 = f3();
        ((BoolOption) f3.getOption("Show System")).set(false);
        assertEquals(List.of("minecraft:grass", "snowy: §cfalse"), texts(f3.arrange(RIGHT_189)));
        ((BoolOption) f3.getOption("Show Target")).set(false);
        assertTrue(f3.arrange(RIGHT_189).isEmpty());
        ((BoolOption) f3.getOption("Show Position")).set(false);
        assertFalse(texts(f3.arrange(LEFT_189)).stream().anyMatch(t -> t.startsWith("XYZ") || t.startsWith("Facing")));
    }

    @Test
    void linesSlideInOnOpenAndStayWithSlideInOff() {
        assertEquals(0, BetterF3Module.slide(0, 0), 1e-9);
        assertEquals(1, BetterF3Module.slide(200, 0), 1e-9);
        assertTrue(BetterF3Module.slide(100, 0) > BetterF3Module.slide(100, 3), "later lines follow");
        assertEquals(1, BetterF3Module.slide(200 + 15 * 20, 20), 1e-9);
        BetterF3Module f3 = f3();
        assertEquals(0, f3.sinceOpened(true, 10_000));
        assertEquals(16, f3.sinceOpened(true, 10_016));
        assertEquals(0, f3.sinceOpened(true, 11_000), "drawn again after a pause: the screen was reopened");
        assertEquals(10, f3.sinceOpened(false, 11_010), "26.x: entries shown with the screen closed keep the time");
        assertEquals(0, f3.sinceOpened(true, 11_020), "opening the screen over them restarts it");
        ((BoolOption) f3.getOption("Slide In")).set(false);
        assertEquals(1, BetterF3Module.slide(f3.sinceOpened(true, 20_000), 0), 1e-9);
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
