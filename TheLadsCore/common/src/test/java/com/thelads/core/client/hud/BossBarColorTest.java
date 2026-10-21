package com.thelads.core.client.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BossBarColorTest {

    @Test
    void directCodeMappingMatchesSpec() {
        // Red: §c
        assertSame(BossBarColor.RED, BossBarColor.fromCode('c'));
        assertSame(BossBarColor.RED, BossBarColor.fromCode('C'));
        assertSame(BossBarColor.RED, BossBarColor.fromCode('4'));

        // Yellow: §e
        assertSame(BossBarColor.YELLOW, BossBarColor.fromCode('e'));
        assertSame(BossBarColor.YELLOW, BossBarColor.fromCode('E'));
        assertSame(BossBarColor.YELLOW, BossBarColor.fromCode('6'));

        // Green: §a, §2
        assertSame(BossBarColor.GREEN, BossBarColor.fromCode('a'));
        assertSame(BossBarColor.GREEN, BossBarColor.fromCode('A'));
        assertSame(BossBarColor.GREEN, BossBarColor.fromCode('2'));

        // Blue: §b, §9, §3, §1
        assertSame(BossBarColor.BLUE, BossBarColor.fromCode('b'));
        assertSame(BossBarColor.BLUE, BossBarColor.fromCode('B'));
        assertSame(BossBarColor.BLUE, BossBarColor.fromCode('9'));
        assertSame(BossBarColor.BLUE, BossBarColor.fromCode('3'));
        assertSame(BossBarColor.BLUE, BossBarColor.fromCode('1'));

        // Pink: §d
        assertSame(BossBarColor.PINK, BossBarColor.fromCode('d'));
        assertSame(BossBarColor.PINK, BossBarColor.fromCode('D'));

        // Purple: §5
        assertSame(BossBarColor.PURPLE, BossBarColor.fromCode('5'));

        // White: §f, §7
        assertSame(BossBarColor.WHITE, BossBarColor.fromCode('f'));
        assertSame(BossBarColor.WHITE, BossBarColor.fromCode('F'));
        assertSame(BossBarColor.WHITE, BossBarColor.fromCode('7'));
        assertSame(BossBarColor.WHITE, BossBarColor.fromCode('8'));

        // Formatting or unknown codes return null
        assertNull(BossBarColor.fromCode('l'));
        assertNull(BossBarColor.fromCode('k'));
        assertNull(BossBarColor.fromCode('m'));
        assertNull(BossBarColor.fromCode('n'));
        assertNull(BossBarColor.fromCode('o'));
        assertNull(BossBarColor.fromCode('r'));
        assertNull(BossBarColor.fromCode('z'));
    }

    @Test
    void formattedNameColorExtraction() {
        // Simple color prefixes
        assertSame(BossBarColor.RED, BossBarColor.fromFormattedName("§cRed Boss"));
        assertSame(BossBarColor.YELLOW, BossBarColor.fromFormattedName("§eYellow Dragon"));
        assertSame(BossBarColor.GREEN, BossBarColor.fromFormattedName("§aGreen Giant"));
        assertSame(BossBarColor.GREEN, BossBarColor.fromFormattedName("§2Dark Green Giant"));
        assertSame(BossBarColor.BLUE, BossBarColor.fromFormattedName("§bAqua Lord"));
        assertSame(BossBarColor.BLUE, BossBarColor.fromFormattedName("§9Blue Beast"));
        assertSame(BossBarColor.BLUE, BossBarColor.fromFormattedName("§3Dark Aqua"));
        assertSame(BossBarColor.BLUE, BossBarColor.fromFormattedName("§1Dark Blue"));
        assertSame(BossBarColor.PINK, BossBarColor.fromFormattedName("§dPink Wither"));
        assertSame(BossBarColor.PURPLE, BossBarColor.fromFormattedName("§5Purple Entity"));
        assertSame(BossBarColor.WHITE, BossBarColor.fromFormattedName("§fWhite Phantom"));
        assertSame(BossBarColor.WHITE, BossBarColor.fromFormattedName("§7Gray Phantom"));

        // Ampersand format support
        assertSame(BossBarColor.RED, BossBarColor.fromFormattedName("&cRed Boss"));
        assertSame(BossBarColor.BLUE, BossBarColor.fromFormattedName("&9Blue Boss"));

        // Formatting codes before color code are skipped to find the first color
        assertSame(BossBarColor.RED, BossBarColor.fromFormattedName("§l§cBold Red Boss"));
        assertSame(BossBarColor.GREEN, BossBarColor.fromFormattedName("§o§n§2Styled Green Boss"));
        assertSame(BossBarColor.YELLOW, BossBarColor.fromFormattedName("§r§eReset Yellow Boss"));

        // First color takes precedence over subsequent colors
        assertSame(BossBarColor.YELLOW, BossBarColor.fromFormattedName("§eGold §cWarrior"));
        assertSame(BossBarColor.BLUE, BossBarColor.fromFormattedName("§bIce §dPrincess"));

        // No color codes (plain text, null, style-only) return null for vanilla look
        assertNull(BossBarColor.fromFormattedName("Ender Dragon"));
        assertNull(BossBarColor.fromFormattedName("Wither"));
        assertNull(BossBarColor.fromFormattedName(null));
        assertNull(BossBarColor.fromFormattedName(""));
        assertNull(BossBarColor.fromFormattedName("§l§oPlain Styled Boss"));
        assertNull(BossBarColor.fromFormattedName("§r§kGlitch"));
    }

    @Test
    void paletteRgbValuesMatch19Spec() {
        assertEquals(0xFFB90090, BossBarColor.PINK.rgb());
        assertEquals(0xFF008FB9, BossBarColor.BLUE.rgb());
        assertEquals(0xFFB92A00, BossBarColor.RED.rgb());
        assertEquals(0xFF16B900, BossBarColor.GREEN.rgb());
        assertEquals(0xFFB6B900, BossBarColor.YELLOW.rgb());
        assertEquals(0xFF6100B9, BossBarColor.PURPLE.rgb());
        assertEquals(0xFFB9B9B9, BossBarColor.WHITE.rgb());

        for (BossBarColor color : BossBarColor.values()) {
            assertTrue(color.r() >= 0.0f && color.r() <= 1.0f);
            assertTrue(color.g() >= 0.0f && color.g() <= 1.0f);
            assertTrue(color.b() >= 0.0f && color.b() <= 1.0f);
            assertNotNull(color.colorName());
        }
    }
}
