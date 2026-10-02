package com.thelads.core.client.title;

import static com.thelads.core.client.title.PauseMenuLayout.Slot.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class PauseMenuLayoutTest {
    @Test void groupsWithQuitApartAtTheBottom() {
        // The order buttons arrive in does not matter; their slots do.
        var slots = List.of(QUIT, LADS, BACK, EXTRAS, STATS, OPTIONS, MULTIPLAYER, ADVANCEMENTS, WORLD, REPLAYS);
        var boxes = PauseMenuLayout.arrange(slots, 640, 90, 300);
        var back = boxes.get(2); var quit = boxes.get(0);
        assertEquals(320, back.width(), "Back to Game spans the menu");
        assertEquals(320, quit.width(), "Save and Quit spans the menu");
        assertTrue(quit.y() > boxes.get(9).y(), "Save and Quit is last");
        assertEquals(boxes.get(7).y(), boxes.get(4).y(), "Advancements and Statistics share a row");
        assertTrue(boxes.get(7).x() < boxes.get(4).x());
        assertEquals(boxes.get(5).y(), boxes.get(1).y(), "Options and Lads Client share a row");
        assertEquals(boxes.get(6).y(), boxes.get(8).y(), "Multiplayer and world options share a row");
        assertEquals(boxes.get(9).y(), boxes.get(3).y(), "Replays and Extras share a row");
        int gapAboveQuit = quit.y() - (boxes.get(9).y() + boxes.get(9).height());
        assertEquals(PauseMenuLayout.GAP + PauseMenuLayout.APART, gapAboveQuit, "Save and Quit sits apart");
        assertTrue(back.y() >= 90 && quit.y() + quit.height() <= 300, "inside the space given");
    }

    @Test void loneGroupMembersSpanAndOthersPair() {
        var boxes = PauseMenuLayout.arrange(List.of(BACK, OPTIONS, OTHER, OTHER, OTHER, QUIT), 640, 90, 300);
        assertEquals(320, boxes.get(1).width(), "Options without Lads Client spans the row");
        assertEquals(boxes.get(2).y(), boxes.get(3).y(), "other mods' buttons pair up");
        assertEquals(320, boxes.get(4).width(), "an odd one out spans");
    }

    @Test void narrowScreensGetOneColumn() {
        var boxes = PauseMenuLayout.arrange(List.of(BACK, ADVANCEMENTS, STATS, QUIT), 300, 60, 260);
        for (int i = 1; i < boxes.size(); i++) assertTrue(boxes.get(i).y() > boxes.get(i - 1).y());
        assertTrue(boxes.stream().allMatch(b -> b.width() == 268));
    }
}
