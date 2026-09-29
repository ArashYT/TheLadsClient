package com.thelads.core;

import com.google.gson.JsonElement;
import com.thelads.core.client.bridge.DefaultGameBridge;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.hud.PaperdollHudElement;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.HudSettings;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;
import com.thelads.core.config.SliderOption;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PaperdollHudTest {
    @Test void anchorsOffsetsSavedPositionAndDragUseTheSameBounds() {
        var module = ModuleManager.getInstance().getModule("Paperdoll");
        Map<Option, JsonElement> saved = new LinkedHashMap<>();
        module.getOptions().forEach(option -> saved.put(option, option.save().deepCopy()));
        var oldPosition = HudSettings.getInstance().getPosition("Paperdoll");
        var oldBridge = LadsGameBridge.get();
        try {
            LadsGameBridge.set(new DefaultGameBridge() { @Override public boolean hasPaperDollRenderer() { return true; } });
            HudSettings.getInstance().setPosition("Paperdoll", 42, 53);
            var hud = new PaperdollHudElement(); hud.setModuleName("Paperdoll");
            var graphics = new LadsGraphicsTest.MockGraphics();
            ((SliderOption) module.getOption("Model Scale")).setValue(4);
            ((SliderOption) module.getOption("X Offset")).setValue(7);
            ((SliderOption) module.getOption("Y Offset")).setValue(-3);
            var anchor = (DropdownOption) module.getOption("Anchor");
            anchor.setIndex(0); hud.prepareRender(graphics, false);
            assertTrue(hud.isAvailable()); assertEquals(70, hud.getWidth());
            assertEquals(49, hud.getDisplayX(graphics)); assertEquals(50, hud.getDisplayY(graphics));
            for (int i = 1; i <= 9; i++) {
                anchor.setIndex(i);
                assertEquals(730 * ((i - 1) % 3) / 2 + 7, hud.getDisplayX(graphics));
                assertEquals(530 * ((i - 1) / 3) / 2 - 3, hud.getDisplayY(graphics));
            }
            hud.setDisplayPosition(200, 250);
            assertEquals(0, anchor.getIndex());
            assertEquals(200, hud.getDisplayX(graphics)); assertEquals(250, hud.getDisplayY(graphics));
            ((SliderOption) module.getOption("Model Scale")).setValue(24);
            hud.prepareRender(graphics, true); assertEquals(420, hud.getWidth());
            ((SliderOption) module.getOption("Model Scale")).setValue(1);
            hud.prepareRender(graphics, true); assertEquals(17, hud.getWidth());
            LadsGameBridge.set(new DefaultGameBridge()); assertFalse(hud.isAvailable());
        } finally {
            saved.forEach(Option::load); LadsGameBridge.set(oldBridge);
            if (oldPosition == null) HudSettings.getInstance().getPositions().remove("Paperdoll");
            else HudSettings.getInstance().setPosition("Paperdoll", oldPosition[0], oldPosition[1]);
        }
    }
}
