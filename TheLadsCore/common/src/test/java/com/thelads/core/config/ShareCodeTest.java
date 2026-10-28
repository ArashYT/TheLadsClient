package com.thelads.core.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import java.util.Set;
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
        ModuleManager.getInstance().resetToDefaults();
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
        ModuleManager.getInstance().resetToDefaults();
        JsonObject full = ConfigManager.toJson();
        JsonObject payload = ShareCode.createSharePayload(full);
        String code = ShareCode.encode(payload);
        System.out.println("TYPICAL_SHARE_CODE_LENGTH: " + code.length() + " chars (bytes: " + code.getBytes(StandardCharsets.UTF_8).length + ")");
        assertTrue(code.startsWith(ShareCode.PREFIX));
        // v2 leaves defaults out (v1 wrote every module and option: 10 KB); the server takes 16 KB and old clients decode up to 16 KB.
        assertTrue(code.length() > 50 && code.length() < 8_000, "Typical code size should be between 50 chars and 8 KB");
    }

    @Test
    void defaultConfigCodeIsTiny() {
        ModuleManager.getInstance().resetToDefaults();
        JsonObject payload = ShareCode.createSharePayload(ConfigManager.toJson());
        assertEquals(2, payload.get("v").getAsInt());
        assertEquals(0, payload.getAsJsonObject("modules").size(), "no module differs: " + payload.getAsJsonObject("modules").keySet());
        String code = ShareCode.encode(payload);
        System.out.println("DEFAULT_SHARE_CODE_LENGTH: " + code.length());
        // No module is left; what remains is the HUD layout section (positions and anchors of every HUD), about 1.5 KB by itself.
        assertTrue(code.length() < 1_600, "default config code: " + code.length());
    }

    @Test
    void thirtyChangedOptionsStaySmall() {
        ModuleManager.getInstance().resetToDefaults();
        int changed = 0;
        for (Module m : ModuleManager.getInstance().getModules()) {
            for (Option o : m.getOptions()) {
                if (changed < 30 && o instanceof BoolOption b && !ShareCode.isExcludedModule(m.getName()) && !ShareCode.isExcludedOption(m.getName(), o.getName())) {
                    b.toggle();
                    changed++;
                }
            }
        }
        assertEquals(30, changed);
        String code = ShareCode.encode(ShareCode.createSharePayload(ConfigManager.toJson()));
        System.out.println("CHANGED30_SHARE_CODE_LENGTH: " + code.length());
        assertTrue(code.length() < 4_000, "30 changed options: " + code.length());
    }

    @Test
    void v2RoundTripGivesImporterTheSharersSettings() {
        ModuleManager mm = ModuleManager.getInstance();
        mm.resetToDefaults();
        // The sharer changes 3 options on 2 modules.
        ((BoolOption) mm.getModule("FPS").getOption("Smooth")).set(false);
        ((DropdownOption) mm.getModule("FPS").getOption("Update rate")).setIndex(3);
        ((BoolOption) mm.getModule("Coordinates").getOption("Vertical")).set(false);
        JsonObject sharer = settings();
        JsonObject payload = ShareCode.decode(ShareCode.encode(ShareCode.createSharePayload(ConfigManager.toJson())));

        assertEquals(2, payload.get("v").getAsInt());
        JsonObject modules = payload.getAsJsonObject("modules");
        assertEquals(Set.of("FPS", "Coordinates"), modules.keySet());
        assertEquals(Set.of("Smooth", "Update rate"), modules.getAsJsonObject("FPS").getAsJsonObject("options").keySet());
        assertEquals(Set.of("Vertical"), modules.getAsJsonObject("Coordinates").getAsJsonObject("options").keySet());

        // The importer has other changes (and a personal, never shared option).
        mm.resetToDefaults();
        ((DropdownOption) mm.getModule("FPS").getOption("Display")).setIndex(2);
        ((DropdownOption) mm.getModule("Memory").getOption("Display")).setIndex(0);
        ((BoolOption) mm.getModule("Coordinates").getOption("Show Biome")).set(true);
        mm.getModule("Clock").setEnabled(true);
        TextOption image = (TextOption) mm.getModule("Crosshair Tweaks").getOption("Background Image");
        image.setValue("C:/mine.png");
        assertNotEquals(sharer, settings());

        ShareCode.applySharePayload(payload, ShareCode.ImportMode.BOTH);

        assertEquals(sharer, settings());
        assertEquals("C:/mine.png", image.getValue(), "excluded options are never touched");
    }

    @Test
    void v2ImportFillsDefaultsAndLeavesExcludedAlone() {
        ModuleManager mm = ModuleManager.getInstance();
        mm.resetToDefaults();
        BoolOption smooth = (BoolOption) mm.getModule("FPS").getOption("Smooth");
        smooth.set(false);
        TextOption image = (TextOption) mm.getModule("Crosshair Tweaks").getOption("Background Image");
        image.setValue("C:/mine.png");

        JsonObject payload = new JsonObject();
        payload.addProperty("v", 2);
        JsonObject clock = new JsonObject();
        clock.addProperty("enabled", true);
        JsonObject clockOpts = new JsonObject();
        clockOpts.addProperty("12-hour clock", true);
        clock.add("options", clockOpts);
        JsonObject modules = new JsonObject();
        modules.add("Clock", clock);
        payload.add("modules", modules);

        JsonObject prepared = ShareCode.prepareImportJson(payload, ShareCode.ImportMode.BOTH).getAsJsonObject("modules");
        assertFalse(prepared.has("DiscordRPC"));
        assertTrue(prepared.getAsJsonObject("FPS").getAsJsonObject("options").get("Smooth").getAsBoolean(), "an unlisted option is filled in with its default");
        assertFalse(prepared.getAsJsonObject("Crosshair Tweaks").getAsJsonObject("options").has("Background Image"));

        ShareCode.applySharePayload(payload, ShareCode.ImportMode.BOTH);

        assertTrue(smooth.get(), "an unlisted option is reset to its default");
        assertTrue(mm.getModule("Clock").isEnabledSetting());
        assertTrue(((BoolOption) mm.getModule("Clock").getOption("12-hour clock")).get());
        assertEquals("C:/mine.png", image.getValue(), "excluded options are never touched");
    }

    @Test
    void v1CodeStillImportsAsBefore() {
        ModuleManager mm = ModuleManager.getInstance();
        mm.resetToDefaults();
        DropdownOption memory = (DropdownOption) mm.getModule("Memory").getOption("Display");
        memory.setIndex(0);

        JsonObject payload = new JsonObject();
        payload.addProperty("v", 1);
        JsonObject fps = new JsonObject();
        fps.addProperty("enabled", true);
        JsonObject fpsOpts = new JsonObject();
        fpsOpts.addProperty("Smooth", false);
        fps.add("options", fpsOpts);
        JsonObject modules = new JsonObject();
        modules.add("FPS", fps);
        payload.add("modules", modules);

        assertEquals(Set.of("FPS"), ShareCode.prepareImportJson(payload, ShareCode.ImportMode.BOTH).getAsJsonObject("modules").keySet());
        ShareCode.applySharePayload(payload, ShareCode.ImportMode.BOTH);

        assertFalse(((BoolOption) mm.getModule("FPS").getOption("Smooth")).get());
        assertEquals(0, memory.getIndex(), "v1 only touches what it lists");
    }

    @Test
    void codesCarryOnlyVisualValuesAndNeverChangeTheRest() {
        ModuleManager mm = ModuleManager.getInstance();
        mm.resetToDefaults();
        TextOption macro = (TextOption) mm.getModule("Auto Text").getOption("Message 1");
        SoundOption oof = (SoundOption) mm.getModule("Oof Mod").getOption("Oof sound");
        TextOption shortcuts = (TextOption) mm.getModule("Chat").getOption("Shortcuts");
        BoolOption letServers = (BoolOption) mm.getModule("Server Control").getOption("Let servers change my Lads settings");
        Module hypixel = mm.getModule("HYPIXEL Mods");
        BoolOption autoFriend = (BoolOption) hypixel.getOption("Auto Friend");
        BoolOption autoTip = (BoolOption) hypixel.getOption("Auto Tip");
        SliderOption ggDelay = (SliderOption) hypixel.getOption("Auto GG: Delay seconds");
        BoolOption streamer = (BoolOption) hypixel.getOption("Streamer Mode");
        BoolOption cleanup = (BoolOption) hypixel.getOption("Chat Cleanup");
        TextOption gg = (TextOption) hypixel.getOption("Auto GG: Message");

        // Exporting: private text, sounds, security switches and chat automation stay home; the visual changes travel.
        macro.setValue("/login hunter2");
        oof.set("mine.mp3");
        shortcuts.setValue("brb=be right back");
        letServers.set(false);
        autoTip.set(false);
        hypixel.setEnabled(false);
        streamer.set(true);
        ((BoolOption) mm.getModule("FPS").getOption("Smooth")).set(false);
        JsonObject exported = ShareCode.createSharePayload(ConfigManager.toJson()).getAsJsonObject("modules");
        assertEquals(Set.of("FPS", "HYPIXEL Mods"), exported.keySet());
        assertEquals(Set.of("options"), exported.getAsJsonObject("HYPIXEL Mods").keySet(), "HYPIXEL Mods' on/off stays home");
        assertEquals(Set.of("Streamer Mode"), exported.getAsJsonObject("HYPIXEL Mods").getAsJsonObject("options").keySet(),
            "its visual toggles travel, its automation does not");
        assertFalse(exported.toString().contains("hunter2"));
        streamer.set(false);

        // Importing a stranger's v2 code that tries to change all of it: only the visual values apply, nothing else is reset.
        mm.getModule("StackRefill").setEnabled(false); // on by default; the player turned it off
        JsonObject payload = JsonParser.parseString("{\"v\":2,\"modules\":{"
            + "\"Oof Mod\":{\"enabled\":true,\"options\":{\"Oof sound\":\"evil.mp3\",\"Volume\":0.5}},"
            + "\"Auto Text\":{\"enabled\":true,\"options\":{\"Message 1\":\"/pay attacker 1000\"}},"
            + "\"HYPIXEL Mods\":{\"enabled\":true,\"options\":{\"Auto GG: Message\":\"buy cheats\",\"Auto Friend\":true,\"Auto Tip\":true,"
            + "\"Auto GG: Delay seconds\":0,\"Streamer Mode\":true,\"Chat Cleanup\":false}},"
            + "\"Server Control\":{\"options\":{\"Let servers change my Lads settings\":true}},"
            + "\"IgnorePacketErrors\":{\"enabled\":true},\"StackRefill\":{\"enabled\":true},"
            + "\"Chat\":{\"options\":{\"Shortcuts\":\"gg=/pay attacker 1\",\"Custom Tabs\":\"A=(a+)+$\"}}}}").getAsJsonObject();
        ShareCode.applySharePayload(payload, ShareCode.ImportMode.BOTH);

        assertEquals("/login hunter2", macro.getValue());
        assertFalse(mm.getModule("Auto Text").isEnabledSetting());
        assertEquals("mine.mp3", oof.get(), "a code never sets a sound file");
        assertEquals("brb=be right back", shortcuts.getValue());
        assertEquals("", ((TextOption) mm.getModule("Chat").getOption("Custom Tabs")).getValue());
        assertFalse(letServers.get(), "Server Control stays the player's choice");
        assertFalse(autoFriend.get());
        assertFalse(autoTip.get(), "the player's own Auto Tip, not the code's (nor its default, on)");
        assertEquals(1, ggDelay.getValue(), 1e-9, "Auto GG's settings stay the player's");
        assertEquals("gg", gg.getValue());
        assertFalse(hypixel.isEnabledSetting(), "HYPIXEL Mods stays off: a code never switches its automation on");
        assertTrue(streamer.get(), "HYPIXEL Mods' visual toggles still import");
        assertFalse(cleanup.get());
        assertFalse(mm.getModule("IgnorePacketErrors").isEnabledSetting());
        assertFalse(mm.getModule("StackRefill").isEnabledSetting(), "automation is never switched on by a code");
        assertTrue(mm.getModule("Oof Mod").isEnabledSetting(), "visual switches still import");
        assertEquals(0.5, ((SliderOption) mm.getModule("Oof Mod").getOption("Volume")).getValue(), 1e-9);
        assertTrue(((BoolOption) mm.getModule("FPS").getOption("Smooth")).get(), "an unlisted visual option is reset (v2)");
    }

    @Test
    void defaultsComeFromTheLiveModulesNotASecondModuleList() {
        var optifine = com.thelads.core.modules.OptiFine189Module.getInstance();
        long monitors = priorityMonitors();
        ModuleManager.getInstance().resetToDefaults();
        JsonObject payload = ShareCode.createSharePayload(ConfigManager.toJson());
        ShareCode.prepareImportJson(payload, ShareCode.ImportMode.BOTH);
        assertSame(optifine, com.thelads.core.modules.OptiFine189Module.getInstance(), "the OptiFine module the screen edits is still the saved one");
        assertEquals(monitors, priorityMonitors(), "no Threads monitor started by a throwaway module list");
    }

    private static long priorityMonitors() {
        return Thread.getAllStackTraces().keySet().stream().filter(t -> t.getName().equals("Lads priority monitor")).count();
    }

    @Test
    void olderProfilesAndCodesRestoreWhatTheySaved() {
        ModuleManager mm = ModuleManager.getInstance();
        mm.resetToDefaults();
        Module clouds = mm.getModule(com.thelads.core.modules.CloudHeightModule.NAME);
        DropdownOption style = (DropdownOption) mm.getModule("Keystrokes").getOption("Style");
        clouds.setEnabled(false);
        style.setIndex(1);
        ProfileManager pm = ProfileManager.get();
        pm.saveAs("Saved in 1.9.11");
        JsonObject hud = pm.getProfile("Saved in 1.9.11").getAsJsonObject("hud");
        for (String flag : new String[] {ConfigManager.KEYSTROKES_1912_FLAG, ConfigManager.KEYSTROKES_1914_FLAG, ConfigManager.CLOUD_HEIGHT_1914_FLAG,
                ConfigManager.MOB_KILL_BANNER_1914_FLAG}) {
            hud.remove(flag); // written before those one-time overrides existed
        }
        clouds.setEnabled(true);
        style.setIndex(0);

        pm.apply("Saved in 1.9.11");
        assertFalse(clouds.isEnabledSetting(), "the profile's Cloud Height, not the 1.9.14 override again");
        assertEquals(1, style.getIndex(), "the profile's Keystrokes style, not the 1.9.12 override again");

        JsonObject code = JsonParser.parseString("{\"v\":1,\"modules\":{\"Keystrokes\":{\"options\":{\"Style\":1}}},\"hud\":{\"hudScale\":2}}").getAsJsonObject();
        style.setIndex(0);
        ShareCode.applySharePayload(code, ShareCode.ImportMode.SETTINGS);
        assertEquals(1, style.getIndex());
        assertFalse(clouds.isEnabledSetting(), "an old code does not switch Cloud Height on");
        HudSettings.getInstance().resetToDefaults();
    }

    @Test
    void eachImportKeepsTheBackupsBeforeIt() {
        ModuleManager mm = ModuleManager.getInstance();
        mm.resetToDefaults();
        BoolOption smooth = (BoolOption) mm.getModule("FPS").getOption("Smooth");
        smooth.set(false);
        ProfileManager pm = ProfileManager.get();
        ShareCode.applySharePayload(JsonParser.parseString("{\"v\":1,\"modules\":{\"FPS\":{\"options\":{\"Smooth\":true}}}}").getAsJsonObject(), ShareCode.ImportMode.BOTH);
        ShareCode.applySharePayload(JsonParser.parseString("{\"v\":1,\"modules\":{\"FPS\":{\"options\":{\"Update rate\":3}}}}").getAsJsonObject(), ShareCode.ImportMode.BOTH);

        assertTrue(smooth(pm.getProfile("Before import")), "before the second import");
        assertFalse(smooth(pm.getProfile("Before import (2)")), "before the first: the settings the player had");
        pm.saveBeforeImport();
        pm.saveBeforeImport(); // nothing changed since: nothing moves
        assertFalse(smooth(pm.getProfile("Before import (3)")));
        smooth.set(false);
        pm.saveBeforeImport();
        assertTrue(smooth(pm.getProfile("Before import (3)")), "three are kept: the oldest moved out");
        assertFalse(pm.hasProfile("Before import (4)"));
    }

    private static boolean smooth(JsonObject snapshot) {
        return snapshot.getAsJsonObject("modules").getAsJsonObject("FPS").getAsJsonObject("options").get("Smooth").getAsBoolean();
    }

    @Test
    void sharedLayoutsCannotBloatTheConfig() {
        JsonObject positions = new JsonObject();
        positions.add("x".repeat(200), xy(1, 1));
        for (int i = 0; i < 5000; i++) positions.add("junk" + i, xy(i, i));
        JsonArray fade = new JsonArray();
        for (int i = 0; i < 5000; i++) fade.add(0x111111 * (i % 15));
        JsonObject hud = new JsonObject();
        hud.add("positions", positions);
        hud.add("fadePlaylist", fade);
        JsonObject payload = new JsonObject();
        payload.addProperty("v", 1);
        payload.add("hud", hud);
        HudSettings settings = HudSettings.getInstance();
        try {
            settings.resetToDefaults();
            ShareCode.applySharePayload(payload, ShareCode.ImportMode.SETTINGS);
            assertEquals(ConfigManager.MAX_HUD_ENTRIES, settings.getPositions().size());
            assertFalse(settings.getPositions().containsKey("x".repeat(200)), "no name longer than 128");
            assertEquals(HudSettings.MAX_FADE, settings.getFadePlaylist().size());
            assertSame(settings.getFadePalette(), settings.getFadePalette(), "one palette, not a new array per HUD element per frame");
        } finally {
            settings.resetToDefaults();
        }
    }

    private static JsonArray xy(int x, int y) {
        JsonArray a = new JsonArray();
        a.add(x);
        a.add(y);
        return a;
    }

    /** Every non-excluded module's on/off, HUD colours and options as the config saves them (no favourite / modified time). */
    private static JsonObject settings() {
        JsonObject saved = ConfigManager.toJson().getAsJsonObject("modules");
        JsonObject out = new JsonObject();
        for (Module m : ModuleManager.getInstance().getModules()) {
            if (ShareCode.isExcludedModule(m.getName())) continue;
            JsonObject entry = saved.getAsJsonObject(m.getName());
            entry.remove("favorite");
            entry.remove("lastModified");
            if (entry.get("options") instanceof JsonObject opts) opts.keySet().removeIf(k -> ShareCode.isExcludedOption(m.getName(), k));
            out.add(m.getName(), entry);
        }
        return out;
    }
}
