package com.thelads.core.config;

import com.thelads.core.client.gui.ModuleOptionsScreen;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.SliderOption;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ModuleOptionsScreenChallengerTest {

    private Path tempGameDir;
    private MockedStatic<FabricLoader> fabricLoaderStatic;
    private MockedStatic<Minecraft> minecraftStatic;
    private Minecraft mockMinecraft;

    @BeforeEach
    public void setUp() throws IOException {
        tempGameDir = Files.createTempDirectory("thelads_game_dir_opts");
        File configDir = tempGameDir.resolve("config").toFile();
        configDir.mkdirs();

        fabricLoaderStatic = mockStatic(FabricLoader.class);
        FabricLoader mockLoader = mock(FabricLoader.class);
        when(mockLoader.getGameDir()).thenReturn(tempGameDir);
        fabricLoaderStatic.when(FabricLoader::getInstance).thenReturn(mockLoader);

        minecraftStatic = mockStatic(Minecraft.class);
        mockMinecraft = mock(Minecraft.class);
        minecraftStatic.when(Minecraft::getInstance).thenReturn(mockMinecraft);

        // Mock ConfigManager config file
        ConfigManager.setTestConfigFile(new File(configDir, "thelads_config.json"));
    }

    @AfterEach
    public void tearDown() {
        if (fabricLoaderStatic != null) fabricLoaderStatic.close();
        if (minecraftStatic != null) minecraftStatic.close();
        ConfigManager.setTestConfigFile(null);
        deleteDirectory(tempGameDir.toFile());
    }

    private void deleteDirectory(File file) {
        File[] contents = file.listFiles();
        if (contents != null) {
            for (File f : contents) {
                deleteDirectory(f);
            }
        }
        file.delete();
    }

    private void setupScreen(Screen screen) {
        try {
            Field mcField = Screen.class.getDeclaredField("minecraft");
            mcField.setAccessible(true);
            mcField.set(screen, mockMinecraft);

            Field fontField = Screen.class.getDeclaredField("font");
            fontField.setAccessible(true);
            fontField.set(screen, mock(Font.class));

            screen.width = 400;
            screen.height = 300;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private double getScrollOffset(ModuleOptionsScreen screen) throws Exception {
        Field f = ModuleOptionsScreen.class.getDeclaredField("scrollOffset");
        f.setAccessible(true);
        return f.getDouble(screen);
    }

    private void setScrollOffset(ModuleOptionsScreen screen, double val) throws Exception {
        Field f = ModuleOptionsScreen.class.getDeclaredField("scrollOffset");
        f.setAccessible(true);
        f.setDouble(screen, val);
    }

    @SuppressWarnings("unchecked")
    private List<String> callRows(ModuleOptionsScreen screen) throws Exception {
        Method m = ModuleOptionsScreen.class.getDeclaredMethod("rows");
        m.setAccessible(true);
        return (List<String>) m.invoke(screen);
    }

    @Test
    public void testScrollingBoundaryConditions_0Options() throws Exception {
        Module m = new Module("Test0", "No Options");
        ModuleOptionsScreen screen = new ModuleOptionsScreen(null, m);
        setupScreen(screen);

        List<String> rows = callRows(screen);
        assertEquals(1, rows.size()); // Just "enabled"
        assertTrue(rows.contains("enabled"));

        // Call render / extractRenderState to trigger layout calculations
        GuiGraphicsExtractor extractor = mock(GuiGraphicsExtractor.class);
        screen.extractRenderState(extractor, 0, 0, 0f);

        // Clamp checks
        // visibleH = (300 - 40) - 55 = 205
        // totalH = 1 * 34 = 34
        // maxScroll = Math.max(0, 34 - 205) = 0
        // scrollOffset should clamp to 0.0
        assertEquals(0.0, getScrollOffset(screen));

        // Attempting to set scrollOffset manually and rendering should clamp it back to 0.0
        setScrollOffset(screen, 100.0);
        screen.extractRenderState(extractor, 0, 0, 0f);
        assertEquals(0.0, getScrollOffset(screen));
    }

    @Test
    public void testScrollingBoundaryConditions_1Option() throws Exception {
        Module m = new Module("Test1", "One Option");
        m.addOption(new SliderOption("Speed", 5.0, 0.0, 10.0, 1.0));
        ModuleOptionsScreen screen = new ModuleOptionsScreen(null, m);
        setupScreen(screen);

        List<String> rows = callRows(screen);
        assertEquals(2, rows.size()); // "enabled" + "opt:Speed"

        GuiGraphicsExtractor extractor = mock(GuiGraphicsExtractor.class);
        screen.extractRenderState(extractor, 0, 0, 0f);

        // totalH = 2 * 34 = 68
        // maxScroll = Math.max(0, 68 - 205) = 0
        assertEquals(0.0, getScrollOffset(screen));

        setScrollOffset(screen, 50.0);
        screen.extractRenderState(extractor, 0, 0, 0f);
        assertEquals(0.0, getScrollOffset(screen));
    }

    @Test
    public void testScrollingBoundaryConditions_20Options() throws Exception {
        Module m = new Module("Test20", "20 Options");
        for (int i = 0; i < 20; i++) {
            m.addOption(new SliderOption("Opt" + i, 0.0, 0.0, 10.0, 1.0));
        }
        ModuleOptionsScreen screen = new ModuleOptionsScreen(null, m);
        setupScreen(screen);

        List<String> rows = callRows(screen);
        assertEquals(21, rows.size()); // "enabled" + 20 options

        GuiGraphicsExtractor extractor = mock(GuiGraphicsExtractor.class);
        screen.extractRenderState(extractor, 0, 0, 0f);

        // totalH = 21 * 34 = 714
        // visibleH = 205
        // maxScroll = 714 - 205 = 509
        // scrollOffset was 0, so should stay 0.
        assertEquals(0.0, getScrollOffset(screen));

        // Now set scrollOffset to something in-bounds (e.g. 300)
        setScrollOffset(screen, 300.0);
        screen.extractRenderState(extractor, 0, 0, 0f);
        assertEquals(300.0, getScrollOffset(screen));

        // Set scrollOffset beyond maxScroll (e.g. 1000)
        setScrollOffset(screen, 1000.0);
        screen.extractRenderState(extractor, 0, 0, 0f);
        assertEquals(509.0, getScrollOffset(screen)); // Clamped to maxScroll = 509.0
    }

    @Test
    public void testSliderDragCoordinateEdgeCases() throws Exception {
        Module m = new Module("SliderTest", "Testing Sliders");
        SliderOption speedOpt = m.addOption(new SliderOption("Speed", 5.0, 0.0, 10.0, 1.0));
        ModuleOptionsScreen screen = new ModuleOptionsScreen(null, m);
        setupScreen(screen);

        // We need to set draggingSlider to our option to simulate drag
        Field dsField = ModuleOptionsScreen.class.getDeclaredField("draggingSlider");
        dsField.setAccessible(true);
        dsField.set(screen, speedOpt);

        // Visual Layout parameters from code:
        // width = 400
        // cx = 30
        // cw = width - 60 = 340
        // sw = 120
        // sx = cx + cw - sw - 6 = 30 + 340 - 120 - 6 = 244
        // Slider X bounds: [244, 364]

        // 1. Drag coordinate far outside the LEFT of the slider bounds (e.g., event.x() = -50)
        MouseButtonEvent eventLeft = mock(MouseButtonEvent.class);
        when(eventLeft.button()).thenReturn(0);
        when(eventLeft.x()).thenReturn(-50.0);

        screen.mouseDragged(eventLeft, 0.0, 0.0);
        assertEquals(0.0, speedOpt.getValue(), "Slider value should be clamped to min (0.0) when dragging far left");

        // 2. Drag coordinate far outside the RIGHT of the slider bounds (e.g., event.x() = 600)
        MouseButtonEvent eventRight = mock(MouseButtonEvent.class);
        when(eventRight.button()).thenReturn(0);
        when(eventRight.x()).thenReturn(600.0);

        screen.mouseDragged(eventRight, 0.0, 0.0);
        assertEquals(10.0, speedOpt.getValue(), "Slider value should be clamped to max (10.0) when dragging far right");
    }

    @Test
    public void testDropdownClickMismatchWhenScrolled() throws Exception {
        Module m = new Module("DropdownTest", "Dropdown Screen");
        DropdownOption dropdownOpt = m.addOption(new DropdownOption("Mode", 0, "A", "B", "C"));
        ModuleOptionsScreen screen = new ModuleOptionsScreen(null, m);
        setupScreen(screen);

        // Set scrollOffset = 100
        setScrollOffset(screen, 100.0);

        // Set activeDropdownOption to simulate the dropdown being open
        Field activeField = ModuleOptionsScreen.class.getDeclaredField("activeDropdownOption");
        activeField.setAccessible(true);
        activeField.set(screen, dropdownOpt);

        // Let's determine where the dropdown choices are visually drawn when scrolled vs where they are clicked.
        // List of rows:
        // Index 0: enabled
        // Index 1: opt:Mode
        // Since rowIdx = 1, rowY(1) = 60 + 34 = 94.
        //
        // In extractRenderState (rendering/visual):
        // ry = 94 - 100 (scrollOffset) = -6
        // rby = -6 + 5 = -1
        // listY = -1 + 18 = 17 (since rby + 18 + listH = -1 + 18 + 54 = 71 <= 300 (height), it renders down)
        // Visually: listY = 17. The dropdown options are at Y in [17, 71].
        //
        // In mouseClicked (click handling):
        // ry = rowY(1) = 94 (no scrollOffset subtracted!)
        // rby = 94 + 5 = 99
        // listY = 99 + 18 = 117.
        // Code expects click at Y in [117, 171].
        //
        // Therefore, if the user clicks at the visual location (e.g., Y = 40):
        // mx = 250 (inside dropdown button/list x bounds: [244, 364])
        // my = 40 (inside visual bounds [17, 71])
        // Let's simulate this click:
        MouseButtonEvent visualClickEvent = mock(MouseButtonEvent.class);
        when(visualClickEvent.button()).thenReturn(0);
        when(visualClickEvent.x()).thenReturn(250.0);
        when(visualClickEvent.y()).thenReturn(40.0);

        screen.mouseClicked(visualClickEvent, false);

        // Since the bug is fixed, the click at visual location Y=40 should now successfully update the value to "B"!
        assertEquals("B", dropdownOpt.getValue(), "Visual click at Y=40 should successfully update the value to B");

        // Now, let's reopen the dropdown
        activeField.set(screen, dropdownOpt);

        // And simulate a click at the old incorrect unscrolled location Y = 140:
        MouseButtonEvent codeExpectedClickEvent = mock(MouseButtonEvent.class);
        when(codeExpectedClickEvent.button()).thenReturn(0);
        when(codeExpectedClickEvent.x()).thenReturn(250.0);
        when(codeExpectedClickEvent.y()).thenReturn(140.0);

        screen.mouseClicked(codeExpectedClickEvent, false);

        // This click should NOT change the value (it should remain "B") because Y=140 is now out of the visual bounds [17, 71]
        assertEquals("B", dropdownOpt.getValue(), "Click at unscrolled coordinate Y=140 should not register and value remains B");
    }
}
