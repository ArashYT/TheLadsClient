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
        assertEquals(10, ChatLift.lift(0, true, 0, 40));
        // 1.8.9 ends it 28 above the bottom.
        assertEquals(22, ChatLift.lift(0, true, 0, 28));
    }

    @Test void followsRaisedHotbarGroupAndNeverLowersRaisedChat() {
        assertEquals(12, ChatLift.lift(0, true, 2, 40));
        assertEquals(26, ChatLift.lift(0, true, 16, 40), "the group is higher while chat is open");
        assertEquals(30, ChatLift.lift(30, true, 2, 40));
    }

    @Test void onlyWhereTheArmorRowShowsAndWhileOn() {
        assertEquals(0, ChatLift.lift(0, false, 0, 40), "creative and spectator have no armor row");
        option(false);
        assertFalse(ChatLift.on());
        assertEquals(5, ChatLift.lift(5, true, 0, 40));
        option(true);
        ModuleManager.getInstance().getModule("Chat").setEnabled(false);
        assertEquals(0, ChatLift.lift(0, true, 0, 40), "the Chat module off leaves vanilla chat");
    }
}
