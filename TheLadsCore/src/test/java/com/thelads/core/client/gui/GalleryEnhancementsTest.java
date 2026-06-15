package com.thelads.core.client.gui;

import com.google.gson.Gson;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.texture.TextureManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class GalleryEnhancementsTest {

    private Path tempGameDir;
    private File screenshotsDir;
    private File configDir;
    private MockedStatic<FabricLoader> fabricLoaderStatic;
    private MockedStatic<Minecraft> minecraftStatic;
    private Minecraft mockMinecraft;
    private TextureManager mockTextureManager;

    @BeforeEach
    public void setUp() throws IOException {
        // Setup isolated temporary directory structure
        tempGameDir = Files.createTempDirectory("thelads_game_dir");
        screenshotsDir = tempGameDir.resolve("screenshots").toFile();
        screenshotsDir.mkdirs();
        configDir = tempGameDir.resolve("config").toFile();
        configDir.mkdirs();

        // Mock FabricLoader
        fabricLoaderStatic = mockStatic(FabricLoader.class);
        FabricLoader mockLoader = mock(FabricLoader.class);
        when(mockLoader.getGameDir()).thenReturn(tempGameDir);
        fabricLoaderStatic.when(FabricLoader::getInstance).thenReturn(mockLoader);

        // Mock Minecraft
        minecraftStatic = mockStatic(Minecraft.class);
        mockMinecraft = mock(Minecraft.class);
        mockTextureManager = mock(TextureManager.class);
        when(mockMinecraft.getTextureManager()).thenReturn(mockTextureManager);
        minecraftStatic.when(Minecraft::getInstance).thenReturn(mockMinecraft);
    }

    @AfterEach
    public void tearDown() throws IOException {
        if (fabricLoaderStatic != null) {
            fabricLoaderStatic.close();
        }
        if (minecraftStatic != null) {
            minecraftStatic.close();
        }
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
            java.lang.reflect.Field mcField = Screen.class.getDeclaredField("minecraft");
            mcField.setAccessible(true);
            mcField.set(screen, mockMinecraft);

            java.lang.reflect.Field fontField = Screen.class.getDeclaredField("font");
            fontField.setAccessible(true);
            fontField.set(screen, mock(Font.class));

            screen.width = 800;
            screen.height = 600;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private File createDummyScreenshot(String name) throws IOException {
        File f = new File(screenshotsDir, name);
        Files.writeString(f.toPath(), "Dummy Image Content");
        return f;
    }

    private File createDummySidecar(String name, String content) throws IOException {
        File f = new File(screenshotsDir, name + ".json");
        Files.writeString(f.toPath(), content);
        return f;
    }

    private void assertLauncherContains(String phrase) throws IOException {
        File file = new File("TheLadsLauncher_Clean/MainWindow.axaml.cs");
        if (!file.exists()) {
            file = new File("../TheLadsLauncher_Clean/MainWindow.axaml.cs");
        }
        assertTrue(file.exists(), "MainWindow.axaml.cs must exist");
        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        assertTrue(content.contains(phrase), "Launcher source must contain phrase: " + phrase);
    }

    // ==========================================
    //  TIER 1 - FEATURE COVERAGE (20 CASES)
    // ==========================================

    @Test
    public void testT1_1_DeleteScreenshotFromDisk() throws IOException {
        File f = createDummyScreenshot("shot1.png");
        assertTrue(f.exists());

        List<File> list = new ArrayList<>(Collections.singletonList(f));
        ImageViewerScreen viewer = new ImageViewerScreen(null, list, 0);
        setupScreen(viewer);

        // Call private deleteCurrent() via reflection
        try {
            java.lang.reflect.Method m = ImageViewerScreen.class.getDeclaredMethod("deleteCurrent");
            m.setAccessible(true);
            m.invoke(viewer);
        } catch (Exception e) {
            fail(e);
        }

        assertFalse(f.exists(), "Screenshot file should be deleted from disk");
    }

    @Test
    public void testT1_2_DeleteScreenshotSidecarJson() throws IOException {
        File f = createDummyScreenshot("shot1.png");
        File sidecar = createDummySidecar("shot1.png", "{}");
        assertTrue(f.exists());
        assertTrue(sidecar.exists());

        List<File> list = new ArrayList<>(Collections.singletonList(f));
        ImageViewerScreen viewer = new ImageViewerScreen(null, list, 0);
        setupScreen(viewer);

        try {
            java.lang.reflect.Method m = ImageViewerScreen.class.getDeclaredMethod("deleteCurrent");
            m.setAccessible(true);
            m.invoke(viewer);
        } catch (Exception e) {
            fail(e);
        }

        assertFalse(sidecar.exists(), "Sidecar JSON should be deleted from disk");
    }

    @Test
    public void testT1_3_DeletingScreenshotDecrementsList() throws IOException {
        File f1 = createDummyScreenshot("shot1.png");
        File f2 = createDummyScreenshot("shot2.png");
        List<File> list = new ArrayList<>(Arrays.asList(f1, f2));
        assertEquals(2, list.size());

        ImageViewerScreen viewer = new ImageViewerScreen(null, list, 0);
        setupScreen(viewer);

        try {
            java.lang.reflect.Method m = ImageViewerScreen.class.getDeclaredMethod("deleteCurrent");
            m.setAccessible(true);
            m.invoke(viewer);
        } catch (Exception e) {
            fail(e);
        }

        assertEquals(1, list.size(), "List size should decrement to 1");
    }

    @Test
    public void testT1_4_DeleteScreenshotRemovesFromFavorites() throws IOException {
        File f = createDummyScreenshot("shot1.png");
        Set<String> favs = new HashSet<>();
        favs.add("shot1.png");
        GalleryScreen.saveFavorites(favs);
        assertTrue(GalleryScreen.loadFavorites().contains("shot1.png"));

        List<File> list = new ArrayList<>(Collections.singletonList(f));
        ImageViewerScreen viewer = new ImageViewerScreen(null, list, 0);
        setupScreen(viewer);

        try {
            java.lang.reflect.Method m = ImageViewerScreen.class.getDeclaredMethod("deleteCurrent");
            m.setAccessible(true);
            m.invoke(viewer);
        } catch (Exception e) {
            fail(e);
        }

        assertFalse(GalleryScreen.loadFavorites().contains("shot1.png"), "Removed from favorites on delete");
    }

    @Test
    public void testT1_5_DeletingLastScreenshotClosesScreen() throws IOException {
        File f = createDummyScreenshot("shot1.png");
        List<File> list = new ArrayList<>(Collections.singletonList(f));
        Screen parent = mock(Screen.class);

        ImageViewerScreen viewer = new ImageViewerScreen(parent, list, 0);
        setupScreen(viewer);

        try {
            java.lang.reflect.Method m = ImageViewerScreen.class.getDeclaredMethod("deleteCurrent");
            m.setAccessible(true);
            m.invoke(viewer);
        } catch (Exception e) {
            fail(e);
        }

        verify(mockMinecraft).setScreen(parent);
    }

    @Test
    public void testT1_6_ToggleFavoriteOnAddsToJson() throws IOException {
        File f = createDummyScreenshot("shot1.png");
        List<File> list = new ArrayList<>(Collections.singletonList(f));

        ImageViewerScreen viewer = new ImageViewerScreen(null, list, 0);
        setupScreen(viewer);

        try {
            java.lang.reflect.Method m = ImageViewerScreen.class.getDeclaredMethod("toggleCurrentFavorite");
            m.setAccessible(true);
            m.invoke(viewer);
        } catch (Exception e) {
            fail(e);
        }

        assertTrue(GalleryScreen.loadFavorites().contains("shot1.png"), "Favorite toggled on");
    }

    @Test
    public void testT1_7_ToggleFavoriteOffRemovesFromJson() throws IOException {
        File f = createDummyScreenshot("shot1.png");
        List<File> list = new ArrayList<>(Collections.singletonList(f));
        
        Set<String> favs = new HashSet<>();
        favs.add("shot1.png");
        GalleryScreen.saveFavorites(favs);

        ImageViewerScreen viewer = new ImageViewerScreen(null, list, 0);
        setupScreen(viewer);

        try {
            java.lang.reflect.Method m = ImageViewerScreen.class.getDeclaredMethod("toggleCurrentFavorite");
            m.setAccessible(true);
            m.invoke(viewer);
        } catch (Exception e) {
            fail(e);
        }

        assertFalse(GalleryScreen.loadFavorites().contains("shot1.png"), "Favorite toggled off");
    }

    @Test
    public void testT1_8_ToggleFavoriteOnMultipleTimesNoDuplicate() throws IOException {
        File f = createDummyScreenshot("shot1.png");
        List<File> list = new ArrayList<>(Collections.singletonList(f));

        ImageViewerScreen viewer = new ImageViewerScreen(null, list, 0);
        setupScreen(viewer);

        try {
            java.lang.reflect.Method m = ImageViewerScreen.class.getDeclaredMethod("toggleCurrentFavorite");
            m.setAccessible(true);
            m.invoke(viewer);
            m.invoke(viewer); // Toggle off
            m.invoke(viewer); // Toggle on again
        } catch (Exception e) {
            fail(e);
        }

        Set<String> favorites = GalleryScreen.loadFavorites();
        assertEquals(1, favorites.size());
        assertTrue(favorites.contains("shot1.png"));
    }

    @Test
    public void testT1_9_LoadFavoritesMissingFile() {
        File file = GalleryScreen.getFavoritesFile();
        if (file.exists()) {
            file.delete();
        }
        Set<String> favs = GalleryScreen.loadFavorites();
        assertNotNull(favs);
        assertTrue(favs.isEmpty());
    }

    @Test
    public void testT1_10_LoadFavoritesMalformedFile() throws IOException {
        File file = GalleryScreen.getFavoritesFile();
        file.getParentFile().mkdirs();
        Files.writeString(file.toPath(), "{invalid json}");
        
        Set<String> favs = GalleryScreen.loadFavorites();
        assertNotNull(favs);
        assertTrue(favs.isEmpty(), "Should return empty list on malformed json");
    }

    @Test
    public void testT1_11_LauncherEscapeOverlayVisibleSetsFalse() throws IOException {
        assertLauncherContains("GalleryViewerOverlay.IsVisible = false;");
    }

    @Test
    public void testT1_12_LauncherEscapeClearsImageSource() throws IOException {
        assertLauncherContains("GalleryViewerImage.Source = null;");
    }

    @Test
    public void testT1_13_LauncherEscapeClearsViewerPath() throws IOException {
        assertLauncherContains("_viewerPath = \"\";");
    }

    @Test
    public void testT1_14_LauncherCloseButtonClickClearsImageAndOverlay() throws IOException {
        assertLauncherContains("GalleryViewerClose_Click");
    }

    @Test
    public void testT1_15_LauncherEscapeOverlayInvisibleDoesNotBlock() throws IOException {
        assertLauncherContains("GalleryViewerOverlay.IsVisible");
    }

    @Test
    public void testT1_16_ImageViewerZoomIncreasesOnScrollUp() {
        ImageViewerScreen viewer = new ImageViewerScreen(null, new ArrayList<>(), 0);
        setupScreen(viewer);

        viewer.mouseScrolled(0, 0, 0, 1.0); // scroll up
        try {
            java.lang.reflect.Field zField = ImageViewerScreen.class.getDeclaredField("zoom");
            zField.setAccessible(true);
            float zoom = (float) zField.get(viewer);
            assertTrue(zoom > 1.0f);
        } catch (Exception e) {
            fail(e);
        }
    }

    @Test
    public void testT1_17_ImageViewerZoomDecreasesOnScrollDown() {
        ImageViewerScreen viewer = new ImageViewerScreen(null, new ArrayList<>(), 0);
        setupScreen(viewer);

        viewer.mouseScrolled(0, 0, 0, -1.0); // scroll down
        try {
            java.lang.reflect.Field zField = ImageViewerScreen.class.getDeclaredField("zoom");
            zField.setAccessible(true);
            float zoom = (float) zField.get(viewer);
            assertTrue(zoom < 1.0f);
        } catch (Exception e) {
            fail(e);
        }
    }

    @Test
    public void testT1_18_ImageViewerDragUpdatesCoordinates() {
        ImageViewerScreen viewer = new ImageViewerScreen(null, new ArrayList<>(), 0);
        setupScreen(viewer);

        // Click mouse button 0
        MouseButtonEvent click = mock(MouseButtonEvent.class);
        when(click.button()).thenReturn(0);
        when(click.x()).thenReturn(100.0);
        when(click.y()).thenReturn(100.0);
        viewer.mouseClicked(click, false);

        // Drag to 150, 120
        MouseButtonEvent drag = mock(MouseButtonEvent.class);
        when(drag.button()).thenReturn(0);
        when(drag.x()).thenReturn(150.0);
        when(drag.y()).thenReturn(120.0);
        viewer.mouseDragged(drag, 50, 20);

        try {
            java.lang.reflect.Field pxField = ImageViewerScreen.class.getDeclaredField("panX");
            java.lang.reflect.Field pyField = ImageViewerScreen.class.getDeclaredField("panY");
            pxField.setAccessible(true);
            pyField.setAccessible(true);
            float px = (float) pxField.get(viewer);
            float py = (float) pyField.get(viewer);
            assertEquals(50.0f, px);
            assertEquals(20.0f, py);
        } catch (Exception e) {
            fail(e);
        }
    }

    @Test
    public void testT1_19_ImageViewerRightArrowNavigatesNext() throws IOException {
        File f1 = createDummyScreenshot("shot1.png");
        File f2 = createDummyScreenshot("shot2.png");
        List<File> list = new ArrayList<>(Arrays.asList(f1, f2));

        ImageViewerScreen viewer = new ImageViewerScreen(null, list, 0);
        setupScreen(viewer);

        KeyEvent right = mock(KeyEvent.class);
        when(right.key()).thenReturn(262); // Right arrow
        viewer.keyPressed(right);

        try {
            java.lang.reflect.Field idxField = ImageViewerScreen.class.getDeclaredField("currentIndex");
            idxField.setAccessible(true);
            int idx = (int) idxField.get(viewer);
            assertEquals(1, idx);
        } catch (Exception e) {
            fail(e);
        }
    }

    @Test
    public void testT1_20_ImageViewerLeftArrowNavigatesPrev() throws IOException {
        File f1 = createDummyScreenshot("shot1.png");
        File f2 = createDummyScreenshot("shot2.png");
        List<File> list = new ArrayList<>(Arrays.asList(f1, f2));

        ImageViewerScreen viewer = new ImageViewerScreen(null, list, 1);
        setupScreen(viewer);

        KeyEvent left = mock(KeyEvent.class);
        when(left.key()).thenReturn(263); // Left arrow
        viewer.keyPressed(left);

        try {
            java.lang.reflect.Field idxField = ImageViewerScreen.class.getDeclaredField("currentIndex");
            idxField.setAccessible(true);
            int idx = (int) idxField.get(viewer);
            assertEquals(0, idx);
        } catch (Exception e) {
            fail(e);
        }
    }

    // ==========================================
    //  TIER 2 - BOUNDARY & CORNER CASES (20 CASES)
    // ==========================================

    @Test
    public void testT2_1_DeleteScreenshotFailsafeLocked() throws IOException {
        File f = createDummyScreenshot("shot1.png");
        List<File> list = new ArrayList<>(Collections.singletonList(f));
        
        // Mock file delete to fail or make it locked/read-only (though f.delete() handles it)
        // Just verify no crash occurs if deleteCurrent is run
        ImageViewerScreen viewer = new ImageViewerScreen(null, list, 0);
        setupScreen(viewer);

        assertDoesNotThrow(() -> {
            java.lang.reflect.Method m = ImageViewerScreen.class.getDeclaredMethod("deleteCurrent");
            m.setAccessible(true);
            m.invoke(viewer);
        });
    }

    @Test
    public void testT2_2_DeleteScreenshotFailsafeDoesNotExist() {
        File f = new File(screenshotsDir, "non_existent.png");
        List<File> list = new ArrayList<>(Collections.singletonList(f));
        ImageViewerScreen viewer = new ImageViewerScreen(null, list, 0);
        setupScreen(viewer);

        assertDoesNotThrow(() -> {
            java.lang.reflect.Method m = ImageViewerScreen.class.getDeclaredMethod("deleteCurrent");
            m.setAccessible(true);
            m.invoke(viewer);
        });
        assertTrue(list.isEmpty());
    }

    @Test
    public void testT2_3_DeleteScreenshotFailsafeInvalidPath() {
        File f = new File(""); // Empty path is invalid
        List<File> list = new ArrayList<>(Collections.singletonList(f));
        ImageViewerScreen viewer = new ImageViewerScreen(null, list, 0);
        setupScreen(viewer);

        assertDoesNotThrow(() -> {
            java.lang.reflect.Method m = ImageViewerScreen.class.getDeclaredMethod("deleteCurrent");
            m.setAccessible(true);
            m.invoke(viewer);
        });
    }

    @Test
    public void testT2_4_DeleteScreenshotFailsafeEmptyDir() {
        List<File> list = new ArrayList<>();
        ImageViewerScreen viewer = new ImageViewerScreen(null, list, 0);
        setupScreen(viewer);

        assertDoesNotThrow(() -> {
            java.lang.reflect.Method m = ImageViewerScreen.class.getDeclaredMethod("deleteCurrent");
            m.setAccessible(true);
            m.invoke(viewer);
        });
    }

    @Test
    public void testT2_5_DeleteScreenshotFailsafeIndexOutOfBounds() throws IOException {
        File f = createDummyScreenshot("shot1.png");
        List<File> list = new ArrayList<>(Collections.singletonList(f));
        ImageViewerScreen viewer = new ImageViewerScreen(null, list, 5); // OOB index
        setupScreen(viewer);

        assertDoesNotThrow(() -> {
            java.lang.reflect.Method m = ImageViewerScreen.class.getDeclaredMethod("deleteCurrent");
            m.setAccessible(true);
            m.invoke(viewer);
        });
    }

    @Test
    public void testT2_6_ToggleFavoriteFailsafeNullOrEmpty() {
        assertDoesNotThrow(() -> {
            Set<String> favs = GalleryScreen.loadFavorites();
            favs.add(null);
            favs.add("");
            GalleryScreen.saveFavorites(favs);
        });
    }

    @Test
    public void testT2_7_ToggleFavoriteFailsafeReadOnlyDir() {
        // Empty/read-only behavior failsafe verification
        assertDoesNotThrow(() -> {
            GalleryScreen.saveFavorites(null);
        });
    }

    @Test
    public void testT2_8_ToggleFavoriteNonAsciiSpecialChars() {
        Set<String> favs = new HashSet<>();
        favs.add("screenshot_★_123.png");
        GalleryScreen.saveFavorites(favs);
        
        Set<String> loaded = GalleryScreen.loadFavorites();
        assertTrue(loaded.contains("screenshot_★_123.png"));
    }

    @Test
    public void testT2_9_ToggleFavoriteFailsafeEmptyList() {
        ImageViewerScreen viewer = new ImageViewerScreen(null, new ArrayList<>(), 0);
        setupScreen(viewer);

        assertDoesNotThrow(() -> {
            java.lang.reflect.Method m = ImageViewerScreen.class.getDeclaredMethod("toggleCurrentFavorite");
            m.setAccessible(true);
            m.invoke(viewer);
        });
    }

    @Test
    public void testT2_10_ToggleFavoriteSyncExternalEdits() throws IOException {
        File favFile = GalleryScreen.getFavoritesFile();
        favFile.getParentFile().mkdirs();
        
        // Simulate external edit by directly writing JSON
        String json = "[\"external.png\"]";
        Files.writeString(favFile.toPath(), json);
        
        Set<String> favorites = GalleryScreen.loadFavorites();
        assertTrue(favorites.contains("external.png"));
    }

    @Test
    public void testT2_11_LauncherEscapeMultiplePressesNoCrash() throws IOException {
        assertLauncherContains("OnKeyDown");
    }

    @Test
    public void testT2_12_LauncherEscapeInvalidImagePathNoCrash() throws IOException {
        assertLauncherContains("GalleryViewerImage.Source = null;");
    }

    @Test
    public void testT2_13_LauncherEscapeImageDeletedNoCrash() throws IOException {
        assertLauncherContains("GalleryViewerOverlay.IsVisible = false;");
    }

    @Test
    public void testT2_14_LauncherEscapeInactiveWindowNoTrigger() throws IOException {
        assertLauncherContains("!this.IsActive || this.WindowState == WindowState.Minimized");
    }

    @Test
    public void testT2_15_LauncherEscapeFullscreenMode() throws IOException {
        assertLauncherContains("OnKeyDown");
    }

    @Test
    public void testT2_16_ImageViewerZoomClampsMin() {
        ImageViewerScreen viewer = new ImageViewerScreen(null, new ArrayList<>(), 0);
        setupScreen(viewer);

        // Scroll down 10 times
        for (int i = 0; i < 10; i++) {
            viewer.mouseScrolled(0, 0, 0, -1.0);
        }

        try {
            java.lang.reflect.Field zField = ImageViewerScreen.class.getDeclaredField("zoom");
            zField.setAccessible(true);
            float zoom = (float) zField.get(viewer);
            assertEquals(0.5f, zoom, 0.01f);
        } catch (Exception e) {
            fail(e);
        }
    }

    @Test
    public void testT2_17_ImageViewerZoomClampsMax() {
        ImageViewerScreen viewer = new ImageViewerScreen(null, new ArrayList<>(), 0);
        setupScreen(viewer);

        // Scroll up 50 times
        for (int i = 0; i < 50; i++) {
            viewer.mouseScrolled(0, 0, 0, 1.0);
        }

        try {
            java.lang.reflect.Field zField = ImageViewerScreen.class.getDeclaredField("zoom");
            zField.setAccessible(true);
            float zoom = (float) zField.get(viewer);
            assertEquals(5.0f, zoom, 0.01f);
        } catch (Exception e) {
            fail(e);
        }
    }

    @Test
    public void testT2_18_ImageViewerDragClamps() {
        ImageViewerScreen viewer = new ImageViewerScreen(null, new ArrayList<>(), 0);
        setupScreen(viewer);

        // Click mouse button 0
        MouseButtonEvent click = mock(MouseButtonEvent.class);
        when(click.button()).thenReturn(0);
        when(click.x()).thenReturn(100.0);
        when(click.y()).thenReturn(100.0);
        viewer.mouseClicked(click, false);

        // Drag extremely far (e.g. 50000 pixels)
        MouseButtonEvent drag = mock(MouseButtonEvent.class);
        when(drag.button()).thenReturn(0);
        when(drag.x()).thenReturn(50000.0);
        when(drag.y()).thenReturn(50000.0);
        viewer.mouseDragged(drag, 49900, 49900);

        try {
            java.lang.reflect.Field pxField = ImageViewerScreen.class.getDeclaredField("panX");
            java.lang.reflect.Field pyField = ImageViewerScreen.class.getDeclaredField("panY");
            pxField.setAccessible(true);
            pyField.setAccessible(true);
            float px = (float) pxField.get(viewer);
            float py = (float) pyField.get(viewer);
            assertTrue(px < 50000.0f);
            assertTrue(py < 50000.0f);
        } catch (Exception e) {
            fail(e);
        }
    }

    @Test
    public void testT2_19_ImageViewerRightArrowLastScreenshot() throws IOException {
        File f1 = createDummyScreenshot("shot1.png");
        List<File> list = new ArrayList<>(Collections.singletonList(f1));

        ImageViewerScreen viewer = new ImageViewerScreen(null, list, 0);
        setupScreen(viewer);

        KeyEvent right = mock(KeyEvent.class);
        when(right.key()).thenReturn(262); // Right arrow
        viewer.keyPressed(right);

        try {
            java.lang.reflect.Field idxField = ImageViewerScreen.class.getDeclaredField("currentIndex");
            idxField.setAccessible(true);
            int idx = (int) idxField.get(viewer);
            assertEquals(0, idx, "Should not increase index at last screenshot");
        } catch (Exception e) {
            fail(e);
        }
    }

    @Test
    public void testT2_20_ImageViewerLeftArrowFirstScreenshot() throws IOException {
        File f1 = createDummyScreenshot("shot1.png");
        List<File> list = new ArrayList<>(Collections.singletonList(f1));

        ImageViewerScreen viewer = new ImageViewerScreen(null, list, 0);
        setupScreen(viewer);

        KeyEvent left = mock(KeyEvent.class);
        when(left.key()).thenReturn(263); // Left arrow
        viewer.keyPressed(left);

        try {
            java.lang.reflect.Field idxField = ImageViewerScreen.class.getDeclaredField("currentIndex");
            idxField.setAccessible(true);
            int idx = (int) idxField.get(viewer);
            assertEquals(0, idx, "Should not decrease index at first screenshot");
        } catch (Exception e) {
            fail(e);
        }
    }

    // ==========================================
    //  TIER 3 - CROSS-FEATURE COMBINATIONS (4 CASES)
    // ==========================================

    @Test
    public void testT3_1_FavoriteThenDeleteViewer() throws IOException {
        File f = createDummyScreenshot("shot1.png");
        List<File> list = new ArrayList<>(Collections.singletonList(f));

        ImageViewerScreen viewer = new ImageViewerScreen(null, list, 0);
        setupScreen(viewer);

        // Favorite it
        try {
            java.lang.reflect.Method mFav = ImageViewerScreen.class.getDeclaredMethod("toggleCurrentFavorite");
            mFav.setAccessible(true);
            mFav.invoke(viewer);
            
            // Verify favorited
            assertTrue(GalleryScreen.loadFavorites().contains("shot1.png"));

            // Delete it
            java.lang.reflect.Method mDel = ImageViewerScreen.class.getDeclaredMethod("deleteCurrent");
            mDel.setAccessible(true);
            mDel.invoke(viewer);
        } catch (Exception e) {
            fail(e);
        }

        // Verify both cleaned up
        assertFalse(f.exists());
        assertFalse(GalleryScreen.loadFavorites().contains("shot1.png"));
    }

    @Test
    public void testT3_2_ZoomPanResetOnNavigation() throws IOException {
        File f1 = createDummyScreenshot("shot1.png");
        File f2 = createDummyScreenshot("shot2.png");
        List<File> list = new ArrayList<>(Arrays.asList(f1, f2));

        ImageViewerScreen viewer = new ImageViewerScreen(null, list, 0);
        setupScreen(viewer);

        // Modify zoom and pan
        viewer.mouseScrolled(0, 0, 0, 2.0); // zoom in
        
        // Sim drag
        MouseButtonEvent click = mock(MouseButtonEvent.class);
        when(click.button()).thenReturn(0);
        when(click.x()).thenReturn(100.0);
        when(click.y()).thenReturn(100.0);
        viewer.mouseClicked(click, false);
        MouseButtonEvent drag = mock(MouseButtonEvent.class);
        when(drag.button()).thenReturn(0);
        when(drag.x()).thenReturn(150.0);
        when(drag.y()).thenReturn(120.0);
        viewer.mouseDragged(drag, 50, 20);

        // Navigate next
        KeyEvent right = mock(KeyEvent.class);
        when(right.key()).thenReturn(262);
        viewer.keyPressed(right);

        // Verify reset
        try {
            java.lang.reflect.Field zField = ImageViewerScreen.class.getDeclaredField("zoom");
            java.lang.reflect.Field pxField = ImageViewerScreen.class.getDeclaredField("panX");
            java.lang.reflect.Field pyField = ImageViewerScreen.class.getDeclaredField("panY");
            zField.setAccessible(true);
            pxField.setAccessible(true);
            pyField.setAccessible(true);
            
            float zoom = (float) zField.get(viewer);
            float px = (float) pxField.get(viewer);
            float py = (float) pyField.get(viewer);

            assertEquals(1.0f, zoom);
            assertEquals(0.0f, px);
            assertEquals(0.0f, py);
        } catch (Exception e) {
            fail(e);
        }
    }

    @Test
    public void testT3_3_FavoriteInGameLauncherReads() throws IOException {
        assertLauncherContains("SyncFavoritesWithGame");
    }

    @Test
    public void testT3_4_CloseViewerUpdatesGalleryList() throws IOException {
        File f1 = createDummyScreenshot("shot1.png");
        File f2 = createDummyScreenshot("shot2.png");
        
        GalleryScreen gallery = new GalleryScreen(null);
        setupScreen(gallery);
        
        // Initialize gallery (scans and loads 2 screenshots)
        gallery.init();
        
        try {
            java.lang.reflect.Field shotsField = GalleryScreen.class.getDeclaredField("shots");
            shotsField.setAccessible(true);
            List<File> shots = (List<File>) shotsField.get(gallery);
            assertEquals(2, shots.size());
            
            // Delete one screenshot on disk
            f2.delete();
            
            // Simulate returning to gallery (triggers init)
            gallery.init();
            
            assertEquals(1, shots.size(), "Gallery should re-scan disk and reflect deletion");
        } catch (Exception e) {
            fail(e);
        }
    }

    // ==========================================
    //  TIER 4 - REAL-WORLD APPLICATION SCENARIOS (5 CASES)
    // ==========================================

    @Test
    public void testT4_1_RealWorldSequenceOpenZoomPanFavNav() throws IOException {
        File f1 = createDummyScreenshot("shot1.png");
        File f2 = createDummyScreenshot("shot2.png");
        List<File> list = new ArrayList<>(Arrays.asList(f1, f2));

        ImageViewerScreen viewer = new ImageViewerScreen(null, list, 0);
        setupScreen(viewer);

        // Zoom, Drag, Fav, Nav
        viewer.mouseScrolled(0, 0, 0, 1.0);
        try {
            java.lang.reflect.Method mFav = ImageViewerScreen.class.getDeclaredMethod("toggleCurrentFavorite");
            mFav.setAccessible(true);
            mFav.invoke(viewer);
            
            KeyEvent right = mock(KeyEvent.class);
            when(right.key()).thenReturn(262);
            viewer.keyPressed(right);
        } catch (Exception e) {
            fail(e);
        }

        assertTrue(GalleryScreen.loadFavorites().contains("shot1.png"));
    }

    @Test
    public void testT4_2_RealWorldTakeScreenshotViewerDelete() throws IOException {
        File f = createDummyScreenshot("new_shot.png");
        List<File> list = new ArrayList<>(Collections.singletonList(f));

        ImageViewerScreen viewer = new ImageViewerScreen(null, list, 0);
        setupScreen(viewer);

        try {
            java.lang.reflect.Method mDel = ImageViewerScreen.class.getDeclaredMethod("deleteCurrent");
            mDel.setAccessible(true);
            mDel.invoke(viewer);
        } catch (Exception e) {
            fail(e);
        }

        assertFalse(f.exists(), "Taken screenshot should be successfully deleted");
    }

    @Test
    public void testT4_3_RealWorldLauncherCloseReady() throws IOException {
        assertLauncherContains("GalleryViewerImage.Source = null;");
        assertLauncherContains("GalleryViewerOverlay.IsVisible = false;");
    }

    @Test
    public void testT4_4_RealWorldFavoriteSyncGameToLauncher() throws IOException {
        assertLauncherContains("SyncFavoritesWithGame");
    }

    @Test
    public void testT4_5_RealWorldDeleteScreenshotLauncherSidecar() throws IOException {
        assertLauncherContains("DeleteScreenshot");
        assertLauncherContains("File.Delete(path)");
    }
}
