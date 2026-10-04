package com.thelads.core.client;

import com.thelads.core.client.DurabilityPresentation.*;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DurabilityPresentationTest {
    private static final int BASE = 0x123456;
    private static Settings settings(Shape shape, Tint tint, boolean label, boolean showMax, boolean colorize) {
        return new Settings(shape, tint, label, showMax, colorize, BASE);
    }
    private static String text(List<Part> parts) { return parts.stream().map(Part::text).reduce("", String::concat); }

    @Test void countShowsUsesLeftAndOptionalMaximum() {
        assertEquals("Uses left: 73 / 100", text(DurabilityPresentation.line(100, 27, settings(Shape.COUNT, Tint.WEAR, true, true, true))));
        assertEquals("Uses left: 100 / 100", text(DurabilityPresentation.line(100, 0, settings(Shape.COUNT, Tint.WEAR, true, true, true))));
        assertEquals("73", text(DurabilityPresentation.line(100, 27, settings(Shape.COUNT, Tint.WEAR, false, false, true))));
    }

    @Test void gaugeIsOneLineOfPipsAndAPercentage() {
        String pips = "|".repeat(DurabilityPresentation.PIPS);
        assertEquals("Uses left: " + pips + " 73%", text(DurabilityPresentation.line(100, 27, settings(Shape.GAUGE, Tint.WEAR, true, true, true))));
        var parts = DurabilityPresentation.line(100, 27, settings(Shape.GAUGE, Tint.WEAR, false, true, true));
        assertEquals(14, parts.get(0).text().length()); // 73% of 20 pips, rounded down
        assertEquals(0x555555, parts.get(1).rgb());
        // One use left still lights a pip and reads 1%: a working tool never shows an empty gauge.
        var last = DurabilityPresentation.line(1561, 1560, settings(Shape.GAUGE, Tint.WEAR, false, true, true));
        assertEquals("|", last.get(0).text());
        assertEquals(" 1%", last.get(2).text());
        assertEquals(" 0%", DurabilityPresentation.line(10, 10, settings(Shape.GAUGE, Tint.WEAR, false, true, true)).get(2).text());
    }

    @Test void wordsFollowQuartersOfTheItemsLife() {
        assertEquals("Like new", DurabilityPresentation.condition(100, 100));
        assertEquals("Good", DurabilityPresentation.condition(99, 100));
        assertEquals("Good", DurabilityPresentation.condition(75, 100));
        assertEquals("Worn", DurabilityPresentation.condition(74, 100));
        assertEquals("Worn", DurabilityPresentation.condition(50, 100));
        assertEquals("Battered", DurabilityPresentation.condition(25, 100));
        assertEquals("About to break", DurabilityPresentation.condition(24, 100));
        assertEquals("About to break", DurabilityPresentation.condition(0, 100));
        assertEquals("Condition: Worn", text(DurabilityPresentation.line(100, 40, settings(Shape.WORDS, Tint.WEAR, true, true, true))));
    }

    @Test void wearColorIsTheDurabilityBarsRedToGreenHue() {
        assertEquals(0x00ff00, DurabilityPresentation.wearColor(100, 100));
        assertEquals(0xffff00, DurabilityPresentation.wearColor(50, 100));
        assertEquals(0xff0000, DurabilityPresentation.wearColor(0, 100));
        assertEquals(0xff0000, DurabilityPresentation.wearColor(-5, 100));
    }

    @Test void tintChoicesAndColorizeOff() {
        assertEquals(BASE, DurabilityPresentation.line(100, 99, settings(Shape.WORDS, Tint.PLAIN, false, true, true)).getFirst().rgb());
        assertEquals(0xffaa00, DurabilityPresentation.line(100, 99, settings(Shape.WORDS, Tint.GOLD, false, true, true)).getFirst().rgb());
        var count = DurabilityPresentation.line(100, 99, settings(Shape.COUNT, Tint.WEAR, true, true, true));
        assertEquals(BASE, count.get(0).rgb()); // label
        assertEquals(DurabilityPresentation.wearColor(1, 100), count.get(1).rgb());
        assertEquals(BASE, count.get(2).rgb()); // maximum
        for (Shape shape : Shape.values()) for (Tint tint : Tint.values())
            assertTrue(DurabilityPresentation.line(100, 60, settings(shape, tint, true, true, false)).stream().allMatch(part -> part.rgb() == BASE));
    }

    @Test void outOfRangeDamageIsClamped() {
        assertTrue(DurabilityPresentation.line(0, 1, settings(Shape.GAUGE, Tint.PLAIN, true, true, true)).isEmpty());
        assertEquals("0", text(DurabilityPresentation.line(100, Integer.MAX_VALUE, settings(Shape.COUNT, Tint.PLAIN, false, false, true))));
        assertEquals("100", text(DurabilityPresentation.line(100, -20, settings(Shape.COUNT, Tint.PLAIN, false, false, true))));
        assertEquals("100", text(DurabilityPresentation.line(100, Integer.MIN_VALUE, settings(Shape.COUNT, Tint.PLAIN, false, false, true))));
    }

    @Test void chatColorPicksTheNearestOfSixteen() {
        assertEquals(0xa, DurabilityPresentation.chatColor(0x55ff55));
        assertEquals(0x6, DurabilityPresentation.chatColor(0xffaa00));
        assertEquals(0xc, DurabilityPresentation.chatColor(0xff5555));
        assertEquals(0x7, DurabilityPresentation.chatColor(0xaaaaaa));
        assertEquals(0x8, DurabilityPresentation.chatColor(0x123456));
        assertEquals(0x1, DurabilityPresentation.chatColor(0x0000a0));
        assertEquals(0xf, DurabilityPresentation.chatColor(0xfefefe));
    }

    @Test void exclusionsTakeWholeModsOrSingleItems() {
        var hidden = DurabilityPresentation.exclusions(" Botania; mekanism:atomic_disassembler  ,, not/valid,create");
        assertEquals(Set.of("botania", "mekanism:atomic_disassembler", "create"), hidden);
        assertFalse(DurabilityPresentation.shows("botania:manasteel_pick", 100, 10, false, true, hidden));
        assertTrue(DurabilityPresentation.shows("botanicadds:pick", 100, 10, false, true, hidden)); // no prefix match
        assertFalse(DurabilityPresentation.shows("mekanism:atomic_disassembler", 100, 10, false, true, hidden));
        assertTrue(DurabilityPresentation.shows("mekanism:meka_tool", 100, 10, false, true, hidden));
        assertEquals(Set.of(), DurabilityPresentation.exclusions(null));
        assertEquals(Set.of(), DurabilityPresentation.exclusions("   "));
    }

    @Test void vanillaOnlyAndWhenFullAreSeparateFilters() {
        assertFalse(DurabilityPresentation.shows("create:wrench", 100, 10, true, true, Set.of()));
        assertTrue(DurabilityPresentation.shows("minecraft:iron_pickaxe", 100, 10, true, false, Set.of()));
        assertTrue(DurabilityPresentation.shows("iron_pickaxe", 100, 10, true, false, Set.of()));
        assertFalse(DurabilityPresentation.shows("minecraft:iron_pickaxe", 100, 0, false, false, Set.of()));
        assertTrue(DurabilityPresentation.shows("minecraft:iron_pickaxe", 100, 0, false, true, Set.of()));
        assertFalse(DurabilityPresentation.shows("minecraft:stick", 0, 0, false, true, Set.of()));
    }
}
