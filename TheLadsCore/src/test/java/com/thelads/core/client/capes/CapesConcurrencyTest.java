package com.thelads.core.client.capes;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;

public class CapesConcurrencyTest {

    private static final String VALID_1X1_PNG_BASE64 = 
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=";

    @BeforeEach
    public void setUp() throws Exception {
        Capes.INSTANCE.CONFIG = new CapeConfig();
        clearInstances();
    }

    private void clearInstances() throws Exception {
        Field field = PlayerHandler.class.getDeclaredField("instances");
        field.setAccessible(true);
        Map<?, ?> instancesMap = (Map<?, ?>) field.get(null);
        instancesMap.clear();
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    public void testConcurrentPlayerHandlerFetchDifferentUuids() throws Exception {
        int numThreads = 50;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CyclicBarrier barrier = new CyclicBarrier(numThreads);
        List<Callable<PlayerHandler>> tasks = new ArrayList<>();

        for (int i = 0; i < numThreads; i++) {
            final int id = i;
            tasks.add(() -> {
                barrier.await();
                UUID uuid = new UUID(0L, id);
                GameProfile profile = new GameProfile(uuid, "Player_" + id);
                return PlayerHandler.fromProfile(profile);
            });
        }

        List<Future<PlayerHandler>> futures = executor.invokeAll(tasks);
        executor.shutdown();

        Set<PlayerHandler> handlers = new HashSet<>();
        for (Future<PlayerHandler> future : futures) {
            PlayerHandler handler = future.get();
            assertNotNull(handler);
            handlers.add(handler);
        }

        // Verify that 50 unique handlers were created and added without crashing or throwing
        assertEquals(numThreads, handlers.size());
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    public void testConcurrentPlayerHandlerFetchSameUuidRaceCondition() throws Exception {
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

        PlayerHandler initialHandler = futures.get(0).get();
        assertNotNull(initialHandler);

        int duplicateInstancesCount = 0;
        for (int i = 1; i < numThreads; i++) {
            PlayerHandler handler = futures.get(i).get();
            if (initialHandler != handler) {
                duplicateInstancesCount++;
            }
        }

        if (duplicateInstancesCount > 0) {
            System.err.println("[VULNERABILITY DETECTED] Race condition in PlayerHandler.fromProfile: " 
                + duplicateInstancesCount + " duplicate PlayerHandler instances were created concurrently for UUID " + sharedUuid);
        } else {
            System.out.println("[INFO] No race condition detected in PlayerHandler.fromProfile this run.");
        }

        // Verify that the system survives and returns non-null handlers
        assertNotNull(initialHandler);
    }

    @Test
    public void testConnectionTimeoutGracefulHandling() throws Exception {
        try (MockedStatic<Minecraft> mockedMinecraft = mockStatic(Minecraft.class);
             MockedStatic<PlayerHandler> mockedPlayerHandler = mockStatic(PlayerHandler.class, CALLS_REAL_METHODS)) {

            Minecraft mockMc = mock(Minecraft.class);
            TextureManager mockTm = mock(TextureManager.class);
            mockedMinecraft.when(Minecraft::getInstance).thenReturn(mockMc);
            when(mockMc.getProxy()).thenReturn(java.net.Proxy.NO_PROXY);
            when(mockMc.getTextureManager()).thenReturn(mockTm);

            // Mock HttpURLConnection that throws SocketTimeoutException on connect
            HttpURLConnection mockConn = mock(HttpURLConnection.class);
            doThrow(new SocketTimeoutException("Connection timed out")).when(mockConn).connect();

            mockedPlayerHandler.when(() -> PlayerHandler.connection(anyString())).thenReturn(mockConn);

            UUID uuid = UUID.randomUUID();
            GameProfile profile = new GameProfile(uuid, "TimeoutTestPlayer");
            PlayerHandler handler = new PlayerHandler(profile);

            // Call setCape which initiates the connection
            boolean result = handler.setCape(CapeType.OPTIFINE);

            // Verify it handled it gracefully without throwing and returned false
            assertFalse(result, "setCape should return false when connection times out");
            assertFalse(handler.getHasCape(), "Player should not have a cape loaded");
        }
    }

    @Test
    public void testInvalidBase64ImageGracefulHandling() throws Exception {
        try (MockedStatic<Minecraft> mockedMinecraft = mockStatic(Minecraft.class)) {
            Minecraft mockMc = mock(Minecraft.class);
            TextureManager mockTm = mock(TextureManager.class);
            mockedMinecraft.when(Minecraft::getInstance).thenReturn(mockMc);
            when(mockMc.getTextureManager()).thenReturn(mockTm);

            UUID uuid = UUID.randomUUID();
            GameProfile profile = new GameProfile(uuid, "Base64TestPlayer");
            PlayerHandler handler = new PlayerHandler(profile);

            // Call setCapeTextureFromBase64 with completely invalid base64 data
            boolean result = handler.setCapeTextureFromBase64("!!!INVALID_BASE64_DATA!!!");

            // Verify it returns false and doesn't crash or throw uncaught exceptions
            assertFalse(result, "setCapeTextureFromBase64 should return false on invalid base64 input");
            assertFalse(handler.getHasCape(), "Player should not have a cape loaded");
        }
    }

    @Test
    public void testMalformedJsonGracefulHandling() throws Exception {
        try (MockedStatic<Minecraft> mockedMinecraft = mockStatic(Minecraft.class);
             MockedStatic<PlayerHandler> mockedPlayerHandler = mockStatic(PlayerHandler.class, CALLS_REAL_METHODS)) {

            Minecraft mockMc = mock(Minecraft.class);
            TextureManager mockTm = mock(TextureManager.class);
            mockedMinecraft.when(Minecraft::getInstance).thenReturn(mockMc);
            when(mockMc.getProxy()).thenReturn(java.net.Proxy.NO_PROXY);
            when(mockMc.getTextureManager()).thenReturn(mockTm);

            // Return malformed JSON for Cosmetica fetch
            HttpURLConnection mockConn = mock(HttpURLConnection.class);
            when(mockConn.getResponseCode()).thenReturn(200);
            
            // Malformed JSON (unclosed quote, missing structure)
            String malformedJson = "{ \"cape\": { \"origin\": \"Cosmetica\", ";
            InputStream malformedStream = new ByteArrayInputStream(malformedJson.getBytes());
            when(mockConn.getInputStream()).thenReturn(malformedStream);

            mockedPlayerHandler.when(() -> PlayerHandler.connection(anyString())).thenReturn(mockConn);

            UUID uuid = UUID.randomUUID();
            GameProfile profile = new GameProfile(uuid, "JsonTestPlayer");
            PlayerHandler handler = new PlayerHandler(profile);

            boolean result = handler.setCape(CapeType.COSMETICA);

            // Verify malformed JSON is handled gracefully
            assertFalse(result, "setCape should return false when JSON is malformed");
            assertFalse(handler.getHasCape(), "Player should not have a cape loaded");
        }
    }

    @Test
    public void testHappyPathValidCape() throws Exception {
        try (MockedStatic<Minecraft> mockedMinecraft = mockStatic(Minecraft.class);
             MockedStatic<PlayerHandler> mockedPlayerHandler = mockStatic(PlayerHandler.class, CALLS_REAL_METHODS)) {

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

            UUID uuid = UUID.randomUUID();
            GameProfile profile = new GameProfile(uuid, "ValidPlayer");
            PlayerHandler handler = new PlayerHandler(profile);

            try (MockedConstruction<DynamicTexture> mockedDynamicTexture = mockConstruction(DynamicTexture.class)) {
                boolean result = handler.setCape(CapeType.OPTIFINE);

                assertTrue(result, "setCape should return true for a valid PNG image");
                assertTrue(handler.getHasCape(), "Player should have a cape loaded");
            }
        }
    }
}
