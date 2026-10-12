package com.thelads.core;

import com.thelads.core.client.ChatLift;
import com.thelads.core.config.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class ChatLiftTest {
    private com.google.gson.JsonObject before;
    @BeforeEach void save() { before = ConfigManager.toJson(); }
    @AfterEach void restore() { ConfigManager.applyJson(before); }

    private static void option(boolean on) { ((BoolOption) ModuleManager.getInstance().getModule("Chat").getOption(ChatLift.OPTION)).set(on); }

    @Test void onByDefaultAndSitsOnePixelAboveTheArmorRow() {
        assertTrue(ChatLift.on());
        // 26.x / 1.20.1 end the newest line 40 above the bottom, the armor row spans 49..40: 10 up puts the bottom at 50.
        assertEquals(10, ChatLift.lift(0, true, 0, 40, 1));
        // 1.8.9 ends it 28 above the bottom.
        assertEquals(22, ChatLift.lift(0, true, 0, 28, 1));
    }

    @Test void followsRaisedHotbarGroupAndNeverLowersRaisedChat() {
        assertEquals(12, ChatLift.lift(0, true, 2, 40, 1));
        assertEquals(26, ChatLift.lift(0, true, 16, 40, 1), "the group is higher while chat is open");
        assertEquals(30, ChatLift.lift(30, true, 2, 40, 1));
    }

    @Test void onlyWhereTheArmorRowShowsAndWhileOn() {
        assertEquals(0, ChatLift.lift(0, false, 0, 40, 1), "creative and spectator have no armor row");
        option(false);
        assertFalse(ChatLift.on());
        assertEquals(5, ChatLift.lift(5, true, 0, 40, 1));
        option(true);
        ModuleManager.getInstance().getModule("Chat").setEnabled(false);
        assertEquals(0, ChatLift.lift(0, true, 0, 40, 1), "the Chat module off leaves vanilla chat");
    }

    @Test void healthRowsAreTenHeartsAndTheArmorRowRisesWithThem() {
        assertEquals(1, ChatLift.healthRows(20, 20, 0));
        assertEquals(1, ChatLift.healthRows(20, 3.5f, 0));
        assertEquals(2, ChatLift.healthRows(20, 20, 4), "a golden apple's absorption");
        assertEquals(2, ChatLift.healthRows(40, 40, 0), "Health Boost");
        assertEquals(2, ChatLift.healthRows(20, 20, 20));
        assertEquals(3, ChatLift.healthRows(20, 20, 20.5f), "absorption counts rounded up");
        assertEquals(2, ChatLift.healthRows(20, 24, 0), "health above the maximum still has its row");
        // Vanilla (Gui.renderPlayerHealth / Forge's renderHealth): rows 10 px apart, then 9, 8... down to 3, armor one row above the top one.
        assertEquals(49, ChatLift.armorTop(1));
        assertEquals(59, ChatLift.armorTop(2));
        assertEquals(67, ChatLift.armorTop(3));
        assertEquals(73, ChatLift.armorTop(4));
        assertEquals(77, ChatLift.armorTop(5));
        assertEquals(20, ChatLift.lift(0, true, 0, 40, 2), "armor top 59, chat bottom 1 px above it");
        assertEquals(28, ChatLift.lift(0, true, 0, 40, 3), "armor top 67, chat bottom 1 px above it");
    }
}
