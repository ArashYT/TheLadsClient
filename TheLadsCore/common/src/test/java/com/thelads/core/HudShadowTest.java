package com.thelads.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.thelads.core.client.bridge.DefaultGameBridge;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.hud.HudElement;
import com.thelads.core.client.hud.HudManager;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.HudSettings;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 1.9.1: every Lads HUD element draws its text with the global text shadow setting (the Scoreboard with its own Text Shadow option). */
class HudShadowTest {
    private JsonObject saved;
    private LadsGameBridge previous;

    private static final class Graphics extends LadsGraphicsTest.MockGraphics {
        final List<String> flags = new ArrayList<>();
        @Override public void drawText(String text, int x, int y, int color, boolean shadow) { flags.add(shadow + ":" + text); }
        @Override public void drawCenteredText(String text, int centerX, int y, int color, boolean shadow) { flags.add(shadow + ":" + text); }
    }

    private static final class Game extends DefaultGameBridge {
        @Override public boolean hasPlayer() { return true; }
        @Override public boolean hasMinimap() { return true; }
        @Override public float getYaw() { return 135; }
        @Override public List<ArmorPiece> getArmor() { return List.of(new ArmorPiece("Helmet", 120, 165)); }
        @Override public List<String> getActivePotionEffects() { return List.of("Speed II (45s)"); }
        @Override public ScoreboardSnapshot getScoreboard() { return new ScoreboardSnapshot("Objective", List.of(new ScoreLine("Player", "123"))); }
    }

    @BeforeEach void setup() {
        saved = ConfigManager.toJson();
        previous = LadsGameBridge.get();
        LadsGameBridge.set(new Game());
        for (Module module : ModuleManager.getInstance().getModules()) {
            module.setEnabled(true);
            module.getOptions().forEach(Option::reset);
        }
        ((BoolOption) ModuleManager.getInstance().getModule("Coordinates").getOption("Show Biome")).set(true);
    }

    @AfterEach void restore() {
        ConfigManager.applyJson(saved);
        LadsGameBridge.set(previous);
    }

    private List<String> drawn(HudElement element, boolean editor) {
        Graphics g = new Graphics();
        element.measureBounds(g, editor);
        element.renderAt(g, 10, 10, editor);
        return g.flags;
    }

    /** A fresh element like each HudManager registered: the registered ones cache what they drew (FPS), which other tests read. */
    private static List<HudElement> elements() throws ReflectiveOperationException {
        List<HudElement> fresh = new ArrayList<>();
        for (HudElement registered : HudManager.getInstance().getElements()) {
            HudElement element = registered.getClass().getDeclaredConstructor().newInstance();
            element.setModuleName(registered.getModuleName());
            fresh.add(element);
        }
        return fresh;
    }

    @Test void everyElementFollowsTheGlobalShadow() throws ReflectiveOperationException {
        int checked = 0;
        for (boolean shadow : new boolean[] {true, false}) {
            HudSettings.getInstance().setTextShadow(shadow);
            for (HudElement element : elements()) {
                for (boolean editor : new boolean[] {false, true}) {
                    for (String draw : drawn(element, editor)) {
                        checked++;
                        // The Scoreboard has its own Text Shadow option, on by default whatever the global one.
                        boolean expected = element.getModuleName().equals("Scoreboard") || shadow;
                        assertEquals(expected, draw.startsWith("true:"), element.getModuleName() + " drew '" + draw + "' with global shadow " + shadow);
                    }
                }
            }
        }
        assertTrue(checked > 100, "texts checked: " + checked);
    }

    @Test void theScoreboardOptionOverridesTheGlobalSetting() {
        HudSettings.getInstance().setTextShadow(true);
        ((BoolOption) ModuleManager.getInstance().getModule("Scoreboard").getOption("Text Shadow")).set(false);
        var board = new com.thelads.core.client.hud.ScoreboardHudElement();
        board.setModuleName("Scoreboard");
        assertFalse(drawn(board, true).isEmpty());
        assertTrue(drawn(board, true).stream().allMatch(draw -> draw.startsWith("false:")));
    }
}
