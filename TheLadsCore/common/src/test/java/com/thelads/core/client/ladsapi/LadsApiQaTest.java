package com.thelads.core.client.ladsapi;

import com.google.gson.JsonObject;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.SliderOption;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LadsApiQaTest {
    private final List<byte[]> sentPayloads = new ArrayList<>();

    @BeforeEach
    void setUp() {
        sentPayloads.clear();
        LadsApiClient.install(sentPayloads::add, "1.20.1");
        LadsApiClient.reset();
        ServerPolicy.set(null);
        LadsPresence.serverClear();

        Module sc = ModuleManager.getInstance().getModule("Server Control");
        if (sc != null) {
            BoolOption b = (BoolOption) sc.getOption("Let servers change my Lads settings");
            if (b != null) b.set(true);
        }
    }

    @AfterEach
    void tearDown() {
        LadsApiClient.reset();
        ServerPolicy.set(null);
        LadsPresence.serverClear();
        ModuleManager.getInstance().resetToDefaults();
    }

    @Test
    void testProbeWithoutPolicy() {
        JsonObject probe = LadsApiQa.probe("init");
        assertNotNull(probe);
        assertEquals("init", probe.get("step").getAsString());
        assertFalse(probe.get("policyActive").getAsBoolean());
        assertEquals("", probe.get("serverName").getAsString());
        assertEquals(0, probe.get("presenceSize").getAsInt());
        assertEquals(0, probe.get("sentBeforeHello").getAsInt());

        JsonObject modules = probe.getAsJsonObject("modules");
        assertNotNull(modules);
        assertTrue(modules.has("Fullbright"));
        assertTrue(modules.has("XaeroMinimap"));
        assertEquals("NONE", modules.getAsJsonObject("Fullbright").get("policyState").getAsString());

        JsonObject options = probe.getAsJsonObject("options");
        assertNotNull(options);
        assertTrue(options.has("Fullbright.Brightness"));
        JsonObject fb = options.getAsJsonObject("Fullbright.Brightness");
        assertEquals(fb.get("effective").getAsDouble(), fb.get("own").getAsDouble());

        String jsonString = LadsApiQa.probeJson("init");
        assertTrue(jsonString.contains("\"step\":\"init\""));
        assertTrue(jsonString.contains("\"policyActive\":false"));
    }

    @Test
    void testProbeWithPolicy() {
        // Feed hello and policy to LadsApiClient
        String hello = "{\"t\":\"hello\",\"v\":1,\"server\":\"Lads QA Lobby\"}";
        LadsApiClient.receive(hello.getBytes(StandardCharsets.UTF_8));

        String policy = "{\"t\":\"policy\",\"rev\":1,\"notice\":\"Fullbright capped at 10%\","
            + "\"modules\":{\"Fullbright\":\"allow\",\"XaeroMinimap\":\"block\"},"
            + "\"options\":{"
            + "\"Fullbright\":{\"Brightness\":{\"max\":10}},"
            + "\"Crosshair Tweaks\":{\"Shape\":{\"value\":\"Cross\"},\"Gap\":{\"min\":1,\"max\":4},\"Outline Color\":{\"value\":\"#FFFF0000\"}},"
            + "\"1.7 Animations\":{\"1.7 sword block pose\":{\"value\":false}}"
            + "}}";
        LadsApiClient.receive(policy.getBytes(StandardCharsets.UTF_8));

        // Add 2 players to presence
        LadsPresence.serverAdd(List.of(UUID.randomUUID(), UUID.randomUUID()));

        // Set player's own values
        Module crosshair = ModuleManager.getInstance().getModule("Crosshair Tweaks");
        if (crosshair != null && crosshair.getOption("Shape") instanceof DropdownOption shape) {
            shape.setIndex(1); // "Dot"
        }
        if (crosshair != null && crosshair.getOption("Gap") instanceof SliderOption gap) {
            gap.setValue(6.0);
        }
        Module fbMod = ModuleManager.getInstance().getModule("Fullbright");
        if (fbMod != null && fbMod.getOption("Brightness") instanceof SliderOption fbBright) {
            fbBright.setValue(100.0);
        }

        JsonObject probe = LadsApiQa.probe("chk05");
        assertTrue(probe.get("policyActive").getAsBoolean());
        assertEquals("Lads QA Lobby", probe.get("serverName").getAsString());
        assertEquals(2, probe.get("presenceSize").getAsInt());
        assertEquals(0, probe.get("sentBeforeHello").getAsInt());

        JsonObject modules = probe.getAsJsonObject("modules");
        assertEquals("ALLOW", modules.getAsJsonObject("Fullbright").get("policyState").getAsString());
        assertEquals("BLOCK", modules.getAsJsonObject("XaeroMinimap").get("policyState").getAsString());

        JsonObject options = probe.getAsJsonObject("options");
        JsonObject fb = options.getAsJsonObject("Fullbright.Brightness");
        assertEquals(10.0, fb.get("effective").getAsDouble());
        assertEquals(100.0, fb.get("own").getAsDouble());

        JsonObject shapeOpt = options.getAsJsonObject("Crosshair Tweaks.Shape");
        assertEquals("Cross", shapeOpt.get("effective").getAsString());
        assertEquals("Dot", shapeOpt.get("own").getAsString());

        JsonObject gapOpt = options.getAsJsonObject("Crosshair Tweaks.Gap");
        assertEquals(4.0, gapOpt.get("effective").getAsDouble());
        assertEquals(6.0, gapOpt.get("own").getAsDouble());

        JsonObject colorOpt = options.getAsJsonObject("Crosshair Tweaks.Outline Color");
        assertEquals("#FFFF0000", colorOpt.get("effective").getAsString());

        JsonObject animOpt = options.getAsJsonObject("1.7 Animations.1.7 sword block pose");
        assertFalse(animOpt.get("effective").getAsBoolean());
    }
}
