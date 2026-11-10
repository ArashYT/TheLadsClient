package com.thelads.core.client;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LauncherUpdateTest {
    @TempDir Path dir;

    private void notice(String json) throws Exception {
        Files.write(dir.resolve(LauncherUpdate.NOTICE_FILE), json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void noNoticeMeansNoUpdate() {
        assertNull(LauncherUpdate.available(dir, pid -> true));
    }

    @Test
    void aNoticeFromARunningLauncherIsAnUpdate() throws Exception {
        notice("{\"version\":\"1.13.9\",\"launcher\":4321}");
        assertEquals("1.13.9", LauncherUpdate.available(dir, pid -> pid == 4321));
    }

    @Test
    void aNoticeWhoseLauncherIsGoneIsNoUpdate() throws Exception {
        notice("{\"version\":\"1.13.9\",\"launcher\":4321}");
        assertNull(LauncherUpdate.available(dir, pid -> false));
    }

    @Test
    void aBrokenNoticeIsNoUpdate() throws Exception {
        for (String json : new String[] { "", "not json", "[]", "{}", "{\"version\":\"1.13.9\"}", "{\"launcher\":4321}",
            "{\"version\":\"\",\"launcher\":4321}", "{\"version\":\"<b>new</b>\",\"launcher\":4321}", "{\"version\":\"1.13.9\",\"launcher\":0}",
            "{\"version\":\"1.13.9\",\"launcher\":\"x\"}", "{\"version\":null,\"launcher\":4321}" }) {
            notice(json);
            assertNull(LauncherUpdate.available(dir, pid -> true), json);
        }
    }

    @Test
    void theRequestIsWrittenForTheLauncher() throws Exception {
        assertTrue(LauncherUpdate.request(dir));
        assertTrue(JsonParser.parseString(new String(Files.readAllBytes(dir.resolve(LauncherUpdate.REQUEST_FILE)), StandardCharsets.UTF_8))
            .getAsJsonObject().has("requested"));
    }
}
