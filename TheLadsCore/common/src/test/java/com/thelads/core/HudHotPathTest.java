package com.thelads.core;

import com.google.gson.JsonElement;
import com.thelads.core.client.bridge.DefaultGameBridge;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.hud.*;
import com.thelads.core.config.*;
import com.thelads.core.config.Module;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class HudHotPathTest {
    private final Map<Option, JsonElement> options = new LinkedHashMap<>();
    private LadsGameBridge oldBridge;
    private Game game;
    private Graphics graphics;

    private static class Game extends DefaultGameBridge {
        final List<ArmorPiece> armor = new ArrayList<>(List.of(new ArmorPiece("Helmet", 120, 200)));
        ScoreboardSnapshot board = new ScoreboardSnapshot("Server objective",
            List.of(new ScoreLine("Player", "123"), new ScoreLine("Team", "456")));
        String direction = "East";
        float yaw = 45.67f, health = 19.1f, maximum = 20, absorption = 4.5f, saturation = 7.25f;
        double speed = 4.325;
        int food = 20, armorReads, boardReads, yawReads;
        @Override public List<ArmorPiece> getArmor() { armorReads++; return armor; }
        @Override public ScoreboardSnapshot getScoreboard() { boardReads++; return board; }
        @Override public String getPlayerDirection() { return direction; }
        @Override public float getYaw() { yawReads++; return yaw; }
        @Override public double getSpeed() { return speed; }
        @Override public float getHealth() { return health; }
        @Override public float getMaxHealth() { return maximum; }
        @Override public float getAbsorption() { return absorption; }
        @Override public int getFoodLevel() { return food; }
        @Override public float getSaturation() { return saturation; }
    }

    private static class Graphics extends LadsGraphicsTest.MockGraphics {
        final List<String> texts = new ArrayList<>();
        int widthCalls, glyphWidth = 6, font = 9;
        @Override public int textWidth(String text) { widthCalls++; return text.length() * glyphWidth; }
        @Override public int fontHeight() { return font; }
        @Override public void drawText(String text, int x, int y, int color, boolean shadow) {
            texts.add(text); super.drawText(text, x, y, color, shadow);
        }
        void clear() { texts.clear(); drawCalls.clear(); widthCalls = 0; }
    }

    @BeforeEach void isolate() {
        oldBridge = LadsGameBridge.get(); game = new Game(); graphics = new Graphics(); LadsGameBridge.set(game);
        for (String name : List.of("ArmorHUD", "Scoreboard", "Direction", "Speed", "Health", "Hunger")) {
            for (Option option : module(name).getOptions()) { options.put(option, option.save()); option.reset(); }
        }
        bool("ArmorHUD", "Attach to hotbar", false);
        cycle("ArmorHUD", "Style", 1); // these cover the List style (Hotbar Slots: ArmorSlotsHudTest)
    }

    @AfterEach void restore() { options.forEach(Option::load); LadsGameBridge.set(oldBridge); }
    private static Module module(String name) { return ModuleManager.getInstance().getModule(name); }
    private static void cycle(String name, String option, int index) { ((DropdownOption) module(name).getOption(option)).setIndex(index); }
    private static void bool(String name, String option, boolean value) { ((BoolOption) module(name).getOption(option)).set(value); }
    private HudElement element(String name) {
        HudElement element = switch (name) {
            case "ArmorHUD" -> new ArmorHudElement();
            case "Scoreboard" -> new ScoreboardHudElement();
            case "Direction" -> new DirectionHudElement();
            case "Speed" -> new SpeedHudElement();
            case "Health" -> new HealthHudElement();
            case "Hunger" -> new HungerHudElement();
            default -> throw new IllegalArgumentException(name);
        };
        element.setModuleName(name); return element;
    }
    private String renderText(HudElement element) {
        graphics.clear(); element.render(graphics); return String.join("|", graphics.texts);
    }

    @Test void armorPreparationIsConsumedOnceAndMutableEquipmentChangesAreVisible() {
        var element = element("ArmorHUD");
        element.prepareRender(graphics, false); element.render(graphics);
        assertEquals(2, game.armorReads); assertEquals(1, graphics.widthCalls);
        assertEquals(List.of("Helmet 120/200"), graphics.texts);
        game.armor.set(0, new LadsGameBridge.ArmorPiece("Helmet", 50, 200));
        assertEquals("Helmet 50/200", renderText(element));
        assertEquals(3, game.armorReads);
        cycle("ArmorHUD", "Durability", 2);
        assertEquals("Helmet 25%", renderText(element));
        cycle("ArmorHUD", "Durability", 0);
        assertEquals("Helmet", renderText(element));
    }

    @Test void scoreboardPreparationMeasuresEachNameAndValueOnlyOnce() {
        List<LadsGameBridge.ScoreLine> lines = new ArrayList<>();
        for (int i = 0; i < 15; i++) lines.add(new LadsGameBridge.ScoreLine("Player " + i, String.valueOf(i)));
        game.board = new LadsGameBridge.ScoreboardSnapshot("Objective", lines);
        var element = element("Scoreboard");
        element.prepareRender(graphics, false); element.render(graphics);
        assertEquals(2, game.boardReads);
        assertEquals(31, graphics.widthCalls);
        assertEquals(31, graphics.texts.size());
        cycle("Scoreboard", "Background", 2);
        bool("Scoreboard", "Hide Red Numbers", true);
        graphics.clear(); element.prepareRender(graphics, false); element.render(graphics);
        assertEquals(16, graphics.widthCalls); assertEquals(16, graphics.texts.size());
    }

    @Test void scoreboardNextFrameAndDirectRenderObserveNewServerData() {
        var element = element("Scoreboard");
        assertTrue(renderText(element).contains("123"));
        game.board = new LadsGameBridge.ScoreboardSnapshot("Updated objective",
            List.of(new LadsGameBridge.ScoreLine("New name", "")));
        assertEquals("Updated objective|New name", renderText(element));
        assertEquals(2, game.boardReads);
        game.board = null;
        assertEquals("", renderText(element));
    }

    @Test void equipmentRemovedBetweenPreparationAndDrawingDisappearsImmediately() {
        var element = element("ArmorHUD");
        element.prepareRender(graphics, false);
        game.armor.clear();
        element.render(graphics);
        assertTrue(graphics.texts.isEmpty());
    }

    @ParameterizedTest @ValueSource(strings = {"ArmorHUD", "Scoreboard"})
    void editorPreparationNeverLeaksSampleIntoLiveRendering(String name) {
        game.armor.clear(); game.board = null;
        var element = element(name);
        element.prepareRender(graphics, true);
        element.render(graphics);
        assertTrue(graphics.texts.isEmpty());
        graphics.clear(); element.renderEditor(graphics);
        assertTrue(graphics.texts.stream().anyMatch(t -> t.contains("(sample)")));
        graphics.clear(); element.render(graphics);
        assertTrue(graphics.texts.isEmpty());
    }

    @ParameterizedTest @ValueSource(strings = {"ArmorHUD", "Scoreboard"})
    void layoutOptionChangeBetweenPreparationAndDrawInvalidatesPreparation(String name) {
        var element = element(name);
        element.prepareRender(graphics, false);
        if (name.equals("ArmorHUD")) cycle(name, "Durability", 2);
        else bool(name, "Hide Red Numbers", true);
        element.render(graphics);
        if (name.equals("ArmorHUD")) assertEquals(List.of("Helmet 60%"), graphics.texts);
        else assertEquals(List.of("Server objective", "Player", "Team"), graphics.texts);
    }

    @ParameterizedTest @ValueSource(strings = {"ArmorHUD", "Scoreboard", "Direction", "Speed", "Health", "Hunger"})
    void unchangedContentIsRemeasuredAfterFontMetricsChange(String name) {
        if (name.equals("Direction")) cycle(name, "Format", 2);
        if (name.equals("Hunger")) bool(name, "Show saturation", true);
        var element = element(name);
        element.prepareRender(graphics, false); element.render(graphics);
        var text = List.copyOf(graphics.texts); int width = element.getWidth();
        graphics.clear(); graphics.glyphWidth = 11; graphics.font = 13;
        element.prepareRender(graphics, false); element.render(graphics);
        assertEquals(text, graphics.texts); assertTrue(element.getWidth() > width);
        assertTrue(graphics.widthCalls > 0);
    }

    @Test void directionTextUpdatesAndCardinalOnlySkipsUnusedYawFormatting() {
        var element = element("Direction");
        cycle("Direction", "Format", 2);
        assertEquals("E (45.7°)", renderText(element));
        bool("Direction", "Long names", true);
        assertEquals("East (45.7°)", renderText(element));
        cycle("Direction", "Format", 1); game.yaw = -1;
        assertEquals("Yaw unavailable", renderText(element));
        cycle("Direction", "Format", 0); game.direction = "North"; game.yawReads = 0;
        assertEquals("North", renderText(element)); assertEquals(0, game.yawReads);
    }

    @Test void speedTextRetainsUnitsPrecisionAndSignedZero() {
        var element = element("Speed");
        assertEquals("4.3 b/s", renderText(element));
        cycle("Speed", "Unit", 1);
        assertEquals("15.6 km/h", renderText(element));
        cycle("Speed", "Precision", 2);
        assertEquals("15.57 km/h", renderText(element));
        game.speed = -0.0;
        assertEquals("-0.00 km/h", renderText(element));
        game.speed = Double.NaN;
        assertEquals("NaN km/h", renderText(element));
    }

    @Test void healthTextUpdatesForFormatMaximumAndAbsorptionChanges() {
        var element = element("Health");
        assertEquals("20/20 (+4.5 absorption)", renderText(element));
        cycle("Health", "Format", 3);
        assertEquals("95% (+4.5 absorption)", renderText(element));
        bool("Health", "Show absorption", false);
        assertEquals("95%", renderText(element));
        game.health = 10; game.maximum = 40;
        assertEquals("25%", renderText(element));
        bool("Health", "Show absorption", true); game.absorption = 0;
        assertEquals("25%", renderText(element));
    }

    @Test void hungerTextUpdatesForLabelFoodSaturationAndUnavailableData() {
        var element = element("Hunger");
        bool("Hunger", "Show saturation", true);
        assertEquals("Food: 20/20 | Sat: 7.3", renderText(element));
        bool("Hunger", "Show label", false);
        assertEquals("20/20 | Sat: 7.3", renderText(element));
        bool("Hunger", "Show saturation", false); game.food = 15;
        assertEquals("15/20", renderText(element));
        bool("Hunger", "Show saturation", true); game.saturation = Float.NaN;
        assertEquals("15/20 | Sat: unavailable", renderText(element));
    }
}
