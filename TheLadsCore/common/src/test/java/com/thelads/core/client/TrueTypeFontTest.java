package com.thelads.core.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TrueTypeFontTest {

    @BeforeEach
    void setUp() {
        TrueTypeFont.on = true;
        TrueTypeFont.fontSize = 18;
        TrueTypeFont.fontFamily = "SansSerif";
        TrueTypeFont.reloadFont();
    }

    @Test
    void fontLoadsAndHasPositiveHeight() {
        assertTrue(TrueTypeFont.isReady(), "Font should initialize successfully");
        assertEquals(9, TrueTypeFont.getFontHeight());
    }

    @Test
    void emptyStringReturnsZeroWidth() {
        assertEquals(0, TrueTypeFont.getStringWidth(""));
        assertEquals(0, TrueTypeFont.getStringWidth(null));
    }

    @Test
    void measuringStringComputesValidWidth() {
        int width = TrueTypeFont.getStringWidth("Hello World");
        assertTrue(width > 0, "String width should be positive");
    }

    @Test
    void formattingCodesAreStrippedFromWidth() {
        int plainWidth = TrueTypeFont.getStringWidth("Hello World");
        int formattedWidth = TrueTypeFont.getStringWidth("§aHello §cWorld§r");
        assertEquals(plainWidth, formattedWidth, "Color codes should not affect measured width");
    }

    @Test
    void drawStringReturnsWidth() {
        int width = TrueTypeFont.drawString("Test Message", 10, 20, 0xFFFFFFFF, true);
        assertEquals(TrueTypeFont.getStringWidth("Test Message"), width);
    }
}
