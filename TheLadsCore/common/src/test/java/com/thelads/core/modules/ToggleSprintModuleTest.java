package com.thelads.core.modules;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thelads.core.client.hud.HudManager;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.HudSettings;
import com.thelads.core.config.ModuleManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ToggleSprintModuleTest {
    private JsonObject before;
    @BeforeEach void save() { before = ConfigManager.toJson(); }
    @AfterEach void restore() { ConfigManager.applyJson(before); }

    private static ToggleSprintModule fresh() {
        ToggleSprintModule module = new ToggleSprintModule();
        module.setEnabled(true);
        return module;
    }

    /**
     * Minecraft's own per-tick sprint rule, as 1.8.9 and 26.x apply it to a held sprint key: start when the key is down and
     * the player may sprint; stop when a wall, a hit, hunger, an item, sneaking, shallow water, blindness or no forward input
     * forbids it. Returns the sprint state each tick.
     */
    private static boolean[] vanilla(ToggleSprintModule module, boolean[] forbidden, boolean keyDown) {
        boolean[] sprinting = new boolean[forbidden.length];
        boolean state = false;
        for (int tick = 0; tick < forbidden.length; tick++) {
            if (module.sprintEnded(keyDown, false, false)) state = false;
            if (!state && module.sprintInput(keyDown, false, false)) state = true;
            if (forbidden[tick]) state = false;
            sprinting[tick] = state;
        }
        return sprinting;
    }

    @Test void theSprintKeyTogglesWhileToggleSprintIsUnbound() {
        ToggleSprintModule module = fresh();
        assertFalse(module.sprintInput(false, false, false));
        assertTrue(module.pressSprint());
        assertTrue(module.sprintInput(false, false, false), "released, the toggle keeps the sprint key down");
        assertTrue(module.sprintInput(true, false, false));
        assertTrue(module.pressSprint());
        assertFalse(module.sprintInput(true, false, false), "toggled off by the same key: holding it does not sprint on");
    }

    @Test void aSeparateToggleKeyLeavesSprintAHoldKey() {
        ToggleSprintModule module = fresh();
        assertTrue(module.sprintInput(true, true, false), "held Sprint sprints while the toggle is off");
        assertFalse(module.sprintInput(false, true, false));
        module.pressSprint();
        assertTrue(module.sprintInput(false, true, false));
    }

    @Test void modesAndTheModuleSwitch() {
        ToggleSprintModule module = fresh();
        ((DropdownOption) module.getOption("Sprint")).setIndex(ToggleSprintModule.ALWAYS);
        assertTrue(module.sprintInput(false, false, false));
        assertFalse(module.pressSprint(), "Always has nothing to toggle");
        ((DropdownOption) module.getOption("Sprint")).setIndex(ToggleSprintModule.VANILLA);
        assertFalse(module.sprintInput(false, false, false));
        assertTrue(module.sprintInput(true, false, false));
        ((DropdownOption) module.getOption("Sprint")).setIndex(ToggleSprintModule.TOGGLE);
        module.pressSprint();
        module.setEnabled(false);
        assertFalse(module.sprintInput(false, false, false), "off, Minecraft gets the real key");
        assertTrue(module.isSprintToggled(), "switching the module off does not clear the toggle");
        module.setEnabled(true);
        assertTrue(module.sprintInput(false, false, false));
    }

    @Test void sneakingPausesTheSprintToggle() {
        ToggleSprintModule module = fresh();
        module.pressSprint();
        assertFalse(module.sprintInput(false, false, true));
        ((BoolOption) module.getOption("Pause sprint while sneaking")).set(false);
        assertTrue(module.sprintInput(false, false, true));
    }

    @Test void toggleSneakWithItsOwnKeyOrTheSneakKey() {
        ToggleSprintModule module = fresh();
        assertFalse(module.pressSneak(), "Sneak defaults to Vanilla");
        assertTrue(module.sneakInput(true, false));
        ((DropdownOption) module.getOption("Sneak")).setIndex(ToggleSprintModule.TOGGLE);
        assertTrue(module.pressSneak());
        assertTrue(module.sneakInput(false, false));
        assertTrue(module.pressSneak());
        assertFalse(module.sneakInput(true, false), "same key: the press toggled it off");
        assertTrue(module.sneakInput(true, true), "own toggle key: Sneak is held");
    }

    /** Wall bump, knockback, hunger 6 or less, item use, sneaking, shallow water, blindness: Minecraft's to stop, not ours. */
    @Test void minecraftStoppingTheSprintNeitherFlickersNorClearsTheToggle() {
        ToggleSprintModule module = fresh();
        module.pressSprint();
        boolean[] forbidden = {false, false, true, true, true, false, false, true, false, false};
        boolean[] sprinting = vanilla(module, forbidden, false);
        int changes = 0;
        for (int tick = 1; tick < sprinting.length; tick++) if (sprinting[tick] != sprinting[tick - 1]) changes++;
        assertEquals(4, changes, "one stop and one start per interruption, never a flicker within one");
        for (int tick = 0; tick < sprinting.length; tick++) assertEquals(!forbidden[tick], sprinting[tick], "tick " + tick);
        assertTrue(module.isSprintToggled());
    }

    @Test void theModuleStopsOnlyTheSprintItAskedFor() {
        ToggleSprintModule module = fresh();
        assertFalse(module.sprintEnded(false, false, false));
        module.pressSprint();
        assertFalse(module.sprintEnded(false, false, false));
        assertFalse(module.sprintEnded(false, false, false), "a wall or a hit is not an end of ours");
        assertTrue(module.sprintEnded(false, false, true), "sneaking pauses it");
        assertFalse(module.sprintEnded(false, false, true), "once");
        assertFalse(module.sprintEnded(false, false, false));
        module.pressSprint();
        assertTrue(module.sprintEnded(true, false, false), "toggled off with the Sprint key itself");
        module.pressSprint();
        module.sprintEnded(false, true, false);
        module.pressSprint();
        assertFalse(module.sprintEnded(true, true, false), "the held separate Sprint key keeps Minecraft's own sprint");
        module.pressSprint();
        module.sprintEnded(false, false, false);
        module.setEnabled(false);
        assertTrue(module.sprintEnded(false, false, false), "switching the module off ends its sprint");
    }

    @Test void hudLine() {
        ToggleSprintModule module = fresh();
        assertEquals("", module.status());
        module.observe(true, false, true);
        assertEquals("[Sprinting (Key Held)]", module.status());
        module.observe(true, false, false);
        assertEquals("[Sprinting (Vanilla)]", module.status());
        module.pressSprint();
        module.observe(false, true, false);
        assertEquals("[Sprinting (Toggled)] [Sneaking (Key Held)]", module.status());
        ((DropdownOption) module.getOption("Sneak")).setIndex(ToggleSprintModule.TOGGLE);
        module.pressSneak();
        module.pressSprint();
        module.observe(false, true, false);
        assertEquals("[Sneaking (Toggled)]", module.status());
        ((DropdownOption) module.getOption("Sprint")).setIndex(ToggleSprintModule.ALWAYS);
        module.pressSneak();
        module.observe(true, false, false);
        assertEquals("[Sprinting (Always)]", module.status());
    }

    @Test void theToggledStatesAreSavedWithTheConfigAndHiddenFromTheMenu() {
        ToggleSprintModule live = (ToggleSprintModule) ModuleManager.getInstance().getModule(ToggleSprintModule.NAME);
        live.setEnabled(true);
        if (!live.isSprintToggled()) live.pressSprint();
        JsonObject saved = ConfigManager.toJson();
        live.pressSprint();
        assertFalse(live.isSprintToggled());
        ConfigManager.applyJson(saved);
        assertTrue(live.isSprintToggled(), "a restart (or any reload) brings the toggle back");
        assertTrue(live.hidden(live.getOption("Sprint toggled")) && live.hidden(live.getOption("Sneak toggled")));
        assertFalse(live.hidden(live.getOption("Sprint")));
    }

    @Test void oneSixConfigsMigrate() {
        ConfigManager.applyJson(JsonParser.parseString("{\"modules\":{"
            + "\"ToggleSprint\":{\"enabled\":false,\"options\":{\"Mode\":1,\"Disable on sneak\":false},\"favorite\":true},"
            + "\"ToggleSneak\":{\"enabled\":true,\"options\":{\"Mode\":0}}},"
            + "\"hud\":{\"positions\":{\"ToggleSprint\":[1,2],\"ToggleSneak\":[30,40]},\"locked\":[\"ToggleSneak\",\"FPS\"],"
            + "\"groups\":[[\"ToggleSprint\",\"ToggleSneak\",\"FPS\"],[\"CPS\",\"ToggleSprint\"]]}}").getAsJsonObject());
        ToggleSprintModule module = (ToggleSprintModule) ModuleManager.getInstance().getModule(ToggleSprintModule.NAME);
        assertTrue(module.isEnabled() && module.isFavorite());
        assertEquals(ToggleSprintModule.VANILLA, ((DropdownOption) module.getOption("Sprint")).getIndex(), "Toggle Sprint was off");
        assertEquals(ToggleSprintModule.TOGGLE, ((DropdownOption) module.getOption("Sneak")).getIndex());
        assertFalse(((BoolOption) module.getOption("Pause sprint while sneaking")).get());
        assertArrayEquals(new int[]{30, 40}, HudSettings.getInstance().getPosition(ToggleSprintModule.NAME), "the sneak element was the one on");
        assertTrue(HudSettings.getInstance().isLocked(ToggleSprintModule.NAME) && HudSettings.getInstance().isLocked("FPS"));
        assertEquals(java.util.Set.of(ToggleSprintModule.NAME, "FPS"), HudSettings.getInstance().getGroupMembers("FPS"));
        assertNull(ModuleManager.getInstance().getModule("ToggleSprint"));
        assertNull(ModuleManager.getInstance().getModule("ToggleSneak"));
        assertEquals(1, HudManager.getInstance().getElements().stream().filter(e -> e.getModuleName().contains("Sprint")
            || e.getModuleName().contains("Sneak")).count(), "one HUD element");
        JsonObject resaved = ConfigManager.toJson();
        assertFalse(resaved.getAsJsonObject("modules").has("ToggleSprint") || resaved.getAsJsonObject("modules").has("ToggleSneak"));
        HudSettings.getInstance().getPositions().remove(ToggleSprintModule.NAME);
    }

    @Test void oneSixSprintOnlyKeepsItsModeAndPosition() {
        ConfigManager.applyJson(JsonParser.parseString("{\"modules\":{"
            + "\"ToggleSprint\":{\"enabled\":true,\"options\":{\"Mode\":1}},\"ToggleSneak\":{\"enabled\":false,\"options\":{\"Mode\":0}}},"
            + "\"hud\":{\"positions\":{\"ToggleSprint\":[7,8],\"ToggleSneak\":[30,40]}}}").getAsJsonObject());
        ToggleSprintModule module = (ToggleSprintModule) ModuleManager.getInstance().getModule(ToggleSprintModule.NAME);
        assertTrue(module.isEnabled());
        assertEquals(ToggleSprintModule.ALWAYS, ((DropdownOption) module.getOption("Sprint")).getIndex());
        assertEquals(1, ((DropdownOption) module.getOption("Sneak")).getIndex(), "Toggle Sneak was off: Vanilla");
        assertArrayEquals(new int[]{7, 8}, HudSettings.getInstance().getPosition(ToggleSprintModule.NAME));
        assertNull(HudSettings.getInstance().getPosition("ToggleSneak"));
        HudSettings.getInstance().getPositions().remove(ToggleSprintModule.NAME);
    }

    @Test void bothOffKeepsNewDefaultsForWhenItIsSwitchedOn() {
        ConfigManager.applyJson(JsonParser.parseString("{\"modules\":{"
            + "\"ToggleSprint\":{\"enabled\":false,\"options\":{\"Mode\":0}},\"ToggleSneak\":{\"enabled\":false,\"options\":{\"Mode\":0}}}}")
            .getAsJsonObject());
        ToggleSprintModule module = (ToggleSprintModule) ModuleManager.getInstance().getModule(ToggleSprintModule.NAME);
        assertFalse(module.isEnabled());
        assertEquals(ToggleSprintModule.TOGGLE, ((DropdownOption) module.getOption("Sprint")).getIndex());
        assertEquals(1, ((DropdownOption) module.getOption("Sneak")).getIndex());
    }
}
