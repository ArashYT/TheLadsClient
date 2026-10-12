package com.thelads.core;

import com.google.gson.JsonElement;
import com.thelads.core.client.bridge.DefaultGameBridge;
import com.thelads.core.client.bridge.LadsGameBridge;
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

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HudBehaviorTest {
    private record SavedOption(Option option, JsonElement value) {}
    private record SavedModule(Module module, boolean enabled) {}
    private record Fill(int left, int top, int right, int bottom) {}
    private record Text(String value, int x, int y, int color, boolean shadow) {}
    private record PackIcon(String pack, int x, int y, int size) {}

    private static class RecordingGraphics extends LadsGraphicsTest.MockGraphics {
        final List<Fill> fills = new ArrayList<>();
        final List<Text> texts = new ArrayList<>();
        final List<PackIcon> icons = new ArrayList<>();

        @Override
        public void drawPackIcon(String pack, int x, int y, int size) {
            icons.add(new PackIcon(pack, x, y, size));
        }

        @Override
        public void fill(int left, int top, int right, int bottom, int color) {
            super.fill(left, top, right, bottom, color);
            fills.add(new Fill(left, top, right, bottom));
        }

        @Override
        public void drawText(String text, int x, int y, int color, boolean shadow) {
            super.drawText(text, x, y, color, shadow);
            texts.add(new Text(text, x, y, color, shadow));
        }
    }

    private static class Game extends DefaultGameBridge {
        int ping = 180;
        String time = "13:05";
        List<String> packs = List.of("vanilla", "file/Long resource pack name.zip", "third pack");
        List<String> effects = List.of("effect.minecraft.speed (40s)", "Custom Effect (special)");
        @Override public boolean hasPlayer() { return true; }
        @Override public int getPlayerX() { return -29999999; }
        @Override public int getPlayerY() { return -64; }
        @Override public int getPlayerZ() { return 29999999; }
        float yaw = -1;
        @Override public float getYaw() { return yaw; }
        @Override public int getPing() { return ping; }
        @Override public double getSpeed() { return 12.345; }
        @Override public String getGameTime() { return time; }
        @Override public long getDayCount() { return 123456789L; }
        @Override public long getUsedMemoryMb() { return 16384; }
        @Override public long getMaxMemoryMb() { return 32768; }
        @Override public List<String> getActiveResourcePacks() { return packs; }
        @Override public List<String> getActivePotionEffects() { return effects; }
    }

    private final List<SavedOption> options = new ArrayList<>();
    private final List<SavedModule> modules = new ArrayList<>();
    private LadsGameBridge previousBridge;
    private int previousBackground;
    private boolean previousShadow;
    private Game game;
    private RecordingGraphics graphics;

    @BeforeEach
    void setup() {
        previousBridge = LadsGameBridge.get();
        previousBackground = HudSettings.getInstance().getGlobalBackground();
        previousShadow = HudSettings.getInstance().isTextShadow();
        HudSettings.getInstance().setGlobalBackground(0x80000000);
        HudSettings.getInstance().setTextShadow(true);
        for (Module module : ModuleManager.getInstance().getModules()) {
            modules.add(new SavedModule(module, module.isEnabled()));
            for (Option option : module.getOptions()) {
                options.add(new SavedOption(option, option.save()));
                option.reset();
            }
        }
        game = new Game();
        LadsGameBridge.set(game);
        graphics = new RecordingGraphics();
    }

    @AfterEach
    void restore() {
        options.forEach(saved -> saved.option().load(saved.value()));
        modules.forEach(saved -> saved.module().setEnabled(saved.enabled()));
        LadsGameBridge.set(previousBridge);
        HudSettings.getInstance().setGlobalBackground(previousBackground);
        HudSettings.getInstance().setTextShadow(previousShadow);
    }

    private Module module(String name) {
        return ModuleManager.getInstance().getModule(name);
    }

    private void flag(String name, String option, boolean value) {
        ((BoolOption) module(name).getOption(option)).set(value);
    }

    private void choice(String name, String option, int value) {
        ((DropdownOption) module(name).getOption(option)).setIndex(value);
    }

    private <T extends HudElement> T named(T element, String name) {
        element.setModuleName(name);
        return element;
    }

    private void assertBackgroundFits(HudElement element) {
        assertFalse(graphics.fills.isEmpty());
        Fill background = graphics.fills.get(0);
        assertEquals(element.getX(), background.left());
        assertEquals(element.getY(), background.top());
        assertEquals(element.getX() + element.getWidth(), background.right(), "background uses this frame's width");
        assertEquals(element.getY() + element.getHeight(), background.bottom());
        for (Text text : graphics.texts) {
            assertTrue(text.x() >= background.left(), text.value());
            assertTrue(text.x() + graphics.textWidth(text.value()) <= background.right(), text.value());
            assertTrue(text.y() >= background.top(), text.value());
            assertTrue(text.y() + graphics.fontHeight() <= background.bottom(), text.value());
        }
        for (PackIcon icon : graphics.icons) {
            assertTrue(icon.x() >= background.left() && icon.x() + icon.size() <= background.right(), icon.pack());
            assertTrue(icon.y() >= background.top() && icon.y() + icon.size() <= background.bottom(), icon.pack());
        }
    }

    @ParameterizedTest
    @CsvSource({"50,0.5", "100,1.0", "125,1.25", "175,1.75", "200,2.0"})
    void sizeSliderControlsRenderedDimensions(int percent, float expected) {
        HudElement element = named(new FPSHudElement(), "FPS");
        ((SliderOption) module("FPS").getOption("Size")).setValue(percent);
        assertEquals(expected, element.getScale());
        assertEquals(Math.round(element.getWidth() * expected), element.getRenderWidth());
        assertEquals(Math.round(element.getHeight() * expected), element.getRenderHeight());
    }

    @Test
    void malformedSizeCannotCreateNonFiniteTransform() {
        HudElement element = named(new FPSHudElement(), "FPS");
        ((SliderOption) module("FPS").getOption("Size")).setValue(Double.NaN);
        assertTrue(Float.isFinite(element.getScale()));
        assertTrue(element.getScale() >= 0.5f && element.getScale() <= 2.0f);
    }

    @ParameterizedTest
    @CsvSource({"0,FF55FF55", "59,FF55FF55", "60,FFFFFF55", "119,FFFFFF55", "120,FFFF5555", "300,FFFF5555"})
    void pingThresholdColorReachesRenderer(int ping, String color) {
        game.ping = ping;
        HudElement element = named(new PingHudElement(), "PingHUD");
        element.render(graphics);
        assertEquals((int) Long.parseLong(color, 16), graphics.texts.get(0).color());
        assertBackgroundFits(element);
    }

    @Test
    void disablingPingColorsRestoresConfiguredHudColor() {
        flag("PingHUD", "Color by ping", false);
        HudElement element = named(new PingHudElement(), "PingHUD");
        element.render(graphics);
        assertEquals(HudSettings.getInstance().getGlobalColor(), graphics.texts.get(0).color());
    }

    @ParameterizedTest
    @CsvSource({"0,0,12 b/s", "0,1,12.3 b/s", "0,2,12.35 b/s", "1,0,44 km/h", "1,1,44.4 km/h", "1,2,44.44 km/h"})
    void speedHonorsUnitAndPrecision(int unit, int precision, String expected) {
        choice("Speed", "Unit", unit);
        choice("Speed", "Precision", precision);
        HudElement element = named(new SpeedHudElement(), "Speed");
        element.render(graphics);
        assertEquals(expected, graphics.texts.get(0).value());
        assertBackgroundFits(element);
    }

    @ParameterizedTest
    @CsvSource({"00:00,12:00 AM", "01:05,1:05 AM", "11:59,11:59 AM", "12:00,12:00 PM", "13:05,1:05 PM", "23:59,11:59 PM", "dawn,dawn"})
    void twelveHourTimeHandlesMidnightNoonAndUnknownFormats(String source, String expected) {
        game.time = source;
        flag("Time", "12-hour", true);
        flag("Time", "Show label", true);
        HudElement element = named(new TimeHudElement(), "Time");
        element.render(graphics);
        assertEquals("Time: " + expected, graphics.texts.get(0).value());
        assertBackgroundFits(element);
    }

    @Test
    void twentyFourHourTimeRemainsUnchanged() {
        named(new TimeHudElement(), "Time").render(graphics);
        assertEquals("13:05", graphics.texts.get(0).value());
    }

    @ParameterizedTest
    @CsvSource({"0,false", "1,false", "2,false", "0,true", "1,true", "2,true"})
    void coordinateLayoutContainsWorldBorderValues(int format, boolean vertical) {
        choice("Coordinates", "Format", format);
        flag("Coordinates", "Vertical", vertical);
        flag("Coordinates", "Per-axis colors", true);
        HudElement element = named(new CoordinatesHudElement(), "Coordinates");
        element.render(graphics);
        assertBackgroundFits(element);
        String rendered = graphics.texts.stream().map(Text::value).reduce("", String::concat);
        assertTrue(rendered.contains("-29999999"));
        assertTrue(rendered.contains("29999999"));
    }

    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void keystrokesBoundsContainEveryDrawnKey(boolean cps, boolean space) {
        flag("Keystrokes", "Show CPS", cps);
        flag("Keystrokes", "Show space bar", space);
        HudElement element = named(new KeystrokesHudElement(), "Keystrokes");
        element.render(graphics);
        for (Fill fill : graphics.fills) {
            assertTrue(fill.left() >= element.getX());
            assertTrue(fill.top() >= element.getY());
            assertTrue(fill.right() <= element.getX() + element.getWidth());
            assertTrue(fill.bottom() <= element.getY() + element.getHeight());
        }
        for (Text text : graphics.texts) {
            assertTrue(text.y() + graphics.fontHeight() <= element.getY() + element.getHeight());
        }
    }

    /** 1.9.1: the bounds are the drawn keys' outer edges, A and D, the mouse buttons and the space bar sitting on them, at every Size. */
    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void keystrokesBoundsAreExactlyTheKeysOuterEdges(boolean cps, boolean space) {
        flag("Keystrokes", "Show CPS", cps);
        flag("Keystrokes", "Show space bar", space);
        HudElement element = named(new KeystrokesHudElement(), "Keystrokes");
        for (int percent = 50; percent <= 200; percent += 25) {
            ((SliderOption) module("Keystrokes").getOption("Size")).setValue(percent);
            graphics.fills.clear();
            element.measureBounds(graphics, false);
            element.render(graphics);
            int left = Integer.MAX_VALUE, top = Integer.MAX_VALUE, right = Integer.MIN_VALUE, bottom = Integer.MIN_VALUE;
            for (Fill fill : graphics.fills) {
                left = Math.min(left, fill.left());
                top = Math.min(top, fill.top());
                right = Math.max(right, fill.right());
                bottom = Math.max(bottom, fill.bottom());
            }
            String at = (cps ? "cps " : "") + (space ? "space " : "") + percent + "%";
            assertEquals(element.getX(), left, at);
            assertEquals(element.getY(), top, at);
            assertEquals(element.getX() + element.getWidth(), right, at);
            assertEquals(element.getY() + element.getHeight(), bottom, at);
            float scale = element.getScale();
            assertEquals(element.getWidth() * scale, element.getRenderWidth(), 1e-4, at + ": a whole number of pixels wide");
            assertEquals(element.getHeight() * scale, element.getRenderHeight(), 1e-4, at + ": a whole number of pixels high");
        }
    }

    @Test
    void keystrokesRowsLineUpOnBothEdges() {
        HudElement element = named(new KeystrokesHudElement(), "Keystrokes");
        element.render(graphics);
        int x = element.getX(), right = x + element.getWidth();
        // A, LMB and the space bar start on the left edge; D, RMB and the space bar end on the right one.
        assertEquals(3, graphics.fills.stream().filter(fill -> fill.left() == x).count());
        assertEquals(3, graphics.fills.stream().filter(fill -> fill.right() == right).count());
    }

    @ParameterizedTest
    @CsvSource({"0,S,+Z", "22.4,S,+Z", "22.5,SW,-X +Z", "90,W,-X", "135,NW,-X -Z", "180,N,-Z", "225,NE,+X -Z", "270,E,+X", "315,SE,+X +Z", "337.4,SE,+X +Z", "337.5,S,+Z", "-45,SE,+X +Z", "-90,E,+X", "-180,N,-Z"})
    void coordinatesShowWhereYouFaceOnTheRight(float yaw, String compass, String axes) {
        game.yaw = yaw;
        HudElement element = named(new CoordinatesHudElement(), "Coordinates");
        element.render(graphics);
        assertBackgroundFits(element);
        Text direction = graphics.texts.stream().filter(text -> text.value().equals(compass)).findFirst().orElseThrow();
        Text axis = graphics.texts.stream().filter(text -> text.value().equals(axes)).findFirst().orElseThrow();
        assertEquals(element.getX() + element.getWidth() - 4, direction.x() + graphics.textWidth(compass), "right aligned");
        assertEquals(element.getX() + element.getWidth() - 4, axis.x() + graphics.textWidth(axes), "right aligned");
        Text lastCoordinate = graphics.texts.stream().filter(text -> text.value().startsWith("X: ")).findFirst().orElseThrow();
        assertTrue(lastCoordinate.x() + graphics.textWidth(lastCoordinate.value()) < direction.x(), "beside the coordinates, not over them");
    }

    @Test
    void coordinatesDirectionFollowsTheFacingNameWithoutYawAndCanBeSwitchedOff() {
        HudElement element = named(new CoordinatesHudElement(), "Coordinates");
        element.render(graphics); // the default bridge faces "North (Facing -Z)" and has no yaw
        assertTrue(graphics.texts.stream().anyMatch(text -> text.value().equals("N")));
        assertTrue(graphics.texts.stream().anyMatch(text -> text.value().equals("-Z")));
        flag("Coordinates", "Show Direction", false);
        graphics.texts.clear();
        graphics.fills.clear();
        element.render(graphics);
        assertTrue(graphics.texts.stream().noneMatch(text -> text.value().equals("N") || text.value().equals("-Z")));
        assertBackgroundFits(element);
    }

    @Test
    void coordinatesDirectionEndsTheHorizontalLayoutAndFitsItsBackground() {
        flag("Coordinates", "Vertical", false);
        flag("Coordinates", "Show Biome", true);
        game.yaw = 225;
        HudElement element = named(new CoordinatesHudElement(), "Coordinates");
        element.render(graphics);
        assertBackgroundFits(element);
        assertEquals(" NE (+X -Z)", graphics.texts.get(graphics.texts.size() - 1).value());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CPS", "Day", "Direction", "Health", "Hunger", "Memory", "XP"})
    void textHudBackgroundMatchesFirstFrame(String name) {
        HudElement element = switch (name) {
            case "CPS" -> new CpsHudElement();
            case "Day" -> new DayHudElement();
            case "Direction" -> new DirectionHudElement();
            case "Health" -> new HealthHudElement();
            case "Hunger" -> new HungerHudElement();
            case "Memory" -> new MemoryHudElement();
            default -> new XpHudElement();
        };
        named(element, name).render(graphics);
        assertBackgroundFits(element);
    }

    @Test
    void resourcePackListHonorsLimitAndRetainsRealIds() {
        flag("TexturePacks", "Show All", true);
        choice("TexturePacks", "Max Packs", 1);
        HudElement element = named(new TexturePackHudElement(), "TexturePacks");
        element.render(graphics);
        assertEquals(List.of("Pack: vanilla", "Pack: Long resource pack name"),
                graphics.texts.stream().map(Text::value).toList());
        assertBackgroundFits(element);
    }

    @Test
    void packIconsAreOnByDefaultAndLeadEveryRow() {
        flag("TexturePacks", "Show All", true);
        choice("TexturePacks", "Max Packs", 2);
        HudElement element = named(new TexturePackHudElement(), "TexturePacks");
        element.render(graphics);
        assertEquals(List.of("vanilla", "file/Long resource pack name.zip", "third pack"), graphics.icons.stream().map(PackIcon::pack).toList());
        for (int row = 0; row < 3; row++) {
            PackIcon icon = graphics.icons.get(row);
            Text text = graphics.texts.get(row);
            assertEquals(graphics.fontHeight() + 2, icon.size(), "icon is as tall as the row");
            assertEquals(icon.x() + icon.size() + 4, text.x(), "text follows its icon");
            assertTrue(text.y() >= icon.y() && text.y() + graphics.fontHeight() <= icon.y() + icon.size() + 1, "text level with its icon");
            if (row > 0) assertTrue(icon.y() >= graphics.icons.get(row - 1).y() + icon.size(), "rows do not overlap");
        }
        assertBackgroundFits(element);
    }

    @Test
    void lonePackAndItsIconAreCentredTogether() {
        game.packs = List.of("vanilla");
        HudElement element = named(new TexturePackHudElement(), "TexturePacks");
        element.render(graphics);
        PackIcon icon = graphics.icons.get(0);
        Text text = graphics.texts.get(0);
        int left = icon.x() - element.getX(), right = element.getX() + element.getWidth() - (text.x() + graphics.textWidth(text.value()));
        assertTrue(Math.abs(left - right) <= 1, "margins " + left + " and " + right);
        assertEquals(element.getY() + (element.getHeight() - icon.size()) / 2, icon.y());
        assertBackgroundFits(element);
    }

    @Test
    void packIconsCanBeSwitchedOffForThePlainLayout() {
        flag("TexturePacks", "Show Icons", false);
        game.packs = List.of("vanilla");
        HudElement element = named(new TexturePackHudElement(), "TexturePacks");
        element.render(graphics);
        assertTrue(graphics.icons.isEmpty());
        assertEquals(Math.max(80, graphics.textWidth("Pack: vanilla") + 12), element.getWidth());
        assertEquals(element.getX() + (element.getWidth() - graphics.textWidth("Pack: vanilla")) / 2, graphics.texts.get(0).x());
        assertBackgroundFits(element);
    }

    @Test
    void singlePackModeStaysSingle() {
        HudElement element = named(new TexturePackHudElement(), "TexturePacks");
        element.render(graphics);
        assertEquals(List.of("Pack: vanilla"), graphics.texts.stream().map(Text::value).toList());
        assertBackgroundFits(element);
    }

    @Test
    void emptyPackListShowsDefault() {
        game.packs = List.of();
        HudElement element = named(new TexturePackHudElement(), "TexturePacks");
        element.render(graphics);
        assertEquals("Pack: Default", graphics.texts.get(0).value());
        assertBackgroundFits(element);
    }

    @Test
    void potionDurationToggleOnlyRemovesNativeDurationSuffix() {
        flag("Potion Effects", "Show duration", false);
        HudElement element = named(new PotionHudElement(), "Potion Effects");
        element.render(graphics);
        assertEquals(List.of("effect.minecraft.speed", "Custom Effect (special)"),
                graphics.texts.stream().map(Text::value).toList());
        assertTrue(graphics.texts.stream().allMatch(Text::shadow));
        assertBackgroundFits(element);
    }

    @Test
    void potionDurationRemainsByDefault() {
        named(new PotionHudElement(), "Potion Effects").render(graphics);
        assertEquals("effect.minecraft.speed (40s)", graphics.texts.get(0).value());
    }

    @Test
    void emptyPotionStateClearsStaleDimensionsAndCanStayHidden() {
        HudElement element = named(new PotionHudElement(), "Potion Effects");
        element.render(graphics);
        game.effects = List.of();
        graphics.fills.clear();
        graphics.texts.clear();
        element.render(graphics);
        assertEquals(16, element.getHeight());
        assertTrue(graphics.fills.isEmpty());
        assertTrue(graphics.texts.isEmpty());
        flag("Potion Effects", "Show when empty", true);
        element.render(graphics);
        assertEquals("No Effects", graphics.texts.get(0).value());
        assertBackgroundFits(element);
    }

    @Test
    void managerBalancesPoseWhenElementFails() {
        HudManager manager = HudManager.getInstance();
        List<HudElement> original = new ArrayList<>(manager.getElements());
        HudElement failing = new HudElement() {
            @Override public void render(com.thelads.core.client.bridge.LadsGraphics g) {
                throw new IllegalStateException("test renderer failure");
            }
        };
        failing.setPosition(-10, -10);
        try {
            manager.getElements().clear();
            manager.getElements().add(failing);
            assertThrows(IllegalStateException.class, () -> manager.render(graphics));
            assertEquals("popPose", graphics.drawCalls.get(graphics.drawCalls.size() - 1));
        } finally {
            manager.getElements().clear();
            manager.getElements().addAll(original);
        }
    }
}
