package com.thelads.core.client.title;

import com.thelads.core.client.bridge.IconAtlas;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ButtonIconsTest {
    /** Every icon a label, a pause menu slot or the title maps to has a glyph in the atlas (tools/icons/icons.json). */
    @Test void everyMappedIconHasAGlyph() {
        ButtonIcons.icons().forEach(icon -> assertTrue(IconAtlas.has(icon), "no glyph for " + icon));
        for (PauseMenuLayout.Slot slot : PauseMenuLayout.Slot.values()) assertTrue(IconAtlas.has(PauseMenuLayout.icon(slot)), slot.name());
        for (String kind : new String[] {"user", "switch", "globe", "layout", "fullscreen", "windowed", "play", "multiplayer", "server", "settings", "camera",
                "access", "quit", "lads", "friends", "wardrobe", "dots", "replay", "mods", "more", "language", "copy", "edit", "delete", "refresh", "folder"})
            assertTrue(IconAtlas.has(kind), kind);
    }

    @Test void atlasCellsLieInsideTheTexture() {
        for (String kind : new String[] {"play", "check", "mods", "folder-open"}) {
            int cell = IconAtlas.cell(kind);
            assertTrue(cell >= 0);
            assertTrue(IconAtlas.u(cell) >= 1 && IconAtlas.u(cell) + IconAtlas.CELL <= IconAtlas.SIZE);
            assertTrue(IconAtlas.v(cell) >= 1 && IconAtlas.v(cell) + IconAtlas.CELL <= IconAtlas.SIZE);
        }
        assertEquals(-1, IconAtlas.cell("no-such-icon"));
    }

    @Test void keysWinAndUnmappedLabelsStayWithoutAnIcon() {
        assertEquals("check", ButtonIcons.of("gui.done", "anything"));
        assertEquals("cross", ButtonIcons.of(null, "Cancel"));
        assertEquals("server", ButtonIcons.of(null, com.thelads.core.client.LadsNetwork.TITLE_LABEL));
        assertEquals(IconAtlas.cell("server"), IconAtlas.cell(PauseMenuLayout.icon(PauseMenuLayout.Slot.NETWORK)));
        assertEquals("settings", ButtonIcons.of("menu.options", null));
        assertNull(ButtonIcons.of("options.generic_value", null));
        assertNull(ButtonIcons.of(null, "Fullscreen: ON"));
        assertNull(ButtonIcons.of(null, null));
    }

    @Test void vanillaTextInAnyLanguageMapsThroughItsKey() {
        java.util.function.Function<String, String> german = key -> key.equals("gui.done") ? "Fertig" : key.equals("menu.options") ? "Optionen..." : key;
        assertEquals("check", ButtonIcons.ofText("Fertig", "de_de", german));
        assertEquals("settings", ButtonIcons.ofText("Optionen...", "de_de", german));
        assertEquals("cross", ButtonIcons.ofText("Cancel", "de_de", german)); // English labels keep working
        assertNull(ButtonIcons.ofText("Ganz anders", "de_de", german));
        assertNull(ButtonIcons.ofText("gui.done", "de_de", german)); // an untranslated key is not a label
    }

    @Test void optionButtonsMapThroughTheirCaption() {
        assertTrue(ButtonIcons.wraps("options.generic_value"));
        assertTrue(ButtonIcons.wraps("options.on.composed"));
        assertFalse(ButtonIcons.wraps("gui.done"));
        assertEquals("picture", ButtonIcons.of("options.graphics", null)); // the caption inside the wrapper
        assertEquals("volume", ButtonIcons.of("soundCategory.master", null));
        assertNull(ButtonIcons.of("key.keyboard.home", null)); // a bound key's name stays plain
        assertNull(ButtonIcons.of(null, "[ Home ]"));
        java.util.function.Function<String, String> english = key -> key.equals("options.graphics") ? "Graphics" : key.equals("options.fov") ? "FOV" : key.equals("selectWorld.mapFeatures") ? "Generate Structures:" : key;
        assertEquals("picture", ButtonIcons.ofCaptioned("Graphics: Fancy", "xx_captions", english)); // 1.8.9 draws one string
        assertEquals("eye", ButtonIcons.ofCaptioned("FOV : 70", "xx_captions", english)); // French puts a space before the colon
        assertEquals("home", ButtonIcons.ofCaptioned("Generate Structures: ON", "xx_captions", english)); // 1.8.9 caption ends with a colon
        assertEquals("target", ButtonIcons.of("key.attack", null));
        assertEquals("hand", ButtonIcons.of("key.use", null));
        assertNull(ButtonIcons.ofCaptioned("Search: All", "xx_captions", english));
        assertNull(ButtonIcons.ofCaptioned(null, "xx_captions", english));
    }

    @Test void iconNeedsRoomBesideItsLabel() {
        assertTrue(TitleScreenTheme.iconFits(100, 40));
        assertFalse(TitleScreenTheme.iconFits(60, 40));
    }
}
