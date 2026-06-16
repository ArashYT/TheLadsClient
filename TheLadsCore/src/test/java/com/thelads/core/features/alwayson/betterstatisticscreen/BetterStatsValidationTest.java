package com.thelads.core.features.alwayson.betterstatisticscreen;

import org.junit.jupiter.api.Test;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

public class BetterStatsValidationTest {

    @Test
    public void testMixinConfigurationAndRegistrations() throws Exception {
        // 1. Load the mixin config file
        InputStream mixinStream = getClass().getResourceAsStream("/theladscore.mixins.json");
        assertNotNull(mixinStream, "theladscore.mixins.json not found in resources!");

        StringBuilder jsonContent = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(mixinStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                jsonContent.append(line).append("\n");
            }
        }

        String json = jsonContent.toString();

        // 2. Verify registrations are in the file
        assertTrue(json.contains("alwayson.betterstatisticscreen.AccessorStatsCounter"),
                "theladscore.mixins.json is missing registration for AccessorStatsCounter");
        assertTrue(json.contains("alwayson.betterstatisticscreen.PauseScreenBetterStatsMixin"),
                "theladscore.mixins.json is missing registration for PauseScreenBetterStatsMixin");

        // 3. Load mixin classes and verify they exist on classpath
        Class<?> accessorClass = Class.forName("com.thelads.core.mixin.alwayson.betterstatisticscreen.AccessorStatsCounter");
        Class<?> pauseScreenMixinClass = Class.forName("com.thelads.core.mixin.alwayson.betterstatisticscreen.PauseScreenBetterStatsMixin");

        assertNotNull(accessorClass);
        assertNotNull(pauseScreenMixinClass);

        // 4. Verify mixin targets by reading the source files (since @Mixin annotation has CLASS retention and is not visible via reflection at runtime)
        File accessorSource = new File("src/main/java/com/thelads/core/mixin/alwayson/betterstatisticscreen/AccessorStatsCounter.java");
        assertTrue(accessorSource.exists(), "AccessorStatsCounter.java source file missing");
        String accessorContent = Files.readString(accessorSource.toPath(), StandardCharsets.UTF_8);
        assertTrue(accessorContent.contains("@Mixin") && accessorContent.contains("StatsCounter.class"),
                "AccessorStatsCounter source is missing @Mixin annotation targeting StatsCounter");

        File pauseScreenMixinSource = new File("src/main/java/com/thelads/core/mixin/alwayson/betterstatisticscreen/PauseScreenBetterStatsMixin.java");
        assertTrue(pauseScreenMixinSource.exists(), "PauseScreenBetterStatsMixin.java source file missing");
        String pauseScreenMixinContent = Files.readString(pauseScreenMixinSource.toPath(), StandardCharsets.UTF_8);
        assertTrue(pauseScreenMixinContent.contains("@Mixin") && pauseScreenMixinContent.contains("PauseScreen.class"),
                "PauseScreenBetterStatsMixin source is missing @Mixin annotation targeting PauseScreen");
    }

    @Test
    public void testResourceAssetsExistence() throws Exception {
        // 1. Verify basic resources
        assertNotNull(getClass().getResource("/assets/betterstats/icon.png"), "betterstats icon.png is missing!");
        assertNotNull(getClass().getResource("/assets/betterstats/lang/en_us.json"), "betterstats en_us.json is missing!");
        assertNotNull(getClass().getResource("/assets/betterstats/lang/zh_cn.json"), "betterstats zh_cn.json is missing!");
        assertNotNull(getClass().getResource("/betterstats.properties"), "betterstats.properties is missing from resources!");
        assertNotNull(getClass().getResource("/betterstats.credits.json"), "betterstats.credits.json is missing from resources!");

        // 2. Verify all textures from BTextures
        Class<?> bTexturesClass = Class.forName("com.thelads.core.features.alwayson.betterstatisticscreen.resource.BTextures");
        for (Method method : bTexturesClass.getDeclaredMethods()) {
            if (method.getParameterCount() == 0 && method.getReturnType().getSimpleName().equals("Identifier")) {
                Object identifier = method.invoke(null);
                String path = getPath(identifier);
                String namespace = getNamespace(identifier);
                assertEquals("betterstats", namespace);

                // For BTextures, the returned path is the exact path under betterstats assets, e.g. "icon.png" or "textures/gui/images/..."
                String resourcePath = "/assets/betterstats/" + path;
                URL resourceUrl = getClass().getResource(resourcePath);
                assertNotNull(resourceUrl, "Texture file " + resourcePath + " referenced in BTextures is missing!");
            }
        }

        // 3. Verify all sprites from BSprites
        Class<?> bSpritesClass = Class.forName("com.thelads.core.features.alwayson.betterstatisticscreen.resource.BSprites");
        for (Method method : bSpritesClass.getDeclaredMethods()) {
            if (method.getParameterCount() == 0 && method.getReturnType().getSimpleName().equals("Identifier")) {
                Object identifier = method.invoke(null);
                String path = getPath(identifier);
                String namespace = getNamespace(identifier);
                assertEquals("betterstats", namespace);

                // For BSprites, the returned path is a sprite path, e.g. "editor/menubar/background"
                // It maps to "/assets/betterstats/textures/gui/sprites/editor/menubar/background.png"
                String resourcePath = "/assets/betterstats/textures/gui/sprites/" + path + ".png";
                URL resourceUrl = getClass().getResource(resourcePath);
                assertNotNull(resourceUrl, "Sprite file " + resourcePath + " referenced in BSprites is missing!");
            }
        }
    }

    private String getPath(Object identifier) throws Exception {
        try {
            return (String) identifier.getClass().getMethod("getPath").invoke(identifier);
        } catch (NoSuchMethodException e) {
            return (String) identifier.getClass().getMethod("path").invoke(identifier);
        }
    }

    private String getNamespace(Object identifier) throws Exception {
        try {
            return (String) identifier.getClass().getMethod("getNamespace").invoke(identifier);
        } catch (NoSuchMethodException e) {
            return (String) identifier.getClass().getMethod("namespace").invoke(identifier);
        }
    }
}
