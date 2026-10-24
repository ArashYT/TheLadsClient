package com.thelads.core.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.Deflater;

import static org.junit.jupiter.api.Assertions.*;

class ShareCodeTest {

    @TempDir
    Path tempDir;

    private File testConfigFile;
    private File testProfileFile;

    @BeforeEach
    void setUp() {
        testConfigFile = tempDir.resolve("thelads_config.json").toFile();
        testProfileFile = tempDir.resolve("thelads_profiles.json").toFile();
        ConfigManager.setTestConfigFile(testConfigFile);
        ProfileManager.get().setTestFile(testProfileFile);
    }

    @AfterEach
    void tearDown() {
        ConfigManager.setTestConfigFile(null);
        ProfileManager.get().setTestFile(null);
    }

    @Test
    void encodeDecodeRoundTrip() {
        JsonObject payload = new JsonObject();
        payload.addProperty("v", 1);
        JsonObject modules = new JsonObject();
        JsonObject cps = new JsonObject();
        cps.addProperty("enabled", true);
        cps.addProperty("useGlobalColor", false);
        cps.addProperty("customColor", 0xFF00FF00);
        JsonObject opts = new JsonObject();
        opts.addProperty("Size", 100);
        cps.add("options", opts);
        modules.add("CPS", cps);
        payload.add("modules", modules);

        JsonObject hud = new JsonObject();
        hud.addProperty("hudScale", 2);
        JsonObject positions = new JsonObject();
        JsonArray pos = new JsonArray();
        pos.add(10); pos.add(20);
        positions.add("CPS", pos);
        hud.add("positions", positions);
        payload.add("hud", hud);

        String code = ShareCode.encode(payload);
        assertTrue(code.startsWith(ShareCode.PREFIX));

        JsonObject decoded = ShareCode.decode(code);
        assertEquals(payload, decoded);
    }

    @Test
    void decodeToleratesWhitespaceAndNewlines() {
        JsonObject payload = new JsonObject();
        payload.addProperty("v", 1);
        String code = ShareCode.encode(payload);

        String padded = "\r\n  " + code.substring(0, 10) + " \n " + code.substring(10) + "\t\r\n ";
        JsonObject decoded = ShareCode.decode(padded);
        assertEquals(payload, decoded);
    }

    @Test
    void rejectsNullOrEmptyOrInvalidPrefix() {
        assertThrows(IllegalArgumentException.class, () -> ShareCode.decode(null));
        assertThrows(IllegalArgumentException.class, () -> ShareCode.decode(""));
        assertThrows(IllegalArgumentException.class, () -> ShareCode.decode("   \r\n\t  "));
        assertThrows(IllegalArgumentException.class, () -> ShareCode.decode("INVALID:abcde"));
        assertThrows(IllegalArgumentException.class, () -> ShareCode.decode("LADS2:abcde"));
        assertThrows(IllegalArgumentException.class, () -> ShareCode.decode("LADS1:"));
    }

    @Test
    void rejectsInvalidBase64OrCorruptedDeflate() {
        assertThrows(IllegalArgumentException.class, () -> ShareCode.decode("LADS1:???not-base64!!!"));

        byte[] junk = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
        String junkCode = "LADS1:" + Base64.getUrlEncoder().withoutPadding().encodeToString(junk);
        assertThrows(IllegalArgumentException.class, () -> ShareCode.decode(junkCode));
    }

    @Test
    void rejectsOversizedCodeString() {
        StringBuilder sb = new StringBuilder();
        sb.append(ShareCode.PREFIX);
        sb.append("A".repeat(17 * 1024)); // > 16 KB
        assertThrows(IllegalArgumentException.class, () -> ShareCode.decode(sb.toString()));
    }

