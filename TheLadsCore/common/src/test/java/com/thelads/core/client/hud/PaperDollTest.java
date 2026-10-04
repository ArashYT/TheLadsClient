package com.thelads.core.client.hud;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.PlayerActionOption;
import com.thelads.core.config.SliderOption;
import com.thelads.core.modules.PaperdollModule;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PaperDollTest {
    private final PaperdollModule module = new PaperdollModule();
    private final PaperDoll doll = new PaperDoll(() -> module);

    private void tick(String... happening) { doll.tick(Set.of(happening)::contains, 0); }
    private void slider(String name, double value) { ((SliderOption) module.getOption(name)).setValue(value); }
    private void bool(String name, boolean value) { ((BoolOption) module.getOption(name)).set(value); }

    @Test void staysUpForDisplayTimeAfterTheTriggerStops() {
        module.setEnabled(true);
        slider("Display Time (ticks)", 5);
        tick();
        assertFalse(doll.visible(true), "nothing happened yet");
        tick("Sprinting");
        assertTrue(doll.visible(true));
        for (int i = 0; i < 5; i++) { tick(); assertTrue(doll.visible(true), "tick " + i + " of the display time"); }
        tick();
        assertFalse(doll.visible(true), "display time over");
    }

    @Test void zeroDisplayTimeShowsOnlyWhileTriggered() {
        module.setEnabled(true);
        slider("Display Time (ticks)", 0);
        tick("Sprinting");
        assertTrue(doll.visible(true));
        tick();
        assertFalse(doll.visible(true));
    }

    @Test void onlySwitchedOnTriggersShowItButEveryReadingIsKept() {
        module.setEnabled(true);
        tick("Walking"); // off by default
        assertFalse(doll.visible(true));
        assertTrue(((PlayerActionOption) module.getOption("Walking")).detected(), "the actions screen still sees it happening");
        assertFalse(((PlayerActionOption) module.getOption("Sprinting")).detected());
        ((PlayerActionOption) module.getOption("Walking")).set(true);
        tick("Walking");
        assertTrue(doll.visible(true));
    }

    @Test void moduleOffCameraAndAlwaysDisplay() {
        tick("Sprinting");
        assertFalse(doll.visible(true), "module off by default");
        module.setEnabled(true);
        bool("Always Display", true);
        assertTrue(doll.visible(true));
        bool("Show in Third Person", false);
        assertTrue(doll.visible(true));
        assertFalse(doll.visible(false), "third person switched off");
        module.setEnabled(false);
        assertFalse(doll.visible(true));
    }

    @Test void headLeansIntoTurnsWithinMaximumYawAndSettlesBack() {
        module.setEnabled(true);
        slider("Maximum Yaw", 20);
        for (int i = 0; i < 40; i++) doll.tick(name -> false, 90);
        assertEquals(20, doll.headYaw(1), 0.01, "a fast turn right leans by at most Maximum Yaw");
        doll.tick(name -> false, 0);
        float before = doll.headYaw(0), after = doll.headYaw(1);
        assertTrue(after < before && doll.headYaw(0.5f) > after && doll.headYaw(0.5f) < before, "eases back between ticks");
        for (int i = 0; i < 60; i++) doll.tick(name -> false, 0);
        assertEquals(0, doll.headYaw(1), 0.01);
        for (int i = 0; i < 40; i++) doll.tick(name -> false, -3);
        assertEquals(-7.5f, doll.headYaw(1), 0.01, "a slow turn left leans a little");
        doll.tick(name -> false, Float.NaN);
        assertTrue(Float.isFinite(doll.headYaw(Float.NaN)));
    }

    @Test void headMovementModesAndPitchLimit() {
        module.setEnabled(true);
        for (int i = 0; i < 40; i++) doll.tick(name -> false, 10);
        var mode = (DropdownOption) module.getOption("Head Movement");
        slider("Maximum Pitch", 30);
        mode.setIndex(0);
        assertNotEquals(0, doll.headYaw(1));
        assertEquals(30, doll.headPitch(80));
        assertEquals(-12, doll.headPitch(-12));
        mode.setIndex(1);
        assertNotEquals(0, doll.headYaw(1));
        assertEquals(0, doll.headPitch(80), "yaw only");
        mode.setIndex(2);
        assertEquals(0, doll.headYaw(1));
        assertEquals(0, doll.headPitch(80));
        mode.setIndex(3);
        assertEquals(0, doll.headYaw(1), "pitch only");
        assertEquals(30, doll.headPitch(80));
    }

    @Test void facesTheMiddleOfTheScreen() {
        slider("Default Rotation", 20);
        assertEquals(160, doll.bodyYaw(true));
        assertEquals(200, doll.bodyYaw(false));
        slider("Model Opacity", 50);
        assertEquals(0.5f, doll.opacity());
    }

    @Test void switchingOffDropsTheTimerAndLean() {
        module.setEnabled(true);
        doll.tick(name -> name.equals("Sprinting"), 30);
        module.setEnabled(false);
        tick();
        module.setEnabled(true);
        assertFalse(doll.visible(true));
        assertEquals(0, doll.headYaw(1));
    }
}
