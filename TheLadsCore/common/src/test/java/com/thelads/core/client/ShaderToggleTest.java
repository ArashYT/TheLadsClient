package com.thelads.core.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.thelads.core.client.ShaderToggle.Event.*;
import static org.junit.jupiter.api.Assertions.*;

class ShaderToggleTest {
    private static JsonObject read(Path file) throws Exception {
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Test
    void readsTheLaunchersSuspendedMods(@TempDir Path dir) throws Exception {
        assertFalse(ShaderToggle.launcherStarted(dir));
        assertFalse(ShaderToggle.irisSuspended(dir));
        Files.writeString(dir.resolve(ShaderToggle.STATE_FILE), "{\"Vulkan\":true,\"SuspendedMods\":[\"iris\",\"oculus-addon\"]}");
        assertTrue(ShaderToggle.launcherStarted(dir));
        assertTrue(ShaderToggle.irisSuspended(dir));
        Files.writeString(dir.resolve(ShaderToggle.STATE_FILE), "{\"Vulkan\":false,\"SuspendedMods\":[]}");
        assertFalse(ShaderToggle.irisSuspended(dir));
        Files.writeString(dir.resolve(ShaderToggle.STATE_FILE), "not json");
        assertFalse(ShaderToggle.irisSuspended(dir));
    }

    @Test
    void writesTheChoiceAndRelaunchFiles(@TempDir Path dir) throws Exception {
        assertTrue(ShaderToggle.choose(dir, false));
        assertEquals("OpenGL", read(dir.resolve(ShaderToggle.CHOICE_FILE)).get("renderer").getAsString());
        assertTrue(ShaderToggle.choose(dir, true));
        assertEquals("Vulkan", read(dir.resolve(ShaderToggle.CHOICE_FILE)).get("renderer").getAsString());

        assertTrue(ShaderToggle.requestRelaunch(dir, true, "New World", null));
        JsonObject world = read(dir.resolve(ShaderToggle.RELAUNCH_FILE));
        assertEquals("shaders-on", world.get("reason").getAsString());
        assertEquals("New World", world.get("world").getAsString());
        assertTrue(world.get("server").isJsonNull());

        assertTrue(ShaderToggle.requestRelaunch(dir, false, null, "play.example.net:25570"));
        JsonObject server = read(dir.resolve(ShaderToggle.RELAUNCH_FILE));
        assertEquals("shaders-off", server.get("reason").getAsString());
        assertTrue(server.get("world").isJsonNull());
        assertEquals("play.example.net:25570", server.get("server").getAsString());

        assertTrue(ShaderToggle.requestRelaunch(dir, false, null, null));
        JsonObject title = read(dir.resolve(ShaderToggle.RELAUNCH_FILE));
        assertTrue(title.has("world") && title.get("world").isJsonNull() && title.get("server").isJsonNull());
        try (var files = Files.list(dir)) { assertEquals(2, files.count(), "no temp files left behind"); }
    }

    @Test
    void warnsOncePerToggleOff() {
        ShaderToggle.Watcher w = new ShaderToggle.Watcher();
        assertEquals(NONE, w.update(false, false), "launched without a pack: nothing yet");
        assertEquals(NONE, w.update(true, false));
        assertEquals(OFF, w.update(false, false), "pack screen closed with no pack");
        assertEquals(NONE, w.update(true, false));
        assertEquals(NONE, w.update(false, false), "no nagging after the warning");
        assertEquals(ON, w.update(true, true), "a pack again after the warning");
        assertEquals(NONE, w.update(false, true));
        assertEquals(NONE, w.update(true, false), "turned off inside the pack screen: wait for it to close");
        assertEquals(OFF, w.update(false, false));
        assertEquals(ON, w.update(false, true));
        assertEquals(OFF, w.update(false, false), "toggled off in game (keybind)");
    }

    @Test
    void staysQuietWhileShadersRun() {
        ShaderToggle.Watcher w = new ShaderToggle.Watcher();
        assertEquals(NONE, w.update(false, true));
        assertEquals(NONE, w.update(true, true));
        assertEquals(NONE, w.update(false, true), "closed with a pack in use");
    }
}
