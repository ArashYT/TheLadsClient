package com.thelads.core;

import com.google.gson.JsonElement;
import com.thelads.core.client.bridge.DefaultGameBridge;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.bridge.LadsGameBridge.ScoreLine;
import com.thelads.core.client.bridge.LadsGameBridge.ScoreboardSnapshot;
import com.thelads.core.client.gui.DraggableHudScreen;
import com.thelads.core.client.hud.HudElement;
import com.thelads.core.client.hud.HudManager;
import com.thelads.core.client.hud.ScoreboardHudElement;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.HudSettings;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;
import com.thelads.core.config.SliderOption;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ScoreboardHudTest {
    private record Text(String value, float x, float y, float width, int color, boolean shadow) {}
    private record Fill(float x, float y, float width, float height, int color) {}
    private static class Graphics extends LadsGraphicsTest.MockGraphics {
        final List<Text> texts = new ArrayList<>();
        final List<Fill> fills = new ArrayList<>();
        final ArrayDeque<float[]> poses = new ArrayDeque<>();
        float sx = 1, sy = 1, tx, ty;
        @Override public void pushPose() { poses.push(new float[] {sx, sy, tx, ty}); }
        @Override public void popPose() {
            float[] pose = poses.pop();
            sx = pose[0]; sy = pose[1]; tx = pose[2]; ty = pose[3];
        }
        @Override public void translate(float x, float y) { tx += x * sx; ty += y * sy; }
        @Override public void scale(float x, float y) { sx *= x; sy *= y; }
        @Override public void fill(int left, int top, int right, int bottom, int color) {
            fills.add(new Fill(left * sx + tx, top * sy + ty, (right - left) * sx, (bottom - top) * sy, color));
        }
        @Override public void drawText(String text, int x, int y, int color, boolean shadow) {
            texts.add(new Text(text, x * sx + tx, y * sy + ty, textWidth(text) * sx, color, shadow));
        }
    }

    private static class Game extends DefaultGameBridge {
        ScoreboardSnapshot scoreboard;
        boolean hidden;
        @Override public ScoreboardSnapshot getScoreboard() { return scoreboard; }
        @Override public boolean isHudHidden() { return hidden; }
    }

    private Module module;
    private boolean oldEnabled;
    private long oldModified;
    private LadsGameBridge oldBridge;
    private int oldBackground, oldColor;
    private int[] oldPosition;
    private List<HudElement> oldElements;
    private final Map<Option, JsonElement> oldOptions = new HashMap<>();
    private Game game;
    private Graphics graphics;
    private ScoreboardHudElement scoreboard;

    @BeforeEach
    void setup() {
        module = ModuleManager.getInstance().getModule("Scoreboard");
        oldEnabled = module.isEnabled();
        oldModified = module.getLastModified();
        for (Option option : module.getOptions()) {
            oldOptions.put(option, option.save());
            option.reset();
        }
        oldBridge = LadsGameBridge.get();
        oldBackground = HudSettings.getInstance().getGlobalBackground();
        oldColor = HudSettings.getInstance().getGlobalColor();
        oldPosition = HudSettings.getInstance().getPosition("Scoreboard");
        HudSettings.getInstance().getPositions().remove("Scoreboard");
        HudSettings.getInstance().setGlobalBackground(0x80000000);
        HudSettings.getInstance().setGlobalColor(0xFFFFFFFF);
        oldElements = new ArrayList<>(HudManager.getInstance().getElements());
        game = new Game();
        LadsGameBridge.set(game);
        graphics = new Graphics();
        scoreboard = new ScoreboardHudElement();
        scoreboard.setModuleName("Scoreboard");
        module.setEnabled(true);
    }

    @AfterEach
    void restore() {
        oldOptions.forEach(Option::load);
        module.setEnabled(oldEnabled);
        module.setLastModified(oldModified);
        LadsGameBridge.set(oldBridge);
        HudSettings.getInstance().setGlobalBackground(oldBackground);
        HudSettings.getInstance().setGlobalColor(oldColor);
        if (oldPosition == null) HudSettings.getInstance().getPositions().remove("Scoreboard");
        else HudSettings.getInstance().getPositions().put("Scoreboard", oldPosition);
        HudManager.getInstance().getElements().clear();
        HudManager.getInstance().getElements().addAll(oldElements);
    }

    private ScoreboardSnapshot realObjective() {
        return new ScoreboardSnapshot("Server League", List.of(new ScoreLine("[Red] Player", "1,250 pts"),
                new ScoreLine("[Blue] Rival", "-2"), new ScoreLine("Blank score", "")));
    }

    private void flag(String option, boolean value) { ((BoolOption) module.getOption(option)).set(value); }
    private void choice(String option, int index) { ((DropdownOption) module.getOption(option)).setIndex(index); }
    private void slider(String option, int value) { ((SliderOption) module.getOption(option)).setValue(value); }

    private void onlyScoreboard() {
        HudManager.getInstance().getElements().clear();
        HudManager.getInstance().getElements().add(scoreboard);
    }

    private List<String> textValues() { return graphics.texts.stream().map(Text::value).toList(); }

    @Test
    void bridgeDefaultDoesNotSupplyFakeObjective() {
        assertNull(new DefaultGameBridge().getScoreboard());
    }

    @Test
    void snapshotCopiesItsRowsAndRejectsMissingText() {
        List<ScoreLine> rows = new ArrayList<>(List.of(new ScoreLine("Player", "12")));
        ScoreboardSnapshot snapshot = new ScoreboardSnapshot("Objective", rows);
        rows.clear();
        assertEquals(1, snapshot.lines().size());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.lines().clear());
        assertThrows(NullPointerException.class, () -> new ScoreLine(null, "1"));
        assertThrows(NullPointerException.class, () -> new ScoreLine("Player", null));
        assertThrows(NullPointerException.class, () -> new ScoreboardSnapshot(null, List.of()));
    }

    @ParameterizedTest
    @CsvSource({"false,false,false", "true,false,true", "true,true,false", "false,true,false"})
    void replacementRequiresEnabledModuleAndVisibleRealObjective(boolean enabled, boolean hidden, boolean expected) {
        game.scoreboard = realObjective();
        game.hidden = hidden;
        module.setEnabled(enabled);
        assertEquals(expected, ScoreboardHudElement.shouldReplaceVanillaScoreboard());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void absentAndEmptyObjectivesNeverReplaceVanillaOrDrawLiveSamples(boolean empty) {
        game.scoreboard = empty ? new ScoreboardSnapshot("Server title only", List.of()) : null;
        assertFalse(ScoreboardHudElement.shouldReplaceVanillaScoreboard());
        scoreboard.render(graphics);
        assertTrue(graphics.texts.isEmpty());
        assertTrue(graphics.fills.isEmpty());
    }

    @Test
    void clearingTheObjectiveImmediatelyStopsReplacement() {
        game.scoreboard = realObjective();
        assertTrue(ScoreboardHudElement.shouldReplaceVanillaScoreboard());
        scoreboard.prepareRender(graphics, false);
        game.scoreboard = null;
        assertFalse(ScoreboardHudElement.shouldReplaceVanillaScoreboard());
        scoreboard.render(graphics);
        assertTrue(graphics.texts.isEmpty());
    }

    @Test
    void realTextAndFormattedValuesPreserveNativeOrderWithoutAddedLabels() {
        game.scoreboard = realObjective();
        scoreboard.render(graphics);
        assertEquals(List.of("Server League", "[Red] Player", "1,250 pts", "[Blue] Rival", "-2", "Blank score"), textValues());
        Text firstName = graphics.texts.get(1);
        Text firstValue = graphics.texts.get(2);
        Text secondValue = graphics.texts.get(4);
        assertTrue(firstName.x() + firstName.width() < firstValue.x());
        assertEquals(firstValue.x() + firstValue.width(), secondValue.x() + secondValue.width());
        assertEquals(0xFFFF5555, firstValue.color());
    }

    @Test
    void blankNativeTitleAndNameAreNotReplacedWithInventedLabels() {
        game.scoreboard = new ScoreboardSnapshot("", List.of(new ScoreLine("", "3")));
        scoreboard.render(graphics);
        assertEquals(List.of("", "", "3"), textValues());
        assertTrue(ScoreboardHudElement.shouldReplaceVanillaScoreboard());
    }

    @Test
    void hideRedNumbersRemovesOnlyValuesAndTheirColumnWidth() {
        game.scoreboard = realObjective();
        scoreboard.prepareRender(graphics, false);
        int withNumbers = scoreboard.getWidth();
        flag("Hide Red Numbers", true);
        scoreboard.render(graphics);
        assertEquals(List.of("Server League", "[Red] Player", "[Blue] Rival", "Blank score"), textValues());
        assertTrue(scoreboard.getWidth() < withNumbers);
    }

    @ParameterizedTest
    @CsvSource({"0,80000000", "1,C0000000", "2,C0FFFFFF", "3,00000000"})
    void backgroundChoicesControlActualDraws(int choice, String color) {
        game.scoreboard = realObjective();
        choice("Background", choice);
        scoreboard.render(graphics);
        if (choice == 3) assertTrue(graphics.fills.isEmpty());
        else assertEquals((int) Long.parseLong(color, 16), graphics.fills.get(0).color());
        if (choice == 2) assertEquals(0xFF202020, graphics.texts.get(0).color());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void scoreboardTextShadowOptionControlsEveryTextDraw(boolean shadow) {
        game.scoreboard = realObjective();
        flag("Text Shadow", shadow);
        scoreboard.render(graphics);
        assertTrue(graphics.texts.stream().allMatch(text -> text.shadow() == shadow));
    }

    @ParameterizedTest
    @ValueSource(ints = {50, 100, 125, 150})
    void registeredSizeScalesRealSidebarGeometry(int size) {
        game.scoreboard = realObjective();
        slider("Size", size);
        scoreboard.prepareRender(graphics, false);
        scoreboard.renderAt(graphics, 50, 60, false);
        Fill background = graphics.fills.get(0);
        assertEquals(50, background.x());
        assertEquals(60, background.y());
        assertEquals(scoreboard.getWidth() * size / 100f, background.width());
        assertEquals(scoreboard.getHeight() * size / 100f, background.height());
        assertTrue(graphics.poses.isEmpty());
    }

    @Test
    void offsetsAreGuiPixelsOutsideTheScaleTransform() {
        game.scoreboard = realObjective();
        scoreboard.setPosition(100, 150);
        slider("Size", 150);
        slider("X Offset", 40);
        slider("Y Offset", -20);
        onlyScoreboard();
        HudManager.getInstance().render(graphics);
        assertEquals(140, graphics.fills.get(0).x());
        assertEquals(130, graphics.fills.get(0).y());
    }

    @Test
    void editorUsesActualObjectiveAndSamplesOnlyWhenAbsent() {
        game.scoreboard = realObjective();
        scoreboard.renderEditor(graphics);
        assertEquals("Server League", graphics.texts.get(0).value());
        graphics.texts.clear();
        game.scoreboard = null;
        scoreboard.renderEditor(graphics);
        assertEquals("Scoreboard preview (sample)", graphics.texts.get(0).value());
        assertFalse(ScoreboardHudElement.shouldReplaceVanillaScoreboard());
        graphics.texts.clear();
        graphics.fills.clear();
        scoreboard.render(graphics);
        assertTrue(graphics.texts.isEmpty());
        assertTrue(graphics.fills.isEmpty());
    }

    @Test
    void firstFrameMeasurementContainsWideServerTextAndBoundsRows() {
        List<ScoreLine> lines = new ArrayList<>();
        for (int i = 0; i < 25; i++) lines.add(new ScoreLine("Very long server player name " + i, Integer.toString(i)));
        game.scoreboard = new ScoreboardSnapshot("Server objective", lines);
        scoreboard.setPosition(790, 580);
        onlyScoreboard();
        HudManager.getInstance().render(graphics);
        assertEquals(31, graphics.texts.size(), "one title plus at most fifteen name/value rows");
        Fill background = graphics.fills.get(0);
        assertTrue(background.x() + background.width() <= graphics.width);
        assertTrue(background.y() + background.height() <= graphics.height);
        for (Text text : graphics.texts) {
            assertTrue(text.x() >= background.x());
            assertTrue(text.x() + text.width() <= background.x() + background.width());
            assertTrue(text.y() + graphics.fontHeight() <= background.y() + background.height());
        }
        assertFalse(textValues().contains("15"));
    }

    @Test
    void editorDragPreservesOffsetsWithoutDoubleApplyingThem() {
        game.scoreboard = realObjective();
        scoreboard.setPosition(100, 150);
        slider("X Offset", 40);
        slider("Y Offset", -20);
        onlyScoreboard();
        DraggableHudScreen editor = new DraggableHudScreen(() -> {});
        editor.render(graphics, 0, 0);
        assertTrue(editor.mouseClicked(142, 132, 0));
        assertEquals(140, scoreboard.getDisplayX(graphics));
        assertEquals(130, scoreboard.getDisplayY(graphics));
        editor.mouseDragged(242, 232, 0);
        editor.mouseReleased(242, 232, 0);
        assertEquals(240, scoreboard.getDisplayX(graphics));
        assertEquals(230, scoreboard.getDisplayY(graphics));
        assertArrayEquals(new int[] {200, 250}, HudSettings.getInstance().getPosition("Scoreboard"));
        assertEquals(40, ((SliderOption) module.getOption("X Offset")).getIntValue());
        assertEquals(-20, ((SliderOption) module.getOption("Y Offset")).getIntValue());
        ScoreboardHudElement restored = new ScoreboardHudElement();
        restored.setModuleName("Scoreboard");
        assertEquals(240, restored.getDisplayX(graphics));
        assertEquals(230, restored.getDisplayY(graphics));
    }
}
