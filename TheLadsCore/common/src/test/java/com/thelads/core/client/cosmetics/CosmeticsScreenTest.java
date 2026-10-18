package com.thelads.core.client.cosmetics;

import com.thelads.core.client.cosmetics.anim.EmoteKeys;
import com.thelads.core.client.cosmetics.anim.EmoteLibrary;
import com.thelads.core.modules.CosmeticsModule;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CosmeticsScreenTest {
    @Test
    void wheelSlotsDefaultMoveClearAndForgetUnknownEmotes() {
        CosmeticsModule c = new CosmeticsModule();
        assertEquals(EmoteLibrary.SLOTS, c.slots().length);
        assertEquals(EmoteLibrary.GROOVE, c.slots()[0], "the default wheel starts with the library");
        c.setSlot(5, EmoteLibrary.GROOVE);
        assertNull(c.slots()[0], "an emote put in another slot moves there");
        assertEquals(EmoteLibrary.GROOVE, c.slots()[5]);
        c.setSlot(5, null);
        assertNull(c.slots()[5]);
        c.wheel.set("lads:gone,lads:groove");
        assertNull(c.slots()[0], "an emote that no longer exists reads as an empty slot");
        assertEquals(EmoteLibrary.GROOVE, c.slots()[1]);
        c.setEnabled(false);
        assertTrue(c.isEnabled(), "Cosmetics is always on");
    }

    @Test
    void emoteKeysToggleAndNames() {
        assertEquals("key.theladscore.emote.groove", EmoteKeys.keyName(EmoteLibrary.GROOVE));
        UUID self = UUID.randomUUID();
        try {
            EmoteKeys.pressed(self, EmoteLibrary.GROOVE);
            assertEquals(EmoteLibrary.GROOVE, Emotes.get(self).id());
            assertFalse(Emotes.get(self).stopping());
            EmoteKeys.pressed(self, EmoteLibrary.GROOVE);
            assertTrue(Emotes.get(self).stopping(), "the same emote's key again stops it");
        } finally {
            Emotes.remove(self);
        }
    }

    @Test
    void clickingASlotThenAnEmotePutsItThere() {
        CosmeticsModule c = CosmeticsModule.get();
        String saved = c.wheel.get();
        try {
            c.wheel.set("");
            CosmeticsScreen screen = new CosmeticsScreen(name -> "", () -> {}, () -> {});
            screen.render(new Recorder(480, 270), 0, 0);
            // Slot 4 sits straight below the wheel's centre; the first list row is Groove.
            assertTrue(screen.mouseClicked(screen.wheelX(), screen.wheelY() + 55, 0));
            assertEquals(4, screen.selected());
            CosmeticsScreen.Box row = screen.rowBox(0);
            assertTrue(screen.mouseClicked(row.x() + 2, row.y() + 2, 0));
            assertEquals(EmoteLibrary.GROOVE, c.slots()[4]);
            assertNull(c.slots()[0], "it moved from slot 1");
        } finally {
            c.wheel.set(saved);
        }
    }

    /** Draws nothing; just the screen size and a fixed-width font. */
    record Recorder(int w, int h) implements com.thelads.core.client.bridge.LadsGraphics {
        public void fill(int a, int b, int c, int d, int e) {}
        public void drawText(String t, int x, int y, int c, boolean s) {}
        public void drawCenteredText(String t, int x, int y, int c, boolean s) {}
        public int textWidth(String t) { return t.length() * 6; }
        public int fontHeight() { return 9; }
        public void pushPose() {}
        public void popPose() {}
        public void translate(float x, float y) {}
        public void scale(float x, float y) {}
        public void enableScissor(int a, int b, int c, int d) {}
        public void disableScissor() {}
        public void blit(String t, int x, int y, int u, int v, int w, int h) {}
        public void drawHead(String n, String u, int x, int y, int s) {}
        public int getScaledWidth() { return w; }
        public int getScaledHeight() { return h; }
    }
}
