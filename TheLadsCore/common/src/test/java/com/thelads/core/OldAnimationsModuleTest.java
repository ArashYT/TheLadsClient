package com.thelads.core;

import com.google.gson.JsonObject;
import com.thelads.core.client.OldAnimations.Held;
import com.thelads.core.client.OldAnimations.Use;
import com.thelads.core.client.gui.LadsSettingsScreen;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.config.Option;
import com.thelads.core.mods.CoreCatalogExporter;
import com.thelads.core.modules.OldAnimationsModule;
import com.thelads.core.modules.OldAnimationsModule.Feature;
import com.thelads.core.modules.OldAnimationsModule.Platform;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OldAnimationsModuleTest {
    private static OldAnimationsModule enabled() {
        var module = new OldAnimationsModule();
        module.setEnabled(true);
        return module;
    }

    @Test void registeredOffAsAGameplayModuleWithEveryOptionOn() {
        Module registered = ModuleManager.getInstance().getModule(OldAnimationsModule.NAME);
        assertInstanceOf(OldAnimationsModule.class, registered);
        assertEquals("Gameplay", LadsSettingsScreen.categoryOf(registered));
        var module = new OldAnimationsModule();
        assertFalse(module.isEnabled(), "visual modules start off, like LegacySwing and OldDamageTilt");
        assertEquals(Feature.values().length, module.getOptions().size(), "one toggle per option");
        var names = new HashSet<String>();
        for (Feature feature : Feature.values()) {
            BoolOption option = module.option(feature);
            assertSame(option, module.getOption(feature.option));
            assertTrue(option.get(), feature + " defaults on inside the module");
            assertTrue(names.add(feature.option));
            assertFalse(feature.tooltip.isBlank(), feature + " has a tooltip");
        }
    }

    @Test void optionsGateOnTheModuleTheirToggleAndThePlatform() {
        var module = new OldAnimationsModule();
        for (Feature feature : Feature.values())
            for (Platform platform : Platform.values()) assertFalse(module.active(feature, platform), "module off: " + feature);
        module.setEnabled(true);
        for (Feature feature : Feature.values()) {
            assertTrue(module.active(feature, Platform.MODERN), feature + " on 1.21.x and 26.x");
            assertEquals(feature != Feature.NO_COOLDOWN_DIP && feature != Feature.LOW_SHIELD, module.active(feature, Platform.V1_8_9), feature + " on 1.8.9");
        }
        for (Feature off : Feature.values()) {
            module.option(off).set(false);
            for (Feature feature : Feature.values())
                assertEquals(feature != off, module.active(feature, Platform.MODERN), off + " off leaves " + feature);
            module.option(off).set(true);
        }
    }

    @Test void onlyTheCooldownDipIsMissingOn189AndItSaysWhy() {
        for (Feature feature : Feature.values()) {
            assertTrue(feature.appliesTo(Platform.MODERN));
            assertNull(feature.unavailableReason(Platform.MODERN));
            boolean on189 = feature != Feature.NO_COOLDOWN_DIP && feature != Feature.LOW_SHIELD;
            assertEquals(on189, feature.appliesTo(Platform.V1_8_9), feature.toString());
            assertEquals(on189, feature.unavailableReason(Platform.V1_8_9) == null, feature.toString());
        }
        assertTrue(Feature.NO_COOLDOWN_DIP.unavailableReason(Platform.V1_8_9).contains("no attack cooldown"));
        assertTrue(Feature.LOW_SHIELD.unavailableReason(Platform.V1_8_9).contains("no shields"));
    }

    @Test void the189MenuHidesTheOptions189LacksAndKeepsTheirValues() throws Exception {
        var module = (OldAnimationsModule) ModuleManager.getInstance().getModule(OldAnimationsModule.NAME);
        try (var ownership = new ModsMenuTest.OwnershipFixture()) {
            ModuleSupport.registerBuiltIn(OldAnimationsModule.NAME);
            module.setPlatform(Platform.V1_8_9);
            module.option(Feature.NO_COOLDOWN_DIP).set(false);
            var menu = new LadsSettingsScreen();
            menu.openModule(OldAnimationsModule.NAME);
            var g = new LadsGraphicsTest.MockGraphics();
            g.height = 1200; // every option row fits
            menu.render(g, -1, -1);
            for (Feature feature : Feature.values())
                assertEquals(feature.appliesTo(Platform.V1_8_9), menu.controlBounds("option:" + feature.option) != null, feature.toString());
            assertFalse(module.option(Feature.NO_COOLDOWN_DIP).get(), "a hidden option keeps its value for 1.21 and 26.x");
            module.setPlatform(Platform.MODERN);
            menu.render(g, -1, -1);
            for (Feature feature : Feature.values()) assertNotNull(menu.controlBounds("option:" + feature.option), "modern lists " + feature);
        } finally {
            module.setPlatform(Platform.MODERN);
            module.option(Feature.NO_COOLDOWN_DIP).reset();
        }
    }

    @Test void helpersFollowTheirOptions() {
        var module = enabled();
        assertEquals(0.5f, module.swingShown(Platform.V1_8_9, Use.NONE, 0.5f), "the normal swing is vanilla's");
        for (Use use : new Use[]{Use.BLOCK, Use.BOW}) {
            Feature gate = use == Use.BLOCK ? Feature.BLOCKHIT : Feature.SWING_WHILE_USING;
            assertEquals(0.5f, module.swingShown(Platform.V1_8_9, use, 0.5f), use + " shows the 1.7 swing");
            module.option(gate).set(false);
            assertEquals(0, module.swingShown(Platform.V1_8_9, use, 0.5f), use + " without " + gate + " is 1.8");
            module.option(gate).set(true);
        }
        for (Platform platform : Platform.values())
            assertEquals(0, module.swingShown(platform, Use.EAT_DRINK, 0.5f), "eating and drinking never swing the food, " + platform);
        for (Use use : new Use[]{Use.BLOCK, Use.BOW, Use.EAT_DRINK}) assertTrue(module.useWhileMining(use), use + " starts while mining");
        assertFalse(module.useWhileMining(Use.NONE), "an item without a use waits, as in 1.8");
        assertFalse(module.useWhileMining(null), "a use 1.7 never drew (a spyglass) waits, as in 1.8");
        assertTrue(module.iconPlacement(Platform.MODERN, Use.BLOCK, Held.TOOL));
        assertTrue(module.iconPlacement(Platform.MODERN, Use.EAT_DRINK, Held.ITEM));
        assertTrue(module.iconPlacement(Platform.MODERN, Use.BOW, Held.BOW));
        assertTrue(module.iconPlacement(Platform.MODERN, Use.NONE, Held.BOW), "the 1.7 bow position also covers a held bow");
        assertTrue(module.iconPlacement(Platform.MODERN, Use.NONE, Held.ROD));
        assertTrue(module.iconPlacement(Platform.MODERN, Use.NONE, Held.TOOL), "idle swords take the 1.7 position (1.7 held item positions)");
        assertTrue(module.iconPlacement(Platform.MODERN, Use.NONE, Held.ITEM));
        module.option(Feature.HELD_ITEMS).set(false);
        assertFalse(module.iconPlacement(Platform.MODERN, Use.NONE, Held.TOOL), "without 1.7 held item positions idle swords are vanilla");
        module.option(Feature.ROD).set(false);
        assertFalse(module.iconPlacement(Platform.MODERN, Use.NONE, Held.ROD));
        module.option(Feature.BLOCK_POSE).set(false);
        assertFalse(module.iconPlacement(Platform.MODERN, Use.BLOCK, Held.TOOL));

        assertTrue(module.tintArmour(Platform.V1_8_9, 5, 0));
        assertTrue(module.tintArmour(Platform.MODERN, 0, 3), "dying players stay red");
        assertFalse(module.tintArmour(Platform.MODERN, 0, 0));
        assertFalse(module.heartsBlink(Platform.MODERN, true));
        assertEquals(1, module.equipScale(Platform.MODERN, 0.2f));
        assertEquals(0.2f, module.equipScale(Platform.V1_8_9, 0.2f), "n/a on 1.8.9");
        module.option(Feature.RED_ARMOUR).set(false);
        module.option(Feature.NO_HEART_FLASH).set(false);
        module.option(Feature.NO_COOLDOWN_DIP).set(false);
        assertFalse(module.tintArmour(Platform.MODERN, 5, 0));
        assertTrue(module.heartsBlink(Platform.MODERN, true));
        assertEquals(0.2f, module.equipScale(Platform.MODERN, 0.2f));
        module.setEnabled(false);
        assertEquals(0, module.swingShown(Platform.MODERN, Use.BLOCK, 0.5f), "module off is vanilla");
        assertFalse(module.iconPlacement(Platform.MODERN, Use.BOW, Held.BOW));
        assertFalse(module.useWhileMining(Use.BLOCK), "module off: the use waits for the mining, as in 1.8");
    }

    @Test void togglesSurviveASaveAndLoad() {
        var module = (OldAnimationsModule) ModuleManager.getInstance().getModule(OldAnimationsModule.NAME);
        boolean wasEnabled = module.isEnabled();
        long modified = module.getLastModified();
        try {
            module.setEnabled(true);
            module.option(Feature.DROPPED_2D).set(false);
            module.option(Feature.INSTANT_SNEAK).set(false);
            // Only this module's saved entry, so no other module or HUD setting is re-applied.
            JsonObject modules = new JsonObject(), saved = new JsonObject();
            modules.add(OldAnimationsModule.NAME, ConfigManager.toJson().getAsJsonObject("modules").get(OldAnimationsModule.NAME));
            saved.add("modules", modules);
            module.setEnabled(false);
            module.getOptions().forEach(Option::reset);
            ConfigManager.applyJson(saved);
            assertTrue(module.isEnabled());
            for (Feature feature : Feature.values())
                assertEquals(feature != Feature.DROPPED_2D && feature != Feature.INSTANT_SNEAK, module.option(feature).get(), feature.toString());
        } finally {
            module.getOptions().forEach(Option::reset);
            module.setEnabled(wasEnabled);
            module.setLastModified(modified);
        }
    }

    @Test void theCatalogNeverListsItBuiltInUntilAnAdapterRegistersIt() throws Exception {
        Module module = ModuleManager.getInstance().getModule(OldAnimationsModule.NAME);
        try (var ownership = new ModsMenuTest.OwnershipFixture()) {
            JsonObject row = CoreCatalogExporter.toJson("1.4.0", "26.3", List.of(module)).getAsJsonArray("modules").get(0).getAsJsonObject();
            assertEquals(OldAnimationsModule.NAME, row.get("name").getAsString());
            assertNotEquals("builtIn", row.get("support").getAsString());
            assertEquals("Unavailable", row.get("label").getAsString());
            assertFalse(row.get("detail").getAsString().isBlank(), "the launcher shows why");
            assertFalse(row.get("toggleable").getAsBoolean());
            assertEquals("Gameplay", row.get("category").getAsString());
            ModuleSupport.registerBuiltIn(OldAnimationsModule.NAME); // what A2 to A4 do once their hooks work
            row = CoreCatalogExporter.toJson("1.4.0", "26.3", List.of(module)).getAsJsonArray("modules").get(0).getAsJsonObject();
            assertEquals("builtIn", row.get("support").getAsString());
            assertTrue(row.get("toggleable").getAsBoolean());
        }
    }
}
