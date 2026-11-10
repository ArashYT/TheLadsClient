package com.thelads.core.client.ladsapi;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.HudSettings;
import com.thelads.core.config.ModuleManager;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** HUD and vanilla options under a server policy: applied for the session, never saved, back on reset. */
class ServerSettingsTest {
    private final List<byte[]> sent = new ArrayList<>();
    private final HudSettings hud = HudSettings.getInstance();

    @BeforeEach
    void setUp() {
        LadsApiClient.install(sent::add, "26.3");
        LadsApiClient.reset();
        ServerSettings.vanillaHooked = true;
        letServersChange(true);
        ownHud(true, false, true, 0);
    }

    @AfterEach
    void tearDown() {
        LadsApiClient.reset();
        ServerSettings.vanillaHooked = false;
        ModuleManager.getInstance().resetToDefaults();
        ownHud(true, false, true, 0);
    }

    private void ownHud(boolean shadow, boolean soft, boolean backgrounds, int scale) {
        hud.setTextShadow(shadow);
        hud.setSoftShadow(soft);
        hud.setBackgrounds(backgrounds);
        hud.setHudScale(scale);
    }

    private static void letServersChange(boolean allowed) {
        ((BoolOption) ModuleManager.getInstance().getModule("Server Control").getOption("Let servers change my Lads settings")).set(allowed);
    }

    /** Server hello, then a policy with these "modules" and "options" bodies; returns the client's ack. */
    private JsonObject policy(String modules, String options) {
        LadsApiClient.receive("{\"t\":\"hello\",\"v\":1,\"server\":\"Lobby\",\"caps\":[\"policy\",\"reset\"]}".getBytes(StandardCharsets.UTF_8));
        LadsApiClient.receive(("{\"t\":\"policy\",\"rev\":1,\"modules\":{" + modules + "},\"options\":{" + options + "}}").getBytes(StandardCharsets.UTF_8));
        return new Gson().fromJson(new String(sent.get(sent.size() - 1), StandardCharsets.UTF_8), JsonObject.class);
    }

    private static List<String> names(JsonObject ack, String list) {
        List<String> names = new ArrayList<>();
        ack.getAsJsonArray(list).forEach(e -> names.add(e.getAsString()));
        return names;
    }

    @Test
    void hudRulesApplyForTheSessionAndAreNeverSaved() {
        JsonObject ack = policy("", "\"HUD\":{\"Text Shadow\":{\"value\":false},\"Backgrounds\":{\"value\":false},"
            + "\"Soft Shadow\":{\"value\":true},\"Scale\":{\"value\":3}}");
        assertEquals(List.of(), names(ack, "ignored"));
        assertFalse(hud.isTextShadow());
        assertFalse(hud.isBackgrounds());
        assertTrue(hud.isSoftShadow());
        assertEquals(3, hud.getHudScale());

        JsonObject saved = ConfigManager.toJson().getAsJsonObject("hud");
        assertTrue(saved.get("textShadow").getAsBoolean(), "the config keeps the player's own value");
        assertTrue(saved.get("backgrounds").getAsBoolean());
        assertFalse(saved.get("softShadow").getAsBoolean());
        assertEquals(0, saved.get("hudScale").getAsInt());
        assertFalse(ConfigManager.toJson().getAsJsonObject("modules").has("HUD"), "not a module of the config");

        LadsApiClient.reset(); // leaving the server
        assertTrue(hud.isTextShadow());
        assertTrue(hud.isBackgrounds());
        assertFalse(hud.isSoftShadow());
        assertEquals(0, hud.getHudScale());
    }

