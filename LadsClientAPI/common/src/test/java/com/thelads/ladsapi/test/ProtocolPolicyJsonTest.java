package com.thelads.ladsapi.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.thelads.ladsapi.config.LadsConfig;
import com.thelads.ladsapi.policy.PolicyBuilder;
import com.thelads.ladsapi.rules.LadsRules;
import com.thelads.ladsapi.rules.ModuleRule;
import com.thelads.ladsapi.rules.RuleOption;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ProtocolPolicyJsonTest {

    @Test
    public void testPolicyJsonEqualsProtocolExample() {
        String expectedJson = """
                {"t":"policy","rev":7,"unlisted":"inherit",
                 "modules":{"Fullbright":"allow","XaeroMinimap":"block","Crosshair":"on"},
                 "options":{
                   "Fullbright":{"Brightness":{"max":10}},
                   "Crosshair":{"Style":{"value":"Cross","lock":true},"Color":{"value":"#FF0000"}},
                   "1.7 Animations":{"1.7 sword block pose":{"value":false}}
                 },
                 "notice":"Lads SMP: fullbright capped at 10%"}
                """;

        LadsConfig config = new LadsConfig();
        config.setUnlistedModules("inherit");
        config.setNotice("Lads SMP: fullbright capped at 10%");

        LadsRules rules = new LadsRules();

        // Fullbright: allow, Brightness: max=10
        ModuleRule fullbright = new ModuleRule("allow");
        fullbright.setOption("Brightness", new RuleOption(null, null, 10.0, null, null));
        rules.getModules().put("Fullbright", fullbright);

        // XaeroMinimap: block
        ModuleRule xaero = new ModuleRule("block");
        rules.getModules().put("XaeroMinimap", xaero);

        // Crosshair: on, Style: value="Cross", lock=true, Color: value="#FF0000"
        ModuleRule crosshair = new ModuleRule("on");
        crosshair.setOption("Style", new RuleOption("Cross", null, null, null, true));
        crosshair.setOption("Color", new RuleOption("#FF0000", null, null, null, null));
        rules.getModules().put("Crosshair", crosshair);

        // 1.7 Animations: 1.7 sword block pose: value=false
        ModuleRule anim = new ModuleRule(null);
        anim.setOption("1.7 sword block pose", new RuleOption(false, null, null, null, null));
        rules.getModules().put("1.7 Animations", anim);

        FakePlayerHandle player = new FakePlayerHandle(UUID.randomUUID(), "TestPlayer", "world");

        String actualJson = PolicyBuilder.buildPolicyJson(rules, config, player, null, 7);

        JsonElement expectedElem = JsonParser.parseString(expectedJson);
        JsonElement actualElem = JsonParser.parseString(actualJson);

        assertEquals(expectedElem, actualElem, "Generated policy JSON must equal the PROTOCOL.md example");
    }
}
