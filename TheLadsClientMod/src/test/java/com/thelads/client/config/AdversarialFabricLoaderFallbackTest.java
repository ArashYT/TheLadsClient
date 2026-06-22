package com.thelads.client.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;

import org.mockito.Mockito;
import org.mockito.MockedStatic;
import net.fabricmc.loader.api.FabricLoader;
import java.io.File;

public class AdversarialFabricLoaderFallbackTest {

    @Test
    public void testGetConfigFileWithNullLoader() throws Exception {
        try (MockedStatic<FabricLoader> mockLoaderClass = Mockito.mockStatic(FabricLoader.class)) {
            mockLoaderClass.when(FabricLoader::getInstance).thenReturn(null);
            
            // Should fallback without crashing
            ConfigManager.loadConfig();
            
            File expected = new File("config/theladsclient.json");
            assertTrue(expected.getParentFile().exists() || expected.exists(), "Should have fallen back to local config dir");
        }
    }

    @Test
    public void testGetConfigFileWithNullConfigDir() throws Exception {
        FabricLoader mockLoader = Mockito.mock(FabricLoader.class);
        Mockito.when(mockLoader.getConfigDir()).thenReturn(null);
        
        try (MockedStatic<FabricLoader> mockLoaderClass = Mockito.mockStatic(FabricLoader.class)) {
            mockLoaderClass.when(FabricLoader::getInstance).thenReturn(mockLoader);
            
            // Should fallback without crashing
            ConfigManager.loadConfig();
            
            File expected = new File("config/theladsclient.json");
            assertTrue(expected.getParentFile().exists() || expected.exists(), "Should have fallen back to local config dir");
        }
    }
}
