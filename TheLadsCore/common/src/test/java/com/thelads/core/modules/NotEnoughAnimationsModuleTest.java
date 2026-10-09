package com.thelads.core.modules;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.ModuleManager;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NotEnoughAnimationsModuleTest {
    @Test void onWithEveryFamilyByDefault() {
        var module = new NotEnoughAnimationsModule();
        for (NotEnoughAnimationsModule.Family family : NotEnoughAnimationsModule.Family.values()) assertTrue(module.on(family), family.label);
        module.setEnabled(false);
        assertFalse(module.on(NotEnoughAnimationsModule.Family.ITEMS));
    }

    @Test void unchangedOldOffGivesWayToTheNewDefault() {
        var live = (NotEnoughAnimationsModule) ModuleManager.getInstance().getModule(NotEnoughAnimationsModule.NAME);
        JsonObject before = ConfigManager.toJson();
        try {
            live.setEnabled(true);
            // Before 1.8.5 the module did nothing and was off by default: saved with lastModified 0.
            ConfigManager.applyJson(JsonParser.parseString("{\"modules\":{\"NotEnoughAnimations\":{\"enabled\":false,\"lastModified\":0}}}").getAsJsonObject());
            assertTrue(live.isEnabled());
            // A player's own choice is kept.
            ConfigManager.applyJson(JsonParser.parseString("{\"modules\":{\"NotEnoughAnimations\":{\"enabled\":false,\"lastModified\":1790000000000}}}").getAsJsonObject());
            assertFalse(live.isEnabled());
        } finally {
            ConfigManager.applyJson(before);
        }
    }
}
