package com.thelads.core.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class NewEngineIntegrationsTest {
    @TempDir Path directory;
    public enum Layout { COMPACT, FULL }
    public static class Config {
        public boolean enabled() { return true; }
        public Layout layout() { return Layout.FULL; }
    }
    @Test void verifiesEngineOwnedSaveWithoutRewritingUnrelatedPreferences() throws Exception {
        Path path = directory.resolve("config.json");
        String contents = "{\"enabled\":true,\"layout\":\"FULL\",\"defaultService\":\"custom\",\"nested\":{\"keep\":42}}";
        NewEngineIntegrations.saveAndVerify(new Config(), () -> Files.writeString(path, contents), path,
            Map.of("enabled", Config.class.getMethod("enabled"), "layout", Config.class.getMethod("layout")));
        assertEquals(contents, Files.readString(path));
    }
    @Test void detectsSwallowedSaveFailureAndWrongJsonType() throws Exception {
        Path path = directory.resolve("config.json");
        var fields = Map.of("enabled", Config.class.getMethod("enabled"));
        assertThrows(IOException.class, () -> NewEngineIntegrations.saveAndVerify(new Config(), () -> {}, path, fields));
        Files.writeString(path, "{\"enabled\":false}");
        assertThrows(IOException.class, () -> NewEngineIntegrations.saveAndVerify(new Config(), () -> {}, path, fields));
        Files.writeString(path, "{\"enabled\":\"true\"}");
        assertThrows(IOException.class, () -> NewEngineIntegrations.saveAndVerify(new Config(), () -> {}, path, fields));
    }
}
