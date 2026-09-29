package com.thelads.core;

import com.google.gson.JsonElement;
import com.thelads.core.client.bridge.DefaultGameBridge;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.gui.DraggableHudScreen;
import com.thelads.core.client.hud.*;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class HudNativeDataTest {
    private record SavedOption(Option option, JsonElement value) {}
    private record SavedModule(Module module, boolean enabled, long modified) {}
    private record Rect(float x, float y, float width, float height) {}
    private static class Graphics extends LadsGraphicsTest.MockGraphics {
        final List<String> texts = new ArrayList<>();
        final List<Rect> rectangles = new ArrayList<>();
        final ArrayDeque<float[]> poses = new ArrayDeque<>();
        float sx = 1, sy = 1, tx, ty;
        @Override public void pushPose() {
            super.pushPose();
            poses.push(new float[] { sx, sy, tx, ty });
        }
        @Override public void popPose() {
            super.popPose();
            float[] pose = poses.pop();
            sx = pose[0]; sy = pose[1]; tx = pose[2]; ty = pose[3];
        }
        @Override public void translate(float x, float y) {
            super.translate(x, y);
            tx += x * sx; ty += y * sy;
        }
        @Override public void scale(float x, float y) {
            super.scale(x, y);
            sx *= x; sy *= y;
        }
        @Override public void fill(int left, int top, int right, int bottom, int color) {
            super.fill(left, top, right, bottom, color);
            rectangles.add(new Rect(left * sx + tx, top * sy + ty, (right - left) * sx, (bottom - top) * sy));
        }
        @Override public void drawText(String text, int x, int y, int color, boolean shadow) {
            super.drawText(text, x, y, color, shadow);
            texts.add(text);
        }
    }

    private static class Game extends DefaultGameBridge {
        float yaw = 90, absorption = 4.5f, saturation = 2.5f;
        String biomeId = "example:crystal_forest";
        List<ArmorPiece> armor = List.of(new ArmorPiece("Diamond Helmet", 50, 100),
                new ArmorPiece("Pumpkin", 0, 0));
        @Override public String getBiomeName() { return "crystal_forest"; }
        @Override public String getBiomeId() { return biomeId; }
        @Override public String getPlayerDirection() { return "west"; }
        @Override public float getYaw() { return yaw; }
        @Override public float getAbsorption() { return absorption; }
        @Override public float getSaturation() { return saturation; }
        @Override public List<ArmorPiece> getArmor() { return armor; }
    }

    private final List<SavedOption> savedOptions = new ArrayList<>();
    private final List<SavedModule> savedModules = new ArrayList<>();
    private final Map<String, int[]> savedPositions = new HashMap<>();
    private List<HudElement> savedElements;
    private LadsGameBridge savedBridge;
    private Game game;
    private Graphics graphics;

    @BeforeEach
    void setup() {
        savedBridge = LadsGameBridge.get();
        HudSettings.getInstance().getPositions().forEach((name, position) -> savedPositions.put(name, position.clone()));
        HudSettings.getInstance().getPositions().clear();
        for (var module : ModuleManager.getInstance().getModules()) {
            savedModules.add(new SavedModule(module, module.isEnabled(), module.getLastModified()));
            for (Option option : module.getOptions()) {
                savedOptions.add(new SavedOption(option, option.save()));
                option.reset();
            }
        }
        savedElements = new ArrayList<>(HudManager.getInstance().getElements());
        game = new Game();
        LadsGameBridge.set(game);
        graphics = new Graphics();
    }

    @AfterEach
    void restore() {
        savedOptions.forEach(option -> option.option().load(option.value()));
        savedModules.forEach(saved -> {
            saved.module().setEnabled(saved.enabled());
            saved.module().setLastModified(saved.modified());
        });
        HudSettings.getInstance().getPositions().clear();
        HudSettings.getInstance().getPositions().putAll(savedPositions);
        HudManager.getInstance().getElements().clear();
        HudManager.getInstance().getElements().addAll(savedElements);
        LadsGameBridge.set(savedBridge);
    }

    private <T extends HudElement> T named(T element, String name) {
        element.setModuleName(name);
        return element;
    }

    private void flag(String module, String name, boolean value) {
        ((BoolOption) ModuleManager.getInstance().getModule(module).getOption(name)).set(value);
    }

    private void choice(String module, String name, int index) {
        ((DropdownOption) ModuleManager.getInstance().getModule(module).getOption(name)).setIndex(index);
    }

    private void only(HudElement element) {
        HudManager.getInstance().getElements().clear();
        HudManager.getInstance().getElements().add(element);
    }

    @Test
    void bridgeDefaultsDoNotFabricateNewNativeData() {
        LadsGameBridge fallback = new DefaultGameBridge();
        assertNull(fallback.getBiomeId());
        assertEquals(-1f, fallback.getYaw());
        assertEquals(-1f, fallback.getAbsorption());
        assertEquals(-1f, fallback.getSaturation());
        assertTrue(fallback.getArmor().isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"0,Crystal Forest", "1,example:crystal_forest"})
    void biomeHonorsNameVersusFullNamespacedId(int format, String expected) {
        choice("Biome", "Format", format);
        flag("Biome", "Show label", true);
        named(new BiomeHudElement(), "Biome").render(graphics);
        assertEquals(List.of("Biome: " + expected), graphics.texts);
    }

    @Test
    void biomeDoesNotInventNamespaceWhenIdIsUnavailable() {
        game.biomeId = null;
        choice("Biome", "Format", 1);
        named(new BiomeHudElement(), "Biome").render(graphics);
        assertEquals(List.of("Unknown"), graphics.texts);
    }

    @ParameterizedTest
    @CsvSource({"0,false,W", "0,true,West", "1,false,90.0°", "2,false,W (90.0°)", "2,true,West (90.0°)"})
    void directionUsesItsFormatAndLongNameOptions(int format, boolean longNames, String expected) {
        choice("Direction", "Format", format);
        flag("Direction", "Long names", longNames);
        named(new DirectionHudElement(), "Direction").render(graphics);
        assertEquals(List.of(expected), graphics.texts);
    }

    @ParameterizedTest
    @CsvSource({"-90,270.0°", "450,90.0°", "359.99,0.0°", "-1,Yaw unavailable", "NaN,Yaw unavailable", "Infinity,Yaw unavailable"})
    void yawWrapsAndUnavailableValueIsNeverDisplayedAsAngle(float yaw, String expected) {
        game.yaw = yaw;
        choice("Direction", "Format", 1);
        named(new DirectionHudElement(), "Direction").render(graphics);
        assertEquals(List.of(expected), graphics.texts);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void absorptionIsSeparateHealthPointsInEveryHealthFormat(int format) {
        choice("Health", "Format", format);
        named(new HealthHudElement(), "Health").render(graphics);
        assertTrue(graphics.texts.get(0).endsWith(" (+4.5 absorption)"));
    }

    @ParameterizedTest
    @ValueSource(floats = {-1, 0, Float.NaN, Float.POSITIVE_INFINITY})
    void missingOrInvalidAbsorptionIsNotInvented(float absorption) {
        game.absorption = absorption;
        named(new HealthHudElement(), "Health").render(graphics);
        assertEquals(List.of("20/20"), graphics.texts);
    }

    @Test
    void absorptionOptionCanHideRealAbsorption() {
        flag("Health", "Show absorption", false);
        named(new HealthHudElement(), "Health").render(graphics);
        assertEquals(List.of("20/20"), graphics.texts);
    }

    @ParameterizedTest
    @CsvSource({"0,0.0", "2.5,2.5", "-1,unavailable", "NaN,unavailable", "Infinity,unavailable"})
    void saturationDistinguishesKnownZeroFromUnavailable(float saturation, String expected) {
        game.saturation = saturation;
        flag("Hunger", "Show saturation", true);
        named(new HungerHudElement(), "Hunger").render(graphics);
        assertEquals(List.of("Food: 20/20 | Sat: " + expected), graphics.texts);
    }

    @Test
    void saturationStaysHiddenWhenOptionIsOff() {
        named(new HungerHudElement(), "Hunger").render(graphics);
        assertEquals(List.of("Food: 20/20"), graphics.texts);
    }

    @ParameterizedTest
    @CsvSource({"0,Diamond Helmet", "1,Diamond Helmet 50/100", "2,Diamond Helmet 50%"})
    void armorUsesRealEquipmentAndSelectedDurabilityMode(int mode, String expected) {
        choice("ArmorHUD", "Durability", mode);
        named(new ArmorHudElement(), "ArmorHUD").render(graphics);
        assertEquals(List.of(expected, "Pumpkin"), graphics.texts);
        assertFalse(graphics.drawCalls.stream().anyMatch(call -> call.startsWith("blit:")));
    }

    @Test
    void armorNumbersHandleOverMaxAndUnknownDurabilityWithoutFakeRatios() {
        game.armor = List.of(new LadsGameBridge.ArmorPiece("Helmet", 200, 100),
                new LadsGameBridge.ArmorPiece("Unknown", -1, 100));
        named(new ArmorHudElement(), "ArmorHUD").render(graphics);
        assertEquals(List.of("Helmet 100/100", "Unknown"), graphics.texts);
    }

    @Test
    void emptyArmorOnlyProducesClearlyLabeledEditorSamples() {
        game.armor = List.of();
        ArmorHudElement armor = named(new ArmorHudElement(), "ArmorHUD");
        armor.render(graphics);
        assertTrue(graphics.drawCalls.isEmpty());
        armor.renderEditor(graphics);
        assertEquals("Helmet 120/165", graphics.texts.get(0));
        assertTrue(graphics.texts.contains("Helmet 120/165"));
        graphics.drawCalls.clear();
        graphics.texts.clear();
        armor.render(graphics);
        assertTrue(graphics.drawCalls.isEmpty(), "preview samples must never leak into live render");
    }

    @Test
    void editorUsesActualArmorWhenAvailable() {
        named(new ArmorHudElement(), "ArmorHUD").renderEditor(graphics);
        assertEquals(List.of("Diamond Helmet 50/100", "Pumpkin"), graphics.texts);
    }

    @Test
    void unsupportedOverlaysProduceNoContentInGameOrEditor() {
        var manager = HudManager.getInstance();
        manager.getElements().clear();
        for (HudElement element : List.of(new PaperdollHudElement(), new XaeroMinimapHudElement())) {
            assertFalse(element.isAvailable());
            element.render(graphics);
            element.renderEditor(graphics);
            manager.getElements().add(element);
        }
        manager.render(graphics);
        assertTrue(graphics.drawCalls.isEmpty());
        DraggableHudScreen editor = new DraggableHudScreen(() -> fail("No configuration should be saved"));
        editor.render(graphics, 0, 0);
        assertFalse(editor.mouseClicked(10, 10, 0));
        assertTrue(graphics.texts.isEmpty());
    }

    @Test
    void loadedPositionsApplyAtRegistrationAndAfterProfileChanges() {
        var settings = HudSettings.getInstance();
        settings.setPosition("FPS", 120, 130);
        HudElement fps = named(new FPSHudElement(), "FPS");
        assertEquals(120, fps.getX());
        assertEquals(130, fps.getY());
        settings.setPosition("FPS", 250, 260);
        fps.restoreSavedPosition();
        assertEquals(250, fps.getX());
        assertEquals(260, fps.getY());
        settings.getPositions().remove("FPS");
        fps.restoreSavedPosition();
        assertEquals(5, fps.getX());
        assertEquals(5, fps.getY());
    }

    @Test
    void managerConsumesLateLoadedSettingsWithoutChangingStoredCoordinates() {
        HudElement fps = named(new FPSHudElement(), "FPS");
        only(fps);
        HudSettings.getInstance().setPosition("FPS", 9999, -20);
        HudManager.getInstance().render(graphics);
        assertEquals(9999, fps.getX());
        assertEquals(-20, fps.getY());
        assertArrayEquals(new int[] {9999, -20}, HudSettings.getInstance().getPosition("FPS"));
    }

    @Test
    void restorationDoesNotOverwriteDragWhenSettingsChange() {
        HudSettings.getInstance().setPosition("FPS", 30, 40);
        HudElement fps = named(new FPSHudElement(), "FPS");
        fps.beginPositionEdit();
        fps.setPosition(100, 110);
        HudSettings.getInstance().setPosition("FPS", 70, 80);
        fps.restoreSavedPosition();
        assertEquals(100, fps.getX());
        assertEquals(110, fps.getY());
        fps.endPositionEdit();
        fps.restoreSavedPosition();
        assertEquals(70, fps.getX());
        assertEquals(80, fps.getY());
    }

    private HudElement rectangleElement() {
        HudElement element = new HudElement() {
            @Override public void render(LadsGraphics g) { g.fill(x, y, x + width, y + height, 0xFFFFFFFF); }
        };
        element.setPosition(100, 100);
        named(element, "FPS");
        ((SliderOption) ModuleManager.getInstance().getModule("FPS").getOption("Size")).setValue(200);
        return element;
    }

    @Test
    void editorScalesContentAroundItsPositionAndHitTestsScaledExtent() {
        ModuleManager.getInstance().getModule("FPS").setEnabled(true);
        HudElement element = rectangleElement();
        only(element);
        DraggableHudScreen editor = new DraggableHudScreen(() -> {});
        editor.render(graphics, 0, 0);
        assertTrue(graphics.rectangles.contains(new Rect(100, 100, 100, 30)));
        assertTrue(graphics.poses.isEmpty());
        assertTrue(editor.mouseClicked(190, 120, 0), "the scaled half must be selectable");
        assertTrue(editor.mouseDragged(240, 170, 0));
        assertEquals(150, element.getX());
        assertEquals(150, element.getY());
        editor.mouseReleased(240, 170, 0);
        assertArrayEquals(new int[] {150, 150}, HudSettings.getInstance().getPosition("FPS"));
    }

    @Test
    void liveAndEditorShareSameScaledContentGeometry() {
        HudElement element = rectangleElement();
        element.renderAt(graphics, 120, 140, false);
        Rect live = graphics.rectangles.get(0);
        graphics.rectangles.clear();
        element.renderAt(graphics, 120, 140, true);
        assertEquals(live, graphics.rectangles.get(0));
        assertEquals(new Rect(120, 140, 100, 30), live);
    }

    @Test
    void escapeCommitsActiveDragOnceAndUnfreezesRestoration() {
        ModuleManager.getInstance().getModule("FPS").setEnabled(true);
        HudElement element = rectangleElement();
        only(element);
        AtomicInteger saves = new AtomicInteger();
        AtomicInteger closes = new AtomicInteger();
        DraggableHudScreen editor = new DraggableHudScreen(saves::incrementAndGet);
        editor.setOnClose(closes::incrementAndGet);
        editor.render(graphics, 0, 0);
        editor.mouseClicked(110, 110, 0);
        editor.mouseDragged(210, 210, 0);
        assertFalse(editor.mouseReleased(210, 210, 1));
        assertTrue(editor.keyPressed(256));
        assertEquals(1, saves.get());
        assertEquals(1, closes.get());
        assertArrayEquals(new int[] {200, 200}, HudSettings.getInstance().getPosition("FPS"));
        HudSettings.getInstance().setPosition("FPS", 70, 80);
        element.restoreSavedPosition();
        assertEquals(70, element.getX());
    }

    @Test
    void manualArmorDragDetachesHotbarAndPersistsActualEditorLocation() {
        ModuleManager.getInstance().getModule("ArmorHUD").setEnabled(true);
        ArmorHudElement element = named(new ArmorHudElement(), "ArmorHUD");
        only(element);
        element.render(graphics); // Measure the data-backed armor before checking its anchored editor location.
        int ex = element.getDisplayX(graphics);
        int ey = element.getDisplayY(graphics);
        DraggableHudScreen editor = new DraggableHudScreen(() -> {});
        editor.render(graphics, 0, 0);
        assertTrue(editor.mouseClicked(ex + 2, ey + 2, 0));
        assertTrue(((BoolOption) ModuleManager.getInstance().getModule("ArmorHUD").getOption("Attach to hotbar")).get());
        editor.mouseDragged(202, 202, 0);
        assertFalse(((BoolOption) ModuleManager.getInstance().getModule("ArmorHUD").getOption("Attach to hotbar")).get());
        editor.mouseReleased(202, 202, 0);
        assertEquals(200, element.getDisplayX(graphics));
        assertEquals(200, element.getDisplayY(graphics));
        assertArrayEquals(new int[] {200, 200}, HudSettings.getInstance().getPosition("ArmorHUD"));
    }

    @Test
    void editorMeasuresArmorSampleBeforeAnchoringItsFirstFrame() {
        ModuleManager.getInstance().getModule("ArmorHUD").setEnabled(false);
        game.armor = List.of();
        ArmorHudElement element = named(new ArmorHudElement(), "ArmorHUD");
        only(element);
        DraggableHudScreen editor = new DraggableHudScreen(() -> {});
        editor.keyPressed(65); // 'A': explicitly opt into disabled HUD previews.
        editor.render(graphics, 0, 0);
        assertTrue(graphics.rectangles.contains(new Rect(graphics.width / 2f + 96,
                graphics.height - element.getRenderHeight() - 4, element.getRenderWidth(), element.getRenderHeight())));
        assertTrue(graphics.rectangles.stream().allMatch(rect -> rect.y() + rect.height() <= graphics.height));
    }

    @Test
    void editorPopsTransformIfPreviewFails() {
        HudElement failing = new HudElement() {
            @Override public void render(LadsGraphics g) {}
            @Override public void renderEditor(LadsGraphics g) { throw new IllegalStateException("preview failure"); }
        };
        only(failing);
        assertThrows(IllegalStateException.class, () -> new DraggableHudScreen(() -> {}).render(graphics, 0, 0));
        assertTrue(graphics.poses.isEmpty());
        assertEquals(1, graphics.sx);
        assertEquals(0, graphics.tx);
    }
}
