package com.thelads.core;

import com.google.gson.JsonParser;
import com.thelads.core.config.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class ChatModuleTest {
    private com.google.gson.JsonObject before;
    @BeforeEach void save(){before=ConfigManager.toJson();}
    @AfterEach void restore(){ConfigManager.applyJson(before);}

    private static boolean chat(String option){return ((BoolOption)ModuleManager.getInstance().getModule("Chat").getOption(option)).get();}

    @Test void oldTimestampsAndIndicatorModuleMoveIntoChat(){
        ConfigManager.applyJson(JsonParser.parseString("{\"modules\":{\"ClientTools\":{\"options\":{\"Chat timestamps\":true}},\"HideChatIndicators\":{\"enabled\":false},\"Chat\":{\"enabled\":true,\"options\":{\"Chat Width\":200}}}}").getAsJsonObject());
        assertTrue(chat("Timestamps"));
        assertFalse(chat("Hide Signing Indicators"));
        assertNull(ModuleManager.getInstance().getModule("HideChatIndicators"));
        assertNull(ModuleManager.getInstance().getModule("ClientTools").getOption("Chat timestamps"));
    }

    @Test void savedChatOptionsWinOverLeftoverLegacyKeys(){
        ConfigManager.applyJson(JsonParser.parseString("{\"modules\":{\"ClientTools\":{\"options\":{\"Chat timestamps\":true}},\"Chat\":{\"options\":{\"Timestamps\":false}}}}").getAsJsonObject());
        assertFalse(chat("Timestamps"));
    }

    @Test void onlyTheNewestMessageAnimatesAndItSettles(){
        long[] now={0};var animation=new com.thelads.core.client.ChatAnimation(()->now[0]);
        assertFalse(animation.running());
        animation.start("new");now[0]=125_000_000L;
        assertTrue(animation.running());
        float half=animation.progress("new");assertTrue(half>0f&&half<1f);
        assertEquals(1f,animation.progress("old"));
        assertTrue(com.thelads.core.client.ChatAnimation.slide(0f)<0f);assertEquals(0f,com.thelads.core.client.ChatAnimation.slide(1f),1e-6);
        now[0]=250_000_000L;assertEquals(1f,animation.progress("new"));assertFalse(animation.running());
    }
}
