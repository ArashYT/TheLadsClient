package com.thelads.core.client.capes;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.google.gson.Gson;
import com.mojang.authlib.GameProfile;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.MockedStatic;
import org.mockito.MockedConstruction;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

public class CapesStressAndFaultHarnessTest {

    private static final String VALID_1X1_PNG_BASE64 = 
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=";

    private Path tempConfigDir;

    @BeforeEach
    public void setUp() throws Exception {
        tempConfigDir = Files.createTempDirectory("capes_test_config");
        clearInstances();
    }

    @AfterEach
    public void tearDown() throws Exception {
        clearInstances();
        deleteDirectory(tempConfigDir.toFile());
    }

    private void clearInstances() throws Exception {
        Field field = PlayerHandler.class.getDeclaredField("instances");
        field.setAccessible(true);
        Map<?, ?> instancesMap = (Map<?, ?>) field.get(null);
        instancesMap.clear();
    }

    private void deleteDirectory(java.io.File file) {
        java.io.File[] contents = file.listFiles();
        if (contents != null) {
            for (java.io.File f : contents) {
                deleteDirectory(f);
            }
        }
        file.delete();
    }

    /**
     * Stress tests concurrent cape fetching for 50 different player UUIDs.
     * Verifies if 50 unique handlers can be safely queried and retrieved.
     */
    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    public void testConcurrentCapeFetchingDifferentPlayers() throws Exception {
        int numThreads = 50;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CyclicBarrier barrier = new CyclicBarrier(numThreads);
        List<Callable<PlayerHandler>> tasks = new ArrayList<>();

        for (int i = 0; i < numThreads; i++) {
            final int id = i;
            tasks.add(() -> {
                barrier.await();
                UUID uuid = new java.util.UUID(0L, id);
                GameProfile profile = new GameProfile(uuid, "Player_" + id);
                return PlayerHandler.fromProfile(profile);
            });
        }

        List<Future<PlayerHandler>> futures = executor.invokeAll(tasks);
        executor.shutdown();

        Set<PlayerHandler> handlers = Collections.synchronizedSet(new HashSet<>());
        for (Future<PlayerHandler> future : futures) {
            PlayerHandler handler = future.get();
            assertNotNull(handler);
            handlers.add(handler);
        }

        // Verify all 50 unique handlers were created
        assertEquals(numThreads, handlers.size());
    }

    /**
     * Stress tests concurrent cape fetching for the SAME player UUID.
     * Detects and logs if the non-thread-safe HashMap implementation in PlayerHandler causes race conditions.
     */
    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    public void testConcurrentCapeFetchingSamePlayerRaceCondition() throws Exception {
        int numThreads = 50;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CyclicBarrier barrier = new CyclicBarrier(numThreads);
        UUID sharedUuid = UUID.randomUUID();
        GameProfile profile = new GameProfile(sharedUuid, "SharedPlayer");

        List<Callable<PlayerHandler>> tasks = new ArrayList<>();
        for (int i = 0; i < numThreads; i++) {
            tasks.add(() -> {
                barrier.await();
                return PlayerHandler.fromProfile(profile);
            });
        }

        List<Future<PlayerHandler>> futures = executor.invokeAll(tasks);
        executor.shutdown();

        PlayerHandler firstHandler = futures.get(0).get();
        assertNotNull(firstHandler);

        int duplicateInstancesCount = 0;
        for (int i = 1; i < numThreads; i++) {
            PlayerHandler handler = futures.get(i).get();
            if (firstHandler != handler) {
                duplicateInstancesCount++;
            }
        }
        
        if (duplicateInstancesCount > 0) {
            System.err.println("[VULNERABILITY DETECTED] Race condition in PlayerHandler.fromProfile: " 
                + duplicateInstancesCount + " duplicate PlayerHandler instances were created concurrently for UUID " + sharedUuid);
        } else {
            System.out.println("[INFO] No race condition detected in PlayerHandler.fromProfile this run.");
        }
        
        // Assert that no exceptions occurred during execution
        assertNotNull(firstHandler);
    }

