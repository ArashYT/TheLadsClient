package com.thelads.core.client;

import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.ToggleNametagsModule;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NicknamesTest {
    @Test void parsesPairsAndSkipsMalformedOnes() {
        assertEquals(Map.of("steve", "Bob", "alex_2", "Big Al"), Nicknames.parse(" Steve = Bob ,Alex_2=Big Al, =x, y=, junk,,"));
        assertTrue(Nicknames.parse("").isEmpty());
        assertTrue(Nicknames.parse(null).isEmpty());
    }

    @Test void replacesWholeWordsCaseInsensitively() {
        var names = Map.of("steve", "Bob");
        assertEquals("<Bob> hi Bob, Bob!", Nicknames.rename("<Steve> hi steve, STEVE!", names));
        assertEquals("Steve123 Steve_ xSteve", Nicknames.rename("Steve123 Steve_ xSteve", names), "only whole words");
        assertEquals("[MVP+] §bBob§f: hi", Nicknames.rename("[MVP+] §bSteve§f: hi", names), "formatting code before a name");
        assertNull(Nicknames.rename(new String[] {"nobody here"}, names));
        assertNull(Nicknames.rename(new String[] {"Steve"}, Map.of()));
    }

    @Test void renamesOnceWithoutChaining() {
        assertEquals("Alice Carol", Nicknames.rename("Bob Alice", Map.of("bob", "Alice", "alice", "Carol")));
    }

    @Test void keepsOneStringPerStyledSegment() {
        var names = Map.of("steve", "Bob");
        assertArrayEquals(new String[] {"<", "Bob", "> hi"}, Nicknames.rename(new String[] {"<", "Steve", "> hi"}, names));
        // A name split over segments (per-letter colours) goes into the segment it starts in.
        assertArrayEquals(new String[] {"Bob", "", "", " joined"}, Nicknames.rename(new String[] {"St", "ev", "e", " joined"}, names));
        assertArrayEquals(new String[] {"hi Bob", "", "!"}, Nicknames.rename(new String[] {"hi Ste", "ve", "!"}, names));
    }

    @Test void activeAddsYourDisplayNameOnlyWhileEnabled() {
        var tags = (ToggleNametagsModule) ModuleManager.getInstance().getModule("Nametags");
        boolean enabled = tags.isEnabled();
        String nicks = tags.nicknames.getValue(), display = tags.displayName.getValue();
        try {
            tags.setEnabled(true);
            tags.nicknames.setValue("Steve=Bob");
            tags.displayName.setValue(" Me ");
            assertEquals(Map.of("steve", "Bob", "lad", "Me"), Nicknames.active("Lad"));
            tags.displayName.setValue("");
            assertEquals(Map.of("steve", "Bob"), Nicknames.active("Lad"), "empty display name is off");
            tags.setEnabled(false);
            assertTrue(Nicknames.active("Lad").isEmpty());
        } finally {
            tags.setEnabled(enabled);
            tags.nicknames.setValue(nicks);
            tags.displayName.setValue(display);
        }
    }

    @Test void moduleDefaultsKeepVanillaExceptTextShadow() {
        var tags = new ToggleNametagsModule();
        assertTrue(tags.isEnabled());
        assertEquals(true, ((com.thelads.core.config.BoolOption) tags.getOption("Text Shadow")).get());
        assertEquals(true, ((com.thelads.core.config.BoolOption) tags.getOption("Render Background")).get());
        assertEquals(false, ((com.thelads.core.config.BoolOption) tags.getOption("Show Own Nametag in Third Person")).get());
        assertEquals("", tags.displayName.getValue());
        assertEquals("", tags.nicknames.getValue());
    }
}
