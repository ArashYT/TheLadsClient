package com.thelads.core.modules;

import com.thelads.core.config.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TabTweaksModuleTest {
    @Test void existingLayoutPreferencesRoundTripWithoutLosingNewPrecision() {
        var module = new TabListModule();
        ((SliderOption) module.getOption("Size")).setValue(123);
        ((SliderOption) module.getOption("X Offset")).setValue(-321);
        ((SliderOption) module.getOption("Y Offset")).setValue(456);
        ((DropdownOption) module.getOption("Background")).setIndex(3);
        ((BoolOption) module.getOption("Text Shadow")).set(false);
        var restored = new TabListModule();
        module.getOptions().forEach(option -> restored.getOption(option.getName()).load(option.save()));
        assertEquals(123, ((SliderOption) restored.getOption("Size")).getIntValue());
        assertEquals(-321, ((SliderOption) restored.getOption("X Offset")).getIntValue());
        assertEquals(456, ((SliderOption) restored.getOption("Y Offset")).getIntValue());
        assertEquals(3, ((DropdownOption) restored.getOption("Background")).getIndex());
        assertFalse(((BoolOption) restored.getOption("Text Shadow")).get());
    }
    @Test void invalidNumericLimitsCannotCreateZeroRowsOrZeroScale() {
        var module = new TabListModule();
        var rows = (SliderOption) module.getOption("Players Per Column");
        var scale = (SliderOption) module.getOption("Size");
        var players = (SliderOption) module.getOption("Max Players");
        rows.setValue(0); scale.setValue(Double.NaN); players.setValue(10000);
        assertEquals(1, rows.getIntValue()); assertEquals(10, scale.getIntValue()); assertEquals(300, players.getIntValue());
    }
    @Test void panelAlphaAndAllLatencyColorsSurviveProfilePersistence() {
        for (var module : java.util.List.of(new TabListModule(), new PingViewModule())) {
            for (var option : module.getOptions()) if (option instanceof ColorOption color) {
                color.setUseGlobal(false); color.setColor(0x7fab12cd);
                var stored = color.save(); color.reset(); color.load(stored);
                assertEquals(0x7fab12cd, color.getColor(), color.getName());
                assertFalse(color.isUseGlobal(), color.getName());
            }
        }
    }
}
