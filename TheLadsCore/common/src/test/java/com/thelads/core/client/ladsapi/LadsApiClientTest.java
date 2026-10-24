package com.thelads.core.client.ladsapi;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.thelads.core.client.HypixelSafetyManager;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.ColorOption;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.IntTextOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.SliderOption;
import com.thelads.core.modules.FullbrightModule;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LadsApiClientTest {
    private final List<byte[]> sentPayloads = new ArrayList<>();

    @BeforeEach
    void setUp() {
        sentPayloads.clear();
        LadsApiClient.install(sentPayloads::add, "26.3");
        LadsApiClient.reset();

        // Ensure "Server Control" "Let servers change my Lads settings" is true
        Module sc = ModuleManager.getInstance().getModule("Server Control");
        if (sc != null) {
            BoolOption b = (BoolOption) sc.getOption("Let servers change my Lads settings");
            if (b != null) b.set(true);
        }
    }

    @AfterEach
    void tearDown() {
        LadsApiClient.reset();
        ModuleManager.getInstance().resetToDefaults();
    }

    private JsonObject sendAndGetLastJson(String jsonStr) {
        int before = sentPayloads.size();
        LadsApiClient.receive(jsonStr.getBytes(StandardCharsets.UTF_8));
        if (sentPayloads.size() > before) {
            byte[] last = sentPayloads.get(sentPayloads.size() - 1);
            return new Gson().fromJson(new String(last, StandardCharsets.UTF_8), JsonObject.class);
        }
        return null;
    }

    @Test
    void testValidPolicyFromProtocolExample() {
        // 1. Send server hello
        JsonObject helloReply = sendAndGetLastJson("{\"t\":\"hello\",\"v\":1,\"server\":\"Lads SMP\",\"caps\":[\"policy\",\"presence\",\"reset\"]}");
        assertNotNull(helloReply, "Should reply to server hello");
        assertEquals("hello", helloReply.get("t").getAsString());
        assertEquals(1, helloReply.get("v").getAsInt());
        assertEquals("26.3", helloReply.get("mc").getAsString());
        assertTrue(LadsApiClient.isSessionOk());

        // 2. Send PROTOCOL example policy
        String policyJson = "{\"t\":\"policy\",\"rev\":7,\"unlisted\":\"inherit\","
            + "\"modules\":{\"Fullbright\":\"allow\",\"XaeroMinimap\":\"block\",\"Crosshair\":\"on\"},"
            + "\"options\":{"
            + "  \"Fullbright\":{\"Brightness\":{\"max\":10}},"
            + "  \"Crosshair\":{\"Style\":{\"value\":\"Cross\",\"lock\":true},\"Color\":{\"value\":\"#FF0000\"}},"
            + "  \"1.7 Animations\":{\"1.7 sword block pose\":{\"value\":false}}"
            + "},"
            + "\"notice\":\"Lads SMP: fullbright capped at 10%\"}";

        JsonObject ack = sendAndGetLastJson(policyJson);
        assertNotNull(ack, "Should send ack for policy");
        assertEquals("ack", ack.get("t").getAsString());
        assertEquals(7, ack.get("rev").getAsInt());
        assertEquals(0, ack.get("ignored").getAsJsonArray().size());
        assertEquals(0, ack.get("refused").getAsJsonArray().size());

        ServerPolicy active = ServerPolicy.active();
        assertNotNull(active);
        assertEquals("Lads SMP", active.serverName());
        assertEquals("Lads SMP: fullbright capped at 10%", active.notice());
        assertEquals(ModuleState.ALLOW, active.module("Fullbright"));
        assertEquals(ModuleState.BLOCK, active.module("XaeroMinimap"));
        assertEquals(ModuleState.BLOCK, active.module("Minimap"), "the visible Minimap module is the same mod as XaeroMinimap");
        assertEquals(ModuleState.ON, active.module("Crosshair"));

        OptionRule fbBright = active.option("Fullbright", "Brightness");
        assertNotNull(fbBright);
        assertEquals(10.0, fbBright.max);

        OptionRule chStyle = active.option("Crosshair", "Style");
        assertNotNull(chStyle);
        assertEquals("Cross", chStyle.choice);
        assertTrue(chStyle.lock);

        OptionRule chColor = active.option("Crosshair", "Color");
        assertNotNull(chColor);
        assertEquals(0xFFFF0000, chColor.color.intValue());

        OptionRule animRule = active.option("1.7 Animations", "1.7 sword block pose");
        assertNotNull(animRule);
        assertEquals(Boolean.FALSE, animRule.bool);
    }

    @Test
    void testMalformedAndOversizedJsonDropped() {
        // Hello first
        sendAndGetLastJson("{\"t\":\"hello\",\"v\":1,\"server\":\"Test\"}");
        int sentBefore = sentPayloads.size();

        // Oversized payload (> 524,288 bytes)
        byte[] big = new byte[LadsApi.MAX_IN + 1];
        LadsApiClient.receive(big);
        assertEquals(sentBefore, sentPayloads.size(), "Oversized payload should be dropped");

        // Malformed JSON
        LadsApiClient.receive("{not valid json at all".getBytes(StandardCharsets.UTF_8));
        assertEquals(sentBefore, sentPayloads.size(), "Malformed JSON should be dropped");

        // Empty payload
        LadsApiClient.receive(new byte[0]);
        assertEquals(sentBefore, sentPayloads.size(), "Empty payload should be dropped");

        // Null payload
        LadsApiClient.receive(null);
        assertEquals(sentBefore, sentPayloads.size(), "Null payload should be dropped");
    }

    @Test
    void testNanAndInfIgnored() {
        sendAndGetLastJson("{\"t\":\"hello\",\"v\":1,\"server\":\"Test\"}");

        // NaN for slider value, Infinity for min/max
        String policyJson = "{\"t\":\"policy\",\"rev\":1,\"unlisted\":\"inherit\","
            + "\"options\":{\"Fullbright\":{\"Brightness\":{\"value\":\"NaN\",\"min\":10}}}}";
        JsonObject ack = sendAndGetLastJson(policyJson);
        assertNotNull(ack);
        assertTrue(ack.get("ignored").getAsJsonArray().size() > 0);
        assertEquals("Fullbright.Brightness", ack.get("ignored").getAsJsonArray().get(0).getAsString());
        assertNull(ServerPolicy.active().option("Fullbright", "Brightness"));
    }

    @Test
    void testBadDropdownChoiceIgnored() {
        sendAndGetLastJson("{\"t\":\"hello\",\"v\":1,\"server\":\"Test\"}");

        // Crosshair style with completely invalid choice
        String policyJson = "{\"t\":\"policy\",\"rev\":2,\"unlisted\":\"inherit\","
            + "\"options\":{\"Crosshair\":{\"Style\":{\"value\":\"NonExistentStyleXYZ\"}}}}";
        JsonObject ack = sendAndGetLastJson(policyJson);
        assertNotNull(ack);
        assertTrue(ack.get("ignored").getAsJsonArray().size() > 0);
        assertEquals("Crosshair.Style", ack.get("ignored").getAsJsonArray().get(0).getAsString());
        assertNull(ServerPolicy.active().option("Crosshair", "Style"));
    }

    @Test
    void testUnknownNamesIgnored() {
        sendAndGetLastJson("{\"t\":\"hello\",\"v\":1,\"server\":\"Test\"}");

        String policyJson = "{\"t\":\"policy\",\"rev\":3,\"unlisted\":\"inherit\","
            + "\"modules\":{\"TotallyFakeModuleXYZ\":\"block\"},"
            + "\"options\":{\"Fullbright\":{\"FakeOptionXYZ\":{\"value\":50}}}}";
        JsonObject ack = sendAndGetLastJson(policyJson);
        assertNotNull(ack);
        var ignored = ack.get("ignored").getAsJsonArray();
        assertEquals(2, ignored.size());
        assertTrue(ignored.toString().contains("TotallyFakeModuleXYZ"));
        assertTrue(ignored.toString().contains("Fullbright.FakeOptionXYZ"));
    }

    @Test
    void testNeverControllableOptionsIgnored() {
        sendAndGetLastJson("{\"t\":\"hello\",\"v\":1,\"server\":\"Test\"}");

        // ClientTools has action option "Copy coordinates" which has serverControl() == NEVER
        String policyJson = "{\"t\":\"policy\",\"rev\":4,\"unlisted\":\"inherit\","
            + "\"options\":{\"ClientTools\":{\"Copy coordinates\":{\"value\":true}}}}";
        JsonObject ack = sendAndGetLastJson(policyJson);
        assertNotNull(ack);
        var ignored = ack.get("ignored").getAsJsonArray();
        assertEquals(1, ignored.size());
        assertEquals("ClientTools.Copy coordinates", ignored.get(0).getAsString());
    }

    @Test
    void testNeverForcedOnIgnored() {
        sendAndGetLastJson("{\"t\":\"hello\",\"v\":1,\"server\":\"Test\"}");

        // DiscordRPC, StackRefill, UseWhileMining cannot be forced ON
        String policyJson = "{\"t\":\"policy\",\"rev\":5,\"unlisted\":\"inherit\","
            + "\"modules\":{\"DiscordRPC\":\"on\",\"StackRefill\":\"on\",\"UseWhileMining\":\"on\"}}";
        JsonObject ack = sendAndGetLastJson(policyJson);
        assertNotNull(ack);
        var ignored = ack.get("ignored").getAsJsonArray();
        assertEquals(3, ignored.size());
        assertEquals(ModuleState.INHERIT, ServerPolicy.active().module("DiscordRPC"));
        assertEquals(ModuleState.INHERIT, ServerPolicy.active().module("StackRefill"));
        assertEquals(ModuleState.INHERIT, ServerPolicy.active().module(HypixelSafetyManager.USE_WHILE_MINING));
    }

    @Test
    void testAutomationVsUnlistedAllow() {
        sendAndGetLastJson("{\"t\":\"hello\",\"v\":1,\"server\":\"Test\"}");

        // unlisted: allow lifts regular modules, but never automation modules!
        String policyJson = "{\"t\":\"policy\",\"rev\":6,\"unlisted\":\"allow\"}";
        JsonObject ack = sendAndGetLastJson(policyJson);
        assertNotNull(ack);

        ServerPolicy policy = ServerPolicy.active();
        // Automation modules stay INHERIT
        assertEquals(ModuleState.INHERIT, policy.module("StackRefill"));
        assertEquals(ModuleState.INHERIT, policy.module("UseWhileMining"));
        assertEquals(ModuleState.INHERIT, policy.module("AutoReconnect"));
        assertEquals(ModuleState.INHERIT, policy.module("MouseTweaks"));
        assertEquals(ModuleState.INHERIT, policy.module("KeepUiOpen"));

        // Non-automation modules get ALLOW
        assertEquals(ModuleState.ALLOW, policy.module("FPS"));
        assertEquals(ModuleState.ALLOW, policy.module("Fullbright"));
    }

    @Test
    void testServerControlModuleImmune() {
        sendAndGetLastJson("{\"t\":\"hello\",\"v\":1,\"server\":\"Test\"}");

        String policyJson = "{\"t\":\"policy\",\"rev\":8,\"unlisted\":\"block\","
            + "\"modules\":{\"Server Control\":\"block\"},"
            + "\"options\":{\"Server Control\":{\"Let servers change my Lads settings\":{\"value\":false}}}}";
        JsonObject ack = sendAndGetLastJson(policyJson);
        assertNotNull(ack);
        var ignored = ack.get("ignored").getAsJsonArray();
        assertTrue(ignored.toString().contains("Server Control"));

        ServerPolicy policy = ServerPolicy.active();
        assertEquals(ModuleState.INHERIT, policy.module("Server Control"), "Server Control is immune and stays INHERIT");
        assertNull(policy.option("Server Control", "Let servers change my Lads settings"));
    }

    @Test
    void testPlayerRefusalOfForcedValuesAndOn() {
        // Player turns off "Let servers change my Lads settings"
        Module sc = ModuleManager.getInstance().getModule("Server Control");
        assertNotNull(sc);
        BoolOption changeOpt = (BoolOption) sc.getOption("Let servers change my Lads settings");
        changeOpt.set(false);

        sendAndGetLastJson("{\"t\":\"hello\",\"v\":1,\"server\":\"Test\"}");

        String policyJson = "{\"t\":\"policy\",\"rev\":9,\"unlisted\":\"inherit\","
            + "\"modules\":{\"Crosshair\":\"on\",\"XaeroMinimap\":\"block\"},"
            + "\"options\":{"
            + "  \"Fullbright\":{\"Brightness\":{\"value\":10,\"max\":20}},"
            + "  \"Crosshair\":{\"Style\":{\"value\":\"Dot\"}}"
            + "}}";
        JsonObject ack = sendAndGetLastJson(policyJson);
        assertNotNull(ack);

        var refused = ack.get("refused").getAsJsonArray();
        assertTrue(refused.toString().contains("Crosshair"), "Forced ON refused");
        assertTrue(refused.toString().contains("Fullbright.Brightness"), "Forced slider value refused");
        assertTrue(refused.toString().contains("Crosshair.Style"), "Forced dropdown choice refused");

        // Module.isEnabled() must NOT be forced on
        Module chMod = ModuleManager.getInstance().getModule("Crosshair Tweaks");
        chMod.setEnabled(false);
        assertFalse(chMod.isEnabled(), "Crosshair should stay off when refused");

        // Slider value must NOT be forced, but max limit (20) still applies
        Module fbMod = ModuleManager.getInstance().getModule("Fullbright");
        SliderOption brightOpt = (SliderOption) fbMod.getOption("Brightness");
        brightOpt.setValue(50);
        assertEquals(20.0, brightOpt.getValue(), "Slider max limit still applies even when forced value is refused");

        // Module block still applies
        Module xmMod = ModuleManager.getInstance().getModule("XaeroMinimap");
        xmMod.setEnabled(true);
        assertFalse(xmMod.isEnabled(), "Server block always applies");
    }

    @Test
    void testApplyMath() {
        // Fast path: when active policy is null, each returns own
        assertTrue(ServerPolicy.applyBool("Mod", "Opt", true));
        assertFalse(ServerPolicy.applyBool("Mod", "Opt", false));
        assertEquals(50.0, ServerPolicy.applySlider("Mod", "Opt", 50.0, 0.0, 100.0, 5.0));
        String[] allChoices = new String[]{"Cross", "Dot", "Square", "Circle", "Arrow", "Vanilla"};
        assertEquals(1, ServerPolicy.applyDropdown("Mod", "Opt", 1, allChoices));
        assertEquals(0xFF112233, ServerPolicy.applyColor("Mod", "Opt", 0xFF112233));

        // When policy is active with rules:
        sendAndGetLastJson("{\"t\":\"hello\",\"v\":1,\"server\":\"Test\"}");
        String policyJson = "{\"t\":\"policy\",\"rev\":1,\"unlisted\":\"inherit\","
            + "\"options\":{"
            + "  \"Fullbright\":{\"Brightness\":{\"max\":20}},"
            + "  \"Crosshair\":{\"Style\":{\"choices\":[\"Dot\",\"Square\"]}}"
            + "}}";
        sendAndGetLastJson(policyJson);

        // Slider rule max=20: own=50 clamped into [0, 20] -> 20.0
        assertEquals(20.0, ServerPolicy.applySlider("Fullbright", "Brightness", 50.0, 0.0, 100.0, 5.0));
        // Dropdown choices: own="Cross" (index 0) not in [Dot, Square] -> picks first allowed ("Dot", index 1)
        assertEquals(1, ServerPolicy.applyDropdown("Crosshair", "Style", 0, allChoices));
        // Dropdown choices: own="Square" (index 2) in [Dot, Square] -> keeps index 2
        assertEquals(2, ServerPolicy.applyDropdown("Crosshair", "Style", 2, allChoices));
    }

    @Test
    void testResetAndSeedRules() {
        sendAndGetLastJson("{\"t\":\"hello\",\"v\":1,\"server\":\"Test\"}");
        assertTrue(LadsApiClient.isSessionOk());

        // Same seed respawn does NOT reset
        LadsApiClient.joinGame(12345L);
        sendAndGetLastJson("{\"t\":\"hello\",\"v\":1,\"server\":\"Test\"}");
        assertTrue(LadsApiClient.isSessionOk());
        LadsApiClient.respawn(12345L);
        assertTrue(LadsApiClient.isSessionOk(), "Same seed respawn must not reset");

        // Different seed respawn DOES reset
        LadsApiClient.respawn(99999L);
        assertFalse(LadsApiClient.isSessionOk(), "Different seed respawn must reset");
    }

    @Test
    void testRateLimit() {
        sendAndGetLastJson("{\"t\":\"hello\",\"v\":1,\"server\":\"Test\"}");
        int sentInitial = sentPayloads.size();

        // Send 7 more messages (total 8 messages in the 1-second window)
        for (int i = 1; i <= 7; i++) {
            LadsApiClient.receive(("{\"t\":\"policy\",\"rev\":" + i + ",\"unlisted\":\"inherit\"}").getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(sentInitial + 7, sentPayloads.size());

        // 9th message in same second must be dropped!
        LadsApiClient.receive("{\"t\":\"policy\",\"rev\":99,\"unlisted\":\"inherit\"}".getBytes(StandardCharsets.UTF_8));
        assertEquals(sentInitial + 7, sentPayloads.size(), "9th message in same second must be rate-limited and dropped");
    }

    @Test
    void testMaxTwoHelloReplies() {
        // 1st hello -> replies
        JsonObject r1 = sendAndGetLastJson("{\"t\":\"hello\",\"v\":1,\"server\":\"Server1\"}");
        assertNotNull(r1);

        // 2nd hello -> replies
        JsonObject r2 = sendAndGetLastJson("{\"t\":\"hello\",\"v\":1,\"server\":\"Server2\"}");
        assertNotNull(r2);

        // 3rd hello in same connection -> no reply!
        int countBefore = sentPayloads.size();
        LadsApiClient.receive("{\"t\":\"hello\",\"v\":1,\"server\":\"Server3\"}".getBytes(StandardCharsets.UTF_8));
        assertEquals(countBefore, sentPayloads.size(), "3rd hello must not trigger reply");
    }

    @Test
    void testNoSendBeforeServerHello() {
        // Send policy without prior hello
        LadsApiClient.receive("{\"t\":\"policy\",\"rev\":1,\"unlisted\":\"inherit\"}".getBytes(StandardCharsets.UTF_8));
        assertEquals(0, sentPayloads.size(), "Client must send nothing before server hello");
        assertNull(ServerPolicy.active());
    }

    @Test
    void testPresenceParsing() {
        sendAndGetLastJson("{\"t\":\"hello\",\"v\":1,\"server\":\"Test\"}");

        // Full presence with dashed and 32-hex UUIDs
        String dashed = "069a79f4-44e9-4726-a5be-fca90e38aaf5";
        String hex = "069a79f444e94726a5befca90e38aaf5";
        UUID u1 = UUID.fromString(dashed);
        UUID u2 = LadsApiClient.parseUuid(hex);
        assertEquals(u1, u2);

        LadsApiClient.receive(("{\"t\":\"presence\",\"full\":true,\"add\":[\"" + dashed + "\"]}").getBytes(StandardCharsets.UTF_8));
        assertTrue(LadsPresence.isLads(u1));

        // Remove presence
        LadsApiClient.receive(("{\"t\":\"presence\",\"full\":false,\"remove\":[\"" + dashed + "\"]}").getBytes(StandardCharsets.UTF_8));
        assertFalse(LadsPresence.isLads(u1));
    }

    @Test
    void testFullbrightServerLimited() {
        assertFalse(FullbrightModule.serverLimited());

        sendAndGetLastJson("{\"t\":\"hello\",\"v\":1,\"server\":\"Test\"}");
        // Policy with Brightness max rule
        sendAndGetLastJson("{\"t\":\"policy\",\"rev\":1,\"options\":{\"Fullbright\":{\"Brightness\":{\"max\":10}}}}");
        assertTrue(FullbrightModule.serverLimited(), "serverLimited must be true when Fullbright has option rules");

        // Policy clearing
        sendAndGetLastJson("{\"t\":\"reset\"}");
        assertFalse(FullbrightModule.serverLimited(), "serverLimited must be false after reset");
    }

    @Test
    void testHypixelSafetyManagerIntegration() {
        sendAndGetLastJson("{\"t\":\"hello\",\"v\":1,\"server\":\"Lads SMP\"}");

        // Policy blocking Fullbright and allowing Minimap
        sendAndGetLastJson("{\"t\":\"policy\",\"rev\":1,\"modules\":{\"Fullbright\":\"block\",\"Minimap\":\"allow\"}}");

        assertEquals(HypixelSafetyManager.Block.SERVER, HypixelSafetyManager.blockOf("Fullbright"));
        assertTrue(HypixelSafetyManager.isDisallowed("Fullbright"));
        assertEquals("Fullbright is blocked by Lads SMP.", HypixelSafetyManager.getBlockedNotice("Fullbright"));

        // Minimap is allowed by server -> returns Block.NONE
        assertEquals(HypixelSafetyManager.Block.NONE, HypixelSafetyManager.blockOf("Minimap"));
    }

    @Test
    void testDiskLeakGuard() {
        // Player's own config
        Module fb = ModuleManager.getInstance().getModule("Fullbright");
        fb.setEnabled(true);
        SliderOption bright = (SliderOption) fb.getOption("Brightness");
        bright.setValue(100);

        // Apply a server policy that forces Fullbright off and Brightness to 10
        sendAndGetLastJson("{\"t\":\"hello\",\"v\":1,\"server\":\"Lads SMP\"}");
        sendAndGetLastJson("{\"t\":\"policy\",\"rev\":1,\"modules\":{\"Fullbright\":\"block\"},\"options\":{\"Fullbright\":{\"Brightness\":{\"value\":10}}}}");

        // While policy is active:
        assertFalse(fb.isEnabled(), "Fullbright must report blocked while policy active");
        assertEquals(10.0, bright.getValue(), "Brightness must report forced value while policy active");

        // Raw getters must preserve player's own values:
        assertTrue(fb.isEnabledSetting(), "isEnabledSetting must return player's own value");
        assertEquals(100.0, bright.getOwnValue(), "getOwnValue must return player's own value");

        // ConfigManager.toJson() must serialize only player's own values
        JsonObject json = ConfigManager.toJson();
        JsonObject modules = json.getAsJsonObject("modules");
        JsonObject fbJson = modules.getAsJsonObject("Fullbright");
        assertTrue(fbJson.get("enabled").getAsBoolean(), "ConfigManager must serialize true for Fullbright");
        JsonObject opts = fbJson.getAsJsonObject("options");
        assertEquals(100.0, opts.get("Brightness").getAsDouble(), "ConfigManager must serialize 100 for Brightness");

        // Test IntTextOption.save() writes raw value
        IntTextOption intOpt = new IntTextOption("TestInt", 42, 0, 100);
        intOpt.setValue("77");
        assertEquals(77, intOpt.save().getAsInt());
    }
}
