package com.thelads.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 1.9.2: 3D Skin Layers LOD distance defaults to 8 blocks; existing configs with mod default 14 migrate to 8 once;
 * custom player choices (!= 14) are strictly preserved; fastRender is not touched.
 */
class SkinLayersLodConfigTest {
    @TempDir Path dir;

    @AfterEach void cleanup() {
        SkinLayersLodMigration.setTestGameDir(null);
        ConfigManager.setTestConfigFile(null);
    }

    private Path skinlayersFile() {
        return dir.resolve("config").resolve("skinlayers.json");
    }

    private JsonObject readSkinlayers() throws IOException {
        return JsonParser.parseString(Files.readString(skinlayersFile(), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Test void freshInstallCreatesSkinlayersJsonWithLod8() throws IOException {
        assertFalse(Files.exists(skinlayersFile()));
        assertTrue(SkinLayersLodMigration.migrate(dir));
        assertTrue(Files.exists(skinlayersFile()));
        JsonObject json = readSkinlayers();
        assertEquals(8, json.get("renderDistanceLOD").getAsInt());
    }

    @Test void existingDefault14MigratesTo8PreservingOtherKeys() throws IOException {
        Path file = skinlayersFile();
        Files.createDirectories(file.getParent());
        String initial = "{\n  \"enableHat\": true,\n  \"renderDistanceLOD\": 14,\n  \"baseVoxelSize\": 1.15,\n  \"fastRender\": true\n}";
        Files.writeString(file, initial, StandardCharsets.UTF_8);

        assertTrue(SkinLayersLodMigration.migrate(dir));

        JsonObject json = readSkinlayers();
        assertEquals(8, json.get("renderDistanceLOD").getAsInt());
        assertTrue(json.get("enableHat").getAsBoolean());
        assertEquals(1.15, json.get("baseVoxelSize").getAsDouble(), 0.001);
        assertTrue(json.get("fastRender").getAsBoolean());
    }

    @Test void customLodPreserved() throws IOException {
        Path file = skinlayersFile();
        Files.createDirectories(file.getParent());
        String initial = "{\n  \"enableHat\": true,\n  \"renderDistanceLOD\": 12,\n  \"fastRender\": false\n}";
        Files.writeString(file, initial, StandardCharsets.UTF_8);

        assertFalse(SkinLayersLodMigration.migrate(dir));

        JsonObject json = readSkinlayers();
        assertEquals(12, json.get("renderDistanceLOD").getAsInt());
        assertFalse(json.get("fastRender").getAsBoolean());
    }

    @Test void migrationFlagInLadsConfigPreventsSecondMigration() throws IOException {
        SkinLayersLodMigration.setTestGameDir(dir);
        Path file = skinlayersFile();
        Files.createDirectories(file.getParent());
        Files.writeString(file, "{\"renderDistanceLOD\": 14}", StandardCharsets.UTF_8);

        JsonObject lads = new JsonObject();
        lads.addProperty(SkinLayersLodMigration.FLAG, true);
        ConfigManager.applyJson(lads);

        JsonObject json = readSkinlayers();
        assertEquals(14, json.get("renderDistanceLOD").getAsInt(), "a deliberate 14 chosen after migration is preserved");
    }

    @Test void missingFlagInLadsConfigTriggersMigration() throws IOException {
        SkinLayersLodMigration.setTestGameDir(dir);
        Path file = skinlayersFile();
        Files.createDirectories(file.getParent());
        Files.writeString(file, "{\"renderDistanceLOD\": 14}", StandardCharsets.UTF_8);

        JsonObject lads = new JsonObject();
        ConfigManager.applyJson(lads);

        JsonObject json = readSkinlayers();
        assertEquals(8, json.get("renderDistanceLOD").getAsInt());
    }

    @Test void configManagerToJsonIncludesFlag() {
        JsonObject saved = ConfigManager.toJson();
        assertTrue(saved.has(SkinLayersLodMigration.FLAG));
        assertTrue(saved.get(SkinLayersLodMigration.FLAG).getAsBoolean());
    }
}
