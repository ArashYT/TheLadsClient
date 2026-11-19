package com.thelads.ladsapi.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thelads.ladsapi.config.LadsConfig;
import com.thelads.ladsapi.rules.LadsRules;
import com.thelads.ladsapi.session.SessionManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** proxy-rules-servers: the proxy runs hello/policy only for the listed backends; others keep their own plugin. */
public class ProxyRulesServersTest {
    private static final String HELLO = "{\"t\":\"hello\",\"v\":1,\"lads\":\"1.13.28\",\"mc\":\"26.3\"}";

    @Test
    public void listedBackendOnlyGetsProxyPolicyAndIsDroppedOnLeave(@TempDir Path tmp) {
        FakePlatform platform = new FakePlatform("velocity", true, tmp);
        LadsConfig config = new LadsConfig();
        config.applyMap(Map.of("proxy-rules-servers", List.of("limbo")));
        assertFalse(config.isProxyRules());
        assertTrue(config.isProxyActive());
        assertTrue(config.proxyRulesFor("LIMBO"));
        assertFalse(config.proxyRulesFor("Lobby"));

        LadsRules rules = new LadsRules();
        rules.parseYaml(new Yaml().load("worlds:\n  limbo:\n    Minecraft:\n      maxFps: { value: 60, lock: true }\n"));
        SessionManager manager = new SessionManager(platform, config, rules);

        UUID id = UUID.randomUUID();
        FakePlayerHandle p = new FakePlayerHandle(id, "P", "Lobby");
        platform.addPlayer(p);

        // First connect to a backend the proxy does not rule: nothing is sent, nothing is kept.
        manager.proxyServerChanged(p);
        platform.advanceTicks(2);
        assertTrue(p.getSentPayloads().isEmpty());
        manager.receive(p, HELLO.getBytes(StandardCharsets.UTF_8));
        assertFalse(manager.player(id).isPresent());

        // Switch to limbo: hello goes out, the client answers, the policy carries the limbo rules.
        p.setWorld("limbo");
        manager.proxyServerChanged(p);
        platform.advanceTicks(2);
        assertEquals("hello", type(p.getSentStrings().get(0)));
        manager.receive(p, HELLO.getBytes(StandardCharsets.UTF_8));
        assertTrue(manager.player(id).isPresent());
        JsonObject policy = p.getSentStrings().stream().map(s -> JsonParser.parseString(s).getAsJsonObject())
                .filter(o -> "policy".equals(o.get("t").getAsString())).findFirst().orElseThrow();
        assertEquals(60, policy.getAsJsonObject("options").getAsJsonObject("Minecraft")
                .getAsJsonObject("maxFps").get("value").getAsInt());

        // Back to Lobby: the proxy session is gone, so the Lobby plugin's own policy takes over.
        p.setWorld("Lobby");
        manager.proxyServerChanged(p);
        assertFalse(manager.player(id).isPresent());
        int sent = p.getSentPayloads().size();
        manager.receive(p, HELLO.getBytes(StandardCharsets.UTF_8));
        platform.advanceTicks(70);
        assertEquals(sent, p.getSentPayloads().size());
    }

    @Test
    public void unlistedProxyStaysOff(@TempDir Path tmp) {
        FakePlatform platform = new FakePlatform("velocity", true, tmp);
        LadsConfig config = new LadsConfig();
        SessionManager manager = new SessionManager(platform, config, new LadsRules());
        FakePlayerHandle p = new FakePlayerHandle(UUID.randomUUID(), "P", "limbo");
        platform.addPlayer(p);
        manager.proxyServerChanged(p);
        platform.advanceTicks(2);
        assertTrue(p.getSentPayloads().isEmpty());
    }

    private static String type(String json) {
        return JsonParser.parseString(json).getAsJsonObject().get("t").getAsString();
    }
}
