package com.thelads.core;

import com.google.gson.JsonObject;
import com.thelads.core.client.bridge.DefaultGameBridge;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.bridge.LadsGameBridge.ArmorPiece;
import com.thelads.core.client.hud.ArmorHudElement;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.HudSettings;
import com.thelads.core.config.ModuleManager;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 1.7.0: the Armor HUD's default Hotbar Slots style. */
class ArmorSlotsHudTest {
    private static final class Game extends DefaultGameBridge {
        List<ArmorPiece> armor = List.of(new ArmorPiece("Helmet", 10, 100));
        @Override public List<ArmorPiece> getArmor() { return armor; }
    }
    private static final class Graphics extends LadsGraphicsTest.MockGraphics {
        Graphics() { width = 640; height = 360; }
        @Override public void drawHotbarSlots(int x, int y, int slots) { drawCalls.add("slots:" + slots + "@" + x + "," + y); }
        @Override public void drawArmorSlot(int slot, int x, int y, boolean preview) { drawCalls.add("armor:" + slot + "@" + x + "," + y + (preview ? " preview" : "")); }
    }

    private JsonObject saved;
    private LadsGameBridge oldBridge;
    private final Game game = new Game();
    private final Graphics graphics = new Graphics();
    private ArmorHudElement armor;

    @BeforeEach void setup() {
        saved = ConfigManager.toJson();
        oldBridge = LadsGameBridge.get();
        LadsGameBridge.set(game);
        HudSettings.getInstance().getPositions().remove("ArmorHUD");
        ModuleManager.getInstance().getModule("ArmorHUD").getOptions().forEach(com.thelads.core.config.Option::reset);
        ModuleManager.getInstance().getModule("ArmorHUD").setEnabled(true);
        armor = new ArmorHudElement();
        armor.setModuleName("ArmorHUD");
        armor.useOrganizedDefaults();
    }

    @AfterEach void restore() {
        ConfigManager.applyJson(saved);
        LadsGameBridge.set(oldBridge);
    }

    @Test void defaultIsFourHotbarSlotsLeftOfTheOffhandSlot() {
        assertEquals(0, ((DropdownOption) ModuleManager.getInstance().getModule("ArmorHUD").getOption("Style")).getIndex());
        var bounds = armor.measureBounds(graphics, false);
        // Hotbar at 320 - 91; offhand slot 29 px left of it; 6 px gap; 82 px wide; on the hotbar's row.
        assertEquals(320 - 91 - 29 - 6 - 82, bounds.x()); // 112
        assertEquals(360 - 22, bounds.y());
        assertEquals(82, bounds.width());
        armor.renderAt(graphics, bounds.x(), bounds.y(), false);
        assertTrue(graphics.drawCalls.contains("translate:112.0,338.0"));
        int x = armor.getX(), y = armor.getY();
        assertEquals(List.of("slots:4@" + x + "," + y, "armor:0@" + (x + 3) + "," + (y + 3), "armor:1@" + (x + 23) + "," + (y + 3),
            "armor:2@" + (x + 43) + "," + (y + 3), "armor:3@" + (x + 63) + "," + (y + 3)), draws());
    }

    @Test void nothingWithoutArmourButASampleInTheEditor() {
        game.armor = List.of();
        var bounds = armor.measureBounds(graphics, false);
        armor.renderAt(graphics, bounds.x(), bounds.y(), false);
        assertTrue(draws().isEmpty());
        armor.renderAt(graphics, bounds.x(), bounds.y(), true);
        assertEquals(5, draws().size());
        assertTrue(draws().get(1).endsWith(" preview"));
    }

    private List<String> draws() {
        return graphics.drawCalls.stream().filter(call -> call.startsWith("slots:") || call.startsWith("armor:")).toList();
    }

    @Test void theListStyleStaysAvailable() {
        ((DropdownOption) ModuleManager.getInstance().getModule("ArmorHUD").getOption("Style")).setIndex(1);
        var bounds = armor.measureBounds(graphics, false);
        armor.renderAt(graphics, bounds.x(), bounds.y(), false);
        assertTrue(graphics.drawCalls.stream().anyMatch(call -> call.startsWith("text:Helmet")));
    }
}
