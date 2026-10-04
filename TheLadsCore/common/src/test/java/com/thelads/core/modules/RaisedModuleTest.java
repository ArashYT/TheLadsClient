package com.thelads.core.modules;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.thelads.core.config.SliderOption;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RaisedModuleTest {
    @Test void hotbarSitsTwoPixelsUpAndClearsTheChatBoxByDefault() {
        var raised = new RaisedModule();
        assertTrue(raised.isEnabled());
        assertEquals(2, raised.hotbarLift(false));
        assertEquals(16, raised.hotbarLift(true));
        assertEquals(0, raised.chatLift());
    }

    @Test void offLeavesEverythingWhereVanillaDrawsIt() {
        var raised = new RaisedModule();
        ((SliderOption) raised.getOption("Chat")).setValue(30);
        raised.setEnabled(false);
        assertEquals(0, raised.hotbarLift(false));
        assertEquals(0, raised.hotbarLift(true));
        assertEquals(0, raised.chatLift());
    }

    @Test void savedDistanceFrom160KeepsItsMeaning() {
        var raised = new RaisedModule();
        // A 1.6.0 config only has "Distance" (the lift while chat is open).
        var saved = new JsonObject();
        saved.add("Distance", new JsonPrimitive(30));
        raised.getOption("Distance").load(saved.get("Distance"));
        ((SliderOption) raised.getOption("Hotbar")).setValue(5);
        ((SliderOption) raised.getOption("Chat")).setValue(12);
        assertEquals(5, raised.hotbarLift(false));
        assertEquals(35, raised.hotbarLift(true));
        assertEquals(12, raised.chatLift());
    }

    private static JsonObject layout(String json) { return JsonParser.parseString(json).getAsJsonObject(); }

    @Test void the160DefaultLayoutKeepsItsLook() {
        var raised = new RaisedModule();
        raised.adoptLayout(layout("{groups:{Default:{offset:{x:0,y:2},layers:['minecraft:action_bar','minecraft:hotbar']}},"
            + "layers:{'minecraft:hotbar':{anchor:'BOTTOM'},'minecraft:chat':{anchor:'NONE'}}}"));
        assertEquals(2, raised.hotbarLift(false));
        assertEquals(0, raised.chatLift());
    }

    @Test void aCustomLayoutCarriesTheHotbarAndChatLiftsOver() {
        var raised = new RaisedModule();
        raised.adoptLayout(layout("{groups:{A:{offset:{x:5,y:10},layers:['minecraft:hotbar']},B:{offset:{x:0,y:3},layers:['minecraft:hotbar','minecraft:chat']},"
            + "C:{offset:{y:20},layers:['minecraft:chat']}},layers:{'minecraft:chat':{anchor:'bottom_left'}}}"));
        assertEquals(13, raised.hotbarLift(false), "groups add up; a missing anchor is the hotbar's bottom one");
        assertEquals(23, raised.chatLift());
        raised.adoptLayout(layout("{groups:{A:{offset:{y:9},layers:['minecraft:hotbar','minecraft:chat']}},"
            + "layers:{'minecraft:hotbar':{anchor:'TOP'}}}"));
        assertEquals(0, raised.hotbarLift(false), "a top anchor moved it down, which the slider does not go");
        assertEquals(0, raised.chatLift(), "chat's default anchor moved nothing");
        raised.adoptLayout(layout("{groups:'broken'}"));
        assertEquals(0, raised.hotbarLift(false));
    }
}
