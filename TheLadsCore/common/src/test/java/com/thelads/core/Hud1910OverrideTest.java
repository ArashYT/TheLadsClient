package com.thelads.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.ColorOption;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.HudSettings;
import com.thelads.core.config.ModuleManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 1.9.10's one-time HUD override: Action Bar back over the hotbar, no Potion Effects plate, Armor HUD attached hotbar slots. */
class Hud1910OverrideTest {
    private static final String OLD = "{\"modules\":{"
        + "\"Potion Effects\":{\"options\":{\"Background\":%s}},"
        + "\"ArmorHUD\":{\"options\":{\"Style\":1,\"Attach to hotbar\":false}}},"
        + "\"hud\":{%s\"positions\":{\"Action Bar\":[452,435]}}}";
    private JsonObject saved;

    @BeforeEach void save() { saved = ConfigManager.toJson(); }
    @AfterEach void restore() { ConfigManager.applyJson(saved); }

    @Test void appliesOnceToOlderConfigs() {
        ColorOption plate = (ColorOption) ModuleManager.getInstance().getModule("Potion Effects").getOption("Background");
        plate.setUseGlobal(true);
        plate.setColor(0x80000000);
        String global = ConfigManager.toJson().getAsJsonObject("modules").getAsJsonObject("Potion Effects")
            .getAsJsonObject("options").get("Background").toString();

        ConfigManager.applyJson(JsonParser.parseString(String.format(OLD, global, "")).getAsJsonObject());
        assertNull(HudSettings.getInstance().getPosition("Action Bar"), "the Action Bar follows Minecraft's place again");
        assertFalse(plate.isUseGlobal());
        assertEquals(0, plate.getColor(), "no Potion Effects plate");
        var armor = ModuleManager.getInstance().getModule("ArmorHUD");
        assertEquals(0, ((DropdownOption) armor.getOption("Style")).getIndex(), "Hotbar Slots");
        assertTrue(((BoolOption) armor.getOption("Attach to hotbar")).get());
        assertTrue(ConfigManager.toJson().getAsJsonObject("hud").has("hud1910OverrideApplied"));

        ConfigManager.applyJson(JsonParser.parseString(String.format(OLD, global, "\"hud1910OverrideApplied\":true,")).getAsJsonObject());
        assertArrayEquals(new int[]{452, 435}, HudSettings.getInstance().getPosition("Action Bar"), "after the override the player's choice stays");
        assertTrue(plate.isUseGlobal());
        assertEquals(1, ((DropdownOption) armor.getOption("Style")).getIndex());
        assertFalse(((BoolOption) armor.getOption("Attach to hotbar")).get());
    }

    @Test void potionEffectsShipWithoutAPlate() {
        ColorOption plate = (ColorOption) ModuleManager.getInstance().getModule("Potion Effects").getOption("Background");
        plate.reset();
        assertFalse(plate.isUseGlobal());
        assertEquals(0, plate.getColor());
    }
}
