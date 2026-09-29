package com.thelads.core.client;
import com.thelads.core.client.DurabilityPresentation.*;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DurabilityPresentationTest {
    private static Style style(Format format, Coloring coloring, boolean hint, boolean maximum, boolean colorize) {
        return new Style(format, coloring, hint, maximum, colorize, 0x123456);
    }
    private static String text(List<Line> lines) { return lines.stream().map(line -> line.spans().stream().map(Span::text).reduce("", String::concat)).reduce((a, b) -> a + "\n" + b).orElse(""); }
    @Test void numbersRespectHintAndMaximum() {
        assertEquals("Durability: 73 / 100", text(DurabilityPresentation.lines(100, 27, style(Format.NUMBERS, Coloring.VARYING, true, true, true))));
        assertEquals("73", text(DurabilityPresentation.lines(100, 27, style(Format.NUMBERS, Coloring.VARYING, false, false, true))));
        assertEquals("Durability: 100", text(DurabilityPresentation.lines(100, 0, style(Format.NUMBERS, Coloring.VARYING, true, true, true))));
    }
    @Test void barHasTenSegmentsAtBoundsAndNearestTenth() {
        assertEquals("Durability:\n[███████▒▒▒]", text(DurabilityPresentation.lines(100, 27, style(Format.BAR, Coloring.VARYING, true, true, true))));
        assertEquals("[▒▒▒▒▒▒▒▒▒▒]", text(DurabilityPresentation.lines(100, 100, style(Format.BAR, Coloring.VARYING, false, true, true))));
        assertEquals("[██████████]", text(DurabilityPresentation.lines(100, 0, style(Format.BAR, Coloring.VARYING, false, true, true))));
    }
    @Test void fourConditionLabelsHaveExactBoundaries() {
        assertEquals("Pristine", DurabilityPresentation.condition(100, 100));
        assertEquals("Slightly damaged", DurabilityPresentation.condition(40, 100));
        assertEquals("Severely damaged", DurabilityPresentation.condition(39, 100));
        assertEquals("Severely damaged", DurabilityPresentation.condition(10, 100));
        assertEquals("Nearly broken", DurabilityPresentation.condition(9, 100));
    }
    @Test void reactiveColorsHaveExactBoundaries() {
        assertEquals(0x55ff55, DurabilityPresentation.color(40, 100));
        assertEquals(0xffaa00, DurabilityPresentation.color(39, 100));
        assertEquals(0xffaa00, DurabilityPresentation.color(10, 100));
        assertEquals(0xff5555, DurabilityPresentation.color(9, 100));
    }
    @Test void baseAndGoldPoliciesOverrideReactiveColor() {
        var base = DurabilityPresentation.lines(100, 99, style(Format.TEXT, Coloring.BASE, false, true, true)).getFirst();
        assertEquals(0x123456, base.spans().getFirst().rgb());
        var gold = DurabilityPresentation.lines(100, 99, style(Format.TEXT, Coloring.GOLD, false, true, true)).getFirst();
        assertEquals(0xffaa00, gold.spans().getFirst().rgb());
    }
    @Test void disablingColorizeUsesConfiguredBaseEverywhere() {
        for (Format format : Format.values()) for (Coloring coloring : Coloring.values())
            assertTrue(DurabilityPresentation.lines(100, 99, style(format, coloring, true, true, false)).stream().flatMap(line -> line.spans().stream()).allMatch(span -> span.rgb() == 0x123456));
    }
    @Test void varyingNumbersKeepHintAndMaximumInBaseColor() {
        var spans = DurabilityPresentation.lines(100, 99, style(Format.NUMBERS, Coloring.VARYING, true, true, true)).getFirst().spans();
        assertEquals(0x123456, spans.getFirst().rgb()); assertEquals(0xff5555, spans.get(1).rgb()); assertEquals(0x123456, spans.getLast().rgb());
    }
    @Test void invalidAndExtremeDamageIsBounded() {
        assertTrue(DurabilityPresentation.lines(0, 1, style(Format.BAR, Coloring.BASE, true, true, true)).isEmpty());
        assertEquals("0", text(DurabilityPresentation.lines(100, Integer.MAX_VALUE, style(Format.NUMBERS, Coloring.BASE, false, false, true))));
        assertEquals("100", text(DurabilityPresentation.lines(100, -20, style(Format.NUMBERS, Coloring.BASE, false, false, true))));
        assertEquals(0x55ff55, DurabilityPresentation.color(Integer.MAX_VALUE, Integer.MAX_VALUE));
    }
    @Test void namespaceFiltersUseWholeIdsAndIgnoreInvalidTokens() {
        var excluded = DurabilityPresentation.excludedNamespaces(" TConstruct, supplementaries, ,not valid,minecraft:stone,tconstruct");
        assertEquals(Set.of("tconstruct", "supplementaries"), excluded);
        assertFalse(DurabilityPresentation.visible("tconstruct", 100, 10, false, true, excluded));
        assertTrue(DurabilityPresentation.visible("tconstruct_extra", 100, 10, false, true, excluded));
    }
    @Test void vanillaAndFullFiltersRemainIndependent() {
        assertFalse(DurabilityPresentation.visible("other", 100, 10, true, true, Set.of()));
        assertTrue(DurabilityPresentation.visible("minecraft", 100, 10, true, false, Set.of()));
        assertFalse(DurabilityPresentation.visible("minecraft", 100, 0, false, false, Set.of()));
        assertTrue(DurabilityPresentation.visible("minecraft", 100, 0, false, true, Set.of()));
    }
}
