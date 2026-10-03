package com.thelads.core.client.gui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KeyBindSearchTest {
    private static boolean jump(String query) { return KeyBindSearch.matches(query, "Jump", "Movement", "SPACE"); }

    @Test void plainWordsMatchTheNameOnly() {
        assertTrue(jump("")); assertTrue(jump("  ")); assertTrue(jump("JU")); assertTrue(jump("ju mp"));
        assertFalse(jump("movement"), "the category needs category:");
        assertFalse(jump("space"), "the key needs key:");
        assertTrue(KeyBindSearch.matches("drop item", "Drop Item", "Gameplay", "Q"));
    }

    @Test void prefixedTermsMatchTheirPartAndEveryTermMustMatch() {
        assertTrue(jump("category:move")); assertTrue(jump("Key:space")); assertTrue(jump("name:jump"));
        assertTrue(jump("category:movement ju")); assertFalse(jump("category:movement sneak"));
        assertFalse(jump("key:w")); assertTrue(jump("key:"), "a prefix still being typed keeps every row");
        assertFalse(jump("foo:bar"), "an unknown prefix is part of a plain word");
    }

    @Test void quotesKeepSpaces() {
        assertTrue(KeyBindSearch.matches("key:\"button 2\"", "Use Item", "Gameplay", "Button 2"));
        assertFalse(KeyBindSearch.matches("key:\"button 1\"", "Use Item", "Gameplay", "Button 2"));
        assertTrue(KeyBindSearch.matches("\"use it", "Use Item", "Gameplay", "Button 2"), "an unclosed quote runs to the end");
    }
}
