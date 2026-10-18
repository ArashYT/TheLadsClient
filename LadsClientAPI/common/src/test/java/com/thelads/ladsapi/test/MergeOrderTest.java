package com.thelads.ladsapi.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thelads.ladsapi.config.LadsConfig;
import com.thelads.ladsapi.policy.PolicyBuilder;
import com.thelads.ladsapi.rules.LadsRules;
import com.thelads.ladsapi.rules.ModuleRule;
import com.thelads.ladsapi.rules.RuleOption;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class MergeOrderTest {

    @Test
    public void testMergeOrderAndBypass() {
        LadsConfig config = new LadsConfig();
        LadsRules rules = new LadsRules();

        // 1. Base rule: Fullbright allow, Brightness max=100
        ModuleRule baseFullbright = new ModuleRule("allow");
        baseFullbright.setOption("Brightness", new RuleOption(null, null, 100.0, null, null));
        rules.getModules().put("Fullbright", baseFullbright);

        // 2. World pvp_arena: Fullbright block
        ModuleRule worldFullbright = new ModuleRule("block");
        rules.getWorlds().put("pvp_arena", Map.of("Fullbright", worldFullbright));

        // 3. Group staff: Fullbright allow, Brightness max=50
        ModuleRule staffFullbright = new ModuleRule("allow");
        staffFullbright.setOption("Brightness", new RuleOption(null, null, 50.0, null, null));
        rules.getGroups().put("staff", Map.of("Fullbright", staffFullbright));

        // Test A: Normal player in lobby (not pvp_arena, no group)
        FakePlayerHandle lobbyPlayer = new FakePlayerHandle(UUID.randomUUID(), "LobbyPlayer", "lobby");
        String jsonA = PolicyBuilder.buildPolicyJson(rules, config, lobbyPlayer, null, 1);
        JsonObject objA = JsonParser.parseString(jsonA).getAsJsonObject();
        assertEquals("allow", objA.getAsJsonObject("modules").get("Fullbright").getAsString());
        assertEquals(100, objA.getAsJsonObject("options").getAsJsonObject("Fullbright").getAsJsonObject("Brightness").get("max").getAsInt());

        // Test B: Normal player in pvp_arena -> world overrides base
        FakePlayerHandle arenaPlayer = new FakePlayerHandle(UUID.randomUUID(), "ArenaPlayer", "pvp_arena");
        String jsonB = PolicyBuilder.buildPolicyJson(rules, config, arenaPlayer, null, 1);
        JsonObject objB = JsonParser.parseString(jsonB).getAsJsonObject();
        assertEquals("block", objB.getAsJsonObject("modules").get("Fullbright").getAsString());

        // Test C: Staff player in pvp_arena -> group overrides world
        FakePlayerHandle staffPlayer = new FakePlayerHandle(UUID.randomUUID(), "StaffPlayer", "pvp_arena");
        staffPlayer.addPermission("ladsapi.group.staff");
        String jsonC = PolicyBuilder.buildPolicyJson(rules, config, staffPlayer, null, 1);
        JsonObject objC = JsonParser.parseString(jsonC).getAsJsonObject();
        assertEquals("allow", objC.getAsJsonObject("modules").get("Fullbright").getAsString());
        assertEquals(50, objC.getAsJsonObject("options").getAsJsonObject("Fullbright").getAsJsonObject("Brightness").get("max").getAsInt());

        // Test D: Staff player with API override -> API override wins (later wins)
        Map<String, ModuleRule> apiOverrides = new LinkedHashMap<>();
        ModuleRule apiRule = new ModuleRule();
        apiRule.setOption("Brightness", new RuleOption(null, null, 20.0, null, null));
        apiOverrides.put("Fullbright", apiRule);

        String jsonD = PolicyBuilder.buildPolicyJson(rules, config, staffPlayer, apiOverrides, 1);
        JsonObject objD = JsonParser.parseString(jsonD).getAsJsonObject();
        assertEquals("allow", objD.getAsJsonObject("modules").get("Fullbright").getAsString());
        assertEquals(20, objD.getAsJsonObject("options").getAsJsonObject("Fullbright").getAsJsonObject("Brightness").get("max").getAsInt());

        // Test E: Bypass permission -> empty policy
        staffPlayer.addPermission("ladsapi.bypass");
        String jsonE = PolicyBuilder.buildPolicyJson(rules, config, staffPlayer, apiOverrides, 1);
        JsonObject objE = JsonParser.parseString(jsonE).getAsJsonObject();
        assertEquals("inherit", objE.get("unlisted").getAsString());
        assertEquals(0, objE.getAsJsonObject("modules").keySet().size());
        assertEquals(0, objE.getAsJsonObject("options").keySet().size());
    }
}
