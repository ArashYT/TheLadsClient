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
                "{\"current\":\"26.3\",\"versions\":[\"26.3\",\"1.20.1\",\" 1.8.9 \",\"1.20.1\"]}".getBytes(StandardCharsets.UTF_8));
        assertEquals("26.3", VersionSwitch.current(dir, "1.8.9"));
        assertEquals(List.of("1.20.1", "1.8.9"), VersionSwitch.available(dir, "1.8.9"));
        assertTrue(VersionSwitch.request(dir, "1.8.9"));
        String json = new String(Files.readAllBytes(dir.resolve(VersionSwitch.REQUEST_FILE)), StandardCharsets.UTF_8);
        assertEquals("1.8.9", JsonParser.parseString(json).getAsJsonObject().get("version").getAsString());
        assertEquals(0, Files.list(dir).filter(p -> p.getFileName().toString().endsWith(".tmp")).count());
    }

    @Test
    void zeroSmpOffersEveryLadsClientVersion(@TempDir Path dir) throws Exception {
        // The modpack's launcher folder names what runs "Zero SMP": nothing is left out, 1.20.1 included.
        Files.write(dir.resolve(VersionSwitch.VERSIONS_FILE),
                "{\"current\":\"Zero SMP\",\"versions\":[\"26.3\",\"1.20.1\",\"1.8.9\"]}".getBytes(StandardCharsets.UTF_8));
        assertEquals("Zero SMP", VersionSwitch.current(dir, "1.20.1"));
        assertEquals(List.of("26.3", "1.20.1", "1.8.9"), VersionSwitch.available(dir, "1.20.1"));
    }

    @Test
    void withoutALauncherListTheSupportedVersionsMinusTheRunningOne(@TempDir Path dir) {
        assertEquals("1.20.1", VersionSwitch.current(dir, "1.20.1"));
        assertEquals(List.of("26.3", "1.8.9"), VersionSwitch.available(dir, "1.20.1"));
        assertEquals(List.of("1.20.1", "1.8.9"), VersionSwitch.available(dir, "26.3"));
        assertEquals(List.of("26.3", "1.20.1"), VersionSwitch.available(dir, "1.8.9"));
        assertFalse(VersionSwitch.available(dir, "1.20.1").contains("26.2")); // 26.2 is dropped
        assertFalse(VersionSwitch.request(dir, " "));
    }

    @Test
    void anUnreadableListFallsBackToo(@TempDir Path dir) throws Exception {
        Files.write(dir.resolve(VersionSwitch.VERSIONS_FILE), "{\"versions\":".getBytes(StandardCharsets.UTF_8));
        assertEquals(List.of("26.3", "1.8.9"), VersionSwitch.available(dir, "1.20.1"));
    }
}
