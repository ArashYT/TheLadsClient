package com.thelads.core;

import com.google.gson.JsonParser;
import com.thelads.core.client.VersionSwitch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VersionSwitchTest {
    @Test
    void listsOtherVersionsAndWritesRequest(@TempDir Path dir) throws Exception {
        Files.write(dir.resolve(VersionSwitch.VERSIONS_FILE),
                "{\"current\":\"26.3\",\"versions\":[\"26.3\",\"26.2\",\" 1.8.9 \",\"26.2\"]}".getBytes(StandardCharsets.UTF_8));
        assertEquals("26.3", VersionSwitch.current(dir));
        assertEquals(List.of("26.2", "1.8.9"), VersionSwitch.available(dir));
        assertTrue(VersionSwitch.request(dir, "1.8.9"));
        String json = new String(Files.readAllBytes(dir.resolve(VersionSwitch.REQUEST_FILE)), StandardCharsets.UTF_8);
        assertEquals("1.8.9", JsonParser.parseString(json).getAsJsonObject().get("version").getAsString());
        assertEquals(0, Files.list(dir).filter(p -> p.getFileName().toString().endsWith(".tmp")).count());
    }

    @Test
    void missingFileMeansNothingToSwitchTo(@TempDir Path dir) {
        assertEquals("", VersionSwitch.current(dir));
        assertTrue(VersionSwitch.available(dir).isEmpty());
        assertFalse(VersionSwitch.request(dir, " "));
    }
}