    @Test
    void rejectsZipBombExceedingDecompressedLimit() {
        // Create 300 KB of repetitive JSON data (> 256 KB limit)
        byte[] largeData = ("{\"test\":\"" + "A".repeat(300 * 1024) + "\"}").getBytes(StandardCharsets.UTF_8);
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        deflater.setInput(largeData);
        deflater.finish();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        while (!deflater.finished()) {
            int count = deflater.deflate(buf);
            baos.write(buf, 0, count);
        }
        deflater.end();
        byte[] compressed = baos.toByteArray();
        String bombCode = ShareCode.PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(compressed);

        // Code size is small (< 2 KB), but uncompressed is 300 KB -> must be rejected
        assertTrue(bombCode.length() < 5000);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> ShareCode.decode(bombCode));
        assertTrue(ex.getMessage().contains("exceeds maximum allowed size"));
    }

    @Test
    void rejectsNonObjectJsonAndTrailingGarbage() {
        // Non-object: JSON array
        byte[] arrData = "[1, 2, 3]".getBytes(StandardCharsets.UTF_8);
        Deflater d1 = new Deflater();
        d1.setInput(arrData); d1.finish();
        ByteArrayOutputStream b1 = new ByteArrayOutputStream();
        byte[] buf = new byte[256];
        while (!d1.finished()) { b1.write(buf, 0, d1.deflate(buf)); }
        d1.end();
        String arrCode = ShareCode.PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(b1.toByteArray());
        IllegalArgumentException e1 = assertThrows(IllegalArgumentException.class, () -> ShareCode.decode(arrCode));
        assertTrue(e1.getMessage().contains("JSON object"));

        // Trailing characters after valid JSON
        byte[] trailData = "{\"v\":1} extra".getBytes(StandardCharsets.UTF_8);
        Deflater d2 = new Deflater();
        d2.setInput(trailData); d2.finish();
        ByteArrayOutputStream b2 = new ByteArrayOutputStream();
        while (!d2.finished()) { b2.write(buf, 0, d2.deflate(buf)); }
        d2.end();
        String trailCode = ShareCode.PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(b2.toByteArray());
        IllegalArgumentException e2 = assertThrows(IllegalArgumentException.class, () -> ShareCode.decode(trailCode));
        assertTrue(e2.getMessage().contains("Trailing characters") || e2.getMessage().contains("Malformed JSON"));
    }

    @Test
    void excludedKeysNeverExported() {
        JsonObject full = new JsonObject();
        JsonObject mods = new JsonObject();

        // DiscordRPC (excluded module)
        JsonObject discord = new JsonObject();
        discord.addProperty("enabled", true);
        mods.add("DiscordRPC", discord);

        // Crosshair Tweaks with Background Image
        JsonObject crosshair = new JsonObject();
        crosshair.addProperty("enabled", true);
        JsonObject crossOpts = new JsonObject();
        crossOpts.addProperty("Background Image", "C:/secret/path.png");
        crossOpts.addProperty("Scale", 1.5);
        crosshair.add("options", crossOpts);
        mods.add("Crosshair Tweaks", crosshair);

        // Flashback with Replay folder
        JsonObject flashback = new JsonObject();
        flashback.addProperty("enabled", true);
        JsonObject flashOpts = new JsonObject();
        flashOpts.addProperty("Replay folder", "D:/replays");
        flashback.add("options", flashOpts);
        mods.add("Flashback", flashback);

        // ToggleNametags with display name and nicknames
        JsonObject nametags = new JsonObject();
        nametags.addProperty("enabled", true);
        JsonObject nameOpts = new JsonObject();
        nameOpts.addProperty("Your Display Name", "PrivateName");
        nameOpts.addProperty("Nicknames", "Friend=Bob");
        nametags.add("options", nameOpts);
        mods.add("ToggleNametags", nametags);

        // Unmanaged module
        JsonObject unmanaged = new JsonObject();
        unmanaged.addProperty("enabled", true);
        mods.add("custom_hack_module", unmanaged);

        full.add("modules", mods);
        JsonObject hud = new JsonObject();
        hud.addProperty("hudScale", 1);
        full.add("hud", hud);

        JsonObject payload = ShareCode.createSharePayload(full);

        JsonObject outMods = payload.getAsJsonObject("modules");
        assertFalse(outMods.has("DiscordRPC"), "DiscordRPC module must be excluded");
        assertFalse(outMods.has("custom_hack_module"), "Unmanaged / unknown modules must be excluded");

        if (outMods.has("Crosshair Tweaks")) {
            JsonObject co = outMods.getAsJsonObject("Crosshair Tweaks").getAsJsonObject("options");
            assertFalse(co.has("Background Image"), "Background Image must be excluded");
            assertTrue(co.has("Scale"), "Safe options like Scale should remain");
        }

        if (outMods.has("Flashback")) {
            JsonObject fo = outMods.getAsJsonObject("Flashback").getAsJsonObject("options");
            assertFalse(fo.has("Replay folder"), "Replay folder must be excluded");
        }

        if (outMods.has("ToggleNametags")) {
            JsonObject no = outMods.getAsJsonObject("ToggleNametags").getAsJsonObject("options");
            assertFalse(no.has("Your Display Name"), "Your Display Name must be excluded");
            assertFalse(no.has("Nicknames"), "Nicknames must be excluded");
        }
    }

    @Test
    void excludedKeysIgnoredIfInjectedIntoImportPayload() {
        JsonObject injectedPayload = new JsonObject();
        injectedPayload.addProperty("v", 1);

        JsonObject mods = new JsonObject();
        // Injected unknown module
        JsonObject unknown = new JsonObject();
        unknown.addProperty("enabled", true);
        mods.add("TrojanModule", unknown);

        // Injected excluded options into a known module (CPS)
        JsonObject cps = new JsonObject();
        cps.addProperty("enabled", true);
        JsonObject cpsOpts = new JsonObject();
        cpsOpts.addProperty("Your Display Name", "Evil");
        cpsOpts.addProperty("Background Image", "C:/Windows/System32");
        cpsOpts.addProperty("Toggle key", 42);
        cpsOpts.addProperty("Size", 120);
        cps.add("options", cpsOpts);
        mods.add("CPS", cps);

        injectedPayload.add("modules", mods);

        JsonObject prepared = ShareCode.prepareImportJson(injectedPayload, ShareCode.ImportMode.BOTH);
        JsonObject prepMods = prepared.getAsJsonObject("modules");

        assertFalse(prepMods.has("TrojanModule"), "Unknown module must be ignored");
        assertTrue(prepMods.has("CPS"), "Known module must be kept");

        JsonObject prepCpsOpts = prepMods.getAsJsonObject("CPS").getAsJsonObject("options");
        assertFalse(prepCpsOpts.has("Your Display Name"));
        assertFalse(prepCpsOpts.has("Background Image"));
        assertFalse(prepCpsOpts.has("Toggle key"));
        assertTrue(prepCpsOpts.has("Size"));
        assertEquals(120, prepCpsOpts.get("Size").getAsInt());
    }

    @Test
    void importModesDifferentiateModsSettingsBoth() {
        JsonObject payload = new JsonObject();
        payload.addProperty("v", 1);
        JsonObject mods = new JsonObject();
        JsonObject cps = new JsonObject();
        cps.addProperty("enabled", true);
        cps.addProperty("useGlobalColor", false);
        cps.addProperty("customColor", 0xFF123456);
        JsonObject opts = new JsonObject();
        opts.addProperty("Size", 150);
        cps.add("options", opts);
        mods.add("CPS", cps);
        payload.add("modules", mods);

        JsonObject hud = new JsonObject();
        hud.addProperty("hudScale", 3);
        payload.add("hud", hud);

        // MODS mode: only 'enabled' per module, NO hud, NO options, NO colors
        JsonObject modsMode = ShareCode.prepareImportJson(payload, ShareCode.ImportMode.MODS);
        assertFalse(modsMode.has("hud"), "MODS mode must not include hud");
        assertTrue(modsMode.getAsJsonObject("modules").getAsJsonObject("CPS").has("enabled"));
        assertFalse(modsMode.getAsJsonObject("modules").getAsJsonObject("CPS").has("options"));
        assertFalse(modsMode.getAsJsonObject("modules").getAsJsonObject("CPS").has("useGlobalColor"));
        assertFalse(modsMode.getAsJsonObject("modules").getAsJsonObject("CPS").has("customColor"));

        // SETTINGS mode: hud layout + options/colors, NO 'enabled'
        JsonObject settingsMode = ShareCode.prepareImportJson(payload, ShareCode.ImportMode.SETTINGS);
        assertTrue(settingsMode.has("hud"), "SETTINGS mode must include hud");
        assertFalse(settingsMode.getAsJsonObject("modules").getAsJsonObject("CPS").has("enabled"), "SETTINGS mode must not include enabled");
        assertTrue(settingsMode.getAsJsonObject("modules").getAsJsonObject("CPS").has("options"));
        assertTrue(settingsMode.getAsJsonObject("modules").getAsJsonObject("CPS").has("useGlobalColor"));
        assertTrue(settingsMode.getAsJsonObject("modules").getAsJsonObject("CPS").has("customColor"));

        // BOTH mode: everything
        JsonObject bothMode = ShareCode.prepareImportJson(payload, ShareCode.ImportMode.BOTH);
        assertTrue(bothMode.has("hud"));
        assertTrue(bothMode.getAsJsonObject("modules").getAsJsonObject("CPS").has("enabled"));
        assertTrue(bothMode.getAsJsonObject("modules").getAsJsonObject("CPS").has("options"));
        assertTrue(bothMode.getAsJsonObject("modules").getAsJsonObject("CPS").has("useGlobalColor"));
        assertTrue(bothMode.getAsJsonObject("modules").getAsJsonObject("CPS").has("customColor"));
    }

    @Test
    void beforeImportSnapshotCreatedOnApply() {
        ProfileManager pm = ProfileManager.get();
        pm.saveAs("Initial");
        assertTrue(pm.hasProfile("Initial"));
        assertFalse(pm.hasProfile("Before import"));

        JsonObject payload = new JsonObject();
        payload.addProperty("v", 1);
        JsonObject mods = new JsonObject();
        JsonObject cps = new JsonObject();
        cps.addProperty("enabled", true);
        mods.add("CPS", cps);
        payload.add("modules", mods);

        ShareCode.applySharePayload(payload, ShareCode.ImportMode.BOTH);

        assertTrue(pm.hasProfile("Before import"), "'Before import' profile must be automatically created");
    }

    @Test
    void profileManagerOperations() {
        ProfileManager pm = ProfileManager.get();
        assertEquals(0, pm.getProfileNames().size());

        pm.saveAs("Profile A");
        assertTrue(pm.hasProfile("Profile A"));
        assertEquals(1, pm.getProfileNames().size());

        // Rename
        boolean renamed = pm.rename("Profile A", "Profile Renamed");
        assertTrue(renamed);
        assertFalse(pm.hasProfile("Profile A"));
        assertTrue(pm.hasProfile("Profile Renamed"));

        // Delete
        pm.delete("Profile Renamed");
        assertFalse(pm.hasProfile("Profile Renamed"));
        assertEquals(0, pm.getProfileNames().size());
    }

    @Test
    void summaryFormatting() {
        JsonObject payload = new JsonObject();
        assertEquals("0 modules, layout", ShareCode.summarize(payload));

        JsonObject mods = new JsonObject();
        mods.add("CPS", new JsonObject());
        payload.add("modules", mods);
        assertEquals("1 module, layout", ShareCode.summarize(payload));

        mods.add("FPS", new JsonObject());
        assertEquals("2 modules, layout", ShareCode.summarize(payload));
    }

    @Test
    void malformedInteriorJsonTypesIgnoredSafely() {
        JsonObject payload = new JsonObject();
        payload.addProperty("v", 1);
        JsonObject mods = new JsonObject();

        JsonObject badCps = new JsonObject();
        badCps.add("enabled", new JsonObject()); // non-primitive object
        badCps.add("customColor", new JsonPrimitive("not_a_number")); // non-numeric
        badCps.add("useGlobalColor", new com.google.gson.JsonArray()); // array
        mods.add("CPS", badCps);

        JsonObject nullFps = new JsonObject();
        nullFps.add("enabled", com.google.gson.JsonNull.INSTANCE);
        nullFps.add("customColor", com.google.gson.JsonNull.INSTANCE);
        mods.add("FPS", nullFps);

        payload.add("modules", mods);

        // Neither MODS, SETTINGS, nor BOTH should throw UnsupportedOperationException or NumberFormatException
        assertDoesNotThrow(() -> {
            JsonObject outMods = ShareCode.prepareImportJson(payload, ShareCode.ImportMode.MODS);
            assertNotNull(outMods);
            JsonObject outSettings = ShareCode.prepareImportJson(payload, ShareCode.ImportMode.SETTINGS);
            assertNotNull(outSettings);
            JsonObject outBoth = ShareCode.prepareImportJson(payload, ShareCode.ImportMode.BOTH);
            assertNotNull(outBoth);
        });
    }

    @Test
    void typicalShareCodeSize() {
        JsonObject full = ConfigManager.toJson();
        JsonObject payload = ShareCode.createSharePayload(full);
        String code = ShareCode.encode(payload);
        System.out.println("TYPICAL_SHARE_CODE_LENGTH: " + code.length() + " chars (bytes: " + code.getBytes(StandardCharsets.UTF_8).length + ")");
        assertTrue(code.startsWith(ShareCode.PREFIX));
        // 1.9.11: Better F3's per-line toggles and section colours took the full code past 8 KB; the decoder's cap is 16 KB.
        assertTrue(code.length() > 50 && code.length() < ShareCode.MAX_CODE_CHARS, "Typical code size should be between 50 chars and 16 KB");
    }
}