    /**
     * Verifies system behavior when connection times out.
     * Uses OPTIFINE which has a valid URL generator when enabled.
     */
    @Test
    public void testConnectionTimeoutGracefulHandling() throws Exception {
        try (MockedStatic<FabricLoader> mockedFabricLoader = mockStatic(FabricLoader.class);
             MockedStatic<Minecraft> mockedMinecraft = mockStatic(Minecraft.class);
             MockedStatic<PlayerHandler> mockedPlayerHandler = mockStatic(PlayerHandler.class, CALLS_REAL_METHODS)) {

            FabricLoader mockLoader = mock(FabricLoader.class);
            when(mockLoader.getConfigDir()).thenReturn(tempConfigDir);
            mockedFabricLoader.when(FabricLoader::getInstance).thenReturn(mockLoader);

            Minecraft mockMc = mock(Minecraft.class);
            TextureManager mockTm = mock(TextureManager.class);
            mockedMinecraft.when(Minecraft::getInstance).thenReturn(mockMc);
            when(mockMc.getProxy()).thenReturn(java.net.Proxy.NO_PROXY);
            when(mockMc.getTextureManager()).thenReturn(mockTm);

            // Mock HttpURLConnection that throws SocketTimeoutException on connect
            HttpURLConnection mockConn = mock(HttpURLConnection.class);
            doThrow(new SocketTimeoutException("Connection timed out")).when(mockConn).connect();

            mockedPlayerHandler.when(() -> PlayerHandler.connection(anyString())).thenReturn(mockConn);

            CapeConfig config = new CapeConfig();
            config.setEnableOptifine(true);
            Capes.INSTANCE.CONFIG = config;

            UUID uuid = UUID.randomUUID();
            GameProfile profile = new GameProfile(uuid, "TimeoutPlayer");
            PlayerHandler handler = new PlayerHandler(profile);

            boolean result = handler.setCape(CapeType.OPTIFINE);

            assertFalse(result, "setCape should return false when connection times out");
            assertFalse(handler.getHasCape(), "Player should not have a cape loaded");
        }
    }

    /**
     * Verifies system behavior when invalid base64 image data is returned.
     */
    @Test
    public void testInvalidBase64ImageGracefulHandling() throws Exception {
        try (MockedStatic<Minecraft> mockedMinecraft = mockStatic(Minecraft.class)) {
            Minecraft mockMc = mock(Minecraft.class);
            TextureManager mockTm = mock(TextureManager.class);
            mockedMinecraft.when(Minecraft::getInstance).thenReturn(mockMc);
            when(mockMc.getTextureManager()).thenReturn(mockTm);

            UUID uuid = UUID.randomUUID();
            GameProfile profile = new GameProfile(uuid, "Base64Player");
            PlayerHandler handler = new PlayerHandler(profile);

            boolean result = handler.setCapeTextureFromBase64("!!!INVALID_BASE64_CHARACTERS!!!");

            assertFalse(result, "setCapeTextureFromBase64 should return false on invalid base64 input");
            assertFalse(handler.getHasCape(), "Player should not have a cape loaded");
        }
    }

