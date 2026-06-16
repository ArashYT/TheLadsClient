package com.thelads.core.config;

import com.thelads.core.features.alwayson.advancementsreloaded.config.Configuration;
import com.thelads.core.features.alwayson.advancementsreloaded.config.ModConfigurationFile;
import net.fabricmc.loader.api.FabricLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AdvancementsReloadedConfigRobustnessTest {

    private File tempDir;
    private Path tempConfigDir;
    private MockedStatic<FabricLoader> fabricLoaderMockedStatic;
    private FabricLoader fabricLoaderMock;
    private File configFile;

    @BeforeEach
    public void setUp() throws Exception {
        tempDir = Files.createTempDirectory("adv_reloaded_config_test").toFile();
        tempConfigDir = tempDir.toPath().resolve("config");
        Files.createDirectories(tempConfigDir);
        configFile = tempConfigDir.resolve("advancements_reloaded.json").toFile();

        fabricLoaderMockedStatic = Mockito.mockStatic(FabricLoader.class);
        fabricLoaderMock = Mockito.mock(FabricLoader.class);
        fabricLoaderMockedStatic.when(FabricLoader::getInstance).thenReturn(fabricLoaderMock);
        Mockito.when(fabricLoaderMock.getConfigDir()).thenReturn(tempConfigDir);

        resetConfig();
    }

    @AfterEach
    public void tearDown() {
        if (fabricLoaderMockedStatic != null) {
            fabricLoaderMockedStatic.close();
        }
        deleteDirectory(tempDir);
    }

    private void deleteDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        dir.delete();
    }

    private void resetConfig() {
        Configuration.displaySidebar = true;
        Configuration.displayDescription = true;
        Configuration.criteriasAlphabeticOrder = true;
        Configuration.advancementsOrder = Configuration.AdvancementOrder.ALPHABETIC;
        Configuration.tabsOrder = Configuration.TabOrder.ALPHABETIC;
        Configuration.backgroundStyle = Configuration.BackgroundStyle.TRANSPARENT;
        Configuration.criteriasTranslationMode = Configuration.TranslationMode.ONLY_COMPATIBLE;
        Configuration.headerHeight = 48;
        Configuration.footerHeight = 48;
        Configuration.criteriasWidth = 142;
        Configuration.aboveWidgetLimit = 14;
        Configuration.belowWidgetLimit = 14;
        Configuration.customTabsOrder = List.of();
        Configuration.customAdvancementsOrder = List.of();
    }

    private void writeConfig(String content) throws IOException {
        try (FileWriter w = new FileWriter(configFile)) {
            w.write(content);
        }
    }

    @Test
    public void testEmptyConfigDoesNotCrashAndKeepsDefaults() throws Exception {
        writeConfig("{}");
        assertDoesNotThrow(() -> ModConfigurationFile.load(ModConfigurationFile.FileType.JSON));

        // Defaults should be preserved
        assertTrue(Configuration.displaySidebar);
        assertTrue(Configuration.displayDescription);
        assertTrue(Configuration.criteriasAlphabeticOrder);
        assertEquals(Configuration.AdvancementOrder.ALPHABETIC, Configuration.advancementsOrder);
        assertEquals(Configuration.TabOrder.ALPHABETIC, Configuration.tabsOrder);
        assertEquals(48, Configuration.headerHeight);
        assertEquals(142, Configuration.criteriasWidth);
    }

    @Test
    public void testMalformedJsonSyntaxDoesNotCrashAndKeepsDefaults() throws Exception {
        writeConfig("{ \"appearance\": { \"display_sideabar\": ");
        assertDoesNotThrow(() -> ModConfigurationFile.load(ModConfigurationFile.FileType.JSON));

        // Defaults should be preserved
        assertTrue(Configuration.displaySidebar);
    }

    @Test
    public void testPartialMissingKeysLoadCorrectly() throws Exception {
        writeConfig("{\"appearance\": {\"display_sideabar\": false}}");
        assertDoesNotThrow(() -> ModConfigurationFile.load(ModConfigurationFile.FileType.JSON));

        // Configured key is loaded
        assertFalse(Configuration.displaySidebar);
        // Missing keys keep defaults
        assertTrue(Configuration.displayDescription);
        assertEquals(Configuration.AdvancementOrder.ALPHABETIC, Configuration.advancementsOrder);
    }

    @Test
    public void testInvalidEnumNameDoesNotAbortRestOfAppearanceLoading() throws Exception {
        // Here we configure the advancements_order as "INVALID_ORDER", but we also configure
        // tabs_order to "NONE" (which is a valid enum value).
        // Due to individual try-catch blocks, tabs_order should be successfully loaded as NONE
        // even though advancements_order has a malformed value.
        writeConfig("{\"appearance\": {\"display_sideabar\": false, \"advancements_order\": \"INVALID_ORDER\", \"tabs_order\": \"NONE\"}}");
        assertDoesNotThrow(() -> ModConfigurationFile.load(ModConfigurationFile.FileType.JSON));

        // Safe fallback for advancementsOrder (defaults back to its default ALPHABETIC)
        assertEquals(Configuration.AdvancementOrder.ALPHABETIC, Configuration.advancementsOrder);
        // display_sideabar was loaded
        assertFalse(Configuration.displaySidebar);
        // tabs_order was successfully loaded as NONE because advancements_order failure did not abort parsing
        assertEquals(Configuration.TabOrder.NONE, Configuration.tabsOrder);
    }

    @Test
    public void testAllInvalidEnumNamesFallbackToDefaults() throws Exception {
        writeConfig("{" +
            "\"appearance\": {" +
            "\"advancements_order\": \"INVALID_ADV_ORDER\"," +
            "\"tabs_order\": \"INVALID_TAB_ORDER\"," +
            "\"background_style\": \"INVALID_STYLE\"," +
            "\"criterias_translation_mode\": \"INVALID_TRANSLATION_MODE\"" +
            "}" +
            "}");
        assertDoesNotThrow(() -> ModConfigurationFile.load(ModConfigurationFile.FileType.JSON));

        // All should fallback to their default values
        assertEquals(Configuration.AdvancementOrder.ALPHABETIC, Configuration.advancementsOrder);
        assertEquals(Configuration.TabOrder.ALPHABETIC, Configuration.tabsOrder);
        assertEquals(Configuration.BackgroundStyle.TRANSPARENT, Configuration.backgroundStyle);
        assertEquals(Configuration.TranslationMode.ONLY_COMPATIBLE, Configuration.criteriasTranslationMode);
    }

    @Test
    public void testNegativeLayoutBoundsAreAcceptedWithoutValidation() throws Exception {
        writeConfig("{\"advanced_customization\": {\"header_height\": -100, \"criterias_width\": -50}}");
        assertDoesNotThrow(() -> ModConfigurationFile.load(ModConfigurationFile.FileType.JSON));

        // Verify that negative layout bounds are accepted, showing a lack of safety check bounds validation
        assertEquals(-100, Configuration.headerHeight);
        assertEquals(-50, Configuration.criteriasWidth);
    }

    @Test
    public void testTypeMismatchOnBooleanDoesNotCrash() throws Exception {
        writeConfig("{\"appearance\": {\"display_sideabar\": []}}");
        assertDoesNotThrow(() -> ModConfigurationFile.load(ModConfigurationFile.FileType.JSON));

        // It should catch the exception and keep default
        assertTrue(Configuration.displaySidebar);
    }

    @Test
    public void testTypeMismatchOnCustomOrderListDoesNotCrash() throws Exception {
        writeConfig("{\"advanced_customization\": {\"custom_tabs_order\": \"not-an-array\"}}");
        assertDoesNotThrow(() -> ModConfigurationFile.load(ModConfigurationFile.FileType.JSON));

        // customTabsOrder should default to empty list
        assertNotNull(Configuration.customTabsOrder);
        assertTrue(Configuration.customTabsOrder.isEmpty());
    }

    @Test
    public void testTomlFileWithTomlSyntaxCausesJsonSyntaxExceptionAndFailsToLoad() throws Exception {
        // Change file to TOML path
        configFile = tempConfigDir.resolve("advancements_reloaded.toml").toFile();
        writeConfig("[appearance]\ndisplay_sideabar = false\n");

        assertDoesNotThrow(() -> ModConfigurationFile.load(ModConfigurationFile.FileType.TOML));

        // Since parsing failed due to TOML syntax, it should keep the default value (true)
        assertTrue(Configuration.displaySidebar);
    }

    @Test
    public void testTomlSaveWritesJsonFormat() throws Exception {
        configFile = tempConfigDir.resolve("advancements_reloaded.toml").toFile();
        ModConfigurationFile.load(ModConfigurationFile.FileType.TOML); // Sets storedFileType to TOML
        Configuration.displaySidebar = false;

        assertDoesNotThrow(() -> ModConfigurationFile.save());

        // Check if the saved TOML file actually contains valid JSON
        String fileContent = Files.readString(configFile.toPath()).trim();
        assertTrue(fileContent.startsWith("{"));
        assertTrue(fileContent.endsWith("}"));
        assertTrue(fileContent.contains("\"display_sideabar\": false"));
    }
}
