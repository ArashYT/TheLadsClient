package com.thelads.core.client.font;

import com.thelads.core.modules.CustomFontModule;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CustomFontTest {
    @Test
    void testCustomFontDefaults() {
        CustomFontModule module = new CustomFontModule();
        assertFalse(module.isEnabled());
        assertEquals("Default", module.fontFamily.getValue());
        assertTrue(module.smoothFont.get());
        assertTrue(module.textShadow.get());
        assertEquals(100, module.fontScale.getIntValue());
    }

    @Test
    void testManagerDefaultsWithoutModule() {
        assertFalse(CustomFontManager.isCustomFontActive());
        assertEquals(1.0f, CustomFontManager.getFontScaleMultiplier(), 0.001f);
        assertTrue(CustomFontManager.isTextShadowEnabled());
        assertFalse(CustomFontManager.isSmoothFont());
        assertEquals("Default", CustomFontManager.getFontFamily());
    }
}
