package com.thelads.core.e2e.mcmods;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import net.fabricmc.loader.api.FabricLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public abstract class BaseMcModsTest {

    protected File tempDir;
    protected File tempConfigFile;
    protected Path tempConfigDir;
    protected Path tempGameDir;
    protected MockedStatic<FabricLoader> fabricLoaderMockedStatic;
    protected FabricLoader fabricLoaderMock;
    protected static final Gson GSON = new Gson();

    @BeforeEach
    public void setUp() throws Exception {
        // Create unique temporary directories
        tempDir = Files.createTempDirectory("lads_e2e_mcmods_tests").toFile();
        tempConfigFile = new File(tempDir, "thelads_config_test.json");
        tempConfigDir = tempDir.toPath().resolve("config");
        tempGameDir = tempDir.toPath().resolve("game");
        Files.createDirectories(tempConfigDir);
        Files.createDirectories(tempGameDir);

        // Redirect ConfigManager to write and read from the temp file using reflection
        try {
            Method setTestFile = ConfigManager.class.getDeclaredMethod("setTestConfigFile", File.class);
            setTestFile.setAccessible(true);
            setTestFile.invoke(null, tempConfigFile);
        } catch (Exception e) {
            fail("Failed to set test config file redirect: " + e.getMessage());
        }

        // Mock FabricLoader static instances using Mockito
        fabricLoaderMockedStatic = Mockito.mockStatic(FabricLoader.class);
        fabricLoaderMock = Mockito.mock(FabricLoader.class);
        fabricLoaderMockedStatic.when(FabricLoader::getInstance).thenReturn(fabricLoaderMock);

        Mockito.when(fabricLoaderMock.getConfigDir()).thenReturn(tempConfigDir);
        Mockito.when(fabricLoaderMock.getGameDir()).thenReturn(tempGameDir);
    }

    @AfterEach
    public void tearDown() throws Exception {
        // Reset config redirect
        try {
            Method setTestFile = ConfigManager.class.getDeclaredMethod("setTestConfigFile", File.class);
            setTestFile.setAccessible(true);
            setTestFile.invoke(null, (File) null);
        } catch (Exception ignored) {}

        // Close Mockito static mock
        if (fabricLoaderMockedStatic != null) {
            fabricLoaderMockedStatic.close();
        }

        // Clean up temporary directories
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

    // --- Helpers requested by instructions ---

    /**
     * Helper: Checks if a class is present on the classpath.
     */
    protected boolean isClassPresent(String className) {
        try {
            Class.forName(className, false, getClass().getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            try {
                Class.forName(className, false, Thread.currentThread().getContextClassLoader());
                return true;
            } catch (ClassNotFoundException e2) {
                return false;
            }
        }
    }

    /**
     * Helper: Gets a module by name from ModuleManager.
     */
    protected Module getModule(String name) {
        return ModuleManager.getInstance().getModule(name);
    }

    /**
     * Helper: Reads the current test config file as a JsonObject.
     */
    protected JsonObject readConfigFile() throws IOException {
        if (!tempConfigFile.exists()) {
            return new JsonObject();
        }
        try (FileReader reader = new FileReader(tempConfigFile, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    /**
     * Helper: Verifies the module state in the configuration file.
     */
    protected void verifyModuleStateInFile(String moduleName, boolean expectedEnabled) throws IOException {
        JsonObject configJson = readConfigFile();
        assertTrue(configJson.has("modules"), "Configuration missing 'modules' object");
        JsonObject modules = configJson.getAsJsonObject("modules");
        assertTrue(modules.has(moduleName), "Configuration missing module '" + moduleName + "'");
        JsonObject moduleJson = modules.getAsJsonObject(moduleName);
        assertTrue(moduleJson.has("enabled"), "Module configuration missing 'enabled' state");
        assertEquals(expectedEnabled, moduleJson.get("enabled").getAsBoolean(),
                "Module '" + moduleName + "' enabled state mismatch in file!");
    }
}