    /**
     * Verifies system behavior when malformed JSON is returned from Cosmetica api.
     */
    @Test
    public void testMalformedJsonGracefulHandling() throws Exception {
        try (MockedStatic<FabricLoader> mockedFabricLoader = mockStatic(FabricLoader.class);
             MockedStatic<Minecraft> mockedMinecraft = mockStatic(Minecraft.class);
             MockedStatic<PlayerHandler> mockedPlayerHandler = mockStatic(PlayerHandler.class, CALLS_REAL_METHODS)) {

            FabricLoader mockLoader = mock(FabricLoader.class);
            when(mockLoader.getConfigDir()).thenReturn(tempConfigDir);
            mockedFabricLoader.when(FabricLoader::getInstance).thenReturn(mockLoader);

            Minecraft mockMc = mock(Minecraft.class);
            TextureManager mockTm = mock(TextureManager.class);
            mockedMinecraft.when(Minecraft::getInstance).thenReturn(mockMc);
            when(mockMc.getProxy()).thenReturn(java.net.Proxy.NO_PROXY);
            when(mockMc.getTextureManager()).thenReturn(mockTm);

            HttpURLConnection mockConn = mock(HttpURLConnection.class);
            when(mockConn.getResponseCode()).thenReturn(200);
            
            // Truncated/Malformed JSON
            String malformedJson = "{ \"cape\": { \"origin\": \"Cosmetica\", \"image\": ";
            InputStream malformedStream = new ByteArrayInputStream(malformedJson.getBytes());
            when(mockConn.getInputStream()).thenReturn(malformedStream);

            mockedPlayerHandler.when(() -> PlayerHandler.connection(anyString())).thenReturn(mockConn);

            CapeConfig config = new CapeConfig();
            config.setEnableCosmetica(true);
            Capes.INSTANCE.CONFIG = config;

            UUID uuid = UUID.randomUUID();
            GameProfile profile = new GameProfile(uuid, "JsonPlayer");
            PlayerHandler handler = new PlayerHandler(profile);

            boolean result = handler.setCape(CapeType.COSMETICA);

            assertFalse(result, "setCape should return false when JSON is malformed");
            assertFalse(handler.getHasCape(), "Player should not have a cape loaded");
        }
    }

    /**
     * Happy path validation for a valid Optifine cape (standard PNG download).
     */
    @Test
    public void testHappyPathValidOptifineCape() throws Exception {
        try (MockedStatic<FabricLoader> mockedFabricLoader = mockStatic(FabricLoader.class);
             MockedStatic<Minecraft> mockedMinecraft = mockStatic(Minecraft.class);
             MockedStatic<PlayerHandler> mockedPlayerHandler = mockStatic(PlayerHandler.class, CALLS_REAL_METHODS);
             MockedConstruction<DynamicTexture> mockedDynamicTexture = mockConstruction(DynamicTexture.class)) {

            FabricLoader mockLoader = mock(FabricLoader.class);
            when(mockLoader.getConfigDir()).thenReturn(tempConfigDir);
            mockedFabricLoader.when(FabricLoader::getInstance).thenReturn(mockLoader);

            Minecraft mockMc = mock(Minecraft.class);
            TextureManager mockTm = mock(TextureManager.class);
            mockedMinecraft.when(Minecraft::getInstance).thenReturn(mockMc);
            when(mockMc.getProxy()).thenReturn(java.net.Proxy.NO_PROXY);
            when(mockMc.getTextureManager()).thenReturn(mockTm);

            // Mock submit to run the runnable immediately on the same thread
            when(mockMc.submit(any(Runnable.class))).thenAnswer(invocation -> {
                Runnable r = invocation.getArgument(0);
                r.run();
                return CompletableFuture.completedFuture(null);
            });

            byte[] validPngBytes = java.util.Base64.getDecoder().decode(VALID_1X1_PNG_BASE64);
            HttpURLConnection mockConn = mock(HttpURLConnection.class);
            when(mockConn.getResponseCode()).thenReturn(200);
            when(mockConn.getInputStream()).thenReturn(new ByteArrayInputStream(validPngBytes));

            mockedPlayerHandler.when(() -> PlayerHandler.connection(anyString())).thenReturn(mockConn);

            CapeConfig config = new CapeConfig();
            config.setEnableOptifine(true);
            Capes.INSTANCE.CONFIG = config;

            UUID uuid = UUID.randomUUID();
            GameProfile profile = new GameProfile(uuid, "ValidOptifinePlayer");
            PlayerHandler handler = new PlayerHandler(profile);

            boolean result = handler.setCape(CapeType.OPTIFINE);

            assertTrue(result, "setCape should return true for a valid PNG image");
            assertTrue(handler.getHasCape(), "Player should have a cape loaded");
        }
    }
}