    @Test
    void aToggleUnderAForcedValueKeepsWhatThePlayerAskedFor() {
        policy("", "\"HUD\":{\"Text Shadow\":{\"value\":false},\"Scale\":{\"max\":2}}");
        hud.setTextShadow(!hud.isTextShadow()); // the HUD screen's toggle: it shows off, the click asks for on
        hud.setHudScale(6);
        assertFalse(hud.isTextShadow());
        assertEquals(2, hud.getHudScale(), "a limit clamps the player's value");
        LadsApiClient.reset();
        assertTrue(hud.isTextShadow());
        assertEquals(6, hud.getHudScale());
    }

    @Test
    void vanillaRulesGiveTheEffectiveValueAndEndWithTheSession() {
        assertFalse(ServerSettings.hasVanillaRules());
        JsonObject ack = policy("", "\"Minecraft\":{\"FOV\":{\"max\":90},\"Brightness\":{\"max\":50},"
            + "\"Render Distance\":{\"min\":8},\"View Bobbing\":{\"value\":false}}");
        assertEquals(List.of(), names(ack, "ignored"));
        assertTrue(ServerSettings.hasVanillaRules());
        assertEquals(90, ServerSettings.vanilla("FOV", 110));
        assertEquals(70, ServerSettings.vanilla("FOV", 70));
        assertEquals(50, ServerSettings.vanilla("Brightness", 1500), "a gamma above 100% is capped as well");
        assertEquals(8, ServerSettings.vanilla("Render Distance", 4));
        assertFalse(ServerSettings.vanilla("View Bobbing", true));
        assertTrue(ServerSettings.vanilla("Entity Shadows", true), "no rule: the own value");

        LadsApiClient.reset();
        assertFalse(ServerSettings.hasVanillaRules());
        assertEquals(110, ServerSettings.vanilla("FOV", 110));
        assertEquals(1500, ServerSettings.vanilla("Brightness", 1500), "no rule: not even clamped to the range");
        assertTrue(ServerSettings.vanilla("View Bobbing", true));
    }

    @Test
    void aVersionWithoutTheVanillaHookReportsMinecraftRulesAsIgnored() {
        ServerSettings.vanillaHooked = false;
        JsonObject ack = policy("", "\"Minecraft\":{\"FOV\":{\"max\":90}},\"HUD\":{\"Backgrounds\":{\"value\":false}}");
        assertEquals(List.of("Minecraft.FOV"), names(ack, "ignored"));
        assertFalse(ServerSettings.hasVanillaRules());
        assertEquals(110, ServerSettings.vanilla("FOV", 110));
        assertFalse(hud.isBackgrounds(), "the HUD needs no hook");
    }

    @Test
    void moduleStatesUnknownOptionsAndWrongTypesAreIgnored() {
        JsonObject ack = policy("\"HUD\":\"block\",\"Minecraft\":\"on\"",
            "\"HUD\":{\"FPS\":{\"value\":30},\"Text Shadow\":{\"value\":3}},\"Minecraft\":{\"FOV\":{\"value\":\"wide\"}}");
        List<String> ignored = names(ack, "ignored");
        assertTrue(ignored.containsAll(List.of("HUD", "Minecraft", "HUD.FPS", "HUD.Text Shadow", "Minecraft.FOV")), ignored.toString());
        assertTrue(hud.isTextShadow());
        assertEquals(ModuleState.INHERIT, ServerPolicy.active().module("HUD"));
    }

    @Test
    void aPlayerWhoRefusesChangesKeepsOwnValuesButNotLimits() {
        letServersChange(false);
        JsonObject ack = policy("", "\"HUD\":{\"Text Shadow\":{\"value\":false}},\"Minecraft\":{\"FOV\":{\"max\":90},\"View Bobbing\":{\"value\":false}}");
        assertTrue(names(ack, "refused").containsAll(List.of("HUD.Text Shadow", "Minecraft.View Bobbing")), ack.toString());
        assertTrue(hud.isTextShadow());
        assertTrue(ServerSettings.vanilla("View Bobbing", true));
        assertEquals(90, ServerSettings.vanilla("FOV", 110), "a limit is not a forced change");
    }
}
